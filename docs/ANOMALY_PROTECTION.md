# Market-anomaly protection (Chunk 8)

Chunk 8 evaluates whether market evidence may be misleading. It uses neutral
labels such as “unusual price movement” and “concentrated seller activity.” It
does not accuse a player of manipulation, generate an opportunity, send an
alert, or perform any auction action.

## Evidence evaluated

`ManipulationRiskDetector` examines:

- Completed sales removed by Chunk 4's MAD or IQR filters, classified above or
  below the accepted median.
- Recent versus long-term completed-sale median movement.
- Whether a price movement has substantial recent completed-sale volume.
- Largest known seller and buyer shares.
- Repeated identical low-volume prices far from the long-term median.
- Reverse-direction and rapidly alternating seller/buyer pairs when both
  participant identities exist.
- Commodity lowest asks relative to the primary completed-sale median.
- Newly observed custom metadata fingerprints with limited history.
- Approximate or unsupported item metadata.

Unknown participant identities are not invented. Current asks remain supporting
supply evidence and are never converted into completed sales.

## Default thresholds

| Check | Default |
| --- | ---: |
| Minimum sales for ordinary assessment | 3 |
| Sudden recent/long-term movement | 20% |
| Sharp movement | 40% |
| Low-volume maximum | 5 recent sales |
| Concentration warning | 60% |
| High concentration | 80% |
| Repeated identical trade count | 3 |
| Unusual identical-price deviation | 25% |
| Far-low commodity ask | Below 70% of median |
| Far-high commodity ask | Above 150% of median |
| Circular-trade comparison window | 1 hour |
| Rapid alternation window | 15 minutes |
| Rapid alternation threshold | 2 reverse transitions |
| New custom-variant age | At most 24 hours |
| New custom-variant history | At most 7 accepted sales |

A 20% or larger movement backed by more than five recent sales receives only
five risk points and is described as volume-supported. This prevents a broad,
legitimate market trend from being treated like a low-volume spike. Low-volume
movements add a separate high-severity indicator.

## Risk aggregation

Indicators contribute documented integer points; their sum is capped at 100.
Default levels are:

| Risk score | Level |
| --- | --- |
| 0–19 | `LOW` |
| 20–39 | `MODERATE` |
| 40–69 | `HIGH` |
| 70–100 | `SEVERE` |
| Insufficient sales and no indicators | `UNKNOWN` |

Representative indicator weights include 20 for a robustly removed high or low
sale, 15/30 for concentrated activity, 20 for repeated identical unusual
trades, 25 for a circular participant pattern, and 30 for a low-volume spike.
The complete indicator list and per-result explanations remain available in the
immutable assessment.

## Downstream effects

| Level | Confidence reduction | Confidence cap | Safety multiplier | Suppress sound | Reject |
| --- | ---: | ---: | ---: | --- | --- |
| `LOW` | 0 | 100 | 1.00 | No | No |
| `MODERATE` | 5 | 85 | 1.10 | No | No |
| `HIGH` | 15 | 60 | 1.25 | Yes | No |
| `SEVERE` | 25 | 25 | 1.50 | Yes | Yes |
| `UNKNOWN` | 10 | 35 | 1.25 | Yes | No |

`confidenceConstraint()` feeds the reduction and cap into Chunk 7.
`applySafetyBufferGuidance()` raises, but never lowers, Chunk 5's recommended
multiplier; Chunk 6 then applies it to the configured user safety percentage.
For example, severe risk turns a user-assumed 5% safety buffer into 7.5%.

Severe assessments include an explicit rejection explanation listing the most
important neutral evidence. Opportunity and notification stages must retain
that explanation and honor `rejected` and `suppressSoundAlerts` when they are
implemented in later chunks.
