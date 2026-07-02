---
doc_id: analytics-and-metrics
title: Analytics and Metrics
summary: Precise definitions of every number shown in analytics and the overview.
audience: [admin, operator]
keywords: [analytics, metrics, points issued, points redeemed, liability, top rewards, staff activity, redemptions per day, definitions]
---

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
