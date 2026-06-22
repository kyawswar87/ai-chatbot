package com.ai.chatbot.ingestion;

import java.util.List;

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
		String sourceType,
		List<String> sourceUris,
		int documentsFetched,
		int chunksWritten,
		List<String> chunkIds) {
}
