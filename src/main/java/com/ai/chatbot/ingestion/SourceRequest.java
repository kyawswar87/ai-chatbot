package com.ai.chatbot.ingestion;

import java.util.List;
import java.util.Map;

/**
 * Loosely-typed parameter carrier for an ingestion request, so the same
 * controller path works for every {@link DocumentSource}. For example the URL
 * source reads {@code "url"}; the Notion source reads {@code "pageIds"}.
 */
public record SourceRequest(Map<String, Object> params) {

	public SourceRequest {
		params = params == null ? Map.of() : params;
	}

	public static SourceRequest of(String key, Object value) {
		return new SourceRequest(Map.of(key, value));
	}

	public String requireString(String key) {
		Object v = params.get(key);
		if (v == null || v.toString().isBlank()) {
			throw new IllegalArgumentException("Missing required parameter: " + key);
		}
		return v.toString();
	}

	public String getString(String key, String defaultValue) {
		Object v = params.get(key);
		return (v == null || v.toString().isBlank()) ? defaultValue : v.toString();
	}

	public List<String> getStringList(String key) {
		Object v = params.get(key);
		if (v instanceof List<?> list) {
			return list.stream().map(String::valueOf).toList();
		}
		return (v == null) ? List.of() : List.of(v.toString());
	}
}
