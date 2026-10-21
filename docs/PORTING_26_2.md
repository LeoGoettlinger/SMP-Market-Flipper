# Minecraft 26.2 port status and migration notes

**Status: 26.2 port in progress; not verified or ready to advertise.** Both main/client sources have been migrated from Yarn to Mojang names, and the project build coordinates now target Minecraft 26.2, Java 25, non-remapping Loom, and Fabric API 0.161.0+26.2. The first 26.2 compile/test/build has not yet completed, and no 26.2 client launch has been verified.

## Findings from the 26.2 research

Minecraft 26.2 is in the post-1.21.11 unobfuscated era. Fabric's porting guide requires a source/API migration, not just changing `minecraft_version`. The recommended sequence is to first migrate a 1.21.11 Yarn codebase to Mojang names, resolve the resulting source and Mixin descriptors, then switch to the non-remapping Loom plugin and remove the Yarn mapping dependency for 26.2.

The official 26.2 Fabric example uses the `net.fabricmc.fabric-loom` plugin, Java 25, split main/client source sets, and ordinary `implementation` dependencies for libraries. A merged community port (`0Mattias/cheesecake` PR #10) follows the same Yarn→Mojang-first order and reports additional API and Mixin fixes after the name migration. A second example (`iluha168/MCAutoCrafters` PR #4) likewise removes Yarn mappings for the unobfuscated game. Fabric's 26.2 announcement also flags rendering changes, so direct rendering hooks need a specific review.

The current Fabric 26.2 example uses the non-remapping `net.fabricmc.fabric-loom` plugin, Java 25, Loader 0.19.5, and Fabric API 0.161.0+26.2. Its current Gradle example is 9.7.1; this branch pins the stable Loom 1.18.4 release and Gradle 9.7.1. Keep the Fabric API pinned to a release explicitly targeting 26.2 rather than selecting a numerically newer 26.3/26.4-only build.

## Project-specific migration risks

- All Minecraft class references now use Mojang names, but many call sites still reflect the 1.21.11 API; packet, container, player-interaction, and client accessors must be checked against 26.2.
- Existing packet and player-interaction mixins still target 1.21.11 handler method descriptors; each injection and accessor must be checked against 26.2 bytecode.
- HUD rendering and custom widgets must be reviewed for the 26.2 rendering/extraction model; current 1.21 render code must not be assumed compatible.
- SQLite, Fabric API events, key bindings, lifecycle, screen handling, and optional diagnostics need both compile-time and private local-game verification.
- Order settings, full-order gating, and the opt-in diagnostic recorder have migrated source names, but remain subject to 26.2 API, Mixin, and runtime verification.

## Porting progress and verification

The local sandbox still cannot run Gradle: it currently has no `java` executable on PATH, and earlier TLS attempts to the Gradle distribution/Fabric Maven endpoints failed. GitHub Actions is the available verification path; it can download Gradle and game dependencies and run tests/builds remotely.

Loom's `migrateMappings` and `migrateClientMappings` completed on the active branch. Commit `f68ce21` migrated both source sets to Mojang names; follow-up CI fixes resolved the remaining 1.21.11 Mojang-mapped compile and test failures. The full 277-test suite and build passed on commit `832a1c4` (CI run `39650200570`). The build has now been changed to the 26.2 unobfuscated/non-remapping toolchain, but the first 26.2 CI verification is still pending.

Next, resolve all compiler, test, Mixin, and packaging failures on 26.2. A clean `test build` is necessary but not sufficient: launch a dedicated 26.2 client and verify the UI, packet capture, action recorder, and order safeguards using an explicitly configured private-server profile before calling the port complete. Order→AH must remain fail-closed, require a confirmed private-server profile and explicit authorization, and never collect partial orders.

## References

- [Fabric porting guide](https://docs.fabricmc.net/develop/porting/)
- [Fabric mappings migration guide](https://docs.fabricmc.net/develop/porting/mappings/)
- [Official Fabric example mod, `26.2` branch](https://github.com/FabricMC/fabric-example-mod/tree/26.2)
- [0Mattias/cheesecake PR #10: port to 26.2](https://github.com/0Mattias/cheesecake/pull/10)
- [iluha168/MCAutoCrafters PR #4: 26.2 port](https://github.com/iluha168/MCAutoCrafters/pull/4)
- [Fabric 26.2 announcement](https://fabricmc.net/2026/06/15/262.html)
