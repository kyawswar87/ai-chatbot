package com.ai.chatbot.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.evaluation.FactCheckingEvaluator;
import org.springframework.ai.chat.evaluation.RelevancyEvaluator;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.evaluation.EvaluationRequest;
import org.springframework.ai.evaluation.EvaluationResponse;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ai.chatbot.controller.RagChatController;

/**
 * Model-evaluation tests for the RAG pipeline. Each ground-truth question is run
 * through the <em>exact</em> production flow (same {@link QuestionAnswerAdvisor}
 * config, system prompt and template as {@link RagChatController}), and the answer is
 * scored by two LLM-as-judge evaluators from Spring AI:
 *
 * <ul>
 *   <li>{@link RelevancyEvaluator} — is the answer relevant to the question given the
 *       retrieved context? Judged by {@code llama3.2}, a model separate from the
 *       generator ({@code mistral}) so it does not grade its own output.</li>
 *   <li>{@link FactCheckingEvaluator} — are the answer's claims supported by the
 *       retrieved context (i.e. not hallucinated)? Judged by {@code bespoke-minicheck},
 *       a model purpose-built by Bespoke Labs for grounded-factuality checking, via
 *       {@link FactCheckingEvaluator#forBespokeMinicheck}.</li>
 * </ul>
 *
 * <p>The test queries the <strong>existing</strong> pgvector store — it does not ingest
 * anything. It assumes the knowledge base has already been loaded (e.g. via
 * {@code POST /api/ingest/sftp} against {@code sftp-data/docs}); if the store is empty
 * the retrieval assertion fails with a hint to ingest first.
 *
 * <p><strong>Opt-in.</strong> These are heavyweight integration tests that need a
 * running Ollama (with the judge model available) and a populated pgvector store. They
 * only run when {@code RAG_EVAL=true} is set, so the normal {@code ./mvnw test} stays
 * fast and infra-free.
 *
 * <pre>{@code
 *   ollama pull llama3.2           # relevancy judge (skip if already present)
 *   ollama pull bespoke-minicheck  # fact-check judge (skip if already present)
 *   docker compose up -d db
 *   # ensure the KB is ingested, e.g.:
 *   curl -X POST localhost:8080/api/ingest/sftp -H 'Content-Type: application/json' -d '{}'
 *   RAG_EVAL=true ./mvnw test -Dtest=RagEvaluationTests
 * }</pre>
 *
 * <p>Overridable via env: {@code RAG_EVAL_RELEVANCY_MODEL} (default {@code llama3.2}),
 * {@code RAG_EVAL_FACTCHECK_MODEL} (default {@code bespoke-minicheck}).
 */
@SpringBootTest(properties = {
		// Quiet the framework's DEBUG chatter so each case's captured System.out — which the
		// surefire-report HTML renders — is the eval block, not thousands of DEBUG lines.
		"logging.level.org.springframework.ai=WARN",
		"logging.level.org.springframework.web=WARN",
		"logging.level.org.springframework.ai.chat.client.advisor=WARN"
})
@EnabledIfEnvironmentVariable(named = "RAG_EVAL", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagEvaluationTests {

	/**
	 * Relevancy judge prompt. Spring AI's built-in default is terse and makes smaller local
	 * judge models (e.g. {@code llama3.2}) answer "NO" even for clearly relevant answers.
	 * This spells the task out and forces a single bare {@code yes}/{@code no}, which the
	 * evaluator compares with an exact {@code equalsIgnoreCase("yes")}. Placeholders
	 * {@code {query}}, {@code {response}}, {@code {context}} are filled by the evaluator.
	 */
	private static final PromptTemplate RELEVANCY_PROMPT = new PromptTemplate("""
			You are grading whether an ANSWER is relevant to a QUESTION and consistent with the CONTEXT.
			Respond with a single word: yes or no. No punctuation, no explanation, no other text.
			Answer "yes" if the ANSWER is relevant to the QUESTION and supported by the CONTEXT; otherwise "no".

			QUESTION:
			{query}

			ANSWER:
			{response}

			CONTEXT:
			{context}
			""");

	@Autowired
	private ChatModel chatModel;

	@Autowired
	private VectorStore vectorStore;

	private ChatClient ragClient;
	private QuestionAnswerAdvisor qaAdvisor;
	private RelevancyEvaluator relevancyEvaluator;
	private FactCheckingEvaluator factCheckingEvaluator;

	@BeforeAll
	void setUp() {
		// Rebuild the identical RAG pipeline the controller serves in production.
		this.qaAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
				.searchRequest(SearchRequest.builder()
						.topK(RagChatController.TOP_K)
						.similarityThreshold(RagChatController.SIMILARITY_THRESHOLD)
						.build())
				.promptTemplate(RagChatController.QA_PROMPT_TEMPLATE)
				.build();
		this.ragClient = ChatClient.builder(chatModel).build();

		// Judges reuse the autoconfigured Ollama ChatModel, overriding only the model
		// name per judge so no extra beans/wiring are needed.
		String relevancyModel = envOrDefault("RAG_EVAL_RELEVANCY_MODEL", "llama3.2");
		String factCheckModel = envOrDefault("RAG_EVAL_FACTCHECK_MODEL", "bespoke-minicheck");

		// Relevancy still uses an explicit single-word prompt because Spring AI's terse
		// default makes small judge models answer incorrectly or add punctuation, and the
		// evaluator requires an exact "yes"/"no" reply.
		this.relevancyEvaluator = RelevancyEvaluator.builder()
				.chatClientBuilder(ChatClient.builder(chatModel)
						.defaultOptions(OllamaChatOptions.builder().model(relevancyModel)))
				.promptTemplate(RELEVANCY_PROMPT)
				.build();

		// Fact-check uses bespoke-minicheck, a model purpose-built for grounded-factuality
		// checking. forBespokeMinicheck installs the bare Document/Claim prompt the model was
		// trained on (no custom prompt — that would defeat its tuning); the model still comes
		// from the OllamaChatOptions here. It emits "Yes"/"No", which the evaluator matches
		// with strip()+equalsIgnoreCase.
		this.factCheckingEvaluator = FactCheckingEvaluator.forBespokeMinicheck(
				ChatClient.builder(chatModel)
						.defaultOptions(OllamaChatOptions.builder().model(factCheckModel)));
	}

	@ParameterizedTest(name = "[{index}] {0}")
	@MethodSource("groundedQuestions")
	void answersAreRelevantAndFactuallyGrounded(String question) {
		// Retrieve grounding context from the existing store with the same search params
		// the advisor uses — this is the context the answer is judged against.
		List<Document> context = vectorStore.similaritySearch(SearchRequest.builder()
				.query(question)
				.topK(RagChatController.TOP_K)
				.similarityThreshold(RagChatController.SIMILARITY_THRESHOLD)
				.build());

		assertThat(context)
				.withFailMessage("No context retrieved for \"%s\" — is the knowledge base ingested into the "
						+ "vector store? (e.g. POST /api/ingest/sftp)", question)
				.isNotEmpty();

		String answer = ragClient.prompt()
				.system(RagChatController.SYSTEM_PROMPT)
				.advisors(qaAdvisor)
				.user(question)
				.call()
				.content();

		EvaluationRequest request = new EvaluationRequest(question, context, answer);

		// Evaluate both judges before asserting, so every case's full question/answer/verdict is
		// logged (and captured into the surefire-report HTML) even when an assertion below fails.
		EvaluationResponse relevancy = relevancyEvaluator.evaluate(request);
		EvaluationResponse factuality = factCheckingEvaluator.evaluate(request);

		logCase(question, context, answer, relevancy, factuality);

		assertThat(relevancy.isPass())
				.withFailMessage("Relevancy FAILED (score=%s) for \"%s\"%n  answer: %s",
						relevancy.getScore(), question, answer)
				.isTrue();
		assertThat(factuality.isPass())
				.withFailMessage("Fact-check FAILED for \"%s\"%n  answer: %s", question, answer)
				.isTrue();
	}

	/**
	 * Prints a readable per-case block to {@code System.out}. Surefire captures this per test case
	 * (pass or fail) and the {@code surefire-report} HTML renders it, so this is what shows up in the
	 * generated report: the question, the retrieved vectors, the model answer, and the judge verdicts.
	 */
	private static void logCase(String question, List<Document> context, String answer,
			EvaluationResponse relevancy, EvaluationResponse factuality) {
		StringBuilder sb = new StringBuilder("\n");
		sb.append("========================= EVAL CASE =========================\n");
		sb.append("QUESTION: ").append(question).append('\n');

		sb.append("\n--- VECTOR STORE OUTPUT (").append(context.size()).append(" chunk(s) retrieved) ---\n");
		for (int i = 0; i < context.size(); i++) {
			Document d = context.get(i);
			sb.append(String.format("  [%d] score=%.4f  source=%s  title=%s%n",
					i, d.getScore(), d.getMetadata().get("source_uri"), d.getMetadata().get("title")));
			sb.append("      ").append(d.getText().strip().replace("\n", "\n      ")).append('\n');
		}

		sb.append("\n--- MODEL OUTPUT (generator: mistral) ---\n").append(answer.strip()).append('\n');

		sb.append("\n--- JUDGES ---\n");
		sb.append(String.format("  Relevancy (llama3.2)         : pass=%s (score=%.1f)%n",
				relevancy.isPass(), relevancy.getScore()));
		sb.append(String.format("  FactCheck (bespoke-minicheck): pass=%s%n", factuality.isPass()));
		sb.append("=============================================================\n");
		System.out.println(sb);
	}

	/**
	 * Ground-truth questions whose answers are contained in the loyalty-platform KB
	 * (see {@code sftp-data/docs/02-points-and-ledger.md} and {@code 03-earning-points.md}).
	 */
	static Stream<String> groundedQuestions() {
		return Stream.of(
				"How are points from a purchase calculated?",
				"Can a customer's points balance go negative?",
				"What are the four transaction types recorded in the point ledger?",
				"Can entries in the point ledger be edited or deleted after they are written?",
				"How is a signup bonus recorded in the ledger?");
	}

	private static String envOrDefault(String name, String fallback) {
		String value = System.getenv(name);
		return (value != null && !value.isBlank()) ? value : fallback;
	}

}
