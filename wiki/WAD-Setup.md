# WAD Setup

DoomCraft does not include, download, or unlock WAD files.

## WAD directory

The mod creates:

```text
config/doomcraft/wads/
```

Place IWADs and optional PWADs in this directory.

## Loading order

DoomCraft follows this simplified order:

1. Search for a recognized IWAD filename.
2. Use the recognized IWAD as the primary game file.
3. Load remaining `.wad` files alphabetically as PWADs.
4. If no recognized IWAD exists, use the first available WAD as the IWAD.

Recognized examples include:

```text
doom.wad
doom2.wad
freedoom1.wad
freedoom2.wad
```

## Example layouts

### One IWAD

```text
config/doomcraft/wads/
└── freedoom2.wad
```

### IWAD with one map

```text
config/doomcraft/wads/
├── freedoom2.wad
└── custom_map.wad
```

### IWAD with multiple additions

```text
config/doomcraft/wads/
├── freedoom2.wad
├── 01_gameplay_mod.wad
├── 02_custom_map.wad
└── 03_visual_changes.wad
```

Alphabetical filenames can be used to influence PWAD ordering.

## Compatibility advice

When a WAD combination fails:

1. test the IWAD alone;
2. add one PWAD;
3. start a new TV session;
4. repeat until the incompatible combination is identified.

Some WADs require specific engine features or assumptions. Compatibility is not guaranteed for every historical or modern WAD.

## Legal use

Use only WAD files that you are legally allowed to execute.

Do not:

- ask the project to provide commercial WADs;
- upload copyrighted WADs to GitHub issues;
- distribute WADs through a modpack without permission;
- assume that a freely downloadable file is freely redistributable.

When reporting compatibility, provide the filename, version, source page, and expected engine requirements.
