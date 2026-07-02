---
doc_id: faq
title: Frequently Asked Questions
summary: Common operator and admin questions with short, direct answers.
audience: [admin, operator]
keywords: [faq, help, how do I, why, questions, troubleshooting, common problems, points, voucher, referral, api]
---

# Frequently Asked Questions

## Customers and points

### How do I give a customer points manually?
Open **Dashboard → Customers**, find the customer, open their detail page, and use
**Adjust points**. Enter a positive number to add or a negative number to subtract. The
change is recorded as an "adjust" entry stamped with your account. You can't take a
balance below zero. See [Operator dashboard](07-operator-dashboard.md).

### How are points calculated on a purchase?
`points = floor(amount × earn rate)`, rounded down to a whole number. The earn rate is
set in **Dashboard → Settings**. See [How customers earn points](03-earning-points.md).

### Why does a customer's balance look wrong?
Every point change is in their ledger (newest first) on the detail page — read it top to
bottom to see exactly what happened. Balances can never go below zero and always match
the ledger. See [Points and the ledger](02-points-and-ledger.md).

### Can I delete or edit a past transaction?
No. The ledger is append-only for auditability. To correct something, make a new
**adjust** entry that offsets it.

### Why can't I subtract points / redeem — it says insufficient balance?
The customer doesn't have enough points for that debit. Balances can't go negative, so
the action is rejected and nothing is written.

## Referrals

### Why didn't the referrer get their reward?
The referrer is paid **the first time the referred customer earns points** (a purchase),
not at signup. Until then the referral is "pending". Check that (a) the referral was
captured at the referee's signup, (b) the referral referrer points setting is above 0,
and (c) the referee has made a qualifying earn. See [Referrals](05-referrals.md).

### Does the referred customer get anything at signup?
Only if the "referral referee points" welcome bonus is configured above 0. It's granted
immediately when they're created with a valid referral code.

### Where do customers find their referral code?
Every customer automatically has one (format `R-XXXXXXXX`), visible on their detail
page.

## Rewards and vouchers

### A customer says their voucher won't work.
Check the voucher's status under **Dashboard → Redemptions**. If it's already **used**
or **void**, it can't be used again — a voucher can be marked used only once. If it's
**issued**, it's still valid.

### What does deactivating a reward do?
It hides the reward from new redemptions without deleting it or its history. Reactivate
it any time. See [Rewards and redemptions](04-rewards-and-redemptions.md).

### What does empty stock mean on a reward?
Empty stock = **unlimited**. A numeric stock is decremented by one on each redemption
and blocks redemption at zero.

## Accounts and access

### How do I add a new staff member?
An admin creates them under **Admin → Staff** (choose operator or admin). There is no
public sign-up. See [Roles and access](01-roles-and-access.md).

### What's the difference between Admin and Operator?
Operators run the everyday dashboard. Admins can do everything operators can, plus
analytics, staff management, and API keys. See [Roles and access](01-roles-and-access.md).

### How do I reset a forgotten admin password?
Re-run the database seed (`npm run db:seed`) with the desired `ADMIN_EMAIL` /
`ADMIN_PASSWORD` — it resets that admin's password. For other staff, an admin can manage
the account under **Admin → Staff**.

### I disabled someone but they're still logged in — are they still in?
No. A disabled account loses access immediately; the account's status is re-checked on
every request, so an existing session stops working right away.

## POS API

### Why did my API call return 429?
You hit the rate limit — 60 requests per minute per API key. Wait the number of seconds
in the `Retry-After` header and retry. See [POS API reference](10-pos-api-reference.md).

### How do I avoid double-crediting points on a retry?
Send an `Idempotency-Key` header (e.g. the order id). A repeat with the same key replays
the original result instead of applying it twice.

### Why did a redemption return 422?
The customer had too few points, or the reward was inactive or out of stock. No points
are spent when this happens.

## Reporting

### What does "points liability" mean?
The total points currently outstanding across all customers — what you may still have to
honor. See [Analytics and metrics](09-analytics-and-metrics.md).

### Why isn't a POS-driven action showing in staff activity?
Staff activity only counts actions performed by a signed-in staff member. Actions via
the POS API or automatic system rewards aren't attributed to any staff member.

## The assistant itself

### Why doesn't the assistant remember what I just asked?
It's single-turn today — each question is answered independently, and the chat clears on
reload. Ask complete, self-contained questions. See [Chat assistant](11-chat-assistant.md).
