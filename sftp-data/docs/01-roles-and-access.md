---
doc_id: roles-and-access
title: Roles and Access
summary: The Admin and Operator staff roles, how logins work, and how access is controlled.
audience: [admin, operator]
keywords: [roles, admin, operator, login, permissions, access, staff accounts, disable, RBAC, sign in]
---

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
