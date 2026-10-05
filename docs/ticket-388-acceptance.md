# Ticket 388 acceptance

## Scope

Reports: nametag absent with water behind the player; other viewers see a player in a swimming/crawling pose on land, with a short F3+B debug hitbox. A low ceiling/slab trigger is suspected. The client-side debug box does not establish the server's authoritative combat hitbox.

The water mitigation does not solve arbitrary client render-pass/shader bugs. Normal depth is used when unobstructed; occluded text still uses the configured through-wall path. OBSCURED already uses normal depth in clear sight. No change to EntityCulling is required by the server code.

## Staging-only configuration

For the unmerged test artifact, merge this single setting into the existing `visibility` section of staging settings.yml (retain all other settings):

```yaml
visibility:
  preferNormalTextWithLineOfSight: true
```

Retain the current throughWallMode and per-row background.seeThrough choices. The new setting defaults to false. It applies only to SEE_THROUGH; OBSCURED/HIDE behavior remains governed by those modes. Disable the setting to restore the prior SEE_THROUGH presentation. No staging upload or activation has been performed by this implementation task.

## Water acceptance

Use an owner plus two viewers (vanilla and the affected client). Record client, server, resource-pack, shader and mod versions. Repeat EntityCulling enabled/disabled where available.

1. Compare the tag in front of water, glass, ice, a plain wall background, and open sky. Compare the option disabled/enabled without changing opacity or other settings.
2. Move the viewer and owner, sneak/stand, refresh placeholders repeatedly, and observe every row. Clear-sight names should remain legible and recover when leaving a blocked line of sight.
3. Insert/remove a solid wall. Each row's existing seeThrough setting must still govern wall visibility. Compare OBSCURED dimming and HIDE separately.
4. Verify preference hide/show, own tags, invisibility/spectator and stale-row cleanup remain intact. Repeat across world transfers and reloads.

## Pose acceptance

1. Have one player move under the reported slab/low ceiling, exit onto open ground, and repeat at spawn and the main world. Observe from a second client; compare own and remote view.
2. Correlate the server's actual pose and bounding box with outgoing ENTITY_POSE metadata and the viewer's model/F3+B box. The retry is useful if the server is correct but the viewer remains stale. If the server itself remains crawling, collect the server/plugin cause instead of forcing standing.
3. Verify actual water swimming, crawling under low ceilings, sneaking, sleeping, elytra and spin attack remain legitimate. The plugin must never change server pose or hitboxes.
4. Repeat rapid pose transitions, teleport/world change, untrack/retrack, disconnect/rejoin, death/respawn, invisibility and spectator transitions. Old snapshots must not overwrite a newer state.
5. Repeat the warzone scenario and compare actual attack/hit detection with server bounding-box evidence. A screenshot alone is not a combat regression proof.

These checks are pending player/client acceptance. Automated packet and scheduler tests establish the server adapter's decisions, not client rendering or the original report's root cause.
