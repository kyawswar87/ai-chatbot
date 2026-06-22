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
public class VectorStoreController {

	private final VectorStore vectorStore;
	private final IngestionService ingestionService;

	/**
	 * Ingest a single ad-hoc document. The shared pipeline splits it into chunks,
	 * embeds each via Ollama, and stores the vectors in pgvector. Short text
	 * yields a single chunk; long text yields several.
	 */
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
	@GetMapping("/search")
	public List<SearchResult> search(
			@RequestParam String query,
			@RequestParam(defaultValue = "4") int topK) {
		List<Document> results = vectorStore.similaritySearch(
				SearchRequest.builder().query(query).topK(topK).build());
		return results.stream()
				.map(doc -> new SearchResult(doc.getId(), doc.getText(), doc.getMetadata(), doc.getScore()))
				.toList();
	}

	public record IngestRequest(String content, Map<String, Object> metadata) {
	}

	public record IngestResponse(List<String> ids, int chunks) {
	}

	public record SearchResult(String id, String content, Map<String, Object> metadata, Double score) {
	}
}
