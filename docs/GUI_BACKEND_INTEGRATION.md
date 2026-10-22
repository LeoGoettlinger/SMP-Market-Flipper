# Chunk 11: Live backend and ClickGUI integration

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

Chunk 11 connects the provider-driven ClickGUI shell to immutable production-provider
snapshots. Rendering does not query SQLite, wait for futures, call the auction API, or evaluate
market data.

## Provider flow

```text
API page / scanner activity
        -> asynchronous ingestion and evaluation
        -> SQLite repositories
        -> LiveMarketSnapshotService.refresh()
        -> atomic immutable snapshot
        -> publication listeners (automatic execution) + client provider adapters
        -> ClickGUI render
```

The scanner publishes immutable lifecycle snapshots through its injected client executor. The
live GUI bundle watches listing, completed-sale, and statistics version changes. It also requests
a coalesced refresh after every successful recent-listings poll so availability can expire even
when the returned page is unchanged.

`LiveMarketSnapshotService` runs bounded count and joined opportunity queries. Before publishing,
it marks listings older than the 15-second verification window unverified and moves their active
opportunities to `NO_LONGER_AVAILABLE`. The joined query independently excludes stale or inactive
listings, and the client provider applies the same time cutoff before HUD/GUI delivery. It never
issues a listing query for every opportunity. If SQLite becomes unavailable, the last immutable
snapshot is retained and marked unavailable with a nonintrusive warning.

Snapshot publication listeners are isolated from refresh failures. The production runtime uses one
listener to feed the bounded `AutomaticOpportunityDispatcher`; rendering never initiates automatic
work. The dispatcher accepts only enabled automatic mode on the current exact allowlisted server, deduplicates opportunity and
listing identities, and revalidates queued entries against the latest snapshot before calling the
existing coordinator.

## Live providers

- `MarketOpportunityProvider` supplies current `NEW` and `REVIEWED` opportunities.
- `LiveScannerStatusProvider` exposes scanner state and controls the master toggle.
- `LiveMarketStatisticsProvider` supplies listing, sale, opportunity, and profit totals.
- `DatabaseOpportunityHistoryProvider` supplies stored non-new states and clears only terminal
  history rows after the caller confirms the action.
- `LiveApiConnectionStatusProvider` supplies API state, age, request count, latency, cooldown, and
  sanitized warnings.
- `RegistryItemSearchProvider` builds the Minecraft item registry index once.
- `LiveItemFilterController` preserves whitelist/blacklist exclusivity and invokes an asynchronous
  filter/configuration callback for safe reevaluation.
- `LivePersonalProfitChartProvider` maps the immutable personal-profit ledger snapshot to the
  dashboard without querying SQLite during rendering.

`ClientUiDataSourceRouter` keeps one stable provider bundle in open GUI screens while switching
its delegate between mock and live data.

## Runtime composition

`ClientProductionRuntime` creates the API client, database, scanner, detector, analysis service,
and persistence callbacks when the separate credential file is present. The lower-level attach
boundary remains available for alternate integrations:

```java
LiveClientDataIntegration.attach(
    database,
    apiClient,
    scanner,
    opportunityDetector,
    initialFilterPolicy,
    updatedPolicy -> applyAndReevaluateAsync(updatedPolicy)
);
```

The built-in bootstrap remains in mock mode when the credential file is absent. When it is present,
the runtime waits for SQLite readiness, attaches live providers on the client executor, and starts
the scanner automatically.

## Tab descriptions

### Dashboard

Displays scanner lifecycle state, API state, active-listing count, stored completed sales, live
opportunity count, combined potential profit, and a cumulative tracked realized-profit chart.

### Opportunities

Displays compact live cards with item/count, listing price, conservative fair value, profit, ROI,
confidence, age, and guarded `BOUGHT`/`RUN`/dismiss controls. `BOUGHT` opens a confirmation and
records the exact listing cost basis only after the user confirms a manual purchase. `RUN` delegates to the automation provider;
the default disabled mode rejects it, while dry-run uses a non-Minecraft adapter. In enabled
`AUTOMATIC_AUTHORIZED_SERVER` mode with the current server allowlisted, newly published `NEW` opportunities are also submitted from
the provider layer without requiring the ClickGUI to be open.

### Item Filters

Displays whitelist/blacklist counts, reevaluation status, and the three live filter modes. Mode
changes invoke the asynchronous filter callback. The controller also supports exclusive item
whitelist/blacklist mutations for the existing item-management UI boundary.

### History

Displays bounded database-backed history rows with item, estimated profit, and final state.
Dismissal and manual-purchase state mutations are persisted asynchronously.

### Settings

Displays scanner state, API requests in the current window, average latency, cooldown, selected
data mode, and sanitized API/scanner warnings. The master scanner toggle remains in the header.

### Automation

Displays persisted mode/limits/allowlist summary and current execution state. It exposes exact-current-
server allowlisting, confirm-each continuation, cancellation, and emergency stop. There is no typed
ARM SESSION modal: the exact allowlist is the server-access gate. GUI code only calls
`AutomationSettingsController`; the coordinator and interaction adapters contain execution logic.

## Safe auction behavior

The shipped defaults cannot perform auction interaction. A real adapter is available only through
a custom private-server profile and requires exact semantic screen matching, an exact allowlist,
nonzero limits, enabled settings, and current allowlist verification. No public-server profile is included. See
`AUTOMATION.md` for the complete contract.

## Current limitations

- The GUI is a compact, custom provider-driven shell rather than a general widget framework. Keep
  business rules in providers/services and avoid database/network work from render methods.
- Opportunity details are persisted as a versioned evaluation JSON blob rather than separate
  normalized columns for every comparable-sale, liquidity, enchantment, and risk detail. UI cards
  should show the persisted explainability available; do not infer missing detail.
- Settings can save/remove the API key through a transient text field, but the persisted credential
  remains in its separate file and is not stored in normal config. The compact GUI does not expose
  every threshold/fee field; those remain config-backed.
- Automation monetary limits and custom screen-profile fields remain JSON-configured; the ClickGUI
  exposes modes, exact-current-server allowlisting, per-trade confirmation, cancellation, and emergency stop,
  but does not guess server-specific commands.
- Realized profit is the gross completed-sale price minus the user-confirmed purchase price. The
  public transaction schema does not document auction fees, so the client does not invent or
  subtract a fee. It tracks confirmed scanner flips, not every source of account wealth.

## Chunk 12 HUD and render hardening

The bounded live HUD queue consumes immutable `FlipOpportunity` records from this same provider
bundle. It suppresses duplicate opportunity IDs for a cooldown, queues at most eight alerts, and
renders a small monochrome panel away from the crosshair. Mock mode is excluded from automatic
HUD enqueueing. Provider mappings are cached by immutable source snapshot, so GUI/HUD rendering
does not reconstruct item stacks or query SQLite/network services per frame.
