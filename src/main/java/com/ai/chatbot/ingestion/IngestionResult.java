package com.ai.chatbot.ingestion;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Outcome of an ingestion run.
 *
 * @param sourceType       the {@link DocumentSource#type()} that produced the documents
 * @param sourceUris       distinct {@code source_uri} values that were (re)ingested
 * @param documentsFetched number of raw documents fetched before chunking
 * @param chunksWritten    number of chunks embedded and written to the vector store
 * @param chunkIds         ids of the written chunks
 */
public record IngestionResult(
		@Schema(description = "The source type that produced the documents", example = "url")
		String sourceType,
		@Schema(description = "Distinct source_uri values that were (re)ingested")
		List<String> sourceUris,
		@Schema(description = "Number of raw documents fetched before chunking", example = "1")
		int documentsFetched,
		@Schema(description = "Number of chunks embedded and written to the vector store", example = "7")
		int chunksWritten,
		@Schema(description = "IDs of the written chunks")
		List<String> chunkIds) {
}
