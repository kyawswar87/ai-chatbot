package com.ai.chatbot.controller;

import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ai.chatbot.ingestion.DocumentSource;
import com.ai.chatbot.ingestion.IngestionResult;
import com.ai.chatbot.ingestion.IngestionService;
import com.ai.chatbot.ingestion.SourceRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Manual ingestion triggers. {@code POST /api/ingest/{source}} runs the named
 * {@link DocumentSource} (e.g. {@code url}, {@code sftp}, {@code notion}) with
 * the JSON body as parameters, then funnels the documents through the shared
 * {@link IngestionService}. Spring injects every {@code DocumentSource} bean
 * into a map keyed by its {@link DocumentSource#type()}.
 */
@RestController
@RequestMapping("/api/ingest")
@Tag(name = "Ingestion", description = "Fetch documents from a source (url, sftp, notion) and index them into pgvector")
public class IngestionController {

	private final Map<String, DocumentSource> sources;
	private final IngestionService ingestionService;

	public IngestionController(List<DocumentSource> sources, IngestionService ingestionService) {
		this.sources = sources.stream().collect(java.util.stream.Collectors.toMap(DocumentSource::type, s -> s));
		this.ingestionService = ingestionService;
	}

	@Operation(summary = "Ingest documents from a source",
			description = "Runs the named DocumentSource (`url`, `sftp`, or `notion`) with the JSON body as "
					+ "source-specific params, then chunks, embeds, and stores the documents. Re-ingesting the "
					+ "same source_uri replaces its chunks (idempotent).")
	@PostMapping("/{source}")
	public IngestionResult ingest(
			@Parameter(description = "Document source to run", example = "url",
					schema = @Schema(allowableValues = {"url", "sftp", "notion"}))
			@PathVariable String source,
			@RequestBody(required = false)
			@Schema(description = "Source-specific parameters", example = "{\"url\":\"https://en.wikipedia.org/wiki/Retrieval-augmented_generation\"}")
			Map<String, Object> params) {
		DocumentSource documentSource = sources.get(source);
		if (documentSource == null) {
			throw new IllegalArgumentException("Unknown source '" + source + "'. Available: " + sources.keySet());
		}
		List<Document> documents = documentSource.fetch(new SourceRequest(params));
		return ingestionService.ingest(source, documents);
	}
}
