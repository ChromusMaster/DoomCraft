# Reporting Issues

Official issue tracker:

https://github.com/ChromusMaster/DoomCraft/issues

## Before opening an issue

1. Search existing issues.
2. Confirm the DoomCraft version.
3. Test with the official CurseForge JAR.
4. Remove unrelated WADs when possible.
5. Reproduce the problem more than once.
6. Preserve relevant logs.

## Required information

Include:

- DoomCraft version;
- Minecraft version;
- Java version;
- Fabric Loader version;
- Fabric API version;
- operating system;
- CPU architecture;
- WAD filename and version;
- additional PWAD load order;
- installed mods;
- exact reproduction steps;
- expected result;
- actual result;
- screenshots or video when useful.

## Logs

Minecraft:

```text
logs/latest.log
```

DoomCraft and LZDoom:

```text
config/doomcraft/logs/
```

Session diagnostics may also exist under:

```text
config/doomcraft/sessions/
```

## Privacy

Before uploading:

- remove access tokens;
- remove passwords;
- remove server addresses when private;
- remove personal usernames or paths when necessary;
- do not upload commercial WADs;
- do not upload unrelated private files.

## Useful issue titles

Good:

```text
[Windows x86-64] LZDoom exits before the first frame
[macOS ARM64] No audio after TV resumes
[Save] End loads the wrong manual slot after world restart
[Compatibility] Freedoom2 + example-map.wad freezes on menu
```

Avoid:

```text
It does not work
Help
Crash
Broken mod
```

## Suggestions

Suggestions are welcome in the same issue tracker.

Describe:

- the use case;
- why the current behavior is insufficient;
- expected interaction;
- accessibility impact;
- compatibility risks;
- whether the feature must remain optional.
