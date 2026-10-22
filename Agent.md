# SC-Market — agent briefing and working agreement

**Snapshot date: 2026-10-22.** This is the project handoff for future coding agents. It records the product intent, user requirements, architecture, safety invariants, research, and verification limits that should not be inferred from code alone. It does not replace source, tests, or current tool output; verify claims against the current checkout before acting.

## 1. What this project is

SC-Market is a client-side Minecraft Fabric mod for observing auction-market data, building a local market history, estimating item values conservatively, surfacing opportunities, and offering gated optional workflows for Fishing, AH Deals, and Order→AH. It is not an API server, market bot service, or a permission bypass. Market-analysis code should be as deterministic and testable as possible without launching Minecraft; game interaction belongs in client adapters and is subject to stronger authorization and verification gates.

Important feature boundaries:

- **API Detection** obtains auction listings and completed transaction data from the documented DonutSMP API. An API key authorizes API requests only; it does not grant permission to automate gameplay.
- **Packet Scan** observes the configured, visible in-game auction-house screen. It can provide active-ask observations but does not create completed-sale evidence, issue auction commands, or automatically traverse pages.
- **AH Deals** is the existing buy/relist workflow and its automation state machine. Its live execution is intended only for an explicitly authorized, configured private/test server. Shipped/default behavior is fail-closed.
- **Order→AH** has a provider-neutral economic planner, candidate selection, settings, completion-observation ledger, and serial coordinator. The client-facing live Order GUI adapter is deliberately not treated as ready until it can identify and verify the exact order, fill state, delivery and claim action, and amounts from a tested private profile.
- **Fishing** detects the server-synchronized bite state, confirms catches using inventory deltas, and can quote/list only eligible items after conservative evidence and authorization checks.
- Local database, history, statistics, confidence/risk analysis, diagnostics, and GUI/HUD are distinct subsystems; do not mix asking prices with completed sales or UI state with authoritative market facts.

## 2. Current status and how to interpret verification

### Build target in the current working tree

The build files currently specify Minecraft **26.2**, Java **25**, non-remapping Fabric Loom **1.18.4**, Fabric Loader **0.19.5**, Fabric API **0.161.0+26.2**, and Gradle wrapper **9.7.1**. Main and client source sets are split; Minecraft 26.2 uses Mojang names rather than Yarn mappings. Confirm the actual files (`gradle.properties`, `build.gradle`, `settings.gradle`, wrapper properties, workflow) before changing versions.

### Do not overstate test status

On 2026-10-22, commit `069392322d1ba849d3c9b9c89b1015ba0527a86d` passed GitHub Actions run `39812308743`: tests/build, JAR upload, and the configured offline 26.2 client/GUI smoke. This includes the API-free Packet Scan fallback, Fishing tab, and new mode unit tests at compile/CI level; it does not certify live fishing, server-specific profile parsing, transaction correctness, or private-server behavior. Local `./gradlew test build --no-daemon` could not run because the sandbox has no `java` executable; CI supplied Java 25 and passed. For later source changes, report the exact CI run/revision and distinguish CI smoke from live-server verification.

The earlier `39656235326` CI attempt had 1 failure among 277 tests: `MarketScannerTest.temporaryApiFailureDoesNotImmediatelyKillScanner()` saw a failure cleared by concurrent successful fake scanner work. The test was isolated in the later passing baseline. A separate earlier GUI smoke rendered successfully but hit a native FreeType shutdown crash; shutdown was changed to a one-shot, delayed stop, and a later baseline smoke passed. These are historical findings, not proof that any later revision is good or bad.

A CI GUI smoke only proves the offline title-screen startup, relevant Mixin application, GUI open/render, and clean shutdown covered by that workflow. It does **not** prove live HUD behavior in a world, screen input correctness, packet parsing, market API behavior, server-specific Order/AH flows, exact item attribution, or any live purchase/listing.

### TLS/network constraint

Local wrapper/Fabric Maven downloads previously failed with `SSL_ERROR_SYSCALL`; those hosts were outside the sandbox's permitted outbound host set. No TLS certificate or hostname verification was disabled, and no insecure trust-all workaround should be introduced. The usable alternative was tested: GitHub Actions downloaded dependencies and performed the prior baseline build/smoke. If this network issue recurs, report the exact failed host/error and use CI or a permitted mirror rather than weakening TLS.

## 3. Non-negotiable user requirements and safety invariants

These reflect explicit project-owner requirements and design decisions. Preserve them during refactors; do not downgrade them to UI-only warnings.

1. **Implement, do not just propose.** A request to port or implement means make the code changes and verify them where possible. Do not describe a feature as working merely because a plan, doc, compile, or mock test exists.
2. **No unauthorized gameplay.** Live AH, Order, and Fishing automation must stay limited to a server the user controls or whose operator explicitly permits it. Do not test against a public/unapproved server, add a public-server profile, bypass anti-cheat/CAPTCHA/rate limits, or use pacing as stealth/evasion.
3. **Never pick up or sell an unauthorized partial order.** Order completion/collection must be treated as whole-order, not inferred from partial delivery counts. Do not turn partial stacks or a GUI's partial fill amount into an independent order completion or sale. Keep collection/claim fail-closed until the exact server UI and order identity can be verified.
4. **Fresh prices before economic actions.** Re-query/revalidate current Order/AH quotes immediately before placing an order and before each stack sale. Reject stale, missing, thin, ambiguous, or changed evidence. A cached estimate is not fresh execution authority.
5. **Keep safeguards, evidence checks, and private profiles.** Preserve exact server allowlisting, private interaction profiles, inventory/item identity checks, price/fill verification, capacity/profit/fee constraints, confirmation screens, emergency stop, and failure-closed behavior. Never replace actual validation with permissive defaults. The owner explicitly requests removal of the separate ARM SESSION button and `I HAVE AUTHORIZATION` phrase: the exact allowlist is the server-access gate.
6. **Do not say “bug-free.”** Passing tests is evidence about tested paths only. State exactly which commands, CI jobs, game behaviors, and server behaviors were exercised. Report unknowns and unverified changes explicitly.
7. **Respect the current workspace.** The active Arena branch contains the committed package/build migration and current feature work. Inspect `git status` and diffs before editing. Do not reset, clean, checkout over, mass-rename, or discard user changes. Stage only files intentionally changed for the requested task.

### Authorization gates

For every transactional AH/Order action and any automated sale, retain the layered gates visible in code/docs: automation disabled by default; exact normalized server allowlist (no wildcards); configured, validated private interaction profile; runtime re-check of the current server and allowlist; workflow/module enabled; fresh candidate; single-flight/bounded queue; exact unique target match; confirmation UI checks; and exact post-action inventory/result verification. Fishing-only casting/reeling is allowed without a market profile, but still requires an enabled Fishing-only mode and exact allowlisted server; with no profile, catches must remain in inventory and no sale may occur. There is intentionally no separate ARM SESSION step or typed session phrase; do not reintroduce one. The owner prefers the persistent allowlist as the server-access gate because this is a private mod. This does not make an arbitrary server authorized: preserve the project scope of a server the user controls or whose operator has permitted automation. Clearing the emergency stop must not bypass any other setting or safety check.

Packet Scan is the default live data source when no API key exists and must work without one. A stale saved API-mode preference must fall back to Packet Scan rather than silently leave mock data active. Packet Scan is passive and may still require exact private-screen parsing configuration; an API credential is never gameplay authorization.

Do not use API presence as gameplay authorization. Treat `requireBalanceVerification` and equivalent gates as real constraints. If there is no transactional balance provider, do not fake one with a stale/read-only profile balance.

### Order economics and completion

The current project docs describe a default 1,024-item order, a screenshot-supported maximum of 2,048, hard capacity ceilings of ten total and two per item, 1% Order creation fee, 0% AH tax in the current model, configurable minimum net profit, fresh quote requirements, and one full order per cycle. The order-completion observation ledger is separate from the AH completed-sales table. Validate these against current source and screenshot evidence before changing constants. Screenshot-derived behavior is not the same as API- or server-verified behavior. Keep values configurable only within evidence-supported bounds.

### Pricing and evidence

- Completed sales are transaction evidence; active asks are not.
- API mode may provide both. Packet Scan supplies observed listing asks only.
- Ask-only Fishing quotes are explicitly low-confidence estimates. The current policy requires at least five fresh active asks from at least three sellers; the lowest ask must be at least 70% of the ask median; the proposed price is one whole currency unit under the lowest ask. Asks older than ten minutes are excluded.
- A sale-backed quote requires enough comparable completed sales and fresh evidence, applies conservative lower-quartile/weighted-median bounds, and may only be lowered by a credible ask. Stale, sparse, suspicious, unsupported, or approximate matches mean **no automatic list**.
- Never call an ask, forecast, balance snapshot, or fair-value estimate a completed sale or a guaranteed price.

The exact thresholds are implementation-sensitive. Read `docs/FEATURES_AND_SAFETY.md`, `docs/AUTOMATION.md`, `docs/FAIR_VALUE.md`, `docs/MARKET_STATISTICS.md`, `docs/PROFIT_AND_RISK.md`, and the relevant tests before altering them.

## 4. Repository map and architecture

The current source layout is split by runtime boundary:

- `src/main/java/com/scmarket/` — Minecraft-independent domain/application code: API client and validation, market models, item normalization, statistics, fair value, confidence/risk/opportunities, database/repositories, config, order economics/coordinator, profit, security, diagnostics, and services.
- `src/client/java/com/scmarket/` — Fabric/Minecraft integration: client startup/runtime composition, UI/HUD, GUI data providers, packet capture, Mixin hooks, Fishing controller, and Minecraft auction interaction adapters.
- `src/test/java/com/scmarket/` — JUnit unit/integration tests. Most domain logic should remain testable without a Minecraft client.
- `src/main/resources/` — Fabric metadata, assets, SQL migrations, and legal notices.
- `src/client/resources/` — client Mixin config and client-only resources.
- `.github/workflows/verify.yml` — CI build/test and optional offline client smoke. Read this before modifying verification behavior.
- `gradle/verification-metadata.xml` and `gradle/wrapper/` — supply-chain checksums and pinned Gradle wrapper.

Use `docs/PROJECT_TREE.md` for a concise current subsystem map. Key source entrypoints include `com.scmarket.ScMarketClient`, `ClientProductionRuntime`, `DonutApiClient`, `MarketScanner`, `DatabaseManager`, `LiveMarketSnapshotService`, `OrderAutomationCoordinator`, `FishingAutomationController`, `AuctionHouseDataCapture`, and `ClickGuiScreen`; verify current paths in the active branch before editing.

Keep the dependency direction understandable: core domain/service code should not depend on Minecraft classes; client adapters translate Minecraft state/events into domain ports; providers expose immutable snapshots to the UI; database writes are transactional and repositories own persistence. Prefer injected interfaces and fake adapters in tests over static game globals.

## 5. Minecraft 26.2 research and migration facts

The 26.2 era is post-Yarn/unobfuscated: the port is not a `minecraft_version` edit. The research and prior migration established this process:

1. Migrate the existing Yarn source to Mojang names, resolving descriptors and source/client set differences.
2. Switch Loom to the 26.2 non-remapping plugin and remove Yarn mapping dependencies.
3. Upgrade to Java 25 and versions explicitly targeting Minecraft 26.2.
4. Revisit GUI, input, screens, key mappings, container contents, rendering, and Mixin targets against 26.2 APIs.
5. Compile both main and client source sets; run tests; launch a headless client to catch runtime Mixin failures.
6. Still do a manual private-server verification for actual gameplay/data behavior.

The specific Fabric 26.2 API research corrected mouse capture to Fabric `ScreenMouseEvents` per-screen listeners (`beforeMouseClick(Screen, MouseButtonEvent)`); button and coordinates come from the event. Screen post-init uses `(Minecraft, Screen, int, int)`. Container screens should continue to use slot-aware handling rather than generic dialog capture. Mixin helper methods must not be exposed as illegal static helper methods on a Mixin class; use a normal bridge/registry. `Screen.mouseClicked` is inherited in the 26.2 API, so a Mixin target cannot assume it is declared on `Screen` itself. These were earlier CI-discovered pitfalls; re-check current mappings/source before relying on any descriptor.

References are collected in `docs/PORTING_26_2.md`; primary research included the Fabric porting/mappings docs, official Fabric 26.2 example, and Fabric 26.2 Javadocs. Re-check published APIs for the actual pinned version rather than copying snippets from a different Minecraft release.

## 6. Data, privacy, and persistence rules

- API credentials belong in the dedicated local key store (`config/sc-market/api-key.txt` per the current docs/config), not normal settings, logs, diagnostics, fixtures, screenshots, or commits. Never ask for or echo a real credential.
- Configuration, local market DB, exports, backups, profiles, and user data are valuable. Do not delete/overwrite/migrate destructively. Current migration behavior is copy-if-destination-absent while retaining legacy data; preserve this and test it.
- Use integer minor units / `BigDecimal` or the repository's established value type for money. Avoid `double`/binary floating point for prices, fees, exposure, profit, or rounding. Respect currency rounding semantics (including fee ceiling where specified).
- Database schema changes require a new ordered migration under `src/main/resources/db/migration/`, migration-manager updates only if needed, and tests for fresh install, upgrade, rollback/recovery, idempotency, and existing user data.
- JSON/config inputs are untrusted: bound size/depth/collections, validate types/ranges, sanitize secrets, recover safely from malformed/corrupt input, and preserve existing settings on failure.
- Local action capture is opt-in, bounded, local-only, and intentionally excludes chat and typed text. Review captures for contextual identifiers before sharing; do not add telemetry/uploads without explicit owner authorization and documentation.
- Tests and screenshots must not contain server addresses, player names, credentials, or real private profile data unless explicitly sanitized and approved.

## 7. Documentation map

All subsystem notes are in `docs/`. The most useful starting points are:

- `README.md` — user-facing setup, features, config paths, permissions, and current support boundary.
- `docs/PORTING_26_2.md`, `docs/DEPENDENCIES.md` — version/toolchain and port verification.
- `docs/FEATURES_AND_SAFETY.md`, `docs/AUTOMATION.md`, `docs/ORDER_TO_AH.md`, `docs/LOCAL_ACTION_CAPTURE.md` — behavior, execution gates, user safety, and recorder privacy.
- `docs/API_INTEGRATION.md`, `docs/SCANNER_LIFECYCLE.md` — remote API and scanning behavior.
- `docs/ITEM_NORMALIZATION.md`, `docs/MARKET_STATISTICS.md`, `docs/FAIR_VALUE.md`, `docs/CONFIDENCE.md`, `docs/PROFIT_AND_RISK.md`, `docs/ANOMALY_PROTECTION.md`, `docs/OPPORTUNITY_DETECTION.md`, `docs/PERSONAL_PROFIT.md` — market evidence and analysis.
- `docs/DATABASE.md`, `docs/RELEASE_HARDENING.md`, `docs/GUI_BACKEND_INTEGRATION.md` — persistence, operations, diagnostics, UI wiring.
- `docs/CHUNK12_CHANGES.md` — historical implementation manifest, not necessarily the current tree.
- `docs/PROJECT_TREE.md` — current directory/package overview.

Keep all these documents consistent with the same current status. Correct old release/version claims when discovered; do not leave contradictory port status in README or dependency notes.

## 8. Development and verification workflow

1. Inspect `git status`, current branch, complete diff, workflow, build files, relevant source, and tests. Distinguish committed code from user-authored uncommitted work.
2. Find the actual acceptance criteria and existing safety contracts. Read the narrow relevant docs/tests, then source. Do not make an assumption based only on an old manifest.
3. Make a small, directly scoped implementation. Add/adjust tests for success, invalid, stale, duplicate, ambiguous, interruption, and failure paths—especially “no action” conditions.
4. Verify docs/config example JSON and properties parse; paths, version numbers, defaults, and referenced files exist.
5. Run `./gradlew test build --no-daemon` with Java 25. Then run the workflow's client smoke where available; do not treat compile-only as GUI verification. If blocked by missing Java/TLS, record exact output and do not state the check passed.
6. Review `git diff --check`, inspect the final diff for accidental config/source data loss and secrets, and report tested commands/results plus remaining unverified behavior.
7. Do not weaken TLS, server authorization, price freshness, item identity, full-order completion, inventory verification, privacy, or fail-closed behavior just to make tests or a demo pass.

### Safe commands

```bash
./gradlew test build --no-daemon
# CI's GUI smoke is defined in .github/workflows/verify.yml; only run where an X server is available.
git status --short
git diff --check
```

A successful `test build` is not equivalent to private-server gameplay validation. For all transactions or recorder behavior, use only an explicitly authorized disposable private/test server and avoid submitting unapproved/partial orders.

The workflow changes in this worktree configure upload of the non-sources mod JAR as an Actions artifact named `sc-market-<full commit SHA>` after `test build` succeeds (14-day retention). This upload path is not verified until a workflow run executes on the pushed revision. Find it in the completed run's **Artifacts** section and download the ZIP; it contains the installable `.jar`. Upload occurs before the optional client smoke, so a smoke failure can coexist with a downloadable build artifact; inspect the smoke result separately. No artifact is produced when the test/build step fails.

## 9. Reporting standard

A good handoff says:

- exact source revision and whether the worktree was dirty;
- checks actually run, exact CI run or local command, and whether each passed;
- what the test did and did not cover;
- features not exercised in a live authorized server;
- security, data-loss, network, or compatibility constraints;
- the next concrete acceptance step.

Never answer “everything works,” “no bugs,” or “fully verified” based solely on unit tests, documentation, compilation, or an offline client smoke. The strongest honest statement is bounded by the exact tested paths.
