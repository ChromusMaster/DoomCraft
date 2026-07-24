# Installation

## Required software

| Component | Version |
|---|---:|
| Minecraft Java Edition | 26.2 |
| Java | 25 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.155.2+26.2 |

## Installing the mod

1. Install the required Minecraft, Java, Fabric Loader, and Fabric API versions.
2. Download the official DoomCraft JAR from the DoomCraft CurseForge project page.
3. Place the JAR in the instance `mods` folder.
4. Start the game once.

Typical location:

```text
.minecraft/mods/
```

CurseForge, Prism Launcher, MultiMC-derived launchers, and other instance managers use an instance-specific `mods` directory.

## Important file rule

Install the `.jar`, not the workflow artifact ZIP.

Correct:

```text
mods/doomcraft-1.0.0-multiplatform.jar
```

Incorrect:

```text
mods/DoomCraft-1.0.0-multiplatform.zip
```

## Native extraction

The JAR contains platform-specific native packages. On first use, DoomCraft extracts only the package required by the current operating system and architecture into:

```text
config/doomcraft/native/
```

If a newer DoomCraft JAR contains a changed native build, the native package may need to be extracted again.

For troubleshooting, close Minecraft and remove only:

```text
config/doomcraft/native/
```

Do not remove the WAD or save directories unless you intentionally want to delete their contents.

## Verify the installation

After starting Minecraft, confirm that DoomCraft appears in the installed mods list.

If the mod loads but the TV cannot start LZDoom, see:

- [WAD Setup](WAD-Setup)
- [Troubleshooting](Troubleshooting)
- [Reporting Issues](Reporting-Issues)
