# Getting Started

This page describes the shortest path from installation to playing a WAD on a DoomCraft television.

## Requirements

- Minecraft Java Edition 26.2
- Java 25
- Fabric Loader 0.19.3
- Fabric API 0.155.2+26.2
- DoomCraft 1.0.0
- At least one legally obtained WAD

## Quick start

1. Install Fabric Loader and Fabric API.
2. Place the DoomCraft JAR in the Minecraft `mods` folder.
3. Start Minecraft once.
4. Close Minecraft.
5. Copy your WAD files into:

```text
config/doomcraft/wads/
```

6. Start Minecraft again.
7. Craft or obtain a DoomCraft television.
8. Place the television in the world.
9. Stand within four blocks.
10. Press `Insert` to capture controls.

## First recommended test

For initial testing, use one recognized IWAD without additional PWADs.

Example:

```text
config/doomcraft/wads/
└── freedoom2.wad
```

Once the base game works, add maps or gameplay modifications one at a time. This makes compatibility problems easier to identify.

## What to expect

When the television is active:

- LZDoom runs as a native process outside the Minecraft JVM;
- the screen updates at up to 30 FPS;
- sound is produced by LZDoom;
- supported input is forwarded by DoomCraft;
- the process is paused or unloaded when the player moves away.

## Next pages

- [Installation](Installation)
- [WAD Setup](WAD-Setup)
- [Controls](Controls)
- [Troubleshooting](Troubleshooting)
