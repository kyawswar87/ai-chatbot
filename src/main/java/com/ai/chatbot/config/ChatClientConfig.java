package com.ai.chatbot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ai.chatbot.controller.RagChatController;
import com.ai.chatbot.guard.SelfRefinementAdvisor;

/**
 * Central assembly of the RAG {@link ChatClient} so both chat endpoints share one
 * pipeline instead of each rebuilding it. The client wires the fixed system prompt and,
 * as default advisors, the {@link SelfRefinementAdvisor} (guardrail) wrapped around the
 * {@link QuestionAnswerAdvisor} (retrieval) — the guard's negative order makes it the
 * outermost advisor.
 *
 * <p>The RAG tuning constants stay on {@link RagChatController} (public) so
 * {@code RagEvaluationTests} keeps driving the exact same retrieval configuration.
 */
@Configuration
public class ChatClientConfig {

	@Bean
	QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
		return QuestionAnswerAdvisor.builder(vectorStore)
				.searchRequest(SearchRequest.builder()
						.topK(RagChatController.TOP_K)
						.similarityThreshold(RagChatController.SIMILARITY_THRESHOLD)
						.build())
				.promptTemplate(RagChatController.QA_PROMPT_TEMPLATE)
				.build();
	}

	@Bean
	ChatClient ragChatClient(ChatModel chatModel, SelfRefinementAdvisor selfRefinementAdvisor,
			QuestionAnswerAdvisor questionAnswerAdvisor) {
		return ChatClient.builder(chatModel)
				.defaultSystem(RagChatController.SYSTEM_PROMPT)
				.defaultAdvisors(selfRefinementAdvisor, questionAnswerAdvisor)
				.build();
	}
}
