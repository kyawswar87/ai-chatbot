package com.ai.chatbot.guard;

/**
 * Outcome of classifying a user message with Llama Guard 3.
 *
 * @param safe       {@code true} when the message does not violate the policy.
 * @param categories violated category codes reported by the model (e.g. {@code S1}),
 *                   or empty when safe / not reported.
 * @param raw        the raw model response, kept for logging and prompt tuning.
 */
public record GuardResult(boolean safe, String categories, String raw) {

	/**
	 * A passing result. Used for the disabled path and the fail-open path (when the
	 * classifier is unavailable) so a guard failure never blocks legitimate chat.
	 */
	public static GuardResult pass() {
		return new GuardResult(true, "", "");
	}
}
