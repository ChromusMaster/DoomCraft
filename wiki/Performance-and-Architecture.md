# Performance and Architecture

## Runtime design

DoomCraft does not run LZDoom inside the Minecraft JVM.

```text
Minecraft / Fabric
└── DoomClientRuntime
    ├── TV proximity and state management
    ├── DoomSession
    │   ├── isolated LZDoom process
    │   ├── memory watchdog
    │   ├── save management
    │   └── process lifecycle
    ├── atomic command files
    ├── framebuffer reader
    └── DynamicTexture + BlockEntityRenderer

Patched LZDoom
└── DoomCraftBridge
    ├── framebuffer capture
    ├── 640 × 400 output
    ├── SAVE / LOAD / PAUSE / RESUME / QUIT
    ├── input action forwarding
    └── command allowlist
```

## Operational limits

- one active native LZDoom process per client;
- 640 × 400 RGBA framebuffer;
- maximum 30 FPS;
- 768 MiB RSS watchdog;
- 960 MiB address-space limit on supported Linux environments;
- complete process unloading beyond eight blocks;
- joystick disabled by default;
- Softpoly backend requested;
- file-based atomic communication;
- no JNI.

## Why use a separate process

Advantages:

- a native crash does not directly corrupt the Minecraft JVM heap;
- native memory can be measured independently;
- the process can be fully stopped when no TV is active;
- platform-specific engine packaging remains separate from Java code;
- WAD execution does not consume the Minecraft JVM allocation directly.

## Memory target

The project targets less than 1 GB of memory attributable to DoomCraft.

Operating systems account for shared libraries and resident memory differently. The watchdog is a practical safety mechanism, not a promise of identical measurement on every platform.

## Multiple TVs

Only one TV may be active because each native engine can consume meaningful CPU, memory, audio, and file-system resources.

Supporting several simultaneous engines would require a different resource model and is not planned for version 1.0.x.
