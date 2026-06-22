package com.ai.chatbot.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Notion API settings for {@code com.ai.chatbot.ingestion.source.NotionDocumentSource}.
 * Bind from {@code ingestion.notion.*}; supply the integration token via an
 * environment variable.
 */
@ConfigurationProperties(prefix = "ingestion.notion")
public record NotionProperties(
		String token,
		@DefaultValue("https://api.notion.com") String baseUrl,
		@DefaultValue("2022-06-28") String version,
		/** Default page ids to sync when a request omits them. */
		List<String> pageIds) {

	public boolean isConfigured() {
		return token != null && !token.isBlank();
	}
}
