---
doc_id: rewards-and-redemptions
title: Rewards and Redemptions
summary: The rewards catalog, redeeming points for a voucher, and the voucher lifecycle.
audience: [admin, operator]
keywords: [rewards, catalog, redeem, redemption, voucher, voucher code, stock, active, points cost, used, void, insufficient]
---

# Rewards and Redemptions

## The rewards catalog

Rewards are the things customers spend points on. Each reward has:

- **Name** and an optional **description**.
- **Points cost** — how many points it takes to redeem.
- **Active flag** — only **active** rewards can be redeemed. Deactivating a reward
  hides it from redemption without deleting it or its history.
- **Stock** — how many are available. **Leaving stock empty means unlimited.** A
  numeric stock is decremented by one on each redemption.

Staff create rewards and toggle them active/inactive from **Dashboard → Rewards**. See
[Operator dashboard](07-operator-dashboard.md).

## Redeeming points for a voucher

When a customer redeems a reward, the system, in one atomic step:

1. Checks the reward is **active** and (if stock is tracked) **in stock**.
2. Checks the customer has **enough points**.
3. Subtracts the points (a negative **redeem** entry in the ledger).
4. Decrements the reward's stock (if tracked).
5. Issues a unique **voucher code** and records a redemption marked **issued**.

If the customer doesn't have enough points, or the reward is inactive or out of stock,
the redemption is rejected and **no points are spent** (over the API this returns a
`422` — see [POS API reference](10-pos-api-reference.md)).

## Voucher codes

Each redemption produces a voucher code in the format **`V-XXXXXXXX`** (the prefix
`V-` followed by 8 characters). The characters use an unambiguous alphabet — no
`0`/`O` or `1`/`I`/`L` — so codes are easy to read aloud and type. Every voucher code
is unique.

## Voucher lifecycle

A voucher has one of three statuses:

- **issued** — created and ready to use (the starting state).
- **used** — redeemed/consumed. A voucher can be marked used **only once**; trying to
  use an already-used or void voucher is rejected (a `409` conflict over the API).
- **void** — cancelled/invalidated.

Staff mark a voucher as used from **Dashboard → Redemptions**, or the POS integration
does it via `POST /api/v1/redemptions/:id/use`. See
[Operator dashboard](07-operator-dashboard.md).

## Note on redemptions and stock

Redeeming spends points and (for tracked rewards) reduces stock at the same time, so
you can't oversell a limited reward. Marking a voucher **used** later does **not**
change points or stock — the points were already spent at redemption time; "used" just
records that the customer has now claimed it.
