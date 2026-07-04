package com.ai.chatbot.guard;

/**
 * Thrown by {@link ChatGuardAspect} when Llama Guard 3 flags a chat message as a
 * jailbreak / system-override attempt and blocking is enabled. Mapped to HTTP 403
 * by {@code com.ai.chatbot.controller.ApiExceptionHandler}.
 */
public class GuardViolationException extends RuntimeException {

	public GuardViolationException(String message) {
		super(message);
	}
}
