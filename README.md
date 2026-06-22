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
| Chat model | Ollama `mistral` |
| Doc parsing | Apache Tika (files), Jsoup (HTML) |

---

## Architecture

### RAG query pipeline

```mermaid
flowchart LR
    Q["Question"] --> EMBED["Embed question<br/>(Ollama bge-m3)"]
    EMBED --> SEARCH["Similarity search<br/>(pgvector, topK=4)"]
    SEARCH --> CTX["Inject top chunks<br/>into prompt"]
    CTX --> GEN["Generate answer<br/>(Ollama mistral)"]
    GEN --> ANS["Grounded answer<br/>(or refusal)"]
```

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
- **[Ollama](https://ollama.com)** running locally, with the two models pulled:
  ```bash
  ollama pull bge-m3      # embeddings (1024-dim)
  ollama pull mistral     # chat / generation
  ```
  > On first startup the app also auto-pulls missing models (`pull-model-strategy=when_missing`).

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
Same RAG flow and [grounding](#answer-grounding) as `/api/chat`, but streams the answer as
**Server-Sent Events** (`text/event-stream`): one `data: <token>` event per generated chunk,
terminated by `data: [DONE]`. Use `curl -N` to disable buffering and watch tokens arrive live.
```bash
curl -N -X POST localhost:8080/api/chat/stream \
  -H 'Content-Type: application/json' \
  -d '{"question":"What problem does retrieval-augmented generation reduce?"}'
# → data: Retrieval
#   data:  reduces
#   data:  ...
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
   └─ QuestionAnswerAdvisor embeds the question (Ollama bge-m3)
       └─ similarity search in pgvector → top-K chunks above the threshold
           └─ chunks injected into the prompt as context
               └─ Ollama mistral generates a grounded answer (or declines)
```

The answer is returned either as one JSON payload (`POST /api/chat`) or streamed token-by-token
over SSE (`POST /api/chat/stream`); both share the same retrieval and grounding pipeline.

### Answer grounding

`RagChatController` is locked down to answer **only** from the vector store and to refuse
anything it can't ground there. Three reinforcing layers make this robust rather than relying
on a single soft instruction:

1. **System prompt** — instructs the model to use only the supplied context, never prior
   knowledge, and to decline unrelated questions.
2. **Similarity threshold** (`similarityThreshold = 0.5`) — chunks below the cutoff are
   dropped, so off-topic questions retrieve **no** context. Tune to the corpus if in-domain
   questions get wrongly refused (lower it) or off-topic answers leak through (raise it).
3. **Custom advisor prompt template** — when the context is empty or lacks the answer, the
   model is told to return a fixed refusal line instead of guessing.

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

Errors map to meaningful status codes (via `ApiExceptionHandler`): **400** for a bad request
(unknown source / missing param), **503** when a source is not configured.

---

## Project structure

```
src/main/java/com/ai/chatbot/
├── AiChatbotApplication.java         # @SpringBootApplication, @EnableScheduling
├── config/                           # @ConfigurationProperties (sftp, notion, schedule)
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

## Notes & follow-ups

- **SFTP host-key verification is disabled** (`setAllowUnknownKeys(true)`) — fine for the local
  demo; pin host keys via `known_hosts` for real servers.
- The `atmoz/sftp` image is amd64; on Apple Silicon it runs under emulation (works, just a warning).
- Ingestion is **synchronous** per request. For large volumes, move to async / a job queue.
- Possible next steps: source-type filtering + citations in `/api/chat`, auth on ingestion
  endpoints, and a Confluence connector (same `DocumentSource` contract).
