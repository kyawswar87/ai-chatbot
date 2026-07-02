---
doc_id: admin-dashboard
title: Admin Dashboard
summary: The admin-only screens under /admin — analytics, staff management, and API keys.
audience: [admin]
keywords: [admin, analytics, staff, staff management, api keys, create operator, disable account, revoke key, roles]
---

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
