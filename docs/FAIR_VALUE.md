# Fair-value calculation (Chunk 5)

Chunk 5 adds a conservative, explainable fair-value estimate. It does not
calculate profit, ROI, confidence, manipulation risk, or opportunities. The
GUI remains on mock providers until the designated integration chunk.

## Input windows

`StatisticsBackedFairValueService` asynchronously requests three independent
completed-sale snapshots:

| Purpose | Default window |
| --- | ---: |
| Recent trend | 6 hours |
| Primary estimate | 3 days |
| Long-term baseline | 30 days |

The windows must describe the same normalized SHA-256 fingerprint. The conservative
core profile requires eight accepted primary sales. The live opportunity profile now
requires four primary sales and two trend sales, while still rejecting stale data.
Otherwise the three fair-value fields are empty; observed completed-sale bounds can
still be displayed as raw evidence.

## Formula

All arithmetic uses `BigDecimal` with DECIMAL128 precision. Values remain exact
decimals and are not rounded for presentation inside the estimator.

Primary completed-sale candidates are:

```text
W = recency-weighted median
P40 = interpolated 40th percentile
A2 = credible second-lowest active ask, when available

base conservative unit value = min(W, P40, credible A2)
```

An active ask is credible only when:

```text
P25 × 0.70 <= A2 <= P75 × 1.30
```

Missing or implausible asks are recorded and ignored. The highest current ask
is never used, and an ask is never treated as a completed sale.

Downward adjustments are multiplicative:

```text
adjusted conservative unit value
  = base × product(1 - adjustmentPercent / 100)

listing conservative value
  = min(adjusted base, primary median) × commodity stack count
```

Exact customized-item fingerprints use whole-listing completed-sale prices and
a multiplier of one. The output range is:

```text
Conservative = downward-adjusted conservative candidate
Central      = primary completed-sale median
Optimistic   = max(primary P75, primary median)
Observed low = minimum accepted completed sale
Observed high = maximum accepted completed sale
```

Only the conservative value is intended for future profit alerts.

## Trend and safety behavior

Trend change is:

```text
(recent median - long-term median) / long-term median × 100
```

| Condition | Result |
| --- | --- |
| Absolute change at most 5% | `STABLE` |
| Change above 5% | `RISING` |
| Change below -5% | `FALLING` |
| Fewer than 3 sales in either trend window | `UNKNOWN` |
| Rise at least 20% | Rapid-rise warning |

Rising prices are never extrapolated upward. Falling prices reduce the
conservative value by 5%. A rapid rise emits an elevated-risk warning for the
future confidence/manipulation stages.

## Other default safeguards

| Safeguard | Trigger | Conservative reduction |
| --- | --- | ---: |
| High volatility | coefficient of variation above 0.30 | 10% |
| High active supply | active listings / accepted sales above 1.0 | 5% |
| Falling market | recent-to-long-term change below -5% | 5% |

The output also recommends a future safety-buffer multiplier:

- `1.00` for ordinary evidence.
- `1.25` for falling markets or high active supply.
- `1.50` for high volatility or a rapid rise.

This is guidance for Chunk 6; no fees, buffers, profit, or ROI are calculated
in this chunk.

## Example calculations

Stable commodity market:

```text
Accepted sales: 8
Weighted median: 101
P40: 100
Second-lowest credible ask: 104
Primary median: 101
P75: 104.25

Conservative unit value = min(101, 100, 104) = 100
Central unit value = 101
Optimistic unit value = 104.25
```

For a stack of four, the returned values are `400`, `404`, and `417`.

Falling market with the same primary evidence:

```text
Recent median: 80
Long-term median: 100
Trend change: -20%
Base conservative value: 100
Falling-market reduction: 5%
Conservative value: 95
Recommended safety-buffer multiplier: 1.25
```

Every result carries structured inputs, accepted and rejected sale counts,
active-ask status, trend change, applied reductions, warnings, and GUI-ready
explanation notes.
