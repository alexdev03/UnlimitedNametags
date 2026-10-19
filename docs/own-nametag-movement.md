# Own nametag movement guard

## Behavior

With visibility.hideOwnNametagWhileSwimmingOrFlying enabled (the new default), the player's own name/tag rows are temporarily hidden from that player during:

- SWIMMING pose, including ordinary crawl/prone represented by that pose;
- FALL_FLYING and SPIN_ATTACK poses;
- active swimming, gliding or creative-flight flags, including transitions before the pose updates.

The user selected suppression in every camera view for these states. First/third-person selection is local to standard clients; this feature does not pretend to detect it. Standing, walking, ordinary sneaking and sleeping retain the existing own-tag behavior. Client-only/custom prone implementations that do not expose these server poses/flags require separate acceptance.

Every configured row shares the guard, including name, selected tag, and item/block rows. Other viewers retain their existing permissions, tracking, preference and wall visibility behavior. No player entity is hidden and no server pose, hitbox or movement flag is changed.

On entry, the owner display is hidden without modifying stored preferences or the owner's unrelated camera/API block. On exit, an owner-scheduled callback reads the current state and uses normal show eligibility; no five-tick forced elytra reveal remains. Turning off the own-tag preference while swimming keeps it off after standing. Old connections, dead/invisible/spectator owners and superseded pose callbacks cannot recover displays.

## Configuration

```yaml
visibility:
  hideOwnNametagWhileSwimmingOrFlying: true
```

Merge this setting into the existing visibility section if an explicit override is needed. It defaults true. False removes the new movement restriction; existing own-tag preferences and permission checks still apply. No permission defaults, rank grants or persistent preference/data format changed.

## Implementation and integration

The Paper adapter translates Bukkit pose/flags into the owner-only decision. NameTagManager keeps temporary suppression separate from isEffectiveShowOwnNametag, which remains the preference/global-policy calculation. Glide, swim and flight handlers run at MONITOR with ignoreCancelled; pose events also handle crawl/prone. Recovery uses the owner's scheduler and session identity. The old elytra packet hook retains its disabled-preference early return.

The common direct owner spawn paths honor eligibility, but still allocate a non-spawned metadata template. This is necessary for other viewers' text, item and block display initialization. Temporary hiding retains that template and does not globally remove rows. Recovery uses the normal row API without clearing unrelated blocked flags.

## Interactive preview

[Own Nametag Movement Preview](https://chatgpt.com/space/page_fc1ebc22eb548191b3c82f40d2dce2c0) is a private cloud Page with a responsive interactive simulation. It compares movement, observer, saved preference and the server guard. It assumes other visibility rules allow display. The Page/embed was saved and read back, and its JavaScript state logic passes 84 combinations.

Browser inventory exposed no apps or browsers in this session. The canonical HTTPS Page and its read permissions are verified through the connector; rendered desktop/mobile layout and mobile access could not be verified. Do not label it mobile-ready or treat it as Minecraft rendering proof.

## Remaining player acceptance

Use the owner and a second viewer. Record Minecraft/client/mod versions and test EntityCulling both enabled and disabled where available, without requiring a particular setting.

1. Enable own-tag preference. Enter/leave water swimming, low-ceiling crawl, the actual prone command/plugin, elytra flight and creative flight. Your name and every own-tag row should stay hidden during the affected states; another viewer's normal rendering should stay unchanged.
2. Repeat in first person and both third-person cameras. The guard applies in all camera views. Restore after movement only if the saved preference and other normal visibility gates allow it.
3. Change your own-tag preference to off while prone/swimming, then stand, land, teleport and reconnect. It must remain off. Compare other-tag and hide-own-from-others preferences independently.
4. Repeat rapid swim-to-glide/land/crawl transitions, cancelled flight/glide actions, low ceiling, death/respawn, invisibility/spectator, dimension changes, joins and reconnects. Check for late reappearance and stale callbacks.
5. Test text plus item/block rows, placeholder refresh, row replacement and reload. No owner-row flash should be recreated by refresh/show/recovery paths; other viewers must retain row text, style and item/block content.
6. Verify permissions, normal sneaking/sleeping, server pose and real combat hitboxes remain unchanged. Unit/adapter tests do not establish client acceptance.

This implementation task does not upload, activate or restart shared TEST or production.
