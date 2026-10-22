# Order → AH workflow

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

This document records the confirmed market rules and the currently implemented, testable Order→AH core. The server-specific GUI port remains opt-in and must not be treated as live until it can identify the order-row lore, the current player's order detail/claim action, and the confirmation amounts exactly.

## Confirmed rules

- Order size defaults to **1,024 items** and is adjustable in the UI from 256, 512, 1,024, or 2,048; the upper bound is limited to the largest quantity confirmed by the supplied GUI references.
- Bid at the current competing top Order price plus **$1 per item**.
- Compare AH prices as **stack prices** and list each inventory stack at exactly **$1 below** the cheapest comparable stack.
- AH tax is modeled as **0%**; the Order creation fee is **1%**. The GUI screenshot shows $1 on a $9 deposit, so the estimate rounds the fee up to the next whole currency unit. The server confirmation GUI remains authoritative; a future live adapter must reject a confirmation if item, quantity, unit price, deposit, fee, or total differs from the fresh plan.
- The minimum expected net profit defaults to **$1 per item** and is adjustable upward in the UI; the validator never permits lowering it below $1.
- Rank trades by net profit per expected hour, using both observed Order fill rate and AH sales speed. A quote must be fresh (at most two minutes), have enough independent evidence, and predict a full fill within four days to leave margin before the five-day delivery expiry.
- UI-configurable limits may be lowered to 1–10 total active orders and 1–2 per item; the upper ceilings remain **10** and **2** respectively. Fully filled but unclaimed orders still occupy a slot.
- Each fully completed order is one order-completion observation, independent of the AH completed-sale table. Individual deliveries are not observations.
- Work on one completed order at a time; collect only that order, list stacks serially, re-read the AH price before every stack, and do not process another completed order until the current batch is verified.

## Data-source separation

The Order book and AH are distinct evidence sources. Open Orders are not completed AH transactions. The schema migration adds an `order_completion_observations` table keyed by the server order ID; it does not insert Order fills into AH sale history. The current API client has no order IDs or Order fills, so a GUI/API adapter must supply this evidence without inferring sales from disappearing listings.

## GUI reference screenshots

The user-provided images are kept under [`screenshots/`](screenshots/) for implementation and review. They show the `ORDERS` screen, sorting by “Am meisten Geld pro Item”, `Deine Orders` status filters, create button, `ITEM AUSWÄHLEN` picker, sign prompts `Anzahl eingeben` and `Preis pro Stück`, the 1% fee, a five-day claim warning, and collection controls. The latest supplied references show a complete 2,048/2,048 order, 1,033 items ready to collect, and a displayed `4d 2h` expiry. They establish useful delivery/status UI states, but not a stable unique order ID or permission to collect a partial delivery. The user has explicitly kept automatic partial collection disallowed. The planner nevertheless rejects fill forecasts above four days and emits a manual-review warning for partial deliveries approaching expiry.

The Order settings page provides a separately selectable item whitelist and configurable order-size, total-order, per-item, minimum-profit, and local-capture bounds. Whitelist-only mode is explicit: if enabled with an empty list, no item is eligible (fail-closed); when disabled, the list is retained but all supported items may be considered. Preferences persist in `config/sc-market/order-module.properties`.

## Live-safety boundary

The current GUI evidence does not yet expose enough data to safely automate the server-specific Order row and collection interaction: the actual tooltip/lore fields for an Order (especially order identity, per-item price, fill state, and delivery time) and the player's completed-order detail/claim screen are not shown. The order core therefore fails closed until a private profile adapter can verify those fields and the exact confirmation amounts. Do not populate a public-server profile or bypass the exact-server allowlist, private profile, per-trade confirmation mode, or action pacing. There is no separate ARM SESSION phrase; the allowlist is the server-access gate, while all other safety and verification checks remain mandatory.
