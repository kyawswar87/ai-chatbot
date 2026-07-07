package com.ai.chatbot.guard;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import com.ai.chatbot.config.GuardrailProperties;

/**
 * Self-Refinement Evaluation advisor: wraps the RAG pipeline with an input guard and an
 * output refiner so irrelevant and jailbreak/prompt-injection traffic is filtered out.
 *
 * <p>Registered with an {@linkplain #getOrder() order} below the
 * {@link QuestionAnswerAdvisor} (whose default is {@code 0}) so it is the outermost
 * advisor: its screening runs before retrieval, and its refinement runs after the full
 * RAG answer is produced.
 *
 * <ul>
 *   <li><b>Input</b>: {@link InputGuard} screens the question. On denial the advisor
 *       returns a fixed refusal <em>without</em> calling {@code chain.nextCall(...)}, so no
 *       retrieval or generation happens.</li>
 *   <li><b>Output</b>: {@link ResponseRefiner} evaluates the generated answer and, if it
 *       fails, recursively rewrites it (or falls back to the refusal).</li>
 * </ul>
 */
@Component
public class SelfRefinementAdvisor implements CallAdvisor {

	/** Below {@code QuestionAnswerAdvisor} (default order 0) so this advisor wraps it. */
	public static final int ORDER = -100;

	private static final Logger log = LoggerFactory.getLogger(SelfRefinementAdvisor.class);

	private final InputGuard inputGuard;
	private final ResponseRefiner responseRefiner;
	private final GuardrailProperties properties;

	public SelfRefinementAdvisor(InputGuard inputGuard, ResponseRefiner responseRefiner,
			GuardrailProperties properties) {
		this.inputGuard = inputGuard;
		this.responseRefiner = responseRefiner;
		this.properties = properties;
	}

	@Override
	public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
		if (!properties.enabled()) {
			return chain.nextCall(request);
		}

		String question = request.prompt().getUserMessage().getText();

		// --- Input guard: reject before spending a retrieval/generation call. ---
		InputGuard.Verdict verdict = inputGuard.screen(question);
		if (!verdict.allowed()) {
			log.info("Input guard rejected question ({}).", verdict.reason());
			return refusal(request);
		}

		// --- Generate through the rest of the chain (retrieval + model). ---
		ChatClientResponse response = chain.nextCall(request);
		String answer = extractAnswer(response);
		if (answer == null) {
			return response;
		}

		// --- Output refiner: evaluate and recursively refine the answer. ---
		List<Document> context = extractRetrievedDocuments(response);
		String refined = responseRefiner.refine(question, context, answer, properties.refusalMessage());
		if (refined.equals(answer)) {
			return response;
		}
		return withAnswer(response, refined);
	}

	@Override
	public String getName() {
		return "SelfRefinementAdvisor";
	}

	@Override
	public int getOrder() {
		return ORDER;
	}

	private ChatClientResponse refusal(ChatClientRequest request) {
		return textResponse(properties.refusalMessage(), request.context());
	}

	/** Rebuild the response carrying the refined answer, preserving the response context. */
	private ChatClientResponse withAnswer(ChatClientResponse response, String answer) {
		return textResponse(answer, response.context());
	}

	private static ChatClientResponse textResponse(String text, java.util.Map<String, Object> context) {
		ChatResponse chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
		return ChatClientResponse.builder()
				.chatResponse(chatResponse)
				.context(context)
				.build();
	}

	private static String extractAnswer(ChatClientResponse response) {
		if (response.chatResponse() == null || response.chatResponse().getResult() == null) {
			return null;
		}
		return response.chatResponse().getResult().getOutput().getText();
	}

	@SuppressWarnings("unchecked")
	private static List<Document> extractRetrievedDocuments(ChatClientResponse response) {
		Object docs = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
		return (docs instanceof List<?>) ? (List<Document>) docs : List.of();
	}
}
