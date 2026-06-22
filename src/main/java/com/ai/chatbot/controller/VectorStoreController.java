package com.ai.chatbot.controller;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ai.chatbot.ingestion.IngestionResult;
import com.ai.chatbot.ingestion.IngestionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Demo endpoints for ingesting ad-hoc documents into the pgvector store and
 * running similarity search over them. Ingestion delegates to the shared
 * {@link IngestionService} so chunking/metadata behave identically to the
 * source connectors.
 */
@RestController
@RequestMapping("/api/vectors")
@RequiredArgsConstructor
@Tag(name = "Vector Store", description = "Ad-hoc text ingestion and raw similarity search over the pgvector store")
public class VectorStoreController {

	private final VectorStore vectorStore;
	private final IngestionService ingestionService;

	/**
	 * Ingest a single ad-hoc document. The shared pipeline splits it into chunks,
	 * embeds each via Ollama, and stores the vectors in pgvector. Short text
	 * yields a single chunk; long text yields several.
	 */
	@Operation(summary = "Ingest an ad-hoc text snippet",
			description = "Stores a raw text snippet directly. The shared pipeline auto-chunks it, embeds each "
					+ "chunk via Ollama, and writes the vectors to pgvector.")
	@PostMapping
	public IngestResponse add(@RequestBody IngestRequest request) {
		Map<String, Object> metadata = request.metadata() != null ? request.metadata() : Map.of();
		Document document = new Document(request.content(), metadata);
		IngestionResult result = ingestionService.ingest("manual", List.of(document));
		return new IngestResponse(result.chunkIds(), result.chunksWritten());
	}

	/**
	 * Run a similarity search and return the closest matching documents.
	 */
	@Operation(summary = "Similarity search",
			description = "Runs a raw similarity search over the vector store and returns the closest matching "
					+ "documents with their scores.")
	@GetMapping("/search")
	public List<SearchResult> search(
			@Parameter(description = "Free-text query to embed and match against the store",
					example = "vector search")
			@RequestParam String query,
			@Parameter(description = "Maximum number of matching documents to return", example = "3")
			@RequestParam(defaultValue = "4") int topK) {
		List<Document> results = vectorStore.similaritySearch(
				SearchRequest.builder().query(query).topK(topK).build());
		return results.stream()
				.map(doc -> new SearchResult(doc.getId(), doc.getText(), doc.getMetadata(), doc.getScore()))
				.toList();
	}

	public record IngestRequest(
			@Schema(description = "Raw text to store; auto-chunked by the pipeline",
					example = "Spring AI makes vector search easy",
					requiredMode = Schema.RequiredMode.REQUIRED)
			String content,
			@Schema(description = "Optional metadata stamped onto every chunk",
					example = "{\"source\":\"demo\"}")
			Map<String, Object> metadata) {
	}

	public record IngestResponse(
			@Schema(description = "IDs of the stored chunks") List<String> ids,
			@Schema(description = "Number of chunks written", example = "1") int chunks) {
	}

	public record SearchResult(
			@Schema(description = "Chunk ID") String id,
			@Schema(description = "Chunk text") String content,
			@Schema(description = "Chunk metadata") Map<String, Object> metadata,
			@Schema(description = "Similarity score (higher is closer)") Double score) {
	}
}
