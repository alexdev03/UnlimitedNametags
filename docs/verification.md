# Verification evidence

## Status

Local verification performed 2026-10-04:

- The unchanged repository wrapper (Gradle 8.14.5), running under JDK 23 with the declared JDK 25 toolchain, completes `clean build`: 66 tests, zero failures (30 common, 36 Paper), plugin assembled.
- A second `clean build` substitutes the locally available PacketEvents 2.14.0 artifact for PacketEvents compile/test dependencies using an external init script. All 66 tests pass. This checks the changed adapter against that version's actual classes; it does not claim the currently deployed bytes were freshly verified.
- Unmodified upstream 9f54d4fb27419eff741221ee8cd7583898562a35, with identical test dependency wiring and applicable new tests, fails 17 tests: 4 text presentation/cache tests and 13 Paper recovery/style/visibility tests. Cleanup tests requiring the newly added registration methods are not copied to the baseline and are not claimed as historical red evidence.
- Four new owner-scheduler assertions fail against the preserved pre-review fix and pass after the two new delayed callbacks use the entity-scoped scheduler overload.
- External EARS helper validation and `git diff --check` pass. The baseline has no local SPEAR state helper; tasks.md records actual state.
- A verification workflow now requests a clean build with compiler JDK 25 and Gradle runtime JDK 21. GitHub checks are recorded separately after the PR head is submitted; local success does not imply CI success.

Coverage: owner/current-retired-row removal expansion and deduplication, viewer cleanup/session lifecycle, metadata cache repair and retry, per-viewer independence, deferred potion/spectator recovery, reconnect suppression, HIDE line-of-sight/range/world gates, disabled worlds, and independent own/other-tag visibility preferences.

Review: the common runtime registration hook is default/no-op for other adapters. Paper retains owner-row IDs until shutdown, matching the pre-existing retired-row lifetime. The new registry is explicitly cleared on shutdown. No configuration default, permission node, command, or persisted-data schema was changed. Existing deprecated API/Unsafe fixture warnings remain visible in build output.

Earlier local packaging excluded tests. Its uploaded JAR is an unmerged testing artifact, not an approved release. This change requires new source/PR/build evidence.

## Server/client acceptance still required

Use two viewers including an affected client and record Minecraft/mod versions. Repeat with EntityCulling enabled and disabled:

1. Teleport same-world and cross-world, leave tracking distance, disconnect/rejoin, and repeat transfers. All nametag rows must leave the old location.
2. OBSCURED: compare unobstructed and blocked sight during placeholder refreshes for at least 60 seconds. Blocked tags should dim and clear-sight tags recover.
3. HIDE: test wall/range boundaries during refresh, show commands, preference restoration, and vanish events. No blocked rows should respawn.
4. Apply/remove invisibility, enter/leave spectator, repeat rapidly, and reconnect while recovery is pending. Only the current visible session may recover.
5. Hide other nametags for recording; other entities stay visible and own-tag preference remains independent across reconnect/world transfer.
6. Check companion-provided rank, guild, bounty, selected-tag text, and per-viewer overrides remain intact.

Server unit/adapter tests do not establish client rendering acceptance. Arbitrary client mods can override rendering; the server fix must not require disabling EntityCulling.
