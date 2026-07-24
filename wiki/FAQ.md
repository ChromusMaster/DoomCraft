# Frequently Asked Questions

## Does DoomCraft include Doom or any WAD?

No.

You must provide your own legally obtained WAD files.

## Can I use FreeDoom?

DoomCraft can load recognized FreeDoom IWAD filenames. Compatibility still depends on the exact files and additional PWADs.

## Why does the mod need native executables?

LZDoom is a native engine. DoomCraft runs a platform-specific build outside the Java Virtual Machine and captures its framebuffer.

## Is the native engine inside the Minecraft JVM?

No. DoomCraft does not use JNI for the engine process.

## Why can only one TV run?

Each TV engine consumes CPU, memory, audio, and file resources. Version 1.0.0 limits the client to one active native process.

## Why does the TV pause when I walk away?

The distance system reduces resource use and preserves the session.

## Why is the TV off beyond eight blocks?

The native process is stopped to release RAM. A save state is preserved when possible.

## How do I save?

Press `Home` while controlling the TV.

## How do I load?

Press `End` to load the latest manual save associated with the current TV.

## Why can I not type a save name?

Complete text input forwarding is not implemented in version 1.0.0.

## Why did my save disappear after moving the TV?

Breaking and replacing the TV creates a new UUID. The old files remain on disk but are not automatically linked to the new TV.

## Can I remap Insert, Home, and End?

Yes. Open Minecraft controls and find the DoomCraft category.

## Does DoomCraft support controllers?

Minecraft key remapping may cover some controller integrations provided by other mods. Native joystick input is disabled by default. Controller support is not guaranteed in 1.0.0.

## Can DoomCraft run on a server?

DoomCraft has client-side native runtime requirements. Multiplayer behavior may depend on the modpack and server configuration. The world seed may be unavailable to the client.

## Can I include DoomCraft in a modpack?

Yes, under the conditions in [Project Policy](Project-Policy).

## Is a donation required?

No.

## Where should I report a bug?

https://github.com/ChromusMaster/DoomCraft/issues

## Where should I request a feature?

Use the same issue tracker and explain the use case.

## Is every WAD supported?

No. WADs may depend on different source-port behavior, load order, scripts, or engine assumptions.
