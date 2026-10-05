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

## Ticket 388 follow-up

- `visibility.preferNormalTextWithLineOfSight` is opt-in (false by default). For SEE_THROUGH text, clear-sight viewers use depth-tested text; blocked viewers retain the row's configured wall visibility. Opacity, background, permissions, and other modes retain their existing behavior. Metadata refreshes preserve per-viewer flag ownership; tick, text refresh, sneak and reload paths reconcile it.
- The common text layer owns presentation. The Paper adapter schedules the new line-of-sight work on the viewer's scheduler, rechecking current session, feature settings, and row membership before applying it. Text refresh schedules only its viewer, avoiding a repeated full-viewer traversal.
- The Paper owner scheduler retries authoritative pose metadata one tick after `EntityPoseChangeEvent`. Bukkit SNEAKING maps to protocol CROUCHING; other supported poses are preserved. Only owners with nametag rows participate. This writes only metadata index 6 with ENTITY_POSE through normal packet listeners; it never sets server pose, swimming flags, collision state, or a hitbox.
- Pose retries carry an owner transition token and reject replaced sessions and later transitions. The socket callback also checks the viewer's current connection, tracking veto, connection state, and exact owner-spawn generation. Removal/re-spawn of the same entity ID invalidates the older snapshot.
- This is mitigation/reconciliation, not a demonstrated root-cause reproduction of either ticket symptom. The inspected local Minecraft 26.3 RemotePlayer does not calculate its own pose; Entity pose metadata drives dimensions. The reported client's exact version and mods remain unknown. Server/client acceptance is separate.

No production configuration, files, console, or process is touched by this follow-up. See ticket-388-acceptance.md for the staging-only configuration and remaining client evidence.
