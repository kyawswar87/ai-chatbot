# ai-chatbot

A **local, self-hosted RAG (Retrieval-Augmented Generation) service** built with Spring Boot
and Spring AI. It embeds documents into a **PostgreSQL + pgvector** store using a local
**Ollama** model, retrieves relevant chunks for a question, and has a local LLM answer from
that context — no external AI APIs required.

It also ships a **pluggable document-ingestion pipeline** so the knowledge base can be filled
from real sources: web URLs, files on an SFTP server (PDF/Word/etc.), and Notion.

---

## Stack

| Concern | Choice |
|---|---|
| Runtime | Java 21, Spring Boot 4.1.0 |
| AI framework | Spring AI 2.0.0 |
| Vector store | PostgreSQL 17 + `pgvector` (HNSW, cosine) |
| Embeddings | Ollama `bge-m3` (1024-dim) |
| Chat model | Ollama `mistral` (generation + answer refinement) |
| Safety guard | Ollama `llama-guard3` (jailbreak / unsafe screening) |
| Relevancy judge | Ollama `llama3.2` (off-topic screening + answer grading) |
| Doc parsing | Apache Tika (files), Jsoup (HTML) |

---

## Architecture

### RAG query pipeline

```mermaid
flowchart LR
    Q["Question"] --> GUARD{"Input guard<br/>llama-guard3 (safety)<br/>+ llama3.2 (off-topic)"}
    GUARD -->|blocked| REFUSE["Fixed refusal"]
    GUARD -->|allowed| EMBED["Embed question<br/>(Ollama bge-m3)"]
    EMBED --> SEARCH["Similarity search<br/>(pgvector, topK=4)"]
    SEARCH --> CTX["Inject top chunks<br/>into prompt"]
    CTX --> GEN["Generate answer<br/>(Ollama mistral)"]
    GEN --> JUDGE{"Relevancy judge<br/>(llama3.2)"}
    JUDGE -->|pass| ANS["Grounded answer"]
    JUDGE -->|fail| REFINE["Refine answer<br/>(mistral, up to N)"]
    REFINE --> JUDGE
    JUDGE -->|still failing| REFUSE
```

The guardrail (`SelfRefinementAdvisor`) wraps the retrieval + generation core: it screens the
question **before** any model call and evaluates/refines the answer **after** generation. It can
be turned off with `GUARDRAIL_ENABLED=false`, which reverts to the plain embed→search→generate
flow.

### Ingestion / scheduler pipeline

```mermaid
flowchart LR
    SCH["Scheduler<br/>(@Scheduled)"] --> SRC
    API["POST /api/ingest/{source}"] --> SRC
    SRC["Document sources<br/>URL · SFTP · Notion"] --> FETCH["Fetch documents"]
    FETCH --> SPLIT["De-dup + split<br/>(TokenTextSplitter)"]
    SPLIT --> EMB["Embed chunks<br/>(Ollama bge-m3)"]
    EMB --> STORE["Store in pgvector"]
```

---

## Prerequisites

- **Java 21** and the bundled Maven wrapper (`./mvnw`)
- **Docker** (for Postgres/pgvector and the demo SFTP server)
- **[Ollama](https://ollama.com)** running locally, with the models pulled:
  ```bash
  ollama pull bge-m3       # embeddings (1024-dim)
  ollama pull mistral      # chat / generation + answer refinement
  ollama pull llama-guard3 # guardrail: jailbreak / unsafe screening
  ollama pull llama3.2     # guardrail: off-topic screening + relevancy judge
  ```
  > On first startup the app also auto-pulls missing models (`pull-model-strategy=when_missing`).
  > The two guardrail models are only needed when the guardrail is enabled (the default);
  > set `GUARDRAIL_ENABLED=false` to run the plain RAG pipeline without them.

---

## Quick start

```bash
# 1. Start Postgres+pgvector (and the demo SFTP server)
docker compose up -d

# 2. Run the app (creates the vector_store table + HNSW index on boot)
./mvnw spring-boot:run
```

The API is then available at `http://localhost:8080`.

> **Port note:** Postgres is published on host port **5433** to avoid colliding with a native
> (e.g. Homebrew) Postgres on 5432. The SFTP demo server is on **2222**.

---

## API

Interactive API docs (Swagger UI) are available once the app is running:

- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

### Ingest documents from a source — `POST /api/ingest/{source}`
`{source}` is `url`, `sftp`, or `notion`. The JSON body carries source-specific params.

```bash
# Web URL (no credentials needed)
curl -X POST localhost:8080/api/ingest/url \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://en.wikipedia.org/wiki/Retrieval-augmented_generation"}'

# SFTP (uses the demo server by default — see "Adding files" below)
curl -X POST localhost:8080/api/ingest/sftp -H 'Content-Type: application/json' -d '{}'

# Notion (requires NOTION_TOKEN; pageIds optional, falls back to config)
curl -X POST localhost:8080/api/ingest/notion \
  -H 'Content-Type: application/json' -d '{"pageIds":["<page-id>"]}'
```

Response (`IngestionResult`):
```json
{ "sourceType":"url", "sourceUris":["https://..."], "documentsFetched":1, "chunksWritten":7, "chunkIds":["..."] }
```

### Ask a question (RAG) — `POST /api/chat`
Retrieves the top matching chunks and answers **strictly from them**.
```bash
curl -X POST localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"question":"What problem does retrieval-augmented generation reduce?"}'
# → {"answer":"..."}
```

The assistant is constrained to the knowledge base (see [Answer grounding](#answer-grounding)).
Questions it can't ground in retrieved context are declined, e.g.:
```bash
curl -X POST localhost:8080/api/chat -H 'Content-Type: application/json' \
  -d '{"question":"What is the capital of France?"}'
# → {"answer":"I can only answer questions based on the information in my knowledge base."}
```

### Ask a question (streaming) — `POST /api/chat/stream`
Same RAG flow, [grounding](#answer-grounding) and guardrail as `/api/chat`, delivered over
**Server-Sent Events** (`text/event-stream`). Because the self-refinement guardrail must see the
**complete** answer before it can judge/refine it, this endpoint **buffers**: it runs the same
guarded + refined blocking generation, then emits the final answer as a **single** `data: <answer>`
event terminated by `data: [DONE]`. (Token-by-token streaming is intentionally traded away for the
guardrail; use `curl -N` to consume the events.)
```bash
curl -N -X POST localhost:8080/api/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{"question":"What problem does retrieval-augmented generation reduce?"}'
# → data: Retrieval-augmented generation reduces hallucination by grounding answers in retrieved context.
#   data: [DONE]
```

### Ad-hoc ingest — `POST /api/vectors`
Store a raw text snippet directly (auto-chunked).
```bash
curl -X POST localhost:8080/api/vectors \
  -H 'Content-Type: application/json' \
  -d '{"content":"Spring AI makes vector search easy","metadata":{"source":"demo"}}'
```

### Similarity search — `GET /api/vectors/search`
```bash
curl "localhost:8080/api/vectors/search?query=vector%20search&topK=3"
```

---

## How RAG works here

```
POST /api/chat {"question": "..."}
   └─ SelfRefinementAdvisor screens the question (llama-guard3 + llama3.2)
       │  ↳ jailbreak/unsafe or off-topic → fixed refusal, no retrieval/generation
       └─ QuestionAnswerAdvisor embeds the question (Ollama bge-m3)
           └─ similarity search in pgvector → top-K chunks above the threshold
               └─ chunks injected into the prompt as context
                   └─ Ollama mistral generates a grounded answer (or declines)
                       └─ SelfRefinementAdvisor judges relevancy (llama3.2);
                          on failure, refines with mistral up to N times, else refuses
```

The answer is returned either as one JSON payload (`POST /api/chat`) or as a single buffered SSE
event (`POST /api/chat/stream`); both share the same retrieval, grounding and guardrail pipeline.

### Answer grounding

`RagChatController` is locked down to answer **only** from the vector store and to refuse
anything it can't ground there. Several reinforcing layers make this robust rather than relying
on a single soft instruction:

1. **Input guard** (`SelfRefinementAdvisor` → `InputGuard`) — before any retrieval, the
   question is screened by `llama-guard3` (jailbreak / unsafe content) and an `llama3.2`
   off-topic classifier. A denial short-circuits with a fixed refusal, so no retrieval or
   generation happens.
2. **System prompt** — instructs the model to use only the supplied context, never prior
   knowledge, and to decline unrelated questions.
3. **Similarity threshold** (`similarityThreshold = 0.5`) — chunks below the cutoff are
   dropped, so off-topic questions retrieve **no** context. Tune to the corpus if in-domain
   questions get wrongly refused (lower it) or off-topic answers leak through (raise it).
4. **Custom advisor prompt template** — when the context is empty or lacks the answer, the
   model is told to return a fixed refusal line instead of guessing.
5. **Output self-refinement** (`SelfRefinementAdvisor` → `ResponseRefiner`) — the generated
   answer is graded by an `llama3.2` relevancy judge; if it fails, it is recursively rewritten
   with `mistral` up to `maxRefinementIterations` times, falling back to the refusal line.

All guardrail layers (1 and 5) can be disabled with `GUARDRAIL_ENABLED=false`; the prompt- and
threshold-based layers (2–4) always apply.

Resulting refusal lines:
- Topic not in the knowledge base → `"I don't have information about that in my knowledge base."`
- Unrelated question → `"I can only answer questions based on the information in my knowledge base."`

Ingestion is the mirror image: each source produces raw documents → the shared
`IngestionService` stamps metadata, splits with `TokenTextSplitter`, embeds each chunk, and
writes to pgvector. Re-ingesting the same `source_uri` **replaces** its chunks (idempotent),
so re-runs never duplicate data.

Every stored chunk carries: `source_type`, `source_uri`, `title`, `ingested_at`, plus the
splitter's `parent_document_id` / `chunk_index` / `total_chunks`.

---

## Document sources

| Source | `{source}` | Params | Backed by | Notes |
|---|---|---|---|---|
| Web URL | `url` | `url` | Jsoup | Fully working; sends a browser User-Agent |
| SFTP files | `sftp` | `remoteDir` (optional) | Spring Integration SFTP + Tika | PDF/DOC/DOCX… |
| Notion | `notion` | `pageIds` (optional) | Notion API (RestClient) | Needs `NOTION_TOKEN` |

Adding a new source is one class: implement `DocumentSource` and annotate `@Component`. It is
auto-discovered (injected as a `List<DocumentSource>`) and routed by its `type()` — no change
to `IngestionController`.

### Adding files to the SFTP demo server
`./sftp-data/docs` is bind-mounted into the SFTP container, so just drop files in and re-ingest:
```bash
cp ~/Downloads/report.pdf sftp-data/docs/
curl -X POST localhost:8080/api/ingest/sftp -H 'Content-Type: application/json' -d '{}'
```
Only extensions in `ingestion.sftp.file-extensions` (default `pdf,doc,docx`) are picked up;
add more (Tika also handles `pptx`, `xlsx`, `html`, `md`, `txt`, …) to widen the net.

---

## Configuration

All settings live in `src/main/resources/application.properties` and are overridable via
environment variables (defaults shown).

| Env var | Default | Purpose |
|---|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `postgres` | DB credentials |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Ollama endpoint |
| `SFTP_HOST` / `SFTP_PORT` | `localhost` / `2222` | SFTP server |
| `SFTP_USERNAME` / `SFTP_PASSWORD` | `demo` / `demo` | SFTP auth (or use `SFTP_PRIVATE_KEY`) |
| `SFTP_REMOTE_DIR` | `/docs` | Remote directory to scan |
| `SFTP_FILE_EXTENSIONS` | `pdf,doc,docx` | Comma-separated allow-list |
| `NOTION_TOKEN` | _(empty)_ | Notion integration token |
| `NOTION_PAGE_IDS` | _(empty)_ | Default Notion pages to sync |
| `INGEST_SCHEDULE_ENABLED` | `false` | Enable scheduled URL re-sync |
| `INGEST_SCHEDULE_CRON` | `0 0 * * * *` | Re-sync cron |
| `INGEST_SCHEDULE_URLS` | _(empty)_ | Comma-separated URLs to re-sync |
| `GUARDRAIL_ENABLED` | `true` | Enable input guard + output self-refinement |
| `GUARDRAIL_GUARD_MODEL` | `llama-guard3` | Safety / jailbreak classifier |
| `GUARDRAIL_JUDGE_MODEL` | `llama3.2` | Off-topic classifier + relevancy judge |
| `GUARDRAIL_MAX_REFINEMENT_ITERATIONS` | `2` | Max answer-refinement passes before refusal |

Errors map to meaningful status codes (via `ApiExceptionHandler`): **400** for a bad request
(unknown source / missing param), **503** when a source is not configured.

---

## Project structure

```
src/main/java/com/ai/chatbot/
├── AiChatbotApplication.java         # @SpringBootApplication, @EnableScheduling
├── config/                           # @ConfigurationProperties (sftp, notion, schedule, guardrail)
│                                     #   + ChatClientConfig (shared RAG ChatClient + advisors)
├── guard/                            # self-refinement guardrail
│   ├── InputGuard.java               # llama-guard3 safety + llama3.2 off-topic screen
│   ├── ResponseRefiner.java          # llama3.2 relevancy judge + mistral refine loop
│   └── SelfRefinementAdvisor.java    # CallAdvisor wrapping the QA advisor
├── controller/
│   ├── VectorStoreController.java    # /api/vectors  (ad-hoc ingest + search)
│   ├── RagChatController.java        # /api/chat     (RAG)
│   ├── IngestionController.java      # /api/ingest/{source}
│   └── ApiExceptionHandler.java      # 400/503 mapping
├── ingestion/
│   ├── DocumentSource.java           # the plug point
│   ├── IngestionService.java         # shared pipeline (stamp → de-dup → split → store)
│   ├── SourceRequest.java / IngestionResult.java
│   └── source/                       # UrlDocumentSource, SftpDocumentSource, NotionDocumentSource
└── scheduler/IngestionScheduler.java # gated @Scheduled URL re-sync

docker-compose.yml                    # pgvector (5433) + atmoz/sftp (2222)
sftp-data/docs/                       # files served over the demo SFTP server
```

---

## Verifying it works

```bash
# Ingest the bundled SFTP sample PDF, then ask about it
curl -X POST localhost:8080/api/ingest/sftp -H 'Content-Type: application/json' -d '{}'
curl -X POST localhost:8080/api/chat -H 'Content-Type: application/json' \
  -d '{"question":"When was Project Nimbus launched and which team owns it?"}'

# Inspect the store
psql "postgresql://postgres:postgres@127.0.0.1:5433/chatbot" \
  -c "select metadata->>'source_type', count(*) from vector_store group by 1;"
```

---

## Evaluating answer quality

Model-evaluation tests score the RAG pipeline's answers with two Spring AI LLM-as-judge
evaluators, run against the bundled loyalty-platform knowledge base:

- **`RelevancyEvaluator`** — is the answer relevant to the question given the retrieved context?
  Judged by `llama3.2` (a model separate from the `mistral` generator, so it doesn't grade itself).
- **`FactCheckingEvaluator`** — are the answer's claims actually supported by the retrieved
  context (not hallucinated)? Judged by `llama3.2` using the general-purpose fact-checking prompt.

They live in `src/test/java/com/ai/chatbot/eval/RagEvaluationTests.java` and are **opt-in** (gated
by `RAG_EVAL=true`) since they need Ollama, pgvector, and the judge model. Plain `./mvnw test`
skips them.

### Running the eval tests

**Prerequisites** (all must be up first):

- pgvector DB running — `docker compose up -d db`
- Ollama running with the models: `mistral` (generator), `bge-m3` (embeddings), `llama3.2` (judge).
  Pull the judge if needed: `ollama pull llama3.2`
- The knowledge base already ingested, e.g.:
  `curl -X POST localhost:8080/api/ingest/sftp -H 'Content-Type: application/json' -d '{}'`

**Run:**

```bash
# All eval cases
RAG_EVAL=true ./mvnw test -Dtest=RagEvaluationTests

# A single parameterized method (all its questions)
RAG_EVAL=true ./mvnw test -Dtest='RagEvaluationTests#answersAreRelevantAndFactuallyGrounded'

# Whole suite (drop -Dtest); the eval class still needs RAG_EVAL=true or it is skipped
RAG_EVAL=true ./mvnw test
```

- **`RAG_EVAL=true` is required** — without it the class is disabled and reported as *skipped*
  (`Time elapsed: 0 s`, no output). That is why a plain `./mvnw test` shows nothing for it.
- **From an IDE:** add the environment variable `RAG_EVAL=true` to the run configuration, then run
  the class normally. Without it the IDE will also skip it.

### HTML report

Each case logs a readable block (question, retrieved chunks, model answer, relevancy/fact-check
verdicts) to its captured output. Turn a run into a browsable HTML report with the
`maven-surefire-report-plugin`:

```bash
# 1. Run the eval once (writes target/surefire-reports/*.xml)
RAG_EVAL=true ./mvnw test -Dtest=RagEvaluationTests

# 2. Render the HTML from those results — no re-run, no Ollama/pgvector needed
./mvnw surefire-report:report-only
# → open target/reports/surefire.html
```

Each test case in the report expands to its captured output, which contains the question, the model
answer, and the evaluation results — for passing and failing cases alike. (One-shot alternative that
runs *and* renders: `RAG_EVAL=true ./mvnw surefire-report:report -Dtest=RagEvaluationTests`.)

The test queries the **existing** pgvector store (it does not ingest or modify any data), runs a set
of ground-truth questions through the exact production pipeline, and asserts each answer passes both
evaluators. If the store is empty the retrieval assertion fails with a hint to ingest first.
Overridable via env: `RAG_EVAL_RELEVANCY_MODEL` (default `llama3.2`), `RAG_EVAL_FACTCHECK_MODEL`
(default `llama3.2`). For a purpose-built fact-checker, pull
[`bespoke-minicheck`](https://ollama.com/blog/reduce-hallucinations-with-bespoke-minicheck) and
switch the fact-check evaluator to `FactCheckingEvaluator.forBespokeMinicheck(...)`.

---

## Notes & follow-ups

- **SFTP host-key verification is disabled** (`setAllowUnknownKeys(true)`) — fine for the local
  demo; pin host keys via `known_hosts` for real servers.
- The `atmoz/sftp` image is amd64; on Apple Silicon it runs under emulation (works, just a warning).
- Ingestion is **synchronous** per request. For large volumes, move to async / a job queue.
- Possible next steps: source-type filtering + citations in `/api/chat`, auth on ingestion
  endpoints, and a Confluence connector (same `DocumentSource` contract).
