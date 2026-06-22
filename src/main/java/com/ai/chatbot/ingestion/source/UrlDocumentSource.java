package com.ai.chatbot.ingestion.source;

import java.net.URI;
import java.util.List;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.jsoup.JsoupDocumentReader;
import org.springframework.ai.reader.jsoup.config.JsoupDocumentReaderConfig;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.ai.chatbot.ingestion.DocumentSource;
import com.ai.chatbot.ingestion.IngestionService;
import com.ai.chatbot.ingestion.SourceRequest;

/**
 * Reference connector: fetches a single web page and extracts its readable text
 * with Jsoup. Request param: {@code "url"}. No credentials required.
 *
 * <p>The page is fetched with a browser-like {@code User-Agent} (many sites,
 * e.g. Wikipedia, return 403 to the default Java fetcher), then handed to Jsoup
 * as raw bytes for parsing.
 */
@Component
public class UrlDocumentSource implements DocumentSource {

	private static final String USER_AGENT =
			"Mozilla/5.0 (compatible; ai-chatbot-ingest/1.0; +https://github.com/ai-chatbot)";

	private final RestClient client = RestClient.create();

	@Override
	public String type() {
		return "url";
	}

	@Override
	public List<Document> fetch(SourceRequest request) {
		String url = request.requireString("url");
		byte[] html = client.get()
				.uri(URI.create(url))
				.header("User-Agent", USER_AGENT)
				.header("Accept", "text/html,application/xhtml+xml")
				.retrieve()
				.body(byte[].class);

		// "title" is captured from the page's <title> tag; we also tag the URL as
		// the source_uri so re-ingesting the same page replaces its chunks.
		JsoupDocumentReaderConfig config = JsoupDocumentReaderConfig.builder()
				.selector("body")
				.includeLinkUrls(false)
				.metadataTag("title")
				.additionalMetadata(IngestionService.SOURCE_URI, url)
				.build();

		return new JsoupDocumentReader(new ByteArrayResource(html != null ? html : new byte[0]), config).read();
	}
}
