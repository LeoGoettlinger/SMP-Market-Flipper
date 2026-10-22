# Runtime, build, and test dependencies

> Shared agent guidance, project invariants, and current verification limits: [Agent.md](../Agent.md). Treat this page as subsystem-specific documentation and verify implementation details against the current source and tests.

This document reflects the build configuration currently in the working tree. Dependency declarations are not a statement that the current uncommitted source has passed a build. Confirm the exact successful CI run and source SHA in `Agent.md` and `docs/PORTING_26_2.md`.

## Minecraft/Fabric toolchain

| Component | Current configured version | Role |
| --- | --- | --- |
| Minecraft | `26.2` | Game target; use the unobfuscated Mojang-named API |
| Java | `25` | Compile and runtime level |
| Fabric Loom | `1.18.4` | Non-remapping Loom plugin (`net.fabricmc.fabric-loom`) |
| Fabric Loader | `0.19.5` | Mod runtime |
| Fabric API | `0.161.0+26.2` | Client API version for this game target |
| Gradle wrapper | `9.7.1` | Checksum-pinned build tool; wrapper network timeout is 120 seconds |

The build uses split `main` and `client` source sets. No Yarn mapping artifact should be reintroduced for this 26.2 target. Check `gradle.properties`, `build.gradle`, `settings.gradle`, and `gradle/wrapper/gradle-wrapper.properties` before updating any version; avoid combining unrelated dependency changes with API porting.

## Xerial SQLite JDBC 3.53.1.0

- Coordinates: `org.xerial:sqlite-jdbc:3.53.1.0`
- Purpose: local SQLite market-history storage through JDBC
- Distribution: nested into the mod JAR using Loom's `include` configuration
- Project: <https://github.com/xerial/sqlite-jdbc>
- The upstream project reports Apache License 2.0 and BSD 2-Clause licensing.

The Xerial distribution packages native SQLite libraries for supported platforms inside its JAR; no native binary is downloaded by the mod at runtime. Preserve the `include`/packaging behavior and license notices when changing this dependency. Verify the final mod JAR actually contains the required SQLite classes and native resources.

## JUnit 5.13.4

- Scope: test-only
- Coordinates: JUnit BOM plus `org.junit.jupiter:junit-jupiter`
- JUnit Platform launcher is test runtime only and must not be packaged into the release mod JAR.

The client source-set output is added to test compile/runtime classpaths so Minecraft provider adapters can be tested without launching a game. Keep core logic independently testable and avoid requiring a live Minecraft client for unit tests.

## Supply-chain and TLS notes

- `gradle/verification-metadata.xml` records SHA-256 verification metadata for resolved artifacts. Do not disable dependency verification or replace checksums with permissive values to silence a mismatch. Investigate the artifact and update metadata only through the normal Gradle verification workflow.
- The wrapper distribution is SHA-256 pinned and `validateDistributionUrl=true`.
- The release JAR should include only explicitly intended runtime dependencies; test libraries and build tools are excluded.
- Gson/SLF4J and other game-provided libraries should not be redundantly bundled unless a documented compatibility reason requires it.
- Prior local requests to `services.gradle.org` and Fabric Maven failed with `SSL_ERROR_SYSCALL` in the restricted sandbox. Do not disable TLS verification, install trust-all code, or download unverified binaries. CI was the tested alternative for the earlier baseline, but must run again for current source changes.

## Verification commands

```bash
java -version                 # must be Java 25
./gradlew --version
./gradlew test build --no-daemon
```

The workflow's offline `runClient` smoke is separate from `test build`; see `.github/workflows/verify.yml`. A successful test/build does not verify live server interaction, price freshness against a real feed, order pickup safety, HUD-in-world behavior, or local action capture on real screens.
