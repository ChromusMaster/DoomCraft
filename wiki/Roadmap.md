# 30-Day Post-Release Roadmap

This roadmap describes intended work during the first 30 days after the initial public release. It is a planning target, not a guarantee, and priorities may change according to crash reports, platform failures, accessibility needs, and community feedback.

## Days 1–3 — Release stabilization

Primary goals:

- verify the CurseForge package and dependency metadata;
- collect first-install reports;
- confirm clean installation paths;
- identify startup crashes;
- improve issue templates and log-collection instructions;
- separate configuration mistakes from native runtime defects.

Exit criteria:

- no widespread installation blocker;
- reproducible reports contain the required logs;
- critical defects are classified by platform.

## Days 4–7 — Windows and macOS runtime validation

Primary goals:

- validate Windows x86-64 startup, audio, input, save, and shutdown;
- validate macOS Intel startup and native dependency loading;
- validate macOS Apple Silicon startup and ad-hoc signed runtime behavior;
- document operating-system security prompts;
- compare extracted native manifests with CI artifacts.

Possible release outcome:

- 1.0.1 hotfix if a platform-specific blocker is confirmed.

## Days 8–10 — Save and load reliability

Primary goals:

- test repeated `Home` saves;
- test `End` after pause, unload, and world restart;
- confirm save selection remains TV-specific;
- detect stale or invalid `latest-manual-save.txt` entries;
- improve user-facing messages;
- document manual recovery of preserved save directories.

Deferred unless required by a critical bug:

- complete text entry in LZDoom fields;
- graphical save browser inside Minecraft.

## Days 11–14 — Controls and accessibility

Primary goals:

- review default key conflicts in popular modpacks;
- test remapped controls on compact keyboards;
- improve control-focus messages;
- investigate optional mouse-look strategies;
- evaluate controller support without enabling native joystick behavior by default;
- review readability and TV viewing distance.

Accessibility candidates:

- alternate high-contrast TV frame;
- clearer focus indicator;
- configurable help overlay;
- larger interaction hints.

## Days 15–18 — TV identity and lifecycle research

Primary goals:

- document UUID creation and persistence;
- prototype carrying TV identity in dropped items;
- evaluate save reassignment after moving a TV;
- prevent accidental duplication of identity;
- review behavior during chunk unload, death, dimension change, and world shutdown;
- define safe cleanup rules for abandoned session files.

No identity migration will be released until duplication and data-loss risks are understood.

## Days 19–22 — Compatibility and diagnostics

Primary goals:

- test representative IWAD and PWAD combinations;
- improve native startup diagnostics;
- include clearer missing-library and unsupported-platform messages;
- record active platform, architecture, native build ID, and selected WAD order;
- improve log rotation;
- document mod compatibility conflicts.

Potential tooling:

- diagnostic command;
- copyable environment summary;
- optional session health display.

## Days 23–26 — Documentation and community workflow

Primary goals:

- revise installation pages from real user questions;
- add screenshots for crafting, controls, broken state, and save workflow;
- improve modpack documentation;
- review English text;
- prepare Brazilian Portuguese wiki pages if community demand exists;
- create consistent issue labels and milestones.

## Days 27–30 — Release assessment

Review:

- unresolved critical bugs;
- crash frequency by platform;
- save reliability;
- performance reports;
- user-requested control changes;
- WAD compatibility patterns;
- modpack adoption;
- documentation gaps.

Decision:

- publish 1.0.1 for compatibility and stability fixes; or
- begin 1.1.0 planning for larger features.

## Candidate 1.1.x features

These are research candidates, not promises:

- portable TV identity when the block is moved;
- manual save browser;
- broader keyboard/text input;
- improved accessibility overlays;
- optional mouse input;
- expanded diagnostics;
- additional Minecraft versions when stable dependencies exist;
- additional native architectures when build and runtime testing are available.

## Out of scope for the first 30 days

- distributing WADs;
- bypassing ownership or licensing requirements;
- downloading arbitrary executable content;
- running unlimited simultaneous TV engines;
- promising support for every WAD or mod combination;
- using snapshot, alpha, beta, or release-candidate dependencies without a specific technical reason.

## Suggesting roadmap changes

Open an issue and explain:

- the problem being solved;
- who is affected;
- the expected behavior;
- possible risks;
- whether the request is a blocker, accessibility improvement, compatibility fix, or new feature.

Issue tracker:

https://github.com/ChromusMaster/DoomCraft/issues
