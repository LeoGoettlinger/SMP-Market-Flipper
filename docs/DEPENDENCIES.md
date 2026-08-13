# Runtime, build, and test dependencies

All coordinates are exact versions; no dynamic or snapshot dependency is used.
`gradle/verification-metadata.xml` pins SHA-256 values for the complete resolved
artifact/metadata graph. The Gradle 9.4.0 distribution is checksum-pinned in the
wrapper properties.

## Minecraft/Fabric toolchain

- Minecraft `com.mojang:minecraft:1.21.11`
- Yarn mappings `net.fabricmc:yarn:1.21.11+build.6:v2`
- Fabric Loader `net.fabricmc:fabric-loader:0.19.3`
- Fabric API `net.fabricmc.fabric-api:fabric-api:0.141.4+1.21.11`
- Fabric Loom remap plugin `net.fabricmc.fabric-loom-remap:1.16.3`
- Gradle wrapper `9.4.0`
- Java language/runtime level `21`

Minecraft, Fabric Loader, and Fabric API are provided by the installed game
environment and are not nested into this mod. Yarn is compile-time mapping metadata.
Loom and Gradle are build tools only.

## Xerial SQLite JDBC 3.53.1.0

- Coordinates: `org.xerial:sqlite-jdbc:3.53.1.0`
- Purpose: local SQLite market-history storage through JDBC
- Distribution: nested into the remapped Fabric mod JAR by Loom
- Project: <https://github.com/xerial/sqlite-jdbc>
- Licenses reported by the project: Apache License 2.0 and BSD 2-Clause

The official Xerial distribution supplies native SQLite libraries for Windows,
macOS, and Linux in its JAR. No native binary is downloaded at mod runtime.

## JUnit 5.13.4

- Scope: tests only
- Coordinates: JUnit BOM plus `org.junit.jupiter:junit-jupiter`
- Not included in the distributable mod JAR

Gson and SLF4J are already supplied by the Minecraft/Fabric runtime and are not
duplicated as nested dependencies.

## Supply-chain notes

- The release JAR nests only the pinned Xerial SQLite JDBC distribution and its
  platform-native libraries; it contains no executable downloader or dynamic loader.
- The official Xerial project publishes SHA-256 checksums and Maven signatures.
- JUnit, its platform launcher, and transitive test/build tools are absent from the
  release JAR.
- No obfuscated third-party application dependency is declared. Minecraft's
  obfuscated game classes are mapped by Yarn/Loom as part of the standard Fabric
  toolchain.
