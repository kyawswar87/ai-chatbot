package com.ai.chatbot.ingestion;

import java.util.List;

import org.springframework.ai.document.Document;

/**
 * A pluggable knowledge source. Each connector (web URL, SFTP files, Notion,
 * ...) implements this contract to produce raw Spring AI {@link Document}s.
 * Implementations are responsible for setting per-document
 * {@code source_uri} and {@code title} metadata (see {@link IngestionService}
 * metadata keys); the shared {@link IngestionService} then stamps the common
 * metadata, de-duplicates, chunks, embeds, and stores them.
 */
public interface DocumentSource {

	/** Stable discriminator, e.g. {@code "url"}, {@code "sftp"}, {@code "notion"}. */
	String type();

	/** Fetch raw documents for the given request. */
	List<Document> fetch(SourceRequest request);
}
