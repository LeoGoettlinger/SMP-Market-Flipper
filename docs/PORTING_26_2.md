# Minecraft 26.2 port status and migration notes

**Status: 26.2 build/test passes; client GUI startup/render was observed, but CI shutdown smoke is being hardened. Private-server gameplay validation remains outstanding.** Both source sets use Mojang names and the build targets Minecraft 26.2, Java 25, non-remapping Loom, Fabric Loader 0.19.5, and Fabric API 0.161.0+26.2. CI run `39655321822` passed `test build` and the headless client GUI smoke. A later run still opened/rendered the GUI but hit a native FreeType crash while shutting down during font loading; shutdown has been delayed and made one-shot, and the invalidly packaged font license has been moved out of the resource-pack assets. Retest is pending. No private-server gameplay, order execution, packet capture from a live AH, or user-driven recorder behavior has been verified on 26.2.

## Findings from the 26.2 research

Minecraft 26.2 is in the post-1.21.11 unobfuscated era. Fabric's porting guide requires a source/API migration, not just changing `minecraft_version`. The recommended sequence is to first migrate a 1.21.11 Yarn codebase to Mojang names, resolve the resulting source and Mixin descriptors, then switch to the non-remapping Loom plugin and remove the Yarn mapping dependency for 26.2.

The official 26.2 Fabric example uses the `net.fabricmc.fabric-loom` plugin, Java 25, split main/client source sets, and ordinary `implementation` dependencies for libraries. A merged community port (`0Mattias/cheesecake` PR #10) follows the same Yarn→Mojang-first order and reports additional API and Mixin fixes after the name migration. A second example (`iluha168/MCAutoCrafters` PR #4) likewise removes Yarn mappings for the unobfuscated game. Fabric's 26.2 announcement also flags rendering changes, so direct rendering hooks need a specific review.

The current Fabric 26.2 example uses the non-remapping `net.fabricmc.fabric-loom` plugin, Java 25, Loader 0.19.5, and Fabric API 0.161.0+26.2. Its current Gradle example is 9.7.1; this branch pins the stable Loom 1.18.4 release and Gradle 9.7.1. Keep the Fabric API pinned to a release explicitly targeting 26.2 rather than selecting a numerically newer 26.3/26.4-only build.

## Remaining runtime validation

- CI successfully launches the 26.2 client, applies required mixins, opens SC-Market's custom screen, renders it, and exits cleanly. Mouse/key behavior and data presentation still need a hands-on session.
- The HUD is only visible in-game; the smoke run starts offline at the title screen and does not validate the overlay against a live player/world.
- Packet listener mixins and AH packet parsing have not been exercised against an actual server auction-house UI.
- Order→AH, fresh price lookup/relisting, full-order gating, private-profile confirmation, and opt-in action recording have not been exercised on a configured private test server. No public server or unapproved partial order was used.

## Porting progress and verification

The local sandbox still cannot run Gradle: it has no `java` executable on PATH, and earlier wrapper downloads failed with `SSL_ERROR_SYSCALL` while fetching the Gradle distribution; local requests to Fabric Maven also failed at TLS. The sandbox egress cause could not be changed here. The usable alternative was actually exercised: GitHub Actions downloaded the Gradle/game dependencies and passed test/build plus a headless GUI smoke in run `39655321822`. A separate CI attempt hit the wrapper's old 10-second read timeout; it is now set to 120 seconds, and the retry passed.

Loom's `migrateMappings` and `migrateClientMappings` completed on the active branch. Commit `f68ce21` migrated both source sets to Mojang names; follow-up CI fixes resolved the remaining 1.21.11 Mojang-mapped compile and test failures. The 277-test suite/build passed on commit `832a1c4` (CI run `39650200570`). After switching to the 26.2 toolchain, the first compile exposed old GUI, screen, input, key mapping, and container-content APIs. Those were ported, and `test build` passed on commit `d99770b` (CI run `39651667908`). The first-tick smoke launch exposed two runtime-only Mixin defects: `Screen.mouseClicked` is inherited in 26.2, and a public static helper was incorrectly declared on a mixin class. Generic non-container click capture has been moved to Fabric's per-screen `ScreenMouseEvents` API, and the packet capture bridge is now a standalone registry; handled-container clicks remain slot-aware. These corrections, `test build`, and opening/rendering SC-Market's GUI passed in headless CI run `39655321822`. A later attempt (`39655754785`) also opened and rendered the GUI, but the JVM crashed in native FreeType during shutdown while a font-loading worker was active; the CI-only stop is now one-shot and delayed to let resources settle. The test uses no server profile and does not simulate AH packets or order/recorder actions; private-server integration remains outstanding.

The remaining acceptance step is a hands-on session on an explicitly configured, authorized private-server profile: verify HUD, packet capture, AH parsing, pricing/relisting, full-order safeguards, and opt-in recorder behavior without submitting an unauthorized or partial order. No server address or credentials were added to the repository. Until that server-specific check is performed, this is a build- and GUI-smoke-verified 26.2 port, not a fully gameplay-verified release.

## References

- [Fabric porting guide](https://docs.fabricmc.net/develop/porting/)
- [Fabric mappings migration guide](https://docs.fabricmc.net/develop/porting/mappings/)
- [Official Fabric example mod, `26.2` branch](https://github.com/FabricMC/fabric-example-mod/tree/26.2)
- [0Mattias/cheesecake PR #10: port to 26.2](https://github.com/0Mattias/cheesecake/pull/10)
- [iluha168/MCAutoCrafters PR #4: 26.2 port](https://github.com/iluha168/MCAutoCrafters/pull/4)
- [Fabric 26.2 announcement](https://fabricmc.net/2026/06/15/262.html)
