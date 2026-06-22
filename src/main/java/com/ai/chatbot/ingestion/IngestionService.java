package com.ai.chatbot.ingestion;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

/**
 * The single shared ingestion pipeline. Every path — the manual
 * {@code POST /api/vectors} endpoint and all {@link DocumentSource} connectors —
 * funnels through here so chunking, metadata, and de-duplication behave
 * identically.
 *
 * <p>Steps: stamp common metadata → idempotent delete of prior chunks for each
 * {@code source_uri} → split via {@link TokenTextSplitter} → write to the
 * {@link VectorStore} (which embeds each chunk via Ollama).
 */
@Service
public class IngestionService {

	/** Connector discriminator, e.g. "url" / "sftp" / "notion" / "manual". */
	public static final String SOURCE_TYPE = "source_type";
	/** Unique identifier of the origin (URL, SFTP path, Notion page id) — the de-dup key. */
	public static final String SOURCE_URI = "source_uri";
	/** Human-readable title, used for citations. */
	public static final String TITLE = "title";
	/** ISO-8601 ingestion timestamp. */
	public static final String INGESTED_AT = "ingested_at";

	private final VectorStore vectorStore;
	// TokenTextSplitter is stateless and thread-safe; share one instance.
	private final TokenTextSplitter splitter = TokenTextSplitter.builder().build();

	public IngestionService(VectorStore vectorStore) {
		this.vectorStore = vectorStore;
	}

	/**
	 * Ingest raw documents under the given source type. Documents that carry a
	 * {@code source_uri} have their previous chunks removed first, so re-running
	 * an ingestion is idempotent (no duplicates).
	 */
	public IngestionResult ingest(String sourceType, List<Document> rawDocuments) {
		if (rawDocuments.isEmpty()) {
			return new IngestionResult(sourceType, List.of(), 0, 0, List.of());
		}

		String ingestedAt = Instant.now().toString();

		// Rebuild documents with a fresh, mutable metadata map (reader-provided
		// maps may be immutable) and stamp common fields.
		List<Document> stamped = rawDocuments.stream().map(doc -> {
			Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
			metadata.put(SOURCE_TYPE, sourceType);
			metadata.putIfAbsent(INGESTED_AT, ingestedAt);
			return new Document(doc.getText(), metadata);
		}).toList();

		// Idempotent re-sync: drop existing chunks for every distinct source_uri.
		Set<String> sourceUris = stamped.stream()
				.map(d -> d.getMetadata().get(SOURCE_URI))
				.filter(Objects::nonNull)
				.map(Object::toString)
				.collect(Collectors.toCollection(java.util.LinkedHashSet::new));
		for (String uri : sourceUris) {
			var expression = new FilterExpressionBuilder().eq(SOURCE_URI, uri).build();
			vectorStore.delete(expression);
		}

		// Chunk (preserves metadata, adds parent_document_id/chunk_index) then embed + store.
		List<Document> chunks = splitter.apply(stamped);
		vectorStore.add(chunks);

		List<String> chunkIds = chunks.stream().map(Document::getId).toList();
		return new IngestionResult(sourceType, List.copyOf(sourceUris), stamped.size(), chunks.size(), chunkIds);
	}
}
