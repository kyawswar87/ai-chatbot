---
doc_id: operator-dashboard
title: Operator Dashboard
summary: A task-oriented guide to the everyday staff screens under /dashboard.
audience: [admin, operator]
keywords: [dashboard, operator, customers, rewards, redemptions, settings, overview, adjust points, search customer, how do I]
---

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
