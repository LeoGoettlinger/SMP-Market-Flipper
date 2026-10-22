# Confidence scoring (Chunk 7)

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

Chunk 7 produces an explainable integer confidence score from 0 through 100.
It does not detect manipulation, accept opportunities, or trigger alerts. The
generic external cap is an input hook for Chunk 8; it performs no risk detection
on its own.

## Category weights

| Category | Default maximum |
| --- | ---: |
| Comparable completed sales | 25 |
| Seller diversity | 15 |
| Price stability | 20 |
| Item-match quality | 20 |
| Liquidity | 15 |
| Freshness | 5 |

Weights are configurable, non-negative integers, but construction fails unless
their sum is exactly 100. Each base category is proportionally scaled to its
configured weight and rounded half-up. The uncapped total is the sum of all six
scaled categories.

## Comparable completed sales

| Accepted sales | Base points |
| --- | ---: |
| 0 | 0/25 |
| 1–2 | 3/25 |
| 3–5 | 8/25 |
| 6–10 | 15/25 |
| 11–20 | 21/25 |
| 21 or more | 25/25 |

Only completed sales accepted by Chunk 4 count. Fewer than three also activates
the 35-point hard cap.

## Seller diversity

The 15 base points are split into three five-point components:

- Unique known sellers, reaching five points at five sellers.
- Largest-seller share, full points at 20% or less and zero at 100%.
- Top-three-seller share, full points at 50% or less and zero at 100%.

Shares between boundaries are linearly interpolated. Unknown seller identities
are not fabricated or grouped together; missing participant data produces a
warning. A largest share of at least 60% or top-three share of at least 90%
produces a neutral concentration warning.

## Price stability

Four components contribute five base points each. Lower values are better:

| Metric | Full points | Zero points |
| --- | ---: | ---: |
| Coefficient of variation | ≤ 0.10 | ≥ 0.50 |
| MAD / median | ≤ 0.05 | ≥ 0.30 |
| IQR / median | ≤ 0.15 | ≥ 0.80 |
| Absolute recent/long-term divergence | ≤ 5% | ≥ 30% |

Intermediate values are linearly interpolated. A coefficient above 0.30 emits
a high-volatility warning. A coefficient at least 0.75 activates the default
50-point extreme-volatility cap.

## Item matching

| Match type | Base points |
| --- | ---: |
| Complete exact | 20/20 |
| Exact with normalization issues | 17/20 |
| Commodity | 15/20 |
| Approximate | 8/20 |
| Unsupported | 0/20 |

Approximate items are capped at 60. Unsupported items are capped at 20.

## Liquidity

Liquidity awards:

- Up to 10 points, linearly reaching the maximum at 12 completed sales/day.
- Up to 3 points, reaching the maximum at eight recent completed sales.
- Up to 2 points from median time between completed sales: 2 at one hour or
  less, 1.5 through six hours, 1 through 24 hours, 0.5 through 72 hours.

Active asks never add liquidity points because supply is not demand. When active
listing count exceeds accepted primary-window sales, up to three points are
removed and a high-supply warning is recorded.

## Freshness

Freshness contains three components:

- Newest completed sale: up to 3 points.
- Statistics snapshot age: up to 1 point.
- Listing age: up to 1 point.

Newest sales receive 3 points through one hour, 2 through six hours, 1 through
24 hours, and zero afterward. Snapshot and listing ages use shorter stepped
boundaries documented directly in category evidence. Stale or absent completed
history emits a warning.

## Hard caps

| Trigger | Default maximum confidence |
| --- | ---: |
| Fewer than 3 comparable sales | 35 |
| Approximate item match | 60 |
| Unsupported item | 20 |
| Coefficient of variation ≥ 0.75 | 50 |

Multiple caps may be active; the lowest maximum wins. `ExternalConfidenceCap`
allows the anomaly system to apply both a point reduction and a stricter cap,
such as a 25-point reduction and 25 maximum for severe risk. Adjustments and
caps can never increase the raw score.

## Tooltip integration contract

`ConfidenceTooltipFormatter` converts the immutable breakdown into GUI-neutral
lines containing the total, all category scores, caps, and warnings. The current
dashboard remains on mock opportunity providers; production opportunity-card
wiring belongs to the later backend-to-GUI integration chunk.
