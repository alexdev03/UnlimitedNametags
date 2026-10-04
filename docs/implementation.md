# Nametag lifecycle and rendering design

## Spec and prove

Requirements are in requirements.md. Reports establish stale rows at an old player location, inconsistent OBSCURED rendering, and failure to recover after invisibility/spectator transitions. Screenshots and reported relog recovery establish symptoms, not a complete causal packet trace. The baseline already has viewer cleanup/session regression tests; they are existing evidence, not historical red/green evidence for this work.

## Engine and architecture

- Common code registers an allocated row through a default runtime hook, preserving other platform adapters. The Paper packet adapter retains owner/row associations and expands owner removal packets to include current and retired displays.
- Per-viewer OBSCURED state checks actual metadata before skipping an update and caches only successful writes. Placeholder/style refreshes preserve per-viewer wall state.
- Player recovery waits until potion/gamemode changes take effect on the owner's scheduler, reconciles actual tracking, and rejects callbacks from old connections or currently suppressed owners. The entity-scoped UniversalScheduler overload preserves Paper/Folia scheduling ownership.
- The Paper visibility adapter applies HIDE range/line-of-sight policy to every spawn path. Viewer-owned recording preferences destroy allocated other-player rows without changing own-tag preference or other entities.
- Existing config defaults, permissions, storage, commands, platform ownership, and passenger ordering remain intact. No client culling configuration is changed.

## Refine and delivery

Verification and unresolved acceptance are in verification.md. The Gradle wrapper runs under JDK 23 while compilation/tests use the declared JDK 25 toolchain. External Gradle is not the canonical delivery path. UnlimitedNametags is not listed in the current network .gitmodules, so no submodule pin change is included.
