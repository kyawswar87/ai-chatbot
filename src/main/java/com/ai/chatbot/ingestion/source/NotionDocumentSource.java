package com.ai.chatbot.ingestion.source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ai.chatbot.config.NotionProperties;
import com.ai.chatbot.ingestion.DocumentSource;
import com.ai.chatbot.ingestion.IngestionService;
import com.ai.chatbot.ingestion.SourceRequest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Scaffold connector: reads Notion pages via the public API and flattens each
 * page's blocks into plain text. Wired to the {@link DocumentSource} contract;
 * requires {@code ingestion.notion.token} to run. Block coverage is intentionally
 * basic (rich-text-bearing blocks); extend per your workspace before production.
 *
 * <p>Request params (optional, override config): {@code "pageIds"} (string or list).
 */
@Component
public class NotionDocumentSource implements DocumentSource {

	private final NotionProperties properties;
	private final RestClient client;

	public NotionDocumentSource(NotionProperties properties) {
		this.properties = properties;
		this.client = RestClient.builder().baseUrl(properties.baseUrl()).build();
	}

	@Override
	public String type() {
		return "notion";
	}

	@Override
	public List<Document> fetch(SourceRequest request) {
		if (!properties.isConfigured()) {
			throw new IllegalStateException("Notion is not configured. Set ingestion.notion.token.");
		}
		List<String> pageIds = request.getStringList("pageIds");
		if (pageIds.isEmpty()) {
			pageIds = properties.pageIds() == null ? List.of() : properties.pageIds();
		}

		List<Document> documents = new ArrayList<>();
		for (String pageId : pageIds) {
			String text = flattenBlocks(pageId);
			if (text.isBlank()) {
				continue;
			}
			Map<String, Object> metadata = new HashMap<>();
			metadata.put(IngestionService.SOURCE_URI, pageId);
			metadata.put(IngestionService.TITLE, fetchTitle(pageId));
			documents.add(new Document(text, metadata));
		}
		return documents;
	}

	/** Concatenate plain_text from every rich_text run in the page's child blocks. */
	private String flattenBlocks(String pageId) {
		JsonNode response = get("/v1/blocks/" + pageId + "/children?page_size=100");
		StringBuilder sb = new StringBuilder();
		JsonNode results = response.path("results");
		for (JsonNode block : results) {
			String blockType = block.path("type").asText("");
			JsonNode body = block.path(blockType);
			for (JsonNode run : body.path("rich_text")) {
				String plain = run.path("plain_text").asText("");
				if (!plain.isBlank()) {
					sb.append(plain);
				}
			}
			sb.append('\n');
		}
		return sb.toString().strip();
	}

	private String fetchTitle(String pageId) {
		try {
			JsonNode page = get("/v1/pages/" + pageId);
			JsonNode properties = page.path("properties");
			for (JsonNode property : properties) {
				if ("title".equals(property.path("type").asText())) {
					JsonNode titleArray = property.path("title");
					if (titleArray.isArray() && titleArray.size() > 0) {
						return titleArray.get(0).path("plain_text").asText(pageId);
					}
				}
			}
		}
		catch (RuntimeException ignored) {
			// fall through to id as title
		}
		return pageId;
	}

	private JsonNode get(String path) {
		return client.get()
				.uri(path)
				.header("Authorization", "Bearer " + properties.token())
				.header("Notion-Version", properties.version())
				.retrieve()
				.body(JsonNode.class);
	}
}
