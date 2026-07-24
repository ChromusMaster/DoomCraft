# Saves and Persistence

DoomCraft uses the LZDoom save system rather than copying the native process memory byte for byte.

## Save directory

Each TV has its own UUID and save directory:

```text
config/doomcraft/saves/<TV-UUID>/
```

## Automatic save state

The automatic save slot belongs to the TV session.

It is used when:

- the player moves outside the active range;
- the TV enters hibernation;
- the native process is unloaded;
- the world closes.

Returning to the same TV allows DoomCraft to attempt to restore that session.

## Manual savegame

Press `Home` while controlling an active TV.

DoomCraft generates a unique internal save identifier:

```text
<PLAYER-UUID>_<TIMESTAMP>_<MINECRAFT-VERSION>_<WORLD-SEED>_<TV-UUID>
```

Example:

```text
3883af685d0346d9b9c277edd936649f_1784913561123_262_M4215249874687_77454164867486a716446655440000
```

Rules:

- UUIDs are stored without hyphens;
- timestamp precision is milliseconds;
- Minecraft `26.2` becomes `262`;
- negative seeds use the prefix `M`;
- unavailable multiplayer seeds use `NOSEED`.

DoomCraft records the most recent manual slot in:

```text
latest-manual-save.txt
```

Press `End` to load that slot for the current TV.

## Text entry limitation

Minecraft keyboard input is not currently forwarded as complete text input to LZDoom save-name fields.

Use:

- `Home` to create a save;
- `End` to load the latest manual save;
- the LZDoom Load Game menu when supported by the current session.

## Moving or replacing the TV

A newly placed TV receives a new UUID. Version 1.0.0 does not automatically migrate old saves to the new TV identity.

See:

- [Known Issues](Known-Issues)
- [Roadmap](Roadmap)
