# Local market-history database

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

The local backend uses SQLite through Xerial SQLite JDBC 3.53.1.0. The intended runtime
location is:

```text
config/sc-market/market-data.db
```

The database file is created only when a live integration instantiates a
`DatabaseManager`; default mock-mode startup does not create it.

## Migrations

Migrations are embedded in the mod JAR and applied in version order:

1. `V001__market_records.sql`: fingerprints, active listings, completed sales,
   scanner metadata, indexes, constraints, and foreign keys.
2. `V002__market_statistics.sql`: immutable market-statistic snapshots.
3. `V003__opportunities.sql`: evaluated opportunities and append-only state
   changes.
4. `V004__trade_execution_journal.sql`: automation execution snapshots and
   append-only state transitions used for audit and crash recovery.
5. `V005__personal_profit_ledger.sql`: confirmed scanner purchases, exact cost
   basis, matched completed-sale keys, and realized gross profit.
6. `V006__order_completion_observations.sql`: one deduplicated record per fully
   fully filled Order (1,024 items by default, configurable up to 2,048), kept separate from completed AH transactions.

Each migration runs in its own transaction and is recorded in `schema_version`.
Normal upgrades never drop user tables. The migration framework checkpoints WAL
and creates a timestamped backup before any future migration marked destructive;
none of the current migrations are destructive.

## Threading and transactions

- All JDBC work is submitted to one daemon database thread.
- The queue is bounded at 256 operations.
- Repository methods return `CompletableFuture` and never run JDBC on the caller.
- Every page/batch write uses one explicit transaction.
- Each operation obtains and closes its own connection with foreign keys enabled
  and a five-second busy timeout.
- `VACUUM` is exposed asynchronously and is never invoked automatically.

## Deduplication

Remote listing or transaction IDs take precedence when they are available and
reliable. The persisted key is a SHA-256 digest of a length-prefixed remote-ID
tuple.

When no remote ID exists, listing keys include:

```text
seller identifier
item fingerprint
canonical listing price
item count
listing timestamp when available
expiration timestamp when available
```

Fallback sale keys include seller, buyer, fingerprint, canonical sale price,
count, and sale timestamp. Length-prefixing prevents delimiter ambiguity, and
decimal canonicalization makes values such as `10`, `10.0`, and `10.00`
equivalent. Java `hashCode()` is never used for persistent identity.

Duplicate completed sales are ignored. A duplicate active listing updates its
mutable fields and `last_seen_at` while preserving the earliest `first_seen_at`.

## Listing disappearance

Missing state is changed only through an explicit observation call. Network
errors or incomplete page imports never change listing state automatically.
With the default tested threshold of three observations, transitions are:

```text
ACTIVE -> MISSING_ONCE -> MISSING_REPEATEDLY -> INACTIVE_UNKNOWN
```

Confirmed callers may separately mark `SOLD_CONFIRMED` or `EXPIRED`. Re-importing
an observed listing can restore it to `ACTIVE` and reset its missing count.

## Monetary representation

SQLite signed integers cannot preserve the API layer's arbitrarily large price
values. Prices, profit, ROI, and confidence are therefore stored as canonical
decimal text and reconstructed as `BigDecimal`. This prevents silent overflow or
floating-point coercion. Statistical calculation and numeric database ordering
are deliberately deferred to later chunks.

## Retention

- Completed sales and evaluated opportunities have no automatic deletion path.
- Execution journal rows have no automatic deletion path.
- Personal-profit ledger rows have no automatic deletion path. Clearing opportunity
  history does not erase confirmed cost basis or realized profit.
- Old raw listing JSON can be cleared in bounded batches without deleting the
  normalized listing.
- Inactive listing deletion is bounded and excludes listings referenced by an
  opportunity.
- Opportunity deletion is exposed only as an explicit repository operation for
  the GUI's future confirmation flow.

## Corruption and repair

Existing databases receive a bounded SQLite `quick_check` before migrations. If
corruption is detected, the DB plus any WAL/SHM companions are copied to timestamped
files under `backups/`. Startup then fails closed. The original is never replaced or
deleted automatically, so recovery requires an explicit user decision.
