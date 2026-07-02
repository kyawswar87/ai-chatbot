# Platform Overview

## What the platform is

This is a points-based **loyalty program for a single business**. Customers earn
points when they buy things and when they refer friends, and they spend those points
on rewards, which are issued as voucher codes. The program is run day to day by the
business's staff through a web dashboard.

It is currently an MVP (minimum viable product): the core earning, redeeming,
referral, staff, and reporting features are in place and working.

## Who uses it

There are two kinds of people in the system:

- **Staff** — the people who run the program. They have logins and use the
  dashboards. There are two staff roles: **Admin** and **Operator**. See
  [Roles and access](01-roles-and-access.md).
- **Customers** — the end shoppers who collect points. Customers **do not** have a
  login. They are created and updated automatically through the POS / e-commerce
  integration (the API), or manually by staff. Each customer is tracked by the
  business's own id for them (their "external id", e.g. a POS or online-store id).

## The main parts of the system

- **Operator dashboard** (`/dashboard`) — the everyday staff screens: customers,
  rewards, redemptions, program settings, and an overview. See
  [Operator dashboard](07-operator-dashboard.md).
- **Admin dashboard** (`/admin`) — admin-only screens for analytics, staff accounts,
  and API keys. See [Admin dashboard](08-admin-dashboard.md).
- **POS API** (`/api/v1/...`) — a programmatic interface so a point-of-sale system or
  online store can create customers and record purchases automatically. See
  [POS API reference](10-pos-api-reference.md).
- **Chat assistant** — a floating help assistant on every staff page that answers
  questions about the system (this is what these documents power). See
  [Chat assistant](11-chat-assistant.md).

## Core ideas in one paragraph

Every point movement is written to a permanent **ledger** (the source of truth), and
each customer has a running **points balance** kept in step with it. Points are
**earned** on purchases (a configurable rate turns money spent into points), on
**signup** (an optional bonus), and through **referrals**. Points are **redeemed**
for rewards from a catalog, which issues a **voucher code** the customer can use once.
Staff can also make manual **adjustments**. For definitions of any term, see the
[Glossary](13-glossary.md).

---

<!-- doc_id: roles-and-access -->
# Roles and Access

## The two staff roles

Staff accounts have one of two roles:

- **Operator** — everyday staff. Can use the operator dashboard: manage customers,
  rewards, redemptions, and program settings.
- **Admin** — full access. An **admin can do everything an operator can**, plus the
  admin-only screens: analytics, staff-account management, and API keys.

There is no separate permission list beyond these two roles. Admin is a superset of
Operator.

## Signing in

- Staff sign in at **`/login`** with an email and password.
- There is **no public sign-up**. Accounts are created by an admin (or by the initial
  seed for the very first admin). Customers never log in.
- After signing in, an operator lands on `/dashboard`; admins can also reach `/admin`.
  If an operator tries to open an admin-only page, they are redirected back to
  `/dashboard` (no privilege escalation).
- Visiting a staff page while signed out redirects to `/login`.

## How accounts are created

- **The first admin** is created by the database seed (`npm run db:seed`), using the
  `ADMIN_EMAIL` and `ADMIN_PASSWORD` values from the environment configuration.
  Re-running the seed **resets that admin's password** — this is the supported way to
  recover a forgotten admin login.
- **Additional staff** (operators or admins) are created by an admin from
  **Admin → Staff**. See [Admin dashboard](08-admin-dashboard.md).

## Disabling access

- Every account has a status: **active** or **disabled**.
- An admin can disable a staff account from **Admin → Staff**. A disabled account
  **loses access immediately** — even if it still holds a valid session, the next
  request is rejected because the account's live status is re-checked on every action.
- An admin **cannot disable or demote their own account** (a guard prevents locking
  yourself out).

## Programmatic access (API keys)

The POS / e-commerce integration does **not** use a staff login. It authenticates
with an **API key** sent as `Authorization: Bearer <key>`. API keys are created and
revoked by an admin under **Admin → API keys**. See
[POS API reference](10-pos-api-reference.md) and [Admin dashboard](08-admin-dashboard.md).

---

<!-- doc_id: points-and-ledger -->
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

---

<!-- doc_id: earning-points -->
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

---

<!-- doc_id: rewards-and-redemptions -->
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

---

<!-- doc_id: referrals -->
# Referrals

## Referral codes

Every customer automatically gets their own **referral code** in the format
**`R-XXXXXXXX`** (the prefix `R-` followed by 8 characters from an unambiguous
alphabet). A customer shares this code with friends; when a friend signs up using it,
a referral is created linking the two.

- The person who **shares** the code is the **referrer**.
- The person who **signs up** with the code is the **referee**.

## Capturing a referral

A referral is captured **at the moment a new customer is created** — the new
customer's signup includes the referrer's referral code. A customer can be referred by
**at most one** person, and the link is set once at creation (it can't be added
later).

## The two rewards

There are two separate, configurable referral rewards (see
[Program configuration](06-program-configuration.md)):

- **Referee welcome bonus** (`referral referee points`) — given to the **new customer**
  immediately at signup, if configured above 0. Recorded as a **referral** entry with
  the reason "Referral welcome bonus".
- **Referrer reward** (`referral referrer points`) — given to the **referrer**, but
  **not immediately**. See timing below.

## When the referrer is paid

The referrer is rewarded **the first time their referee earns points** — not at
signup. This prevents rewarding referrals that never turn into real customers.

- Until then the referral sits in the **pending** state.
- On the referee's first earn (a purchase), the referral flips to **rewarded** and the
  referrer's points are credited (a **referral** ledger entry).
- The payout happens **exactly once**, even under retries or simultaneous requests.
  If it has already been paid, later earns do nothing extra.

An earn response over the API includes a `referrerPaid` flag indicating whether that
particular earn triggered the referrer payout.

## Quick example

1. Alice shares her code `R-7H3KQ9MA`.
2. Bob signs up with that code → a **pending** referral is created; Bob gets the
   referee welcome bonus (if configured).
3. Bob makes his first purchase → he earns points as usual **and** Alice is paid the
   referrer reward; the referral becomes **rewarded**.
4. Bob's later purchases earn him points normally, but Alice is not paid again.

---

<!-- doc_id: program-configuration -->
# Program Configuration (Settings)

The program has a single set of program-wide settings that control how points are
earned and rewarded. Staff edit them at **Dashboard → Settings**. There is one shared
configuration for the whole business (not per-customer).

## The settings

- **Earn rate** — how many points a customer earns per unit of currency spent. Points
  on a purchase are `floor(amount × earn rate)`. Example: rate `1` → 100 spent earns
  100 points; rate `0.5` → 100 spent earns 50 points. Supports fractional rates.
- **Referral referrer points** — points awarded to the **referrer** when their referee
  first earns. See [Referrals](05-referrals.md).
- **Referral referee points** — the **welcome bonus** points given to a **new referred
  customer** at signup. Set to 0 to give no welcome bonus.
- **Signup bonus** — points given to **every new customer** at creation, referred or
  not. Set to 0 for no bonus. See [How customers earn points](03-earning-points.md).
- **Currency** — the three-letter currency code (e.g. `USD`) used for display. The
  program stores purchase amounts and shows the points liability in this currency.

## How changes take effect

Settings apply to **future** actions from the moment they are saved. Changing them does
**not** retroactively recalculate past earns, bonuses, or referral rewards already in
the ledger. For example, raising the earn rate only affects purchases recorded after
the change.

## Who can change settings

Both operators and admins can edit program settings from the operator dashboard.
(Admins additionally manage staff accounts and API keys — see
[Admin dashboard](08-admin-dashboard.md).)

## Defaults

Out of the box (from the initial seed), the earn rate is `1`, referral and signup
bonuses are `0`, and the currency is `USD`. Adjust these to fit the business.

---

<!-- doc_id: operator-dashboard -->
# Operator Dashboard

The operator dashboard (`/dashboard`) is the everyday staff workspace. Both operators
and admins can use it. All point changes made here go through the same engine the POS
API uses, so balances stay consistent, and every manual change is stamped with the
staff member who made it.

## Overview

The landing page shows headline numbers for the program at a glance:

- **Customers** — total number of customers.
- **Points liability** — total outstanding points across all customers (points you may
  still have to honor). See [Analytics and metrics](09-analytics-and-metrics.md).
- **Active rewards** — how many rewards are currently redeemable.
- **Open vouchers** — issued vouchers not yet used.

## Customers

- **Search and browse** the customer list.
- **Create a customer** manually (you supply their external id; email is optional).
- **Open a customer** to see their current balance, full point ledger (newest first),
  and details.
- **Adjust points** on the detail page — add or subtract points by hand (for example a
  correction or goodwill gesture). This writes an **adjust** entry stamped with your
  staff account. You cannot push a balance below zero.

## Rewards

- **Create rewards** with a name, points cost, and optional stock (leave stock empty
  for unlimited).
- **Activate / deactivate** a reward. Only active rewards can be redeemed;
  deactivating hides it without deleting its history. See
  [Rewards and redemptions](04-rewards-and-redemptions.md).

## Redemptions

- **List issued vouchers** and see their status.
- **Mark a voucher as used** when the customer claims it. A voucher can be marked used
  only once.

## Settings

- **Edit program configuration** — earn rate, referral rewards, signup bonus, and
  currency. Changes apply to future actions only. See
  [Program configuration](06-program-configuration.md).

## What's admin-only

Analytics, staff-account management, and API keys live on the **admin** dashboard and
are not available to operators. See [Admin dashboard](08-admin-dashboard.md).

---

<!-- doc_id: admin-dashboard -->
# Admin Dashboard

The admin dashboard (`/admin`) is available only to **admin** staff. Operators who try
to open it are sent back to `/dashboard`. Admins also keep full access to everything on
the [operator dashboard](07-operator-dashboard.md).

## Analytics

A reporting view of program health, including:

- **Points issued vs redeemed** — total points ever granted versus spent.
- **Points liability** — outstanding points across all customers.
- **Top rewards** — the most-redeemed rewards.
- **Staff activity** — how many point actions each staff member has performed.
- A **redemptions-per-day** chart over a recent window.

Exact definitions are in [Analytics and metrics](09-analytics-and-metrics.md).

## Staff

Manage the people who can sign in:

- **Create accounts** — add a new operator or admin (email, name, password, role).
  There is no public sign-up, so this is how all staff after the first admin are added.
- **Change a role** — promote an operator to admin or vice versa.
- **Enable / disable a login** — disabling an account revokes its access immediately.
- **Self-lockout guard** — you cannot disable or demote your own account.

See [Roles and access](01-roles-and-access.md) for how the roles differ.

## API keys

Manage programmatic access for the POS / e-commerce integration:

- **Create a key** — give it a label. The full key is shown **once, at creation**;
  only a hashed form is stored, so it can't be shown again. Copy it then and store it
  securely. Keys start with the prefix `lk_`.
- **Revoke a key** — deactivate a key so it can no longer be used. Requests with a
  revoked or unknown key are rejected with `401`.
- Each key records when it was **last used**.

See [POS API reference](10-pos-api-reference.md) for how keys are used in requests.

---

<!-- doc_id: analytics-and-metrics -->
# Analytics and Metrics

This page defines exactly how each reported number is calculated. All figures come
from the ledger and customer records, so they always reflect the source of truth.

## Points issued

The **total points ever granted** — the sum of all **positive** ledger entries (earns,
referral rewards, positive adjustments, signup bonuses). It only ever goes up.

## Points redeemed

The **total points ever spent** — the magnitude (absolute value) of all **negative**
ledger entries. Reported as a positive number.

## Points liability

The **total points currently outstanding** — the sum of every customer's current
balance. This is what the business may still have to honor. Roughly, liability ≈ points
issued − points redeemed − points removed by negative adjustments. It reflects live
balances, so it moves both up (earns) and down (redemptions).

## Total redemptions

The **count of redemptions** ever made (how many vouchers have been issued).

## Top rewards

The **most-redeemed rewards**, ranked by number of redemptions (up to the top 5). For
each it shows the redemption count and the total points spent on it.

## Staff activity

For each staff member, the **number of point actions they performed** (manual earns,
adjustments, redemptions done in the dashboard), ranked most-active first. Actions
driven by the POS API or automatic system rewards are **not** attributed to any staff
member, so they don't appear here.

## Redemptions per day

A **count of redemptions for each of the last N days** (14 by default), with days that
had no redemptions shown as zero. Days are bucketed in **UTC**.

## Overview counters (operator dashboard)

The operator [overview](07-operator-dashboard.md) shows a lighter set: total
**customers**, **points liability**, **active rewards**, and **open (issued) vouchers**.

---

<!-- doc_id: pos-api-reference -->
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

---

<!-- doc_id: chat-assistant -->
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

---

<!-- doc_id: faq -->
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

---

<!-- doc_id: glossary -->
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
