# Minecraft 26.2 port status and migration notes

**Status: not ported.** The branch still targets Minecraft 1.21.11 and Java 21; it must not be advertised or installed for Minecraft 26.2. The Yarn→Mojang source migration has now been applied to both main and client sources, but neither the intermediate 1.21.11 Mojang-mapped build nor the 26.2 port is verified yet.

## Findings from the 26.2 research

Minecraft 26.2 is in the post-1.21.11 unobfuscated era. Fabric's porting guide requires a source/API migration, not just changing `minecraft_version`. The recommended sequence is to first migrate a 1.21.11 Yarn codebase to Mojang names, resolve the resulting source and Mixin descriptors, then switch to the non-remapping Loom plugin and remove the Yarn mapping dependency for 26.2.

The official 26.2 Fabric example uses the `net.fabricmc.fabric-loom` plugin, Java 25, split main/client source sets, and ordinary `implementation` dependencies for libraries. A merged community port (`0Mattias/cheesecake` PR #10) follows the same Yarn→Mojang-first order and reports additional API and Mixin fixes after the name migration. A second example (`iluha168/MCAutoCrafters` PR #4) likewise removes Yarn mappings for the unobfuscated game. Fabric's 26.2 announcement also flags rendering changes, so direct rendering hooks need a specific review.

Fabric's 26.2 announcement specifies Gradle 9.5.1 and Loom 1.17; Loader 0.19.5 and Fabric API 0.161.0+26.2 are known stable 26.2-compatible releases. Recheck the target-version metadata while updating the build rather than selecting 26.3/26.4-only releases just because they are newer.

## Project-specific migration risks

- Current GUI code imports Yarn classes such as `MinecraftClient`, `DrawContext`, `Screen`, `HandledScreen`, `TextFieldWidget`, `Text`, `Registries`, and `ItemStack`. Both names and GUI/rendering APIs need conversion.
- Existing packet and player-interaction mixins target 1.21.11 handler method descriptors; each injection and accessor must be checked against the 26.2 unobfuscated bytecode.
- HUD rendering and custom widgets must be reviewed for the new rendering/extraction model; current 1.21 render code must not be assumed compatible.
- SQLite, Fabric API events, key bindings, lifecycle, screen handling, and optional diagnostics need both compile-time and private local-game verification.
- New Order settings and the opt-in diagnostic recorder are presently attached to the existing Yarn UI/mixins; they are not yet 26.2-compatible.

## Porting progress and verification

The Gradle wrapper and Minecraft/Fabric artifacts cannot currently be downloaded by the local sandbox because its Java environment is absent and its TLS egress to the relevant hosts has failed. GitHub Actions is the working remote verification path: it can download Gradle and game dependencies, run the unit suite, and compile the project. Use its build logs/diagnostics rather than treating local syntax checks as compilation.

A conditional GitHub Actions job was run on the active Arena branch. Loom's `migrateMappings` and `migrateClientMappings` tasks completed, and commit `f68ce21` migrated both source sets and switched the 1.21.11 build to Mojang mappings. The follow-up intermediate compile then stopped before compilation because Loom generated layered-mappings artifacts not yet present in dependency verification. The workflow is being adjusted to write and persist those hashes; after that, resolve any actual compile/test failures before changing Minecraft versions.

The subsequent real port still requires changing to unobfuscated Minecraft 26.2: use the non-remapping `net.fabricmc.fabric-loom` plugin, omit the mappings dependency, use ordinary `implementation` for Fabric dependencies, target Java 25, update Fabric API/metadata, and then resolve source, rendering, packet, GUI, and Mixin changes against the 26.2 client. A clean `test build` is necessary but not sufficient: a dedicated 26.2 client launch and focused private-profile/UI/recorder checks are needed before calling the port complete. Order→AH must remain fail-closed, require a confirmed private-server profile and explicit authorization, and never collect partial orders.

## References

- [Fabric porting guide](https://docs.fabricmc.net/develop/porting/)
- [Fabric mappings migration guide](https://docs.fabricmc.net/develop/porting/mappings/)
- [Official Fabric example mod, `26.2` branch](https://github.com/FabricMC/fabric-example-mod/tree/26.2)
- [0Mattias/cheesecake PR #10: port to 26.2](https://github.com/0Mattias/cheesecake/pull/10)
- [iluha168/MCAutoCrafters PR #4: 26.2 port](https://github.com/iluha168/MCAutoCrafters/pull/4)
- [Fabric 26.2 announcement](https://fabricmc.net/2026/06/15/262.html)
