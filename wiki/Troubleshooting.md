# Troubleshooting

## The TV is black

Check:

1. at least one `.wad` exists in `config/doomcraft/wads/`;
2. the file is readable;
3. the player is within four blocks;
4. the TV is not broken;
5. the native process did not exit;
6. no other TV currently owns the active session.

Review:

```text
config/doomcraft/logs/
```

## No sound

- confirm the system output device is available;
- check the native LZDoom log;
- test with one IWAD and no PWADs;
- verify that another application is not holding an exclusive audio device;
- restart the TV session after changing audio devices.

## Controls do not work

- stand within four blocks;
- press `Insert`;
- check Minecraft key conflicts;
- confirm the TV is active;
- remap keys on compact keyboards;
- verify that a Minecraft screen is not intercepting the key.

## Save does not work

Use `Home`, not the text field in the LZDoom Save Game menu.

Check for:

```text
config/doomcraft/saves/<TV-UUID>/latest-manual-save.txt
```

## Load does not work

- confirm that a manual save was created for the same TV;
- press `End`;
- check whether the TV was broken and replaced;
- review the native log for the requested save slot.

## Native package is missing or stale

Close Minecraft and remove:

```text
config/doomcraft/native/
```

Start the game again to force extraction from the installed JAR.

Do not delete:

```text
config/doomcraft/wads/
config/doomcraft/saves/
```

unless intentional.

## Linux missing shared library

Use the official CurseForge JAR. Do not install a Java-only local development JAR that lacks the complete native package.

If the error remains, include the exact missing library line in a GitHub issue.

## Windows or macOS blocks execution

Provide:

- operating-system version;
- CPU architecture;
- exact security dialog;
- DoomCraft version;
- Minecraft log;
- native log.

Do not disable system-wide security protections as a first troubleshooting step.

## Still unresolved

Follow [Reporting Issues](Reporting-Issues).
