package com.ai.chatbot.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Scheduled auto-sync settings. Bind from {@code ingestion.schedule.*}.
 * Disabled by default; when enabled, the configured URLs are re-ingested on the
 * given cron (idempotent thanks to source_uri de-dup).
 */
@ConfigurationProperties(prefix = "ingestion.schedule")
public record IngestionScheduleProperties(
		@DefaultValue("false") boolean enabled,
		@DefaultValue("0 0 * * * *") String cron,
		List<String> urls) {
}
