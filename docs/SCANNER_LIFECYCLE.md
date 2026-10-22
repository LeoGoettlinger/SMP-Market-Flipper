# Chunk 10: Scanner orchestration and lifecycle

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

The scanner orchestration is now composed by the production client runtime and feeds immutable
live-provider snapshots to the ClickGUI.

## Lifecycle

`MarketScanner` exposes these states:

```text
STOPPED -> STARTING -> RUNNING
                       |  |
                       |  +-> RATE_LIMITED -> RUNNING
                       +----> PAUSED -> RUNNING
                       +----> ERROR
Any active state -> STOPPING -> STOPPED
```

Startup is permitted only when scanning is enabled, the client is running, an API key is
configured, mock-data mode is disabled, and the current server policy permits scanning.
The scanner stores only the boolean API-key capability flag; it never stores or publishes the
key value.

Authentication and database failures stop recurring scheduling and enter `ERROR`. A temporary
API failure is retained as a sanitized warning and becomes fatal only after the configured
consecutive-failure limit. HTTP 429 failures enter `RATE_LIMITED`; all periodic jobs skip work
during cooldown and one bounded resume task restarts the recent-listing poll afterward.

## Scheduling and change detection

One daemon `ScheduledThreadPoolExecutor` owns exactly four recurring handles:

- recently listed auctions;
- completed transactions;
- rotating older active-listing pages; and
- retention cleanup.

Statistics are not timer-polled. Changed item fingerprints are coalesced into a bounded set and
recalculated only after listing or completed-sale data changes. Every activity has a single-flight
gate, and at most one immediate request can be queued for an activity. This prevents slow API or
database work from growing the executor queue.

`ApiMarketScanWork` uses the existing page endpoints. It requests one page per activity rather
than one request per listing and rotates the configured active-page range.

`RepositoryMarketDataIngestionService` validates each raw record, normalizes and fingerprints its
item, derives stable keys, hashes the normalized page, and writes fingerprints and market rows in
batches. An unchanged page hash skips database work. The page hash is committed only after a
successful write, so a database failure cannot cause a later retry to be discarded.

Immutable scanner snapshots expose:

- last processed listing and transaction keys;
- page hashes and response timestamps;
- a bounded set of known active listing keys;
- listing, completed-sale, and statistics data versions;
- completed and overlap-skipped activity counts;
- current scheduled and in-flight counts; and
- sanitized failure state.

Snapshots are dispatched through an injected executor. The production runtime constructs
registry-backed UI providers on Minecraft's client executor and keeps database/API work on
bounded background executors.

## Shutdown

`shutdownAsync()` immediately prevents new work and cancels recurring handles. It then:

1. gives current work the configured grace period;
2. asynchronously flushes the database queue and saves configuration;
3. closes API and database resources exactly once; and
4. stops the scanner executor.

The Fabric client-stopping callback invokes this method without blocking the Minecraft thread.
The Fabric client now owns this lifecycle through `ClientProductionRuntime`. A present
credential triggers asynchronous database readiness, live-provider attachment, and
scanner startup; a missing credential leaves the stable provider router in mock mode.

## Conservative API handling

The official schema does not document a listing ID, creation timestamp, buyer, transaction ID,
or the unit of `time_left`. The ingestion layer therefore:

- never converts `time_left` into an expiration;
- derives listing keys from seller, fingerprint, price, and count;
- derives sale keys from seller, fingerprint, price, count, and documented sale time;
- rejects records without enough identity data for a stable key; and
- does not mark absent listings as sold or purchased.

No scanner code sends Minecraft commands, clicks inventory slots, constructs purchase packets,
or automatically purchases or relists items.
