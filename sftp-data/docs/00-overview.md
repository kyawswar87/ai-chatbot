---
doc_id: overview
title: Platform Overview
summary: What the loyalty platform is, who uses it, and the main parts of the system.
audience: [admin, operator]
keywords: [loyalty, overview, what is, points program, customers, staff, dashboard, POS API]
---

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
