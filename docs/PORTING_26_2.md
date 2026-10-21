# Minecraft 26.2 port status and migration notes

**Status: not ported.** The working tree still targets Minecraft 1.21.11/Yarn/Java 21. Do not advertise or install this build for Minecraft 26.2. The feature work in this branch has not changed the current Minecraft dependency or mapping namespace.

## Findings from the 26.2 research

Minecraft 26.2 is in the post-1.21.11 unobfuscated era. Fabric's porting guide requires a source/API migration, not just changing `minecraft_version`. The recommended sequence is to first migrate a 1.21.11 Yarn codebase to Mojang names, resolve the resulting source and Mixin descriptors, then switch to the non-remapping Loom plugin and remove the Yarn mapping dependency for 26.2.

The official 26.2 Fabric example uses the `net.fabricmc.fabric-loom` plugin, Java 25, split main/client source sets, and ordinary `implementation` dependencies for libraries. A merged community port (`0Mattias/cheesecake` PR #10) follows the same Yarn→Mojang-first order and reports additional API and Mixin fixes after the name migration. A second example (`iluha168/MCAutoCrafters` PR #4) likewise removes Yarn mappings for the unobfuscated game. Fabric's 26.2 announcement also flags rendering changes, so direct rendering hooks need a specific review.

At the time of research, the references indicated Gradle 9.5.1, Loom 1.17, Loader 0.19.5, and Fabric API 0.160.0+26.2; repository search results later surfaced Fabric API 0.161.0+26.2. Pin exact versions against Fabric's current metadata at implementation time rather than treating those historical examples as permanent.

## Project-specific migration risks

- Current GUI code imports Yarn classes such as `MinecraftClient`, `DrawContext`, `Screen`, `HandledScreen`, `TextFieldWidget`, `Text`, `Registries`, and `ItemStack`. Both names and GUI/rendering APIs need conversion.
- Existing packet and player-interaction mixins target 1.21.11 handler method descriptors; each injection and accessor must be checked against the 26.2 unobfuscated bytecode.
- HUD rendering and custom widgets must be reviewed for the new rendering/extraction model; current 1.21 render code must not be assumed compatible.
- SQLite, Fabric API events, key bindings, lifecycle, screen handling, and optional diagnostics need both compile-time and private local-game verification.
- New Order settings and the opt-in diagnostic recorder are presently attached to the existing Yarn UI/mixins; they are not yet 26.2-compatible.

## Why this branch was not switched blindly

The sandbox has outbound access to GitHub, codeload, GitHub API, npm, and PyPI, but not to the Gradle distribution service or Fabric's Maven repository. The wrapper attempted to download Gradle 9.4.0 from `services.gradle.org` and failed its TLS handshake. A Java 25 runtime was obtained from the PyPI `jdk4py` wheel for investigation, but that wheel does not include `javac`; the Gradle distribution and Fabric dependencies were still unavailable. Consequently neither the intermediate mappings migration nor a 26.2 compile/game launch can be performed here.

Changing the build coordinates alone would leave hundreds of source references and Mixin selectors unmigrated, producing a misleading, non-buildable result. The requested port therefore remains an explicit unfinished task. A GitHub Actions verification workflow was added as an alternate remote build path, but it has not run because this branch has not been pushed with that workflow. It does not repair local TLS or replace a 26.2 port. Continue with an accessible Gradle 9.5.1/Fabric Maven environment, perform Yarn→official migration, resolve compilation and Mixin errors in small steps, then launch a dedicated 26.2 client and verify the UI and recorder before calling the port complete.

## References

- [Fabric porting guide](https://docs.fabricmc.net/develop/porting/)
- [Fabric mappings migration guide](https://docs.fabricmc.net/develop/porting/mappings/)
- [Official Fabric example mod, `26.2` branch](https://github.com/FabricMC/fabric-example-mod/tree/26.2)
- [0Mattias/cheesecake PR #10: port to 26.2](https://github.com/0Mattias/cheesecake/pull/10)
- [iluha168/MCAutoCrafters PR #4: 26.2 port](https://github.com/iluha168/MCAutoCrafters/pull/4)
- [Fabric 26.2 announcement](https://fabricmc.net/2026/06/15/262.html)
