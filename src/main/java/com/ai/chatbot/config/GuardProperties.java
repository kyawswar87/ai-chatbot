package com.ai.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the Llama Guard 3 jailbreak/system-override guard applied to every
 * chat request by {@code com.ai.chatbot.guard.ChatGuardAspect}. Bind from
 * {@code chatbot.guard.*}.
 *
 * @param enabled       whether the guard runs at all; when {@code false} every
 *                      request passes straight through to the RAG model.
 * @param model         Ollama model used to classify the message (must be pulled
 *                      into the local Ollama install, e.g. {@code ollama pull llama-guard3}).
 * @param blockOnUnsafe when {@code true} a flagged message is rejected (HTTP 403);
 *                      when {@code false} the classification is only logged and the
 *                      request is allowed through.
 */
@ConfigurationProperties(prefix = "chatbot.guard")
public record GuardProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("llama-guard3") String model,
		@DefaultValue("true") boolean blockOnUnsafe) {
}
