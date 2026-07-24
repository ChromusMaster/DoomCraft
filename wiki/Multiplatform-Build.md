# Multiplatform Build

DoomCraft distributes one JAR containing four native packages.

## Build matrix

| Platform ID | GitHub runner |
|---|---|
| `linux-x86_64` | `ubuntu-24.04` |
| `windows-x86_64` | `windows-2022` |
| `macos-x86_64` | `macos-15-intel` |
| `macos-arm64` | `macos-15` |

## Workflow

```text
.github/workflows/build-multiplatform.yml
```

The workflow:

1. checks out the repository;
2. installs Python and Java 25;
3. installs platform dependencies;
4. compiles LZDoom and DoomCraftBridge;
5. validates each native package;
6. uploads each package as an intermediate artifact;
7. downloads all four packages in the assembly job;
8. places them under `src/main/resources/natives/`;
9. builds the Fabric JAR;
10. validates the final JAR;
11. uploads the multiplatform artifact.

## Manual execution

```text
GitHub
→ Actions
→ DoomCraft - Build Multiplataforma
→ Run workflow
→ main
```

## Tag execution

Tags matching:

```text
v*
```

run the workflow and can publish a GitHub Release according to the workflow configuration.

For official public binary distribution, follow the current [Project Policy](Project-Policy).

## Final artifact

Expected file:

```text
doomcraft-1.0.0-multiplatform.jar
```

The workflow artifact is downloaded as a ZIP. Extract the ZIP before installing the JAR.

## Validation

The final JAR should contain:

```text
natives/linux-x86_64/native-build-id.txt
natives/windows-x86_64/native-build-id.txt
natives/macos-x86_64/native-build-id.txt
natives/macos-arm64/native-build-id.txt
```

Linux also includes the runtime libraries required by the packaged LZDoom build.

## Why native packages are compiled separately

Native executables are not portable between:

- operating systems;
- CPU architectures;
- binary formats;
- system library environments.

The assembly job creates the single distribution JAR only after all native packages pass validation.
