# System Documentation (RAG knowledge base)

This folder is the **knowledge base for the staff chat assistant**. Each file is a
small, self-contained topic written so that a single retrieved chunk can answer a
question on its own. Content is derived from the application code and describes the
**current** behavior of the system.

## How to use these docs

- **For a RAG pipeline**: ingest the individual topic files (recommended — they chunk
  cleanly and each has frontmatter metadata), or ingest the concatenated bundle
  `loyalty-platform-system-docs.md`.
- **For humans**: read them in order, or jump to the topic you need.

Each file starts with YAML frontmatter (`doc_id`, `title`, `summary`, `audience`,
`keywords`) intended as retrieval metadata.

## Contents

| File | Topic |
|------|-------|
| [00-overview.md](00-overview.md) | What the platform is, who uses it, the main parts |
| [01-roles-and-access.md](01-roles-and-access.md) | Admin vs Operator, logins, disabling access |
| [02-points-and-ledger.md](02-points-and-ledger.md) | The ledger, balances, transaction types |
| [03-earning-points.md](03-earning-points.md) | Purchases, signup bonus, referrals, adjustments |
| [04-rewards-and-redemptions.md](04-rewards-and-redemptions.md) | Rewards catalog, redeeming, voucher lifecycle |
| [05-referrals.md](05-referrals.md) | Referral codes, rewards, when the referrer is paid |
| [06-program-configuration.md](06-program-configuration.md) | Earn rate, referral/signup bonuses, currency |
| [07-operator-dashboard.md](07-operator-dashboard.md) | The everyday `/dashboard` screens |
| [08-admin-dashboard.md](08-admin-dashboard.md) | Admin-only `/admin` screens |
| [09-analytics-and-metrics.md](09-analytics-and-metrics.md) | Exact definitions of reported numbers |
| [10-pos-api-reference.md](10-pos-api-reference.md) | The `/api/v1` POS/e-commerce API |
| [11-chat-assistant.md](11-chat-assistant.md) | The staff help assistant itself |
| [12-faq.md](12-faq.md) | Common questions with short answers |
| [13-glossary.md](13-glossary.md) | Key term definitions |

## Regenerating the bundle

`loyalty-platform-system-docs.md` is **generated** by concatenating the topic files.
Edit the topic files, not the bundle, then run:

```bash
npm run docs:build
```
