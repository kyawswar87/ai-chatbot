---
doc_id: pos-api-reference
title: POS API Reference
summary: The programmatic /api/v1 interface for POS and e-commerce — auth, idempotency, rate limits, errors, and every endpoint.
audience: [developer, integrator]
keywords: [API, POS, endpoint, bearer, api key, idempotency, rate limit, 429, error codes, customers, transactions, redemptions, curl]
---

# POS API Reference

The POS API (`/api/v1/...`) lets a point-of-sale system or online store create
customers and record purchases and redemptions automatically. A machine-readable
OpenAPI spec is also available at `docs/openapi.yaml`.

## Authentication

Every request must include an API key:

```
Authorization: Bearer <key>
```

Keys start with the prefix `lk_`. They are created and revoked by an admin under
**Admin → API keys** (see [Admin dashboard](08-admin-dashboard.md)). A missing,
unknown, or revoked key returns **`401`**.

## Idempotency (safe retries)

Mutating endpoints accept an optional header:

```
Idempotency-Key: <your-unique-id>
```

If the same key is seen twice, the original result is replayed instead of applying the
change again — so a POS retry never double-credits points or issues two vouchers. Use a
stable id such as the order number.

## Rate limiting

Mutations are rate-limited **per API key** to **60 requests per minute**. When
exceeded, the API returns **`429`** with a **`Retry-After`** header (seconds to wait).

> **Limitation:** the rate limiter is currently **in-memory per server instance** (an
> MVP simplification). Across multiple instances the effective limit is higher. Moving
> it to a shared store (e.g. Redis/Upstash) is planned hardening.

## Error format

All errors use the envelope:

```json
{ "error": { "code": "unprocessable", "message": "Insufficient points balance." } }
```

Codes map to HTTP statuses: `bad_request` (400), `unauthorized` (401),
`forbidden` (403), `not_found` (404), `conflict` (409), `unprocessable` (422),
`rate_limited` (429), `internal` (500).

## Endpoints

### Create a customer — `POST /customers`

Idempotent on `externalId`. Body: `externalId` (required), `email` (optional),
`referralCode` (optional — an existing customer's referral code to capture a referral).

- New customer → **`201`**; grants the signup bonus and, if a valid `referralCode` was
  given, the referee welcome bonus and a pending referral.
- Already existed → **`200`**, returned unchanged (no bonus re-applied).
- Invalid body → `400`.

### Get a customer — `GET /customers/:id`

Returns the customer including `pointsBalance`. Unknown id → `404`.

### List a customer's ledger — `GET /customers/:id/transactions`

Returns the customer's point history, newest first, as `{ "transactions": [...] }`.

### Record an earn — `POST /transactions`

Body: `customerId` (required), `amount` (required, positive), `reference` (optional).
Points earned = `floor(amount × earn_rate)`. Returns
`{ transaction, balance, referrerPaid }`, where `referrerPaid` is true if this earn
triggered the referrer's referral payout (see [Referrals](05-referrals.md)). Unknown
customer → `404`.

### Redeem a reward — `POST /redemptions`

Body: `customerId` and `rewardId` (both required). Checks active/stock/balance, spends
points, and issues a voucher. Success → **`201`** with `{ redemption, balance }`.
Insufficient balance or an unavailable reward → **`422`**.

### Use a voucher — `POST /redemptions/:id/use`

Marks an issued voucher as `used`. Success → `200`. Unknown id → `404`. A voucher not
in the `issued` state (already used or void) → **`409`**.

## Example: record a purchase

```bash
curl -X POST http://localhost:3000/api/v1/transactions \
  -H "Authorization: Bearer <key>" \
  -H "Idempotency-Key: order-123" \
  -H "Content-Type: application/json" \
  -d '{"customerId":"<uuid>","amount":100,"reference":"order-123"}'
```

## Health check

`GET /api/health` runs a quick database check and returns `{ ok: true, db: "up" }`, or
`503` if the database is unreachable. It does not require an API key.
