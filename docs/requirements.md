# Stale nametag cleanup

### REQ-001 - Player removal
WHEN an owner removal packet is sent THE SYSTEM SHALL include that owner's current and retired nametag row IDs in the removal packet.

### REQ-002 - Viewer release
WHEN a nametag row is hidden THE SYSTEM SHALL destroy its viewer display before forgetting its wrapper, including blocked viewers.

### REQ-003 - Session isolation
WHEN a player respawns or changes world THE SYSTEM SHALL invalidate old connection state before mounting new rows.

### REQ-004 - Client independence
THE SYSTEM SHALL send nametag cleanup packets without requiring changes to client EntityCulling settings.

## Acceptance still required
### REQ-005 - Obscured presentation
WHEN placeholders refresh THE SYSTEM SHALL preserve each viewer's obscured opacity and wall visibility until their line of sight changes.

### REQ-006 - Visibility recovery
WHEN invisibility or spectator mode ends THE SYSTEM SHALL reconcile tracking and recover nametags after the new player state takes effect, only for the current online session.

### REQ-007 - Hidden rows
WHILE HIDE mode has no line of sight or exceeds its configured range THE SYSTEM SHALL reject nametag spawning through every show path.

### REQ-008 - Recording preference
WHILE a viewer has disabled other players' nametags THE SYSTEM SHALL remove only those nametag displays and preserve the viewer's separate own-tag preference.

Reproduce teleports, world transfers, tracking-distance removal, vanish/invisibility, quit/rejoin, and repeated transfers with EntityCulling enabled and disabled. Observe the player and every row from a second client. See verification.md for current local evidence and remaining client acceptance; compilation alone does not establish either.
