# Building from Source

## Development requirements

- JDK 25
- Python 3.13 recommended
- Git
- CMake
- Ninja
- C/C++17 compiler
- platform development tools

The project uses the Gradle Wrapper 9.6.1.

## Java/mod build

```bash
./gradlew clean build \
  -x verifyDoomCraftSource \
  --no-configuration-cache \
  --stacktrace
```

Expected output:

```text
build/libs/doomcraft-1.0.0.jar
```

## Important local-build limitation

A Java build is not automatically a complete distribution JAR.

The required native package must exist under:

```text
src/main/resources/natives/<platform>/
```

A local JAR without the native manifest, native build ID, executable, libraries, and runtime resources cannot start LZDoom.

## Build native engine locally

```bash
./gradlew verifyDoomCraftSource prepareLzDoom buildNative
```

The native pipeline:

1. validates the vendored LZDoom source;
2. applies DoomCraft patches;
3. prepares the compatible ZMusic dependency;
4. configures CMake;
5. builds the native engine;
6. copies executables and runtime resources;
7. creates `native-manifest.txt`;
8. creates `native-build-id.txt`.

## Standard project build command

For consistent diagnostics, use:

```bash
./gradlew clean build \
  -x verifyDoomCraftSource \
  --no-configuration-cache \
  --stacktrace
```

## Clean development artifacts

Do not commit:

- generated native work directories;
- local CMake build directories;
- extracted test instances;
- personal WADs;
- Minecraft logs containing private data;
- local CurseForge instance files.

## Distribution build

Use the GitHub Actions multiplatform workflow described in:

[Multiplatform Build](Multiplatform-Build)
