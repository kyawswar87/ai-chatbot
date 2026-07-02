---
doc_id: points-and-ledger
title: Points and the Ledger
summary: How points are tracked — the append-only ledger, the running balance, and transaction types.
audience: [admin, operator]
keywords: [points, ledger, balance, transaction, earn, redeem, referral, adjust, history, negative, source of truth]
---

# Points and the Ledger

## The ledger is the source of truth

Every change to a customer's points is written as a permanent row in the **point
ledger**. The ledger is **append-only**: entries are never edited or deleted. To
reverse something, you add a new, opposite entry (an adjustment). This gives a
complete, auditable history of every point a customer has ever earned or spent.

Each customer also has a **points balance** — a running total that is kept exactly in
step with the ledger. Every time a ledger entry is written, the balance is updated in
the same database transaction, so the two can never drift apart. (There is a
maintenance check, `npm run check:integrity`, that confirms every customer's balance
still reconciles with their ledger.)

## What a ledger entry records

Each entry captures:

- **Type** — one of four kinds (see below).
- **Points** — a **signed** number: **positive adds** points, **negative subtracts**
  them. For example, an earn might be `+50` and a redemption `-200`.
- **Balance after** — the customer's balance immediately after this entry, so history
  reads like a bank statement.
- **Amount** — for purchases, the original money value of the purchase (used for
  auditing and analytics). Empty for other types.
- **Reason / reference** — a human note and/or an external correlation id such as an
  order number.
- **Who did it** — the staff member who performed a manual action (empty for actions
  driven by the POS API or automatic system rewards).

## The four transaction types

- **earn** — points from a purchase.
- **redeem** — points spent on a reward (always negative).
- **referral** — points awarded through the referral program (to a referrer or as a
  referee welcome bonus).
- **adjust** — a manual correction by staff, or the automatic signup bonus. Can be
  positive or negative.

## Balances cannot go negative

A change that would push a customer's balance below zero is rejected. In practice this
means a redemption or a negative adjustment fails if the customer doesn't have enough
points ("insufficient points balance"). Nothing is written to the ledger when this
happens.

## Safe retries (idempotency)

Point-changing actions can carry an **idempotency key** so that if the same request is
sent twice (for example, a POS retry after a network glitch), the points are applied
**only once**. The second attempt simply replays the original result. See
[POS API reference](10-pos-api-reference.md) for how to send one.
