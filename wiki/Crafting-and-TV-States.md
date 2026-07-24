# Crafting and TV States

## Crafting recipe

```text
I I I
G R G
P P P
```

| Symbol | Ingredient |
|---|---|
| `I` | Iron Ingot |
| `G` | Clear Glass Pane |
| `R` | Redstone Dust |
| `P` | Any Wooden Plank |

Total:

- 3 Iron Ingots
- 2 clear Glass Panes
- 1 Redstone Dust
- 3 Wooden Planks

## Wood appearance

When the three planks are the same supported vanilla wood type, the resulting television uses that wood appearance.

Mixed plank types, unsupported woods, and modded woods use the default oak appearance.

## Distance states

| Distance to nearest player | State | Native simulation |
|---:|---|---|
| 0–4 blocks | Active | Running |
| More than 4 and up to 8 blocks | Paused / Hibernated | Paused after save state |
| More than 8 blocks | Off | Process stopped and RAM released |

Only the nearest eligible TV may own the active native session on a client.

## Broken state

Sneak and use the television to toggle the broken state.

When broken:

- a cracked screen is rendered;
- controls are released;
- the active session is saved when possible;
- the native process is stopped.

## Moving a television

Breaking and replacing a television currently creates a new TV UUID.

Consequences:

- the new block starts as a new TV identity;
- the previous TV save directory remains on disk;
- old save states are not automatically assigned to the new TV;
- old files are not automatically deleted.

This behavior is documented as a [Known Issue](Known-Issues) for version 1.0.0.
