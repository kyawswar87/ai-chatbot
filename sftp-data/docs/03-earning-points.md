---
doc_id: earning-points
title: How Customers Earn Points
summary: The ways points are added to a customer — purchases, signup bonus, referrals, and manual adjustments.
audience: [admin, operator]
keywords: [earn, earning, points, purchase, earn rate, signup bonus, referral, manual adjust, floor, how points work]
---

# How Customers Earn Points

Points can be added to a customer in four ways.

## 1. Purchases (earn rate)

The main way customers earn. When a purchase is recorded, points are calculated from
the money spent using the program's **earn rate**:

> **points earned = floor(amount × earn rate)**

The result is **rounded down** to a whole number. For example, with an earn rate of
`1`, a purchase of `100` earns `100` points; with an earn rate of `0.5`, the same
purchase earns `50` points; a purchase of `99.5` at rate `1` earns `99` (rounded
down).

Purchases are normally recorded automatically by the point-of-sale / e-commerce
integration through the POS API (`POST /api/v1/transactions`). The earn rate is set in
program settings — see [Program configuration](06-program-configuration.md).

## 2. Signup bonus

When a **new customer is first created**, they can receive a one-time **signup bonus**
of points. This is optional and controlled by the `signup bonus` setting (0 means no
bonus). It is recorded as an **adjust** entry with the reason "Signup bonus". A repeat
create for a customer who already exists does **not** grant the bonus again.

## 3. Referrals

Customers can refer friends. The referred friend can get a **welcome bonus** at
signup, and the **referrer** gets a reward the first time their friend earns points.
This is a whole topic on its own — see [Referrals](05-referrals.md).

## 4. Manual adjustments by staff

An operator or admin can add (or subtract) points by hand from a customer's detail
page in the dashboard — for example to fix a mistake or apply a goodwill gesture.
These are recorded as **adjust** entries and are stamped with the staff member who
made the change. See [Operator dashboard](07-operator-dashboard.md).

## Where earned points show up

Every earn — of any kind — appears in the customer's ledger and immediately updates
their balance. See [Points and the ledger](02-points-and-ledger.md).
