# Minecraft 26.2 port status and migration notes

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

## Status at the 2026-10-22 workspace snapshot

The current build files target Minecraft **26.2**, Java **25**, Fabric Loader **0.19.5**, Fabric API **0.161.0+26.2**, non-remapping Loom **1.18.4**, and Gradle **9.7.1**. The source layout is split into `main` and `client`, and the current uncommitted source tree uses `com.scmarket`.

**The present working-tree state is not yet verified.** The most recent passing CI run, `39656997407`, passed tests/build and the offline headless client/GUI smoke on the earlier committed baseline (`2c10d16`). The worktree now contains extensive uncommitted changes across build files, resources, and a broad source/test tree replacement. That CI result does not cover those changes. The exact current diff must pass `./gradlew test build --no-daemon` and the client smoke before it can be called build-verified. Live private-server behavior remains a separate, outstanding verification requirement even after CI passes.

No client smoke can establish that all gameplay logic is correct or bug-free. In particular, no authorized private-server Order/AH operation, completed-order claim, live packet parsing, real API quote freshness, fishing sale, or world HUD interaction is claimed as verified here.

## Porting research and decisions

Minecraft 26.2 is in the post-Yarn, unobfuscated game era. This is a source/API migration, not a `minecraft_version` edit. The researched migration sequence was:

1. Migrate the older Yarn-mapped source to Mojang names and resolve both main/client source and Mixin descriptors.
2. Switch to non-remapping Loom and remove Yarn mapping dependencies.
3. Adopt Java 25 and versions explicitly targeting 26.2.
4. Review screens, rendering, input, key mappings, container contents, Fabric events, resources, and Mixin targets against the 26.2 API.
5. Compile both source sets, test without launching Minecraft, then launch the client to find runtime-only linkage/Mixin failures.
6. Separately validate actual screens, data flows, and guarded behavior on an authorized private/test server.

The official Fabric 26.2 example uses `net.fabricmc.fabric-loom`, Java 25, split source sets, and normal library dependencies. The current build configuration follows that direction. Keep API versions pinned to a release targeting the exact Minecraft version; a numerically higher release for a different game version is not interchangeable.

### API/Mixin pitfalls found during earlier port work

- Generic, non-container GUI clicks use Fabric `ScreenMouseEvents` screen-scoped listeners. In the relevant 26.2 Javadocs, the click listener accepts `(Screen, MouseButtonEvent)`; button and coordinates come from the event.
- `ScreenEvents.AfterInit` uses `(Minecraft, Screen, int, int)`.
- Handled container screens need the slot-aware capture path; they must not be treated as ordinary generic GUI clicks.
- `Screen.mouseClicked` is inherited in 26.2; a Mixin must not assume that method is declared on the `Screen` class.
- Keep shared bridges/registries as ordinary classes rather than illegal static helpers on a Mixin class.
- Font/resource loading and shutdown may expose native failures invisible in compilation. The earlier smoke was made one-shot and delayed after a FreeType shutdown crash; retain a clean-shutdown check.

These are prior findings, not a substitute for recompiling and inspecting current descriptors after the workspace refactor.

## Verification history (historical baseline only)

- Earlier migration/build runs exposed old GUI, input, container, and key-mapping APIs. Those were corrected in the historical source line.
- CI run `39655321822` passed tests/build and opened/rendered the GUI, but later work exposed a native FreeType crash during shutdown.
- CI run `39655754785` rendered the GUI and then crashed in `libfreetype.so` while a font worker was active. Shutdown was changed to a single delayed stop.
- CI run `39656235326` stopped before client smoke because `MarketScannerTest.temporaryApiFailureDoesNotImmediatelyKillScanner()` failed with `NoSuchElementException`: concurrent successful fake scanner tasks could clear the error under test. The test was isolated from those other successes.
- CI run `39656592568` passed tests/build and the client/GUI smoke after the test isolation and shutdown changes.
- CI run `39656997407` also passed tests/build and the client/GUI smoke after the documentation/diagnostic cleanup.

The passing runs were for committed historical snapshots before the extensive uncommitted current worktree changes. Do not reuse them as proof that the current diff passes. For the exact current checkout, read workflow results after running verification on that exact source revision.

The GUI smoke is headless/offline. It verifies startup, configured Mixin application, opening/rendering the mod screen, and shutdown according to the workflow. It does not interact with a server, validate HUD in a world, exercise the player's settings or all input events, or validate order/action semantics.

## Local build/network constraint

The sandbox previously had no `java` executable on `PATH`. Direct Gradle wrapper downloads from `services.gradle.org` and local Fabric Maven requests failed with `SSL_ERROR_SYSCALL`; those download hosts were outside the sandbox's permitted egress host list. No TLS verification was disabled. GitHub Actions successfully downloaded dependencies for the earlier baseline and provided the usable verification route. For the current diff, run the workflow again; if blocked, report the exact host and error rather than implying verification or weakening TLS.

## Remaining acceptance checks

On an explicitly authorized private/test server only:

- verify GUI input and data presentation, HUD in a world, and each active screen/Mixin path;
- validate the exact configured auction-house screen and packet-derived listing parsing;
- confirm prices are fresh immediately before order placement and each stack sale;
- verify Order ID, completion state, delivered count, exact claim control, displayed deposit/fee/total, and safe handling of partial fulfillment;
- test full-order safeguards, capacity, whitelist, failure, disconnect, restart, and duplicate prevention without claiming or selling an unapproved partial order;
- validate opt-in action capture against actual private test screens; inspect output for privacy before sharing.

Do not add real server addresses, credentials, player identifiers, or private profiles to the repository. Keep automatic live actions disabled until the exact server-specific path is proven and explicitly permitted.

## References

- [Fabric porting guide](https://docs.fabricmc.net/develop/porting/)
- [Fabric mappings migration guide](https://docs.fabricmc.net/develop/porting/mappings/)
- [Official Fabric example mod, 26.2 branch](https://github.com/FabricMC/fabric-example-mod/tree/26.2)
- [Fabric `ScreenMouseEvents.BeforeMouseClick` Javadocs](https://maven.fabricmc.net/docs/fabric-api-0.161.0+26.2/net/fabricmc/fabric/api/client/screen/v1/ScreenMouseEvents.BeforeMouseClick.html)
- [Fabric `ScreenEvents.AfterInit` Javadocs](https://maven.fabricmc.net/docs/fabric-api-0.161.0+26.2/net/fabricmc/fabric/api/client/screen/v1/ScreenEvents.AfterInit.html)
- [Fabric 26.2 announcement](https://fabricmc.net/2026/06/15/262.html)
