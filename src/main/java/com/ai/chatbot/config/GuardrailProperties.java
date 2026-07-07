package com.ai.chatbot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Self-Refinement Evaluation guardrail settings. Bind from {@code chatbot.guardrail.*}.
 *
 * <p>Two LLM-as-guard stages wrap the RAG pipeline (see {@code SelfRefinementAdvisor}):
 * an <em>input guard</em> that screens the question for jailbreak/unsafe content and
 * off-topic questions before generation, and an <em>output refiner</em> that evaluates
 * the answer and recursively rewrites it up to {@code maxRefinementIterations} times
 * before falling back to a fixed refusal.
 *
 * <ul>
 *   <li>{@code guardModel} — safety/jailbreak classifier (Meta Llama Guard 3, which
 *       applies its own moderation chat template in Ollama).</li>
 *   <li>{@code judgeModel} — relevancy judge <em>and</em> answer refiner; a model separate
 *       from the generator ({@code mistral}) so it does not grade its own output.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "chatbot.guardrail")
public record GuardrailProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("llama-guard3") String guardModel,
		@DefaultValue("llama3.2") String judgeModel,
		@DefaultValue("2") int maxRefinementIterations,
		@DefaultValue("I can only answer questions based on the information in my knowledge base.")
		String refusalMessage) {
}
