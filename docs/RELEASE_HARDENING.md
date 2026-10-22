# Chunk 12 release hardening (historical review snapshot)

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

This review and its fixture measurements describe a prior code snapshot; they are not a security audit or proof for the current uncommitted source refactor. Re-run the checks on current code before using these as release claims. The current configured toolchain is recorded in [Dependencies](DEPENDENCIES.md), not inferred from this historical report.

## Release scope

This review covers the client GUI/provider boundary, authenticated HTTP client,
scanner lifecycle, normalization and fingerprinting, SQLite repositories, market
statistics, fair value, profit, confidence, anomaly protection, opportunities,
configuration, diagnostics, shutdown behavior, and the authorization-gated private-server
automation framework. No public-server interaction profile is included.

## Security review

- API credentials are supplied as defensive `char[]` copies and cleared by the HTTP
  client after request construction. The key is never copied into scanner or GUI
  snapshots and never appears in application log statements.
- `Authorization` values, common secret assignments, and personal home paths are
  redacted by `SensitiveDataSanitizer` before diagnostic export.
- Credentials are stored separately from configuration and filter exports.
  `FileApiCredentialsStore` enforces a 512-character/control-character limit and
  applies owner-only POSIX permissions or an owner-only Windows ACL.
- No telemetry, analytics, browser/Discord access, account/session token access,
  remote-code loading, dynamic JAR loading, reflection-based plugin loading, process
  execution, or runtime dependency download exists.
- Dependencies are intended to be pinned and checked by Gradle verification metadata. The historical count below is snapshot-specific; inspect the current metadata after dependency changes. The current Gradle distribution is checksum-pinned in the wrapper properties (Gradle 9.7.1 in this workspace snapshot).
- Repositories use prepared statements for every value derived from runtime data.
  Fixed embedded schema DDL and fixed SQLite PRAGMAs use `Statement`; no untrusted
  value is concatenated into SQL.
- API JSON is limited to 4 MiB by default, depth 24, 20,000 array entries, and
  32,768 characters per string. Configuration is limited to 2 MiB characters,
  depth 24, 10,000 IDs/settings, validated types/ranges, and namespaced item IDs.
- Filter imports return only `ItemFilterPolicy`; unrelated imported settings cannot
  be applied. Whitelist/blacklist overlap is rejected.
- API redirects are disabled. Retry count, exponential backoff, jitter, response
  size, request timeout, and shared request rate are bounded.
- Automation is disabled by default with an empty exact-server allowlist, zero spending/exposure
  limits, no interaction profile, and exact server allowlisting. Commands accept only bounded safe
  substitution values. Listing selection requires one exact semantic match before a slot click.

The credential file is protected by local filesystem permissions, not encrypted by
a platform keychain. A user or administrator who can read the Minecraft process or
its files can still obtain it; this is listed as a limitation rather than hidden.

## Performance review

Instrumentation records cumulative sample count, average, and maximum duration for:

- API request latency
- database page insertion
- comparable-sale query plus statistics calculation
- opportunity evaluation per listing
- immutable GUI snapshot generation

Diagnostics also reports estimated bounded-cache memory, database queue depth, and
opportunity/HUD queue depth. Timings use elapsed nanoseconds and store counters only;
they do not retain an unbounded sample history.

Hard limits:

| Boundary | Limit |
|---|---:|
| Database executor queue | 256 |
| Ingestion page-hash LRU | 512 |
| Tracked opportunity cache | 10,000 |
| Scanner active-listing keys | 100,000 |
| Pending statistic fingerprints | 50,000 |
| GUI opportunity/history rows | 500 |
| HUD alert queue | 8 |
| HUD duplicate keys | 128 |
| Repository query limit | 10,000 |

There are no network/database calls from render methods, no market reevaluation per
frame, no busy-wait loops, no per-frame `ItemStack` reconstruction, and no per-frame
file writes. Live GUI providers cache mapped display lists by immutable source
snapshot. Configuration writes are atomic and may be coalesced by
`DebouncedConfigurationStore`.

The full integration test emits the measured values for the current machine under
`CHUNK12_PERFORMANCE` in its JUnit XML. These are fixture-scale engineering timings,
not production API or large-database benchmarks.

Historical clean-build fixture measurement (2026-08-03; previous source snapshot, not a benchmark of the current worktree):

| Measurement | Average/result |
|---|---:|
| Mock HTTP latency reported by transport | 3,000 us |
| Database page insertion | 40,121 us |
| Comparable query plus calculation | 37,418 us |
| Evaluation per listing | 67,510 us |
| GUI snapshot generation | 40,435 us |
| Estimated page-hash cache memory | 476 bytes |
| Database queue after pipeline | 0 |
| Opportunity queue after pipeline | 1 |

The HTTP value is deterministic injected fixture latency. The local timings include
cold SQLite/JVM test overhead and are expected to vary by machine.

## Diagnostics export

`DiagnosticsService` asynchronously gathers version strings, scanner/API state,
request totals and average latency, database record counts, schema version, cache
statistics, last sanitized error, performance timings, and queue sizes.
`DiagnosticsExporter` atomically writes JSON without API keys, authorization headers,
session tokens, or filesystem path fields.

Runtime callers supply `DiagnosticsVersions` so Fabric Loader and Minecraft versions
come from the running environment rather than being guessed by the backend.

## Recovery behavior

| Failure | Behavior |
|---|---|
| Missing config | Create validated defaults atomically |
| Invalid/corrupt config | Copy to timestamped backup, create defaults, return warning |
| New/unknown fields | Defaults for missing fields; unknown fields ignored |
| Old config | Versions 1-2 migrated to current version 3 in memory |
| Interrupted automation | Mark execution `INTERRUPTED_REQUIRES_REVIEW`; never resume automatically |
| Corrupt SQLite | Copy DB/WAL/SHM evidence, fail closed, never replace history automatically |
| Interrupted migration | Per-migration transaction rolls back; version row commits with schema |
| Destructive future migration | Existing migration manager backs up first |
| API schema change | Unknown metadata marks item conservative/unsupported; invalid shape rejected |
| Invalid API key | Authentication state, sanitized message, scanner stops |
| No internet/temporary failure | Controlled retry/backoff; scanner exposes temporary error |
| Empty market | Empty bounded snapshots; no fabricated value/opportunity |
| Full disk/permission denied | Save/DB future fails; existing config/history is not deleted |
| Unexpected shutdown | Bounded scanner flush/save/shutdown and SQLite WAL transactions |

Repairs that would overwrite or discard history require an explicit future user
action. No automatic repair silently deletes opportunity or sale history.

## Full integration path

`FullOpportunityPipelineIntegrationTest` verifies:

```text
fake authenticated API responses
-> bounded raw JSON parsing
-> database page ingestion
-> normalized item fingerprint
-> completed comparable sales
-> robust statistics
-> conservative fair value
-> net profit and ROI
-> explainable confidence and manipulation assessment
-> persisted opportunity
-> immutable live provider snapshot
```

## Supported and unsupported categories

Safe defaults accept exact metadata matches and configured commodity categories.
The live profile also accepts ordinary vanilla commodities and visible-metadata equipment.
It groups tools across durability while preserving enchantment, name, lore, and trim identity.
No category receives special display priority.
Commodity stacks use per-unit completed-sale prices. Customized names, lore,
enchantments, armor trims, and container contents are fingerprinted conservatively.
Approximate, unsupported, malformed, unknown-metadata, truncated/deep-container, and
insufficient-history items are suppressed by default.

## API assumptions still requiring live verification

- DonutSMP's documented `GET` request body for `recently_listed` works through the
  user's network path.
- Listing-page bounds and stable listing IDs remain undocumented.
- `time_left` units remain undocumented and are not converted to expiry.
- Fee values remain user configuration; defaults do not invent server fees.
- A real key and production responses were not used by tests.

## Known limitations

- The Fabric initializer boots mock mode and asynchronously composes the live service graph
  only when the separate credential file is present. The ClickGUI can save/remove a key, but its persisted storage remains the separate file.
- Diagnostics export is a backend API, not yet a visible ClickGUI button.
- The credential file uses filesystem permissions instead of OS keychain encryption.
- Performance values are fixture-scale; production API/database load testing needs a
  user-authorized live environment.
- The DonutSMP contract gaps listed above prevent safe automatic assumptions.

## Automation statement

The project now contains a generic private-server interaction adapter. It has no public-server
profile and is unreachable under shipped defaults. It can send only profile-defined commands and
bounded slot clicks after exact allowlisting, enabled settings, nonzero limits, and semantic
listing verification. It does not manipulate packets, evade anti-cheat, accept remote
control, or bypass server restrictions. Use is restricted to servers the user owns or has explicit
permission to automate.
