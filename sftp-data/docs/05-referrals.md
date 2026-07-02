---
doc_id: referrals
title: Referrals
summary: How the referral program works — referral codes, referrer and referee rewards, and when the referrer is paid.
audience: [admin, operator]
keywords: [referral, referral code, referrer, referee, refer a friend, welcome bonus, referral reward, pending, rewarded, first earn]
---

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
