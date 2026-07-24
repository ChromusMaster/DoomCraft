---
name: Bug report
about: Create a report to help us improve
title: "[BUG] [BRIEF DESCRIPTION]"
labels: bug
assignees: ChromusMaster

---

---
name: 🐛 Bug Report for DoomCraft
about: Report a crash, glitch, or unexpected behavior
title: "[BUG]  + A brief description of what happened"
labels: bug
assignees: ChromusMaster

---

> **⚠️ IMPORTANT WAD POLICY (PLEASE READ BEFORE POSTING)**
> 
> As the maintainer, I can **only legally and practically test and debug issues** using **free and open-source WADs** such as **[Freedoom: Phase 1 & 2](https://freedoom.github.io/)**. 
> 
> - **If the bug happens with Freedoom**, please report it normally. I will fix it quickly.
> - **If the bug happens ONLY with a commercial/proprietary WAD (e.g., `doom.wad`, `doom2.wad`, `tnt.wad`, `plutonia.wad`, or any paid commercial mod)**, you **MUST** provide a **full screen recording** showing the entire process—from launching Minecraft/joining the server—until the error occurs. 
> 
> **Alternative to full video:** If your file is too large, provide a **detailed step-by-step reproduction** alongside your complete `logs/latest.log` and `.minecraft/crash-reports/` folder. Without this visual proof and log context, I cannot reproduce or fix the issue due to lack of access to the proprietary assets.

---

## Describe the Bug
A clear and concise description of what the bug is. (e.g., "Game crashes when I shoot a zombie while standing on the Doom block.")

## WAD Information (CRITICAL)
- **WAD File Used:** (e.g., `freedoom2.wad`, `doom2.wad`, `brutal_doom.pk3`, etc.)
- **Did you test with Freedoom?** (Yes / No / Only happens on my WAD)

## To Reproduce
Steps to reproduce the behavior:
1. Place the DoomCraft block in the world.
2. Load the specific WAD via `/doom load ...`
3. Perform action 'X'.
4. See crash/error.

## Expected Behavior
A clear and concise description of what you expected to happen instead.

## Screenshots / Video Proof
- **If using a commercial WAD:** Paste the link to your video recording here (YouTube, Streamable, or Google Drive). **Issues without video proof for proprietary WADs will be closed.**
- **If using Freedoom:** Screenshots are optional but helpful.

## Environment (please complete the following information):
- **Minecraft Version:** (e.g.,  26.x)
- **DoomCraft Mod Version:** (e.g., 1.x)
- **OS:** (e.g., Windows 11, macOS 14.5, Ubuntu 22.04, Debian 13)
- **Java Version:** (e.g., Java 25)

## Logs & Crash Reports
**Please attach the full log file.** 
- Go to your `.minecraft/logs/` folder and attach `latest.log` (or `debug.log`).
- Go to your `.minecraft/config/doomcraft/logs` folder and attach the logs.
- If it crashed, attach the `.txt` file from `.minecraft/crash-reports/`.
- You can paste the text directly into a code block if you cannot attach files:

```log
[Paste your log here]
