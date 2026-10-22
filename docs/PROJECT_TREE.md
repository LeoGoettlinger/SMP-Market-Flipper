# Project tree and architecture map

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

This is a conceptual map of the current working-tree layout as observed on 2026-10-22. It intentionally groups Java files by subsystem rather than duplicating a generated line-by-line inventory that becomes stale after every rename. The current tree includes extensive uncommitted changes; use `find`/IDE and `git status` for exact file-level detail.

```text
.
├── Agent.md                         # project-owner requirements and agent handoff
├── README.md                        # user setup, features, safety and current support boundary
├── build.gradle                     # Loom, split source sets, dependencies and tasks
├── gradle.properties                # Minecraft/Fabric/Java/project version pins
├── settings.gradle                  # plugin repositories and Gradle project name
├── gradle/
│   ├── wrapper/                     # Gradle 9.7.1 wrapper and checksum
│   └── verification-metadata.xml    # artifact verification metadata
├── .github/workflows/verify.yml     # tests/build and offline client/GUI smoke
├── docs/
│   ├── config.example.json          # sanitized example configuration
│   ├── filter-export.example.json   # filter import/export example
│   ├── order-module.example.properties
│   ├── screenshots/                 # screenshot evidence for Order/AH rules
│   └── *.md                        # subsystem design, testing and port notes
├── src/
│   ├── main/
│   │   ├── java/com/scmarket/       # game-independent domain and services
│   │   │   ├── api/                 # HTTP/API transport, auth, validation, DTOs
│   │   │   ├── automation/          # provider-neutral automation models/services
│   │   │   ├── balance/             # balance provider and parsing
│   │   │   ├── configuration/       # validated settings, persistence and migration
│   │   │   ├── database/            # migrations, repositories and transactions
│   │   │   ├── diagnostics/          # metrics, exports and local action capture
│   │   │   ├── market/              # item, scanner, stats, value, risk, confidence,
│   │   │   │                        # opportunity and profit domain logic
│   │   │   ├── order/               # order planning, capacity and completion core
│   │   │   ├── profit/              # personal profit tracking
│   │   │   ├── provider/            # immutable market snapshots
│   │   │   ├── security/            # bounded JSON and sensitive-data sanitization
│   │   │   ├── service/             # ingestion, retention, statistics and analysis
│   │   │   └── util/                # shared utilities
│   │   └── resources/
│   │       ├── assets/sc_market/    # fonts, language and client assets
│   │       ├── db/migration/        # ordered SQL schema migrations V001 onward
│   │       ├── META-INF/licenses/   # bundled asset/dependency notices
│   │       └── fabric.mod.json      # Fabric metadata and client entrypoint
│   ├── client/
│   │   ├── java/com/scmarket/       # Fabric/Minecraft-only adapters and UI
│   │   │   ├── automation/          # Fishing + Minecraft interaction adapters
│   │   │   ├── command/             # client commands and clipboard integration
│   │   │   ├── data/                # immutable UI models and provider routing
│   │   │   ├── gui/                 # screens, HUD, panels, rendering, theme
│   │   │   ├── mixin/               # screen/player/container/Minecraft event hooks
│   │   │   └── packet/              # AH packet capture and packet-scan work
│   │   └── resources/               # client Mixin configuration
│   └── test/java/com/scmarket/      # JUnit tests; unit and integration coverage
│       ├── api/ automation/ balance/ configuration/ database/
│       ├── diagnostics/ integration/ market/ order/ security/ service/
│       └── client/ data/ command/ provider/
└── config/                          # runtime user data; never commit private contents
```

## Runtime boundaries

- `main` should remain independent of Minecraft classes and directly testable with ordinary Java/JUnit.
- `client` owns the integration boundary: Fabric lifecycle, screen and packet events, player/inventory state, GUI rendering, live user settings, and server-specific interaction adapters.
- Core services use ports/interfaces; the client supplies production adapters and tests supply fakes. Keep GUI rendering separate from business decisions.
- Provider/UI objects should be immutable snapshots. UI reads data; it should not become the authority for market state, authorization, or database writes.
- Repositories own persistence details. Money/fee calculations, item identity, evidence validation, authorization, and trade decisions stay in domain/services where focused tests can exercise them.

## Key concepts by subsystem

| Subsystem | Main responsibility | Further reading |
| --- | --- | --- |
| API and scanner | Documented read-only API access, retries/rate limits, scan scheduling, sanitized status | `API_INTEGRATION.md`, `SCANNER_LIFECYCLE.md` |
| Packet scan | Capture the configured visible AH screen, ingest active listing snapshots only | `README.md`, `PORTING_26_2.md` |
| Item/evidence | Normalize and fingerprint items; keep asks distinct from completed sales | `ITEM_NORMALIZATION.md`, `MARKET_STATISTICS.md` |
| Pricing and opportunities | Statistics, fair value, confidence, risk, anomaly checks and alert gating | `FAIR_VALUE.md`, `CONFIDENCE.md`, `PROFIT_AND_RISK.md`, `ANOMALY_PROTECTION.md`, `OPPORTUNITY_DETECTION.md` |
| Database/history | Versioned SQLite migrations, repository operations, retention and recovery | `DATABASE.md`, `PERSONAL_PROFIT.md` |
| AH Deals/Fishing | Gated profiles and verified game actions; disabled-by-default fail-closed operation | `AUTOMATION.md`, `FEATURES_AND_SAFETY.md` |
| Order→AH | Provider-neutral plan/coordinator and separate whole-order completion ledger; server GUI remains fail-closed until verified | `ORDER_TO_AH.md` |
| GUI/backend | Route live/mock providers into screen/HUD panels without coupling domain rules to UI | `GUI_BACKEND_INTEGRATION.md` |
| Privacy/diagnostics | Explicit, bounded, local opt-in; no implicit upload | `LOCAL_ACTION_CAPTURE.md`, `RELEASE_HARDENING.md` |

## Maintenance note

This page is not a build manifest or proof of coverage. For exact files, run `find src -type f`; for all generated output exclude `.git`, `.gradle`, `build`, and IDE caches. Update this map after moving packages or source sets. Never check in local secrets, server addresses, private runtime profiles, market databases, or diagnostic captures.
