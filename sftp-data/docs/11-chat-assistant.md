---
doc_id: chat-assistant
title: Chat Assistant
summary: The staff help assistant — what it can do, its limits, and what it needs to run.
audience: [admin, operator]
keywords: [chat, assistant, help, AI, chatbot, streaming, single-turn, history, CHAT_STREAM_URL, 401, 503]
---

# Chat Assistant

## What it is

A floating **help assistant** appears in the bottom-right corner of every staff page
(both the operator and admin dashboards). Click the launcher to open a chat panel and
ask a question about the system; the answer **streams in word by word** as it is
generated. This documentation set is the knowledge the assistant draws on.

## What it can help with

General questions about how the platform works and how to use it — concepts (points,
referrals, rewards), where to do things in the dashboards, what a metric means, and how
the POS API behaves. It is an assistant for **staff**, not for customers.

## Limits to be aware of

- **Staff only.** The assistant requires you to be signed in. Requests without a valid
  staff session are rejected (`401`).
- **Single-turn.** Each question is answered on its own — the assistant has **no memory
  of previous messages** in the conversation yet. Ask complete, self-contained
  questions.
- **Ephemeral history.** The visible chat history lives only in your browser tab and
  **clears when you reload** the page. Nothing is saved server-side.

## What it needs to run

The assistant forwards questions to an **external AI backend** that streams the answer.
That backend must be running and reachable, and its streaming endpoint URL must be set
in the environment as **`CHAT_STREAM_URL`** (it defaults to a local address for
development). If the AI backend is unavailable, the assistant returns a `503` and can't
answer until it's back.

## Troubleshooting

- **"Unauthorized" / nothing happens** — make sure you're signed in as staff.
- **Assistant can't answer / service unavailable (`503`)** — the external AI backend is
  down or `CHAT_STREAM_URL` is misconfigured. Check that the AI service is running.
- **History disappeared** — expected after a page reload; the chat is not persisted.
