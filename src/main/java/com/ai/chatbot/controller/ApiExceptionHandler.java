package com.ai.chatbot.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.ai.chatbot.guard.GuardViolationException;

/**
 * Translates common ingestion/API errors into meaningful HTTP status codes
 * instead of a blanket 500:
 * <ul>
 *   <li>{@link IllegalArgumentException} → 400 (bad request: unknown source, missing parameter)</li>
 *   <li>{@link IllegalStateException} → 503 (a source is not configured / unavailable)</li>
 *   <li>{@link GuardViolationException} → 403 (message flagged as a jailbreak / override attempt)</li>
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
		return body(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(GuardViolationException.class)
	public ResponseEntity<Map<String, Object>> guardBlocked(GuardViolationException ex) {
		return body(HttpStatus.FORBIDDEN, ex.getMessage());
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<Map<String, Object>> notConfigured(IllegalStateException ex) {
		return body(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
	}

	private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of(
				"timestamp", Instant.now().toString(),
				"status", status.value(),
				"error", status.getReasonPhrase(),
				"message", message == null ? "" : message));
	}
}
