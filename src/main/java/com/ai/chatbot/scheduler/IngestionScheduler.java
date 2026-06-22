package com.ai.chatbot.scheduler;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.ai.chatbot.config.IngestionScheduleProperties;
import com.ai.chatbot.ingestion.DocumentSource;
import com.ai.chatbot.ingestion.IngestionResult;
import com.ai.chatbot.ingestion.IngestionService;
import com.ai.chatbot.ingestion.SourceRequest;

/**
 * Scheduled auto-sync. Only registered when {@code ingestion.schedule.enabled=true};
 * re-ingests the configured URLs on {@code ingestion.schedule.cron}. Re-runs are
 * idempotent (existing chunks for each source_uri are replaced, not duplicated).
 */
@Component
@ConditionalOnProperty(prefix = "ingestion.schedule", name = "enabled", havingValue = "true")
public class IngestionScheduler {

	private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

	private final IngestionScheduleProperties properties;
	private final DocumentSource urlSource;
	private final IngestionService ingestionService;

	public IngestionScheduler(IngestionScheduleProperties properties,
			List<DocumentSource> sources, IngestionService ingestionService) {
		this.properties = properties;
		this.ingestionService = ingestionService;
		this.urlSource = sources.stream().filter(s -> s.type().equals("url")).findFirst().orElseThrow();
	}

	@Scheduled(cron = "${ingestion.schedule.cron}")
	public void resyncUrls() {
		List<String> urls = properties.urls() == null ? List.of() : properties.urls();
		for (String url : urls) {
			try {
				List<Document> documents = urlSource.fetch(SourceRequest.of("url", url));
				IngestionResult result = ingestionService.ingest("url", documents);
				log.info("Scheduled re-sync of {} → {} chunks", url, result.chunksWritten());
			}
			catch (RuntimeException ex) {
				log.warn("Scheduled re-sync failed for {}: {}", url, ex.getMessage());
			}
		}
	}
}
