package com.ai.chatbot.guard;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ai.chatbot.config.GuardProperties;
import com.ai.chatbot.controller.RagChatController;

/**
 * Cross-cutting guard applied before every chat request. Classifies the user
 * message with Llama Guard 3 (via {@link GuardService}), logs the classification,
 * and — when blocking is enabled — rejects flagged messages before they reach the
 * RAG model by throwing {@link GuardViolationException} (mapped to HTTP 403).
 *
 * <p>The pointcut binds the {@link RagChatController.AskRequest} argument, so it
 * matches both {@code POST /api/chat} and {@code POST /api/chat/stream} (both take
 * that record). Because this is {@code @Before} advice, a thrown exception on the
 * streaming endpoint happens before the {@code Flux} is built — the SSE stream
 * never starts, so the error is mapped normally instead of surfacing mid-stream.
 */
@Aspect
@Component
public class ChatGuardAspect {

	private static final Logger log = LoggerFactory.getLogger(ChatGuardAspect.class);

	/** Max characters of the user message written to the log. */
	private static final int LOG_PREVIEW_LEN = 200;

	private static final String REFUSAL =
			"Your message was blocked because it looks like an attempt to override the "
					+ "assistant's instructions.";

	private final GuardService guardService;
	private final GuardProperties props;

	public ChatGuardAspect(GuardService guardService, GuardProperties props) {
		this.guardService = guardService;
		this.props = props;
	}

	@Before("execution(* com.ai.chatbot.controller.RagChatController.*(..)) && args(request)")
	public void guard(RagChatController.AskRequest request) {
		if (!props.enabled()) {
			return;
		}

		GuardResult result = guardService.classify(request.question());
		String preview = preview(request.question());

		if (result.safe()) {
			log.info("guard classification: safe msg=\"{}\"", preview);
			return;
		}

		log.warn("guard classification: UNSAFE categories=[{}] blocking={} msg=\"{}\"",
				result.categories(), props.blockOnUnsafe(), preview);

		if (props.blockOnUnsafe()) {
			throw new GuardViolationException(REFUSAL);
		}
	}

	private String preview(String message) {
		if (message == null) {
			return "";
		}
		String single = message.replaceAll("\\s+", " ").strip();
		return single.length() <= LOG_PREVIEW_LEN
				? single
				: single.substring(0, LOG_PREVIEW_LEN) + "…";
	}
}
