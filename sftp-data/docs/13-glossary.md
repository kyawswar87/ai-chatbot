---
doc_id: glossary
title: Glossary
summary: One-line definitions of the key terms used across the loyalty platform.
audience: [admin, operator]
keywords: [glossary, definitions, terms, vocabulary, meaning, what is]
---

# Glossary

- **Admin** — a staff role with full access: everything an operator can do, plus
  analytics, staff management, and API keys.
- **Operator** — a staff role for everyday work: customers, rewards, redemptions, and
  program settings.
- **Customer** — an end shopper who collects points. Customers have no login; they're
  managed by staff or the POS API.
- **External id** — the business's own identifier for a customer (e.g. a POS or online-
  store id). Used to create/look up customers idempotently.
- **Points balance** — a customer's current running total of points, always kept in
  step with their ledger.
- **Ledger** — the append-only history of every point change; the source of truth for
  balances.
- **Transaction (ledger entry)** — a single point change: type, signed points, balance
  after, and context. Types are earn, redeem, referral, adjust.
- **Earn** — points added from a purchase.
- **Redeem / redemption** — spending points on a reward, which issues a voucher.
- **Adjust** — a manual staff correction (or the automatic signup bonus).
- **Earn rate** — points earned per unit of currency spent; `points = floor(amount ×
  earn rate)`.
- **Reward** — a catalog item a customer can redeem points for (has a points cost,
  active flag, and optional stock).
- **Voucher / voucher code** — the unique code (`V-XXXXXXXX`) issued by a redemption;
  usable once. Statuses: issued, used, void.
- **Referrer** — the customer who shares their referral code.
- **Referee** — the customer who signs up using someone's referral code.
- **Referral code** — a customer's shareable code (`R-XXXXXXXX`) that captures a
  referral at signup.
- **Signup bonus** — optional one-time points given to every new customer.
- **Points liability** — total outstanding points across all customers; what the
  business may still have to honor.
- **API key** — a secret (prefix `lk_`) that authenticates POS/e-commerce requests via
  `Authorization: Bearer <key>`.
- **Idempotency key** — a client-supplied id that makes a repeated request apply only
  once, so retries don't double-count.
- **Program configuration** — the shared, program-wide settings (earn rate, referral
  rewards, signup bonus, currency).
