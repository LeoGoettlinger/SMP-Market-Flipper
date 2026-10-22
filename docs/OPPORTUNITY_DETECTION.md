# Chunk 9: Opportunity detection

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

Chunk 9 combines the completed local market-analysis domains into a deterministic opportunity
evaluation pipeline. It does not poll the API, schedule scans, send Minecraft commands, click
inventory slots, display alerts, or purchase items.

## Evaluation order

`OpportunityEvaluator` applies the following order:

1. Validate the listing, normalized item, stack count, fingerprint, and market snapshot.
2. Require an active listing.
3. Apply `ALL_ITEMS`, `WHITELIST_ONLY`, or `ALL_EXCEPT_BLACKLIST`.
4. Apply the item profile and supported-match policy.
5. Assess manipulation and anomaly risk.
6. Estimate conservative fair value from completed-sale evidence.
7. Calculate gross profit, net profit, ROI, configured costs, and capital exposure.
8. Calculate confidence with the risk adjustment and cap.
9. Apply global confidence and sample thresholds.
10. Apply item-specific confidence, sample, profit, ROI, and purchase-price overrides.
11. Produce an accepted or rejected structured evaluation.

The relaxed live profile accepts confidence scores of **5% or higher** and requires
four accepted comparable sales. Conservative fair value also starts at four primary
sales and two trend sales. These are 50% reductions from the prior live gates.
Severe manipulation risk, stale data, unsupported metadata, and listing verification
remain independent fail-closed checks.

The live board applies no category priority. Accepted opportunities retain the repository's
newest-first order, so ores, tools, blocks, food, farming, redstone, transport, decoration,
and utility categories are treated equally.

Every accepted evaluation retains its filter decision, item match type, accepted and rejected
sale counts, risk assessment, fair-value estimate, profit breakdown, confidence breakdown,
effective thresholds, applied override status, and passed checks. Rejected evaluations retain
the same evidence that was available plus concise typed rejection reasons.

## Identity, changes, and alert gating

`OpportunityIdFactory` creates a stable SHA-256 identifier from the listing key, evaluation
version, and item fingerprint. A listing price change therefore reevaluates the same logical
opportunity instead of creating a duplicate.

`OpportunityDetector` evaluates only when one or more of these events occur:

- a listing is first observed;
- its price or active state changes;
- the completed-sale version changes;
- the configuration version changes;
- the filter version changes;
- the prior evaluation becomes stale; or
- an active listing is restored after scanner restart.

An unchanged listing is returned as a suppressed duplicate. The detector exposes restorable
tracking snapshots so later scanner orchestration can persist the last revision, state, and
alert time without coupling the evaluator to the database.

High-priority alert eligibility is data only. It requires an accepted evaluation, permitted
risk, a non-terminal state, no silent-display risk policy, an elapsed cooldown, and an explicit
`recordAlerted` call after the future notification layer actually delivers the alert. Accepted
but higher-risk opportunities can remain visible while alert-suppressed.

## Example evaluations

Strong listing (stable mock market around `$99`, purchase price `$50`):

```text
State: NEW
Accepted: true
Alert eligible: true
Comparable sales: 40 accepted
Checks: active listing, filter pass, supported commodity, non-severe risk,
        conservative fair value, profit/ROI/capital pass, confidence pass, sample pass
```

Rejected item-specific threshold (same market, item minimum net profit `$60`):

```text
State: REJECTED_EVALUATION
Accepted: false
Alert eligible: false
Reason: PROFIT_REQUIREMENT_FAILED
Override applied: true
```

Rejected filter example:

```text
Mode: ALL_EXCEPT_BLACKLIST
State: REJECTED_BY_FILTER
Reason: ITEM_BLACKLISTED
Fair value/profit/confidence: not run
```

## Chunk boundary

The future scanner orchestrator will supply validated `ListingEntity`, `NormalizedItem`, and
`FairValueMarketContext` snapshots, call `OpportunityDetector` on change events, persist
`OpportunityTrackingSnapshot`, and route accepted results to the GUI/HUD. Chunk 9 intentionally
contains no timing loop, network request, database query, command dispatch, slot interaction,
or automatic purchase behavior.
