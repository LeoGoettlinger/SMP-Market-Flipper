# Profit, ROI, and capital risk (Chunk 6)

Chunk 6 evaluates the conservative value from Chunk 5 against a listing's
purchase price. It does not calculate confidence, manipulation risk, or final
opportunities, and it performs no auction action.

## Formula

```text
gross profit = conservative fair value - purchase price

purchase fee = purchase price × purchase fee percent / 100
sale fee = conservative fair value × sale fee percent / 100

effective safety percent
  = configured safety percent × fair-value safety multiplier

safety buffer = conservative fair value × effective safety percent / 100

estimated net profit
  = conservative fair value
  - purchase price
  - purchase fee
  - sale fee
  - flat costs
  - safety buffer

total acquisition cost = purchase price + purchase fee + flat costs

ROI = estimated net profit / total acquisition cost × 100
```

Currency additions and subtractions are exact `BigDecimal` operations. Division
uses DECIMAL128. A zero acquisition cost never reaches division; ROI is absent
and the evaluation is rejected explicitly.

## Defaults and assumptions

| Setting | Default |
| --- | ---: |
| Purchase fee | 0% |
| Sale fee | 0% |
| Flat cost | 0 |
| Safety buffer | 5% |
| Minimum gross profit | 0 |
| Minimum net profit | 0 |
| Minimum ROI | 0% |
| Maximum purchase price | Unlimited |
| Bankroll allocation | Disabled |
| Open-exposure limit | Disabled |

No DonutSMP fee is assumed or claimed. Purchase fee, sale fee, flat cost, and
safety buffer are user-entered assumptions. Chunk 5 can recommend a safety
multiplier of `1.25` or `1.50`; for example, a configured 5% buffer becomes
6.25% or 7.5% respectively.

## Threshold precedence

Per-fingerprint settings may override minimum gross profit, minimum net profit,
and minimum ROI. Global and item maximum-purchase ceilings are evaluated
separately, so an item override cannot bypass the global capital ceiling.

Every failed rule is returned as a structured `ProfitRejection`; evaluation
does not stop at the first failure. Reasons cover unavailable fair value,
non-positive purchase price, gross/net/ROI minimums, both purchase-price caps,
bankroll allocation, and projected open exposure.

## Capital policy

The evaluator never reads or infers the player's real balance. Bankroll limits
work only when the user supplies a manual assumed bankroll and maximum percent:

```text
maximum acquisition capital = assumed bankroll × configured percent / 100
```

Open exposure uses:

```text
projected exposure = current configured/open exposure + total acquisition cost
```

Both rules are optional. Current exposure is an explicit request input; no
Minecraft inventory, scoreboard, command, or API balance is inspected.

## Examples

Default-cost example:

```text
Conservative value: 1,000
Purchase price: 600
Fees: 0
Safety buffer: 5% = 50

Gross profit: 400
Net profit: 350
Acquisition cost: 600
ROI: 58.3333...%
```

Configured-cost example:

```text
Conservative value: 1,000
Purchase price: 600
Purchase fee: 10% = 60
Sale fee: 5% = 50
Flat cost: 25
Safety buffer: 5% = 50

Net profit = 1,000 - 600 - 60 - 50 - 25 - 50 = 215
Acquisition cost = 600 + 60 + 25 = 685
ROI = 215 / 685 × 100 = 31.3868...%
```

Future opportunity detection must require `ProfitEvaluation.accepted()` and
retain the complete rejection list for explanations and diagnostics.
