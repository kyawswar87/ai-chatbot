---
doc_id: program-configuration
title: Program Configuration (Settings)
summary: The program-wide settings — earn rate, referral rewards, signup bonus, and currency — and what each one does.
audience: [admin, operator]
keywords: [settings, configuration, earn rate, signup bonus, referral points, currency, program config, how to change]
---

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
