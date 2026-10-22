# Market statistics (Chunk 4)

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

Chunk 4 adds completed-sale comparison and descriptive market statistics. It
does **not** calculate fair value, profit, ROI, confidence, manipulation risk,
or opportunities. Active asks remain separate supporting evidence and never
enter completed-sale statistics.

## Selection pipeline

For a normalized item, `ComparableSaleFinder`:

1. Requires the same SHA-256 item fingerprint.
2. Applies the selected inclusive lookback cutoff and rejects future timestamps.
3. Rejects unsupported items and non-positive comparable prices.
4. Converts commodity totals to unit prices with `BigDecimal` and DECIMAL128.
5. Applies MAD filtering, followed by an IQR sanity check.
6. Returns immutable accepted and rejected lists; every rejection has a reason.

SQLite only filters fingerprint and time. Price columns are lossless decimal
text, so numeric sorting and calculations happen in Java rather than relying on
lexicographic SQL ordering.

Supported windows are 1 hour, 6 hours, 24 hours, 3 days, 7 days, 30 days, and
all stored history. A caller can request independent snapshots with different
windows for fast, primary, and long-term comparisons; this chunk deliberately
does not combine those snapshots into a valuation.

## Formulas

All price arithmetic uses `BigDecimal` with `MathContext.DECIMAL128`.

- Commodity price: `total sale price / item count` (unless a validated unit
  price is already stored).
- Mean: `sum(x) / n`.
- Median: 50th percentile of sorted observations.
- Percentile: linear interpolation at zero-based position `(n - 1) * p`.
- IQR: `P75 - P25`.
- MAD: `median(abs(x - median(x)))`.
- Population standard deviation: `sqrt(sum((x - mean)^2) / n)`.
- Coefficient of variation: `population standard deviation / mean`; it is a
  ratio, not a percentage.
- Weighted median: sort by price and find the point crossing half the total
  recency weight. If a cumulative weight lands exactly at half, use the midpoint
  between that price and the next one.
- Sales/hour: accepted sale count divided by configured-window hours. For all
  history, use the span from the oldest accepted sale to the snapshot time,
  clamped to at least one hour.
- Sales/day: `sales/hour * 24`.
- Active seller concentration: the largest seller's percentage of active
  listings. Unknown sellers receive listing-local identities instead of being
  incorrectly grouped together.

Unique participant counts include only known seller or buyer identities.

## Recency defaults

Age bands are half-open at the upper boundary:

| Sale age | Weight |
| --- | ---: |
| Less than 1 hour | 1.00 |
| 1 hour to less than 6 hours | 0.90 |
| 6 hours to less than 24 hours | 0.75 |
| 1 day to less than 3 days | 0.55 |
| 3 days to less than 7 days | 0.35 |
| 7 days or older | 0.20 |

## Default safeguards

| Setting | Default |
| --- | ---: |
| Main lookback | 7 days |
| Minimum comparable sales | 8 |
| Minimum sample for outlier filtering | 8 |
| Modified z-score threshold | 3.5 |
| IQR fence multiplier | 1.5 |
| Stale after newest sale | 6 hours |
| Maximum completed rows per snapshot | 10,000 |
| Maximum active asks per snapshot | 10,000 |

The live opportunity composition derives this minimum from its fair-value profile and
uses four comparable sales. Standalone/core statistics retain the conservative default
of eight.

MAD filtering uses `0.6745 * abs(x - median) / MAD`. If MAD is zero, that stage
is skipped and the IQR stage can still reject values outside its fences. If the
initial sample is below the configured minimum, neither method removes rows. If
MAD filtering leaves fewer than the minimum, the secondary IQR stage is skipped.

## Low-data and stale behavior

Snapshots below the minimum sample count are marked `lowData` and include a
human-readable explanation. Empty and unsupported results contain no fabricated
statistics. A snapshot is stale when its newest accepted sale is older than the
configured threshold. Future confidence and opportunity chunks must use these
flags to cap confidence and suppress priority alerts.

## Threading and integration

`RepositoryMarketStatisticsService` implements `MarketStatisticsProvider` and
combines bounded sale and active-listing futures. SQLite work runs on the
database executor, and the GUI can consume the completed immutable snapshot
without querying or calculating during rendering.
