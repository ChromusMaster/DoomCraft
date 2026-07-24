# Known Issues

This page tracks documented limitations of DoomCraft 1.0.0.

## Text input in LZDoom menus

Complete text entry is not forwarded from Minecraft to LZDoom.

Affected example:

- typing a custom name in the Save Game menu.

Workaround:

- press `Home` to create a timestamped manual save;
- press `End` to load the latest manual save for the current TV.

## New UUID after moving a TV

Breaking and replacing a television creates a new TV UUID.

Consequences:

- old save files remain preserved;
- the new TV does not automatically inherit them;
- the new TV starts a separate identity.

Automatic TV identity transfer is under evaluation for a future release.

## One active TV per client

Only the nearest eligible TV may run an active native session.

This is a deliberate resource-control limitation.

## Multiplayer seed availability

The client may not know the world seed on a multiplayer server.

Manual save identifiers use:

```text
NOSEED
```

when the seed is unavailable.

## Compact keyboard mappings

Insert, Home, and End may be absent or require function-layer combinations.

All DoomCraft controls can be remapped.

## Platform runtime coverage

Linux x86-64 is author-tested.

Windows x86-64, macOS Intel, and macOS Apple Silicon are built and validated by continuous integration. Broader runtime testing is requested from the community.

## WAD compatibility

Not every WAD combination is guaranteed to work.

Factors include:

- expected source-port behavior;
- load order;
- incompatible PWAD combinations;
- scripts requiring unsupported assumptions;
- corrupt or modified files.

## Reporting a known issue

If you have new evidence, reproducible steps, or a better workaround, open an issue:

https://github.com/ChromusMaster/DoomCraft/issues
