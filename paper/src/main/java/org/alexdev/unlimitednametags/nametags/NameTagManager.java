package org.alexdev.unlimitednametags.nametags;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.Setter;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.api.UntNametagDisplay;
import org.alexdev.unlimitednametags.api.UntNametagDisplayCore;
import org.alexdev.unlimitednametags.api.UntNametagManagerPaper;
import org.alexdev.unlimitednametags.config.DisplayAnimation;
import org.alexdev.unlimitednametags.config.GlowOverride;
import org.alexdev.unlimitednametags.config.NametagDisplayType;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.data.NametagPlayerGlowStorage;
import org.alexdev.unlimitednametags.data.NametagPlayerOverrideStorage;
import org.alexdev.unlimitednametags.data.NametagPlayerPreferences;
import org.alexdev.unlimitednametags.hook.HMCCosmeticsHook;
import org.alexdev.unlimitednametags.hook.ViaVersionHook;
import org.alexdev.unlimitednametags.packet.PacketNameTag;
import org.alexdev.unlimitednametags.packet.PacketNameTags;
import org.alexdev.unlimitednametags.packet.PaperNametagRow;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Getter
public class NameTagManager implements UntNametagManagerPaper {

    private static final float COMPACT_ITEM_DISPLAY_HEIGHT_BLOCKS = 0.35f;
    private static final float COMPACT_BLOCK_DISPLAY_HEIGHT_BLOCKS = 0.5f;

    private final UnlimitedNameTags plugin;
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<PacketNameTag>> nameTags;
    private final Map<Integer, PacketNameTag> entityIdToDisplay;
    private final Set<UUID> creating;
    private final Map<UUID, AtomicInteger> pendingRowCreations;
    private final Set<UUID> blocked;
    private final Set<UUID> hideNametags;
    /** Players who hide their own nametag from themselves (inverse of show-own-to-self preference). */
    private final Set<UUID> hideOwnFromSelf;
    /** Owners who hide their nametag from all other viewers. */
    private final Set<UUID> hideOwnFromOthers;
    private final NametagPlayerPreferences playerPreferences;
    private final NametagPlayerGlowStorage glowStorage;
    private final NametagPlayerOverrideStorage overrideStorage;
    private final Map<UUID, Settings.NameTag> nameTagOverrides;
    private final Map<UUID, Map<Integer, GlowOverride>> sessionGlowOverrides;
    private final Map<UUID, Map<Integer, GlowOverride>> persistentGlowOverrides;
    private final Map<UUID, Map<Integer, DisplayAnimation>> sessionDisplayAnimations;
    private final Map<UUID, Map<Integer, DisplayAnimation>> persistentDisplayAnimations;
    private final Map<UUID, Boolean> shiftSystemBlocked;
    private final List<MyScheduledTask> tasks;
    private final AtomicLong displayAnimationMonotonicTick = new AtomicLong();
    @Setter
    private boolean debug = false;
    private final Attribute scaleAttribute;

    private record ResolvedDisplayRow(
            int index,
            @NotNull Settings.DisplayGroup displayGroup,
            @NotNull PacketNameTag display,
            @NotNull List<Player> relationalPlayers,
            @NotNull Map<Player, Component> components,
            Component ownerComponent) {
    }

    public NameTagManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.nameTags = new ConcurrentHashMap<>();
        this.entityIdToDisplay = Maps.newConcurrentMap();
        this.tasks = Lists.newCopyOnWriteArrayList();
        this.creating = Sets.newConcurrentHashSet();
        this.pendingRowCreations = Maps.newConcurrentMap();
        this.blocked = Sets.newConcurrentHashSet();
        this.hideNametags = Sets.newConcurrentHashSet();
        this.hideOwnFromSelf = Sets.newConcurrentHashSet();
        this.hideOwnFromOthers = Sets.newConcurrentHashSet();
        this.playerPreferences = new NametagPlayerPreferences(plugin);
        this.glowStorage = new NametagPlayerGlowStorage(plugin);
        this.overrideStorage = new NametagPlayerOverrideStorage(plugin);
        this.nameTagOverrides = Maps.newConcurrentMap();
        this.sessionGlowOverrides = Maps.newConcurrentMap();
        this.persistentGlowOverrides = Maps.newConcurrentMap();
        this.sessionDisplayAnimations = Maps.newConcurrentMap();
        this.persistentDisplayAnimations = Maps.newConcurrentMap();
        this.shiftSystemBlocked = Maps.newConcurrentMap();
        this.loadAll();
        this.scaleAttribute = loadScaleAttribute();
    }

    private void loadAll() {
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            plugin.getPlayerListener().getOnlinePlayers().values().forEach(p -> {
                syncPlayerOverridesFromPdc(p);
                addPlayer(p, true);
            });
            this.startTask();
        }, 5);
    }

    private void startTask() {
        tasks.forEach(MyScheduledTask::cancel);
        tasks.clear();

        final MyScheduledTask displayAnimations = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                () -> {
                    final long t = displayAnimationMonotonicTick.incrementAndGet();
                    nameTags.values().forEach(tags -> tags.forEach(tag -> {
                        tag.tickDisplayAnimation(t);
                        tag.tickGlowAnimation(t);
                    }));
                },
                1L,
                1L);
        tasks.add(displayAnimations);

        final MyScheduledTask refresh = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                () -> {
                    if (plugin.isPaper() && plugin.getServer().isStopping()) {
                        return;
                    }
                    nameTags.values().forEach(tags -> tags.forEach(tag -> refresh(paperRow(tag).getOwner(), false)));
                },
                10, plugin.getConfigManager().getSettings().getBehavior().getTaskInterval());
        tasks.add(refresh);

        // Refresh passengers
        final MyScheduledTask passengers = plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> nameTags.values()
                .stream()
                .flatMap(Collection::stream)
                .map(tag -> paperRow(tag).getOwner())
                .distinct()
                .filter(p -> plugin.getHook(HMCCosmeticsHook.class).map(h -> !h.hasBackpack(p)).orElse(true))
                .forEach(player -> getPacketDisplays(player).stream()
                        .findFirst()
                        .ifPresent(tag -> ((PacketNameTag) tag).sendPassengerPacketToViewers())),
                20, 20 * 5L);
        tasks.add(passengers);

        // Scale task
        if (isScalePresent()) {
            final MyScheduledTask scale = plugin.getTaskScheduler()
                    .runTaskTimerAsynchronously(() -> nameTags.values()
                            .forEach(tags -> tags.forEach(tag -> {
                                if (tag.checkScale()) {
                                    tag.refresh();
                                }
                            })), 20, 10);
            tasks.add(scale);
        }

        if (plugin.getConfigManager().getSettings().getVisibility().isShowWhileLooking()) {
            final MyScheduledTask point = plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> {
                nameTags.values().forEach(tags -> tags.forEach(tag -> {
                    final Player targetOwner = paperRow(tag).getOwner();
                    final List<Player> viewers = plugin.getTrackerManager().getWhoTracks(targetOwner);

                    for (Player viewer : viewers) {
                        if (plugin.getHook(ViaVersionHook.class).map(h -> h.hasNotTextDisplays(viewer)).orElse(false)) {
                            continue;
                        }

                        boolean isPointing = isPlayerPointingAt(viewer, targetOwner);
                        if (isPointing && plugin.getConfigManager().getSettings().getVisibility().getThroughWallMode()
                                == Settings.ThroughWallMode.HIDE && !viewer.hasLineOfSight(targetOwner)) {
                            isPointing = false;
                        }

                        if (paperRow(tag).canPlayerSee(viewer) && !isPointing) {
                            paperRow(tag).hideFromPlayer(viewer);
                        } else if (!paperRow(tag).canPlayerSee(viewer) && isPointing) {
                            paperRow(tag).showToPlayer(viewer);
                        }
                    }
                }));
            }, 5, 5);
            tasks.add(point);
        }

        final Settings.Visibility visibility = plugin.getConfigManager().getSettings().getVisibility();
        if (visibility.getThroughWallMode() == Settings.ThroughWallMode.OBSCURED || visibility.getThroughWallMode() == Settings.ThroughWallMode.HIDE) {
            final int obscuredInterval = Math.max(1, visibility.getThroughWallSettings().getCheckInterval());
            final MyScheduledTask obscured = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                    this::tickThroughWalls,
                    obscuredInterval,
                    obscuredInterval);
            tasks.add(obscured);
        }
    }

    private Attribute loadScaleAttribute() {
        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_20_5)) {
            return null;
        }
        if (PacketEvents.getAPI().getServerManager().getVersion().isNewerThan(ServerVersion.V_1_21_1)) {
            return Attribute.SCALE;
        } else {
            return Registry.ATTRIBUTE.get(NamespacedKey.minecraft("generic.scale"));
        }
    }

    private void tickThroughWalls() {
        final Settings s = plugin.getConfigManager().getSettings();
        final Settings.ThroughWallMode mode = s.getVisibility().getThroughWallMode();
        if (mode == Settings.ThroughWallMode.SEE_THROUGH) {
            return;
        }
        final double maxDistance = s.getVisibility().getThroughWallSettings().getMaxDistance();
        final double maxSq = maxDistance * maxDistance;

        if (mode == Settings.ThroughWallMode.OBSCURED) {
            final byte sneakB = clampMcTextOpacity(s.getVisibility().getSneakOpacity());
            final byte obscB = clampMcTextOpacity(s.getVisibility().getThroughWallSettings().getOpacity());
            for (final CopyOnWriteArrayList<PacketNameTag> tags : nameTags.values()) {
                for (final PacketNameTag tag : tags) {
                    if (!tag.isTextDisplay()) {
                        continue;
                    }
                    final Player owner = paperRow(tag).getOwner();
                    final boolean shiftBlocked = shiftSystemBlocked.getOrDefault(owner.getUniqueId(), false);
                    final boolean sneakEff = tag.isSneaking() && !shiftBlocked;
                    tag.applyObscuredLineOfSightPresentation(true, sneakB, obscB, maxSq, sneakEff);
                }
            }
        } else if (mode == Settings.ThroughWallMode.HIDE) {
            for (final CopyOnWriteArrayList<PacketNameTag> tags : nameTags.values()) {
                for (final PacketNameTag tag : tags) {
                    final Player owner = paperRow(tag).getOwner();
                    if (owner == null) {
                        continue;
                    }
                    final PaperNametagRow row = paperRow(tag);
                    final List<Player> viewers = plugin.getTrackerManager().getWhoTracks(owner);
                    for (final Player viewer : viewers) {
                        if (viewer.getUniqueId().equals(owner.getUniqueId())) {
                            continue;
                        }
                        final double distSq = viewer.getLocation().distanceSquared(owner.getLocation());
                        final boolean withinRange = distSq <= maxSq;
                        final boolean hasLoS = withinRange && viewer.hasLineOfSight(owner);

                        if (hasLoS) {
                            if (!row.canPlayerSee(viewer)) {
                                row.showToPlayer(viewer);
                            }
                        } else {
                            if (row.canPlayerSee(viewer)) {
                                row.hideFromPlayer(viewer);
                            }
                        }
                    }
                }
            }
        }
    }

    private static byte clampMcTextOpacity(final int raw) {
        if (raw < -128) {
            return -128;
        }
        if (raw > 127) {
            return 127;
        }
        return (byte) raw;
    }

    public boolean isPlayerPointingAt(Player player1, Player player2) {
        if (player1.getWorld() != player2.getWorld()) {
            return false;
        }

        if (player1.getLocation().distance(player2.getLocation()) < 5) {
            return true;
        }

        final org.bukkit.util.Vector direction = player1.getEyeLocation().getDirection();
        final Vector toPlayer2 = player2.getEyeLocation().toVector().subtract(player1.getEyeLocation().toVector());
        toPlayer2.normalize();

        final double dotProduct = direction.dot(toPlayer2);
        return dotProduct > 0.90;
    }

    public boolean isScalePresent() {
        return PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_20_5);
    }

    /**
     * Raw {@link Attribute#SCALE} value (1.0 = normal), for API use. Does not include per-row {@link Settings.DisplayGroup} scale.
     */
    public float getScale(@NotNull Player player) {
        if (!isScalePresent()) {
            return 1f;
        }

        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_20_5)) {
            return 1f;
        }

        final AttributeInstance attribute = player.getAttribute(scaleAttribute);

        if (attribute == null) {
            return 1f;
        }

        return (float) attribute.getValue();
    }

    /**
     * Combined scale for a nametag row: player attribute × display group scale (same formula as {@link org.alexdev.unlimitednametags.packet.PacketNameTag#checkScale()}).
     */
    public float getScaledDisplayScale(@NotNull Player player, float displayGroupScale) {
        if (!isScalePresent()) {
            return displayGroupScale;
        }
        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_20_5)) {
            return displayGroupScale;
        }
        final AttributeInstance attribute = player.getAttribute(scaleAttribute);
        if (attribute == null) {
            return displayGroupScale;
        }
        return (float) (attribute.getValue() * displayGroupScale);
    }

    public void blockPlayer(@NotNull Player player) {
        blocked.add(player.getUniqueId());
        if (debug) {
            plugin.getLogger().info("Blocked " + player.getName());
        }
    }

    public void unblockPlayer(@NotNull Player player) {
        blocked.remove(player.getUniqueId());
        if (debug) {
            plugin.getLogger().info("Unblocked " + player.getName());
        }
    }

    public void clearCache(@NotNull UUID uuid) {
        blocked.remove(uuid);
        creating.remove(uuid);
        hideNametags.remove(uuid);
        hideOwnFromSelf.remove(uuid);
        hideOwnFromOthers.remove(uuid);
        nameTagOverrides.remove(uuid);
        sessionGlowOverrides.remove(uuid);
        persistentGlowOverrides.remove(uuid);
        sessionDisplayAnimations.remove(uuid);
        persistentDisplayAnimations.remove(uuid);
        shiftSystemBlocked.remove(uuid);
    }

    @NotNull
    public NametagPlayerPreferences getPlayerPreferences() {
        return playerPreferences;
    }

    /**
     * Whether this player should see their own nametag above them (global setting + per-player preference).
     */
    public boolean isEffectiveShowOwnNametag(@NotNull Player player) {
        final Settings s = plugin.getConfigManager().getSettings();
        final boolean wants = !hideOwnFromSelf.contains(player.getUniqueId());
        if (!wants) {
            return false;
        }
        if (s.getVisibility().isShowCurrentNameTag()) {
            return true;
        }
        return s.getVisibility().isAllowPerPlayerShowOwnWhenGlobalDisabled();
    }

    public boolean isHidingOwnNametagFromOthers(@NotNull Player owner) {
        return hideOwnFromOthers.contains(owner.getUniqueId());
    }

    public boolean isShowingOwnNametagToSelf(@NotNull Player player) {
        return !hideOwnFromSelf.contains(player.getUniqueId());
    }

    public void setShowingOwnNametagToSelf(@NotNull Player player, boolean show) {
        playerPreferences.writeShowOwnSelf(player, show);
        if (show) {
            hideOwnFromSelf.remove(player.getUniqueId());
        } else {
            hideOwnFromSelf.add(player.getUniqueId());
        }
        refresh(player, true);
    }

    public boolean isShowingOwnNametagToOthers(@NotNull Player player) {
        return !hideOwnFromOthers.contains(player.getUniqueId());
    }

    public void setShowingOwnNametagToOthers(@NotNull Player player, boolean show) {
        playerPreferences.writeShowOwnToOthers(player, show);
        if (show) {
            hideOwnFromOthers.remove(player.getUniqueId());
            showToTrackedPlayers(player);
        } else {
            hideOwnFromOthers.add(player.getUniqueId());
            for (Player viewer : plugin.getTrackerManager().getWhoTracks(player)) {
                if (viewer.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                for (PaperNametagRow tag : getPacketDisplays(player)) {
                    tag.hideFromPlayer(viewer);
                }
            }
        }
        refresh(player, true);
    }

    public void syncPlayerPreferenceSetsFromPdc(@NotNull Player player) {
        final boolean seeOthers = playerPreferences.readSeeOthers(player);
        final boolean showOwnSelf = playerPreferences.readShowOwnSelf(player);
        final boolean showOwnToOthers = playerPreferences.readShowOwnToOthers(player);

        if (!seeOthers) {
            hideNametags.add(player.getUniqueId());
        } else {
            hideNametags.remove(player.getUniqueId());
        }
        if (!showOwnSelf) {
            hideOwnFromSelf.add(player.getUniqueId());
        } else {
            hideOwnFromSelf.remove(player.getUniqueId());
        }
        if (!showOwnToOthers) {
            hideOwnFromOthers.add(player.getUniqueId());
        } else {
            hideOwnFromOthers.remove(player.getUniqueId());
        }
    }

    /**
     * After the joiner's nametag rows usually exist ({@code addPlayer} ~6 ticks async), re-applies packet
     * state for preferences that need entities: hide own from others, hide own from self. Safe if tags are
     * not ready yet (loops are no-ops).
     */
    public void reconcileJoinNametagPacketsForOwner(@NotNull Player player) {
        if (!player.isOnline()) {
            return;
        }
        if (isHidingOwnNametagFromOthers(player)) {
            for (Player viewer : plugin.getTrackerManager().getWhoTracks(player)) {
                if (viewer.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                for (PaperNametagRow tag : getPacketDisplays(player)) {
                    tag.hideFromPlayer(viewer);
                }
            }
        }
        if (!isEffectiveShowOwnNametag(player)) {
            for (PaperNametagRow tag : getPacketDisplays(player)) {
                if (tag.canPlayerSee(player)) {
                    tag.hideFromPlayer(player);
                }
            }
        }
    }

    /**
     * Hides every other player's nametag from this viewer (not the viewer's own rows). Packet-level.
     */
    public void hideAllOthersNametagsFromViewer(@NotNull Player viewer) {
        nameTags.values().forEach(tags -> tags.forEach(display -> {
            final PaperNametagRow row = paperRow(display);
            if (row.getOwner().getUniqueId().equals(viewer.getUniqueId())) {
                return;
            }
            if (row.canPlayerSee(viewer)) {
                row.hideFromPlayer(viewer);
            }
        }));
    }

    /**
     * Loads PDC into runtime sets and reapplies visibility (call when the player is online and nametags exist).
     */
    public void applyPreferencesFromPersistentData(@NotNull Player player) {
        if (!player.isOnline()) {
            return;
        }
        syncPlayerPreferenceSetsFromPdc(player);

        final boolean seeOthers = playerPreferences.readSeeOthers(player);
        final boolean showOwnSelf = playerPreferences.readShowOwnSelf(player);
        final boolean showOwnToOthers = playerPreferences.readShowOwnToOthers(player);

        if (!seeOthers) {
            hideAllOthersNametagsFromViewer(player);
        } else {
            plugin.getTrackerManager().getTrackedPlayers(player.getUniqueId()).forEach(uuid -> {
                final Player tracked = plugin.getPlayerListener().getPlayer(uuid);
                if (tracked == null) {
                    return;
                }
                for (PaperNametagRow packetNameTag : getPacketDisplays(tracked)) {
                    packetNameTag.showToPlayer(player);
                }
            });
        }

        if (!showOwnToOthers) {
            for (Player viewer : plugin.getTrackerManager().getWhoTracks(player)) {
                if (viewer.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                for (PaperNametagRow tag : getPacketDisplays(player)) {
                    tag.hideFromPlayer(viewer);
                }
            }
        } else {
            showToTrackedPlayers(player, plugin.getTrackerManager().getWhoTracks(player));
        }

        refresh(player, true);
    }

    public boolean hasNametagOverride(@NotNull Player player) {
        return nameTagOverrides.containsKey(player.getUniqueId());
    }

    @NotNull
    public Optional<Settings.NameTag> getNametagOverride(@NotNull Player player) {
        return Optional.ofNullable(nameTagOverrides.get(player.getUniqueId()));
    }

    @NotNull
    public Settings.NameTag getEffectiveNametag(@NotNull Player player) {
        final UUID uuid = player.getUniqueId();
        final Settings.NameTag base = nameTagOverrides.getOrDefault(uuid,
                plugin.getConfigManager().getSettings().resolveNametag(player::hasPermission));
        final Map<Integer, DisplayAnimation> animationOverrides = mergedDisplayAnimationOverrides(uuid);
        if (animationOverrides.isEmpty()) {
            return base;
        }
        final List<Settings.DisplayGroup> groups = new ArrayList<>(base.displayGroups());
        animationOverrides.forEach((index, animation) -> {
            if (index >= 0 && index < groups.size()) {
                groups.set(index, groups.get(index).withAnimation(animation));
            }
        });
        return new Settings.NameTag(base.permission(), List.copyOf(groups));
    }

    @NotNull
    private Map<Integer, DisplayAnimation> mergedDisplayAnimationOverrides(@NotNull UUID playerId) {
        final Map<Integer, DisplayAnimation> merged = new LinkedHashMap<>();
        final Map<Integer, DisplayAnimation> persistent = persistentDisplayAnimations.get(playerId);
        if (persistent != null) {
            merged.putAll(persistent);
        }
        final Map<Integer, DisplayAnimation> session = sessionDisplayAnimations.get(playerId);
        if (session != null) {
            merged.putAll(session);
        }
        return merged;
    }

    @Nullable
    public GlowOverride resolveDisplayGroupGlow(
            @NotNull UUID playerId,
            int groupIndex,
            @NotNull Settings.DisplayGroup group) {
        if (group.resolvedDisplayType() == NametagDisplayType.TEXT) {
            return null;
        }
        final Map<Integer, GlowOverride> session = sessionGlowOverrides.get(playerId);
        if (session != null && session.containsKey(groupIndex)) {
            return session.get(groupIndex);
        }
        final Map<Integer, GlowOverride> persistent = persistentGlowOverrides.get(playerId);
        if (persistent != null && persistent.containsKey(groupIndex)) {
            return persistent.get(groupIndex);
        }
        return group.glow();
    }

    private void applyRowGlow(@NotNull Player owner, int groupIndex, @NotNull PacketNameTag display,
            @NotNull Settings.DisplayGroup group) {
        if (group.resolvedDisplayType() == NametagDisplayType.TEXT || display.isTextDisplay()) {
            if (display.getGlowOverride() != null) {
                display.setGlowOverride(null);
            } else {
                display.clearGlow();
            }
            return;
        }
        final GlowOverride resolved = resolveDisplayGroupGlow(owner.getUniqueId(), groupIndex, group);
        if (!java.util.Objects.equals(display.getGlowOverride(), resolved)) {
            display.setGlowOverride(resolved);
        } else if (resolved == null) {
            display.clearGlow();
        } else {
            display.applyGlowNow(displayAnimationMonotonicTick.get());
        }
    }

    public void syncPlayerOverridesFromPdc(@NotNull Player player) {
        final UUID uuid = player.getUniqueId();
        overrideStorage.readNametagOverride(player).ifPresent(tag -> nameTagOverrides.put(uuid, tag));
        persistentGlowOverrides.put(uuid, new LinkedHashMap<>(glowStorage.read(player)));
        persistentDisplayAnimations.put(uuid, new LinkedHashMap<>(overrideStorage.readDisplayAnimations(player)));
        if (hasNametagRows(uuid)) {
            refresh(player, true);
        }
    }

    @NotNull
    public Settings.NameTag getConfigNametag(@NotNull Player player) {
        return plugin.getConfigManager().getSettings().resolveNametag(player::hasPermission);
    }

    private boolean preAddChecks(@NotNull Player player, boolean canBlock) {
        final CopyOnWriteArrayList<PacketNameTag> existing = nameTags.get(player.getUniqueId());
        if (existing != null && !existing.isEmpty()) {
            if (debug) {
                plugin.getLogger().info("Player " + player.getName() + " already has a nametag");
            }
            return false;
        }

        if (creating.contains(player.getUniqueId())) {
            if (debug) {
                plugin.getLogger().info("Player " + player.getName() + " is already creating a nametag");
            }
            return false;
        }

        if (blocked.contains(player.getUniqueId())) {
            if (debug) {
                plugin.getLogger().info("Player " + player.getName() + " is blocked");
            }
            return false;
        } else {
            if (debug) {
                plugin.getLogger().info("Player " + player.getName() + " is not blocked");
            }
        }

        if (PacketEvents.getAPI().getPlayerManager().getUser(player) == null) {
            if (debug) {
                plugin.getLogger().info("Player " + player.getName() + " is not loaded");
            }
            return false;
        }

        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            if (debug) {
                plugin.getLogger().info("Player " + player.getName() + " has invisibility potion effect, blocking");
            }
            if (canBlock) {
                blockPlayer(player);
            }
            return false;
        }

        if (player.getGameMode() == GameMode.SPECTATOR) {
            if (debug) {
                plugin.getLogger().info("Player " + player.getName() + " is in spectator mode, skipping");
            }
            if (canBlock) {
                blockPlayer(player);
            }
            return false;
        }

        return true;
    }

    public void addPlayer(@NotNull Player player, boolean canBlock) {
        if (!preAddChecks(player, canBlock)) {
            return;
        }

        creating.add(player.getUniqueId());

        final Settings.NameTag nametag = getEffectiveNametag(player);

        updateLineCount(player, nametag);

        final CopyOnWriteArrayList<PacketNameTag> createdTags = nameTags.get(player.getUniqueId());
        final int rowCount = nametag.displayGroups().size();
        pendingRowCreations.put(player.getUniqueId(), new AtomicInteger(rowCount));

        final List<CompletableFuture<ResolvedDisplayRow>> futures = new ArrayList<>(rowCount);
        for (int i = 0; i < rowCount; i++) {
            final Settings.DisplayGroup displayGroup = nametag.displayGroups().get(i);
            final PacketNameTag display = createdTags.get(i);
            futures.add(resolveDisplayRow(player, i, displayGroup, display, List.of(player), "create"));
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenRun(() -> {
            final List<ResolvedDisplayRow> rows = collectResolvedRows(futures);
            final float helmetExtraOffset = plugin.getPlaceholderManager().computeHelmetExtraOffset(player);
            runDeferredDisplayBatch(rows, false, () -> {
                for (ResolvedDisplayRow row : rows) {
                    final Component resolved = row.ownerComponent();
                    if (resolved == null) {
                        plugin.getLogger().warning(
                                "No nametag component for owner " + player.getName() + "; skipping display row.");
                        finishRowCreation(player.getUniqueId());
                        continue;
                    }
                    loadDisplay(player, row.index(), resolved, row.displayGroup(), row.display(), helmetExtraOffset);
                }
                applyDisplayGroupStackLayout(player, rows, helmetExtraOffset);
                applyPersistentGlowOverrides(player);
                replayTrackedViewersAfterCreation(player);
            });
            final int missingRows = rowCount - rows.size();
            for (int i = 0; i < missingRows; i++) {
                finishRowCreation(player.getUniqueId());
            }
        });

    }
    public void refresh(@NotNull Player player, boolean force) {
        final Settings.NameTag nametag = getEffectiveNametag(player);

        if (PacketEvents.getAPI().getPlayerManager().getUser(player) == null) {
            return;
        }

        updateLineCount(player, nametag);

        final CopyOnWriteArrayList<PacketNameTag> playerTags = nameTags.get(player.getUniqueId());
        if (playerTags == null || playerTags.isEmpty()) {
            return;
        }

        final List<CompletableFuture<ResolvedDisplayRow>> futures = new ArrayList<>(nametag.displayGroups().size());
        for (int i = 0; i < nametag.displayGroups().size(); i++) {
            final Settings.DisplayGroup displayGroup = nametag.displayGroups().get(i);
            replaceDisplayIfNeeded(player, i, displayGroup);
            final PaperNametagRow display = paperRow(playerTags.get(i));

            final boolean show = isEffectiveShowOwnNametag(player);
            if (show && !display.canPlayerSee(player)) {
                display.showToPlayer(player);
            } else if (!show && display.canPlayerSee(player)) {
                display.hideFromPlayer(player);
            }

            if (force) {
                if (show) {
                    display.showToPlayer(display.getOwner());
                } else {
                    display.hideFromPlayer(display.getOwner());
                }
            }

            final List<Player> relationalPlayers = relationalPlayersForRefresh(player, (PacketNameTag) display);

            futures.add(resolveDisplayRow(player, i, displayGroup, (PacketNameTag) display, relationalPlayers, "edit"));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenRun(() -> {
            final List<ResolvedDisplayRow> rows = collectResolvedRows(futures);
            final float helmetExtraOffset = plugin.getPlaceholderManager().computeHelmetExtraOffset(player);
            runDeferredDisplayBatch(rows, force, () -> {
                rows.forEach(row -> editDisplay(player, row.index(), row.display(), row.components(),
                        row.displayGroup(), force, helmetExtraOffset));
                applyDisplayGroupStackLayout(player, rows, helmetExtraOffset);
            });
        });
    }

    private void updateLineCount(Player player, Settings.NameTag nametag) {
        CopyOnWriteArrayList<PacketNameTag> list = nameTags.computeIfAbsent(player.getUniqueId(),
                k -> new CopyOnWriteArrayList<>());

        int needed = nametag.displayGroups().size();
        int current = list.size();

        if (needed > current) {
            for (int i = current; i < needed; i++) {
                Settings.DisplayGroup displayGroup = nametag.displayGroups().get(i);
                final PaperNametagRow display = PacketNameTags.create(plugin, player, displayGroup);
                if (displayGroup.resolvedDisplayType() == NametagDisplayType.TEXT) {
                    display.text(player, Component.empty());
                }
                display.spawn(player);

                if (isEffectiveShowOwnNametag(player)) {
                    display.showToPlayer(player);
                }

                handleVanish(player, (PacketNameTag) display);

                list.add((PacketNameTag) display);
                if (debug) {
                    plugin.getLogger().info("Added nametag for " + player.getName());
                }
                entityIdToDisplay.put(((PacketNameTag) display).getEntityId(), (PacketNameTag) display);
            }
        } else if (needed < current) {
            while (list.size() > needed) {
                int last = list.size() - 1;
                final PacketNameTag display = list.remove(last);
                display.remove();
                entityIdToDisplay.remove(display.getEntityId());
                if (debug) {
                    plugin.getLogger().info("Removed nametag for " + player.getName());
                }
            }
            if (list.isEmpty()) {
                nameTags.remove(player.getUniqueId(), list);
            }
        }
    }

    private void editDisplay(@NotNull Player owner, int groupIndex, PacketNameTag packetNameTag,
            Map<Player, Component> components, @NotNull Settings.DisplayGroup displayGroup, boolean force,
            float helmetExtraOffset) {
        if (!packetNameTag.getDisplayGroup().equals(displayGroup)) {
            packetNameTag.setDisplayGroup(displayGroup);
        }
        final Settings cfg = plugin.getConfigManager().getSettings();
        packetNameTag.setBillboard(displayGroup.effectiveBillboard(cfg));
        packetNameTag.setHelmetExtraOffset(helmetExtraOffset);
        applyRowGlow(owner, groupIndex, packetNameTag, displayGroup);
        if (displayGroup.resolvedDisplayType() != NametagDisplayType.TEXT) {
            packetNameTag.syncVisualFromGroup(displayGroup);
            if (force && isScalePresent()) {
                packetNameTag.checkScale();
            }
            if (force) {
                packetNameTag.updateYOOffset();
            }
            return;
        }
        if (force && isScalePresent()) {
            packetNameTag.checkScale();
        }
        if (force) {
            packetNameTag.updateYOOffset();
        }

        final PaperNametagRow row = paperRow(packetNameTag);

        components.forEach((p, c) -> {
            if (c == null) {
                return;
            }
            row.text(p, c);
            final User user = PacketEvents.getAPI().getPlayerManager().getUser(p);
            if (user == null) {
                return;
            }
            packetNameTag.modifyTextForViewer(user, m -> applyTextVisualState(packetNameTag, displayGroup, m, force));
        });
    }

    private void applyTextVisualState(@NotNull PacketNameTag display, @NotNull Settings.DisplayGroup displayGroup, boolean force) {
        display.modifyTextAll(meta -> applyTextVisualState(display, displayGroup, meta, force));
    }

    private void applyTextVisualState(@NotNull PacketNameTag display, @NotNull Settings.DisplayGroup displayGroup,
            @NotNull TextDisplayMeta meta, boolean force) {
        final boolean shadowed = displayGroup.effectiveBackground().shadowed();
        final Settings.ThroughWallMode throughWallMode = plugin.getConfigManager().getSettings()
                .getVisibility().getThroughWallMode();
        final boolean seeThrough = throughWallMode == Settings.ThroughWallMode.SEE_THROUGH
                && displayGroup.effectiveBackground().seeThrough()
                && !display.isSneaking();
        final int backgroundColor = displayGroup.effectiveBackground().getArgb();

        if (force || meta.isShadow() != shadowed) {
            meta.setShadow(shadowed);
        }
        if (force || meta.getBackgroundColor() != backgroundColor) {
            meta.setBackgroundColor(backgroundColor);
        }
        if (force || meta.isSeeThrough() != seeThrough) {
            meta.setSeeThrough(seeThrough);
        }
    }

    private void finishRowCreation(@NotNull UUID uuid) {
        final AtomicInteger pending = pendingRowCreations.get(uuid);
        if (pending != null && pending.decrementAndGet() <= 0) {
            creating.remove(uuid);
            pendingRowCreations.remove(uuid);
        }
    }

    private void loadDisplay(@NotNull Player player, int groupIndex, @NotNull Component component,
            @NotNull Settings.DisplayGroup displayGroup,
            @NotNull PacketNameTag display,
            float helmetExtraOffset) {
        try {
            finishRowCreation(player.getUniqueId());
            final NametagDisplayType dt = displayGroup.resolvedDisplayType();
            if (dt == NametagDisplayType.TEXT) {
                display.modifyTextAll(m -> m.setUseDefaultBackground(false));
                paperRow(display).text(player, component);
            } else {
                display.syncVisualFromGroup(displayGroup);
            }
            display.setBillboard(displayGroup.effectiveBillboard(plugin.getConfigManager().getSettings()));
            if (dt == NametagDisplayType.TEXT) {
                applyTextVisualState(display, displayGroup, true);
            }

            display.resetOffset(plugin.getConfigManager().getSettings().getBehavior().getYOffset());
            display.setHelmetExtraOffset(helmetExtraOffset);

            display.setViewRange(plugin.getConfigManager().getSettings().getBehavior().getViewDistance());
            applyRowGlow(player, groupIndex, display, displayGroup);

            if (dt == NametagDisplayType.TEXT) {
                plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
                    display.modifyTextForOwner(meta -> meta.setText(component));
                    display.flushViewerMetadata(player.getUniqueId(), false);
                }, 1);
            }

            handleVanish(player, display);

        } catch (Throwable e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(),
                    e);
        }
    }

    private void replaceDisplayIfNeeded(@NotNull Player player, int index, @NotNull Settings.DisplayGroup displayGroup) {
        final CopyOnWriteArrayList<PacketNameTag> list = nameTags.get(player.getUniqueId());
        if (list == null || index < 0 || index >= list.size()) {
            return;
        }
        final PacketNameTag current = list.get(index);
        if (current.matchesDisplayGroupEntityType(displayGroup)) {
            return;
        }
        current.remove();
        entityIdToDisplay.remove(current.getEntityId());
        final PaperNametagRow neu = PacketNameTags.create(plugin, player, displayGroup);
        if (displayGroup.resolvedDisplayType() == NametagDisplayType.TEXT) {
            neu.text(player, Component.empty());
        }
        neu.spawn(player);
        if (isEffectiveShowOwnNametag(player)) {
            neu.showToPlayer(player);
        }
        handleVanish(player, (PacketNameTag) neu);
        list.set(index, (PacketNameTag) neu);
        entityIdToDisplay.put(((PacketNameTag) neu).getEntityId(), (PacketNameTag) neu);
    }

    @NotNull
    private CompletableFuture<ResolvedDisplayRow> resolveDisplayRow(@NotNull Player player,
            int index,
            @NotNull Settings.DisplayGroup displayGroup,
            @NotNull PacketNameTag display,
            @NotNull List<Player> relationalPlayers,
            @NotNull String action) {
        final List<Player> layoutPlayers = relationalPlayersWithOwner(player, relationalPlayers);
        return plugin.getPlaceholderManager().applyPlaceholders(player, displayGroup, layoutPlayers)
                .thenApply(lines -> new ResolvedDisplayRow(index, displayGroup, display, layoutPlayers, lines, lines.get(player)))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Failed to " + action + " nametag for " + player.getName(), throwable);
                    return null;
                });
    }

    @NotNull
    private List<Player> relationalPlayersWithOwner(@NotNull Player owner, @NotNull List<Player> relationalPlayers) {
        if (relationalPlayers.contains(owner)) {
            return relationalPlayers;
        }
        final ArrayList<Player> withOwner = new ArrayList<>(relationalPlayers.size() + 1);
        withOwner.add(owner);
        withOwner.addAll(relationalPlayers);
        return withOwner;
    }

    @NotNull
    private List<ResolvedDisplayRow> collectResolvedRows(@NotNull List<CompletableFuture<ResolvedDisplayRow>> futures) {
        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(ResolvedDisplayRow::index))
                .toList();
    }

    private void runDeferredDisplayBatch(@NotNull List<ResolvedDisplayRow> rows, final boolean force,
            @NotNull Runnable action) {
        for (final ResolvedDisplayRow row : rows) {
            row.display().setDeferMetadataFlush(true);
        }
        try {
            action.run();
            flushDirtyForRows(rows, force);
        } finally {
            for (final ResolvedDisplayRow row : rows) {
                row.display().setDeferMetadataFlush(false);
            }
        }
    }

    private void flushDirtyForRows(@NotNull List<ResolvedDisplayRow> rows, final boolean force) {
        final LinkedHashSet<UUID> viewerIds = new LinkedHashSet<>();
        for (final ResolvedDisplayRow row : rows) {
            for (final Player viewer : row.relationalPlayers()) {
                viewerIds.add(viewer.getUniqueId());
            }
        }
        for (final ResolvedDisplayRow row : rows) {
            final PaperNametagRow paperRow = paperRow(row.display());
            for (final UUID viewerId : viewerIds) {
                if (row.display().flushViewerMetadata(viewerId, force)) {
                    paperRow.notifyRefreshedForViewer(viewerId);
                }
            }
        }
    }

    private void applyDisplayGroupStackLayout(@NotNull Player player, @NotNull List<ResolvedDisplayRow> rows,
            float helmetExtraOffset) {
        final Settings settings = plugin.getConfigManager().getSettings();
        final float globalYOffset = settings.getBehavior().getYOffset();

        if (!settings.getBehavior().isCompactDisplayGroupStack()) {
            rows.forEach(row -> {
                row.display().clearCompactStackYOffset();
                row.display().setHelmetExtraOffset(helmetExtraOffset);
            });
            return;
        }

        final boolean perViewerNeeded = rows.stream().anyMatch(r -> plugin.getPlaceholderManager().requiresRelationalEvaluation(r.displayGroup()));
        final LinkedHashSet<Player> viewersUnion = new LinkedHashSet<>();
        for (ResolvedDisplayRow row : rows) {
            viewersUnion.addAll(row.relationalPlayers());
        }

        if (!perViewerNeeded) {
            rows.forEach(row -> {
                row.display().clearPerViewerStackLayout();
                row.display().setHelmetExtraOffset(helmetExtraOffset);
            });
            final float lineHeight = Math.max(0.01f, settings.getBehavior().getDisplayGroupLineHeightBlocks());
            boolean hasVisibleRow = false;
            float nextYOffset = 0f;

            for (ResolvedDisplayRow row : rows) {
                if (!isCompactStackVisible(player, row)) {
                    row.display().setCompactStackYOffset(hasVisibleRow ? nextYOffset : row.displayGroup().yOffset(), false);
                    continue;
                }

                final float rowYOffset = hasVisibleRow ? nextYOffset : row.displayGroup().yOffset();
                row.display().setCompactStackYOffset(rowYOffset, !hasVisibleRow);
                nextYOffset = rowYOffset + estimateDisplayGroupHeight(row, lineHeight);
                hasVisibleRow = true;
            }
            return;
        }

        rows.forEach(row -> {
            row.display().clearCompactStackYOffset();
            row.display().setHelmetExtraOffset(0f);
        });

        final float lineHeight = Math.max(0.01f, settings.getBehavior().getDisplayGroupLineHeightBlocks());
        final List<Map<UUID, Float>> yByRow = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            yByRow.add(new HashMap<>());
        }

        for (Player viewer : viewersUnion) {
            boolean hasVisibleRow = false;
            float nextYOffset = 0f;
            for (int i = 0; i < rows.size(); i++) {
                final ResolvedDisplayRow row = rows.get(i);
                final PacketNameTag display = row.display();
                final float inc = stackIncreasedOffset(display);
                final boolean visible = isCompactStackVisibleForViewer(player, viewer, row);
                final float rowCompactY;
                final boolean helmetForRow;
                rowCompactY = hasVisibleRow ? nextYOffset : row.displayGroup().yOffset();
                if (!visible) {
                    helmetForRow = false;
                } else {
                    helmetForRow = !hasVisibleRow;
                    nextYOffset = rowCompactY + estimateDisplayGroupHeightForViewer(player, viewer, row, lineHeight);
                    hasVisibleRow = true;
                }
                final float baseY = globalYOffset + inc + rowCompactY + (helmetForRow ? helmetExtraOffset : 0f);
                yByRow.get(i).put(viewer.getUniqueId(), baseY);
            }
        }

        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).display().setPerViewerStackBaseY(yByRow.get(i));
        }
    }

    private static float stackIncreasedOffset(@NotNull PacketNameTag display) {
        final float sc = display.getScale();
        return sc > 1f ? sc / 5f : 0f;
    }

    private boolean isCompactStackVisibleForViewer(
            @NotNull Player owner, @NotNull Player viewer, @NotNull ResolvedDisplayRow row) {
        final boolean relational = plugin.getPlaceholderManager().requiresRelationalEvaluation(row.displayGroup());
        if (!plugin.getPlaceholderManager().isDisplayGroupActive(owner, row.displayGroup(),
                relational ? viewer : null)) {
            return false;
        }

        return switch (row.displayGroup().resolvedDisplayType()) {
            case TEXT -> CompactStackVisibility.shouldReserveTextRowStackSpace(row.displayGroup());
            case ITEM -> isMaterialVisible(owner, row.displayGroup().itemMaterial(), true);
            case BLOCK -> isMaterialVisible(owner, row.displayGroup().blockMaterial(), false);
        };
    }

    private float estimateDisplayGroupHeightForViewer(
            @NotNull Player owner, @NotNull Player viewer, @NotNull ResolvedDisplayRow row, float lineHeight) {
        final float scale = row.display().getScale();
        return switch (row.displayGroup().resolvedDisplayType()) {
            case TEXT -> Math.max(1, estimateTextLineCount(row.components().get(viewer))) * lineHeight * scale;
            case ITEM -> COMPACT_ITEM_DISPLAY_HEIGHT_BLOCKS * scale;
            case BLOCK -> COMPACT_BLOCK_DISPLAY_HEIGHT_BLOCKS * scale;
        };
    }

    private boolean isCompactStackVisible(@NotNull Player player, @NotNull ResolvedDisplayRow row) {
        if (!plugin.getPlaceholderManager().isDisplayGroupActive(player, row.displayGroup())) {
            return false;
        }

        return switch (row.displayGroup().resolvedDisplayType()) {
            case TEXT -> CompactStackVisibility.shouldReserveTextRowStackSpace(row.displayGroup());
            case ITEM -> isMaterialVisible(player, row.displayGroup().itemMaterial(), true);
            case BLOCK -> isMaterialVisible(player, row.displayGroup().blockMaterial(), false);
        };
    }

    private boolean isMaterialVisible(@NotNull Player player, String rawMaterial, boolean item) {
        final String raw = (rawMaterial == null || rawMaterial.isBlank()) ? "STONE" : rawMaterial;
        final String expanded = plugin.getPlaceholderManager().expandForOwner(player, raw).trim();

        if (item) {
            try {
                if (org.alexdev.unlimitednametags.platform.BukkitNametagMaterialBridge.resolveItemFromRegistry(expanded) != null) {
                    return true;
                }
            } catch (Throwable ignored) {}
        } else {
            try {
                if (org.alexdev.unlimitednametags.platform.BukkitNametagMaterialBridge.resolveBlockFromRegistry(expanded) != null) {
                    return true;
                }
            } catch (Throwable ignored) {}
        }

        final Material material = Material.matchMaterial(expanded, false);
        if (material == null) {
            return false;
        }
        return item ? material.isItem() : material.isBlock() && !material.isAir();
    }

    private float estimateDisplayGroupHeight(@NotNull ResolvedDisplayRow row, float lineHeight) {
        final float scale = row.display().getScale();
        return switch (row.displayGroup().resolvedDisplayType()) {
            case TEXT -> Math.max(1, estimateTextLineCount(row.ownerComponent())) * lineHeight * scale;
            case ITEM -> COMPACT_ITEM_DISPLAY_HEIGHT_BLOCKS * scale;
            case BLOCK -> COMPACT_BLOCK_DISPLAY_HEIGHT_BLOCKS * scale;
        };
    }

    private int estimateTextLineCount(Component component) {
        if (!hasRenderableText(component)) {
            return 0;
        }
        return Math.max(1, countNewlines(component) + 1);
    }

    private boolean hasRenderableText(Component component) {
        if (component == null) {
            return false;
        }
        if (component instanceof TextComponent textComponent) {
            if (textComponent.content().chars().anyMatch(c -> c != '\n' && c != '\r')) {
                return true;
            }
            return component.children().stream().anyMatch(this::hasRenderableText);
        }
        return !component.equals(Component.empty()) || component.children().stream().anyMatch(this::hasRenderableText);
    }

    private int countNewlines(Component component) {
        if (component == null) {
            return 0;
        }
        int count = 0;
        if (component instanceof TextComponent textComponent) {
            final String content = textComponent.content();
            for (int i = 0; i < content.length(); i++) {
                if (content.charAt(i) == '\n') {
                    count++;
                }
            }
        }
        for (Component child : component.children()) {
            count += countNewlines(child);
        }
        return count;
    }

    @NotNull
    private List<Player> relationalPlayersForRefresh(@NotNull Player owner, @NotNull PacketNameTag display) {
        final List<Player> fromViewers = display.getViewers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .toList();
        if (!fromViewers.isEmpty()) {
            return fromViewers;
        }
        final LinkedHashSet<Player> set = new LinkedHashSet<>();
        set.add(owner);
        set.addAll(plugin.getTrackerManager().getWhoTracks(owner));
        return new ArrayList<>(set);
    }

    private void handleVanish(@NotNull Player player, @NotNull PacketNameTag display) {
        final boolean isVanished = plugin.getVanishManager().isVanished(player);

        // if player is vanished, hide display for all players except for who can see
        // the player
        plugin.getPlayerListener().getOnlinePlayers().values().stream()
                .filter(p -> p != player)
                .filter(p -> p.getLocation().getWorld() == player.getLocation().getWorld())
                .filter(p -> !isVanished || plugin.getVanishManager().canSee(p, player))
                .filter(p -> p.getLocation().distance(player.getLocation()) <= 250)
                .filter(p -> !paperRow(display).canPlayerSee(p))
                .forEach(p -> paperRow(display).showToPlayer(p));
    }

    public void removePlayer(@NotNull Player player) {
        final CopyOnWriteArrayList<PacketNameTag> packetNameTags = nameTags.remove(player.getUniqueId());
        if (packetNameTags != null) {
            for (PacketNameTag packetNameTag : packetNameTags) {
                packetNameTag.remove();
                entityIdToDisplay.remove(packetNameTag.getEntityId());
            }
        }

        nameTags.values().forEach(tags -> tags.forEach(display -> {
            paperRow(display).handleQuit(player);
            display.getBlocked().remove(player.getUniqueId());
        }));
    }

    public void removeAllViewers(@NotNull Player player) {
        final CopyOnWriteArrayList<PacketNameTag> tagList = nameTags.get(player.getUniqueId());
        final List<PacketNameTag> packetNameTags = tagList == null ? List.of() : tagList;
        for (PacketNameTag packetNameTag : packetNameTags) {
            packetNameTag.setVisible(false);
            packetNameTag.clearViewers();
        }
    }

    public void showToTrackedPlayers(@NotNull Player player) {
        showToTrackedPlayers(player, plugin.getTrackerManager().getWhoTracks(player));
    }

    public void showToTrackedPlayers(@NotNull Player player, @NotNull Collection<Player> tracked) {
        showToTrackedPlayers(player, tracked, true, true);
    }

    private void replayTrackedViewersAfterCreation(@NotNull Player player) {
        if (!player.isOnline()) {
            return;
        }
        showToTrackedPlayers(player, plugin.getTrackerManager().getWhoTracks(player), false, isEffectiveShowOwnNametag(player));
    }

    private void showToTrackedPlayers(@NotNull Player player, @NotNull Collection<Player> tracked, boolean ensureCreated, boolean includeOwner) {
        final CopyOnWriteArrayList<PacketNameTag> tagList = nameTags.get(player.getUniqueId());
        final List<PacketNameTag> packetNameTags = tagList == null ? List.of() : tagList;
        for (PacketNameTag packetNameTag : packetNameTags) {
            packetNameTag.setVisible(true);
            final Set<Player> players = tracked.stream()
                    .filter(Objects::nonNull)
                    .filter(Player::isOnline)
                    .collect(Collectors.toSet());
            final PaperNametagRow row = paperRow(packetNameTag);
            if (includeOwner) {
                players.add(row.getOwner());
            }
            row.showToPlayers(players);
            if (debug) {
                plugin.getLogger().info("Showing nametag of " + player.getName() + " to tracked players: " +
                        players.stream().map(Player::getName).collect(Collectors.joining(", ")));
            }
        }

        if (ensureCreated) {
            addPlayer(player, false);
        }
    }

    public void hideAllDisplays(@NotNull Player player) {
        nameTags.values().forEach(tags -> tags.forEach(display -> {
            paperRow(display).hideFromPlayer(player);
            display.getBlocked().add(player.getUniqueId());
        }));
        for (PaperNametagRow row : getPacketDisplays(player)) {
            ((PacketNameTag) row).clearViewers();
        }
    }

    public void removeAll() {
        nameTags.values().forEach(tags -> tags.forEach(PacketNameTag::remove));

        entityIdToDisplay.clear();
        nameTags.clear();
    }

    public void updateSneaking(@NotNull Player player, boolean sneaking) {
        if (shiftSystemBlocked.getOrDefault(player.getUniqueId(), false)) {
            return;
        }

        final Settings settings = plugin.getConfigManager().getSettings();
        final Settings.ThroughWallMode mode = settings.getVisibility().getThroughWallMode();
        final boolean isObscured = mode == Settings.ThroughWallMode.OBSCURED;
        final byte sneakB = clampMcTextOpacity(settings.getVisibility().getSneakOpacity());
        final byte obscB = clampMcTextOpacity(settings.getVisibility().getThroughWallSettings().getOpacity());
        final double maxSq = settings.getVisibility().getThroughWallSettings().getMaxDistance() * settings.getVisibility().getThroughWallSettings().getMaxDistance();

        for (PaperNametagRow row : getPacketDisplays(player)) {
            final PacketNameTag packetNameTag = (PacketNameTag) row;
            packetNameTag.setSneaking(sneaking);
            if (packetNameTag.isTextDisplay()) {
                if (isObscured) {
                    packetNameTag.applyObscuredLineOfSightPresentation(true, sneakB, obscB, maxSq, sneaking);
                } else {
                    applyTextVisualState(packetNameTag, packetNameTag.getDisplayGroup(), false);
                    packetNameTag.setTextOpacity(sneaking ? sneakB : (byte) -1);
                }
            }
            packetNameTag.refresh();
        }
    }

    public void reload() {
        final Settings settings = plugin.getConfigManager().getSettings();
        final float yOffset = settings.getBehavior().getYOffset();
        final float viewDistance = settings.getBehavior().getViewDistance();
        final Settings.ThroughWallMode throughWallMode = settings.getVisibility().getThroughWallMode();
        final byte sneakOpacity = clampMcTextOpacity(settings.getVisibility().getSneakOpacity());
        nameTags.values().forEach(tags -> tags.forEach(tag -> {
            tag.clearObscuredPresentationTracking();
            if (tag.isTextDisplay()) {
                applyTextVisualState(tag, tag.getDisplayGroup(), true);
                if (throughWallMode != Settings.ThroughWallMode.OBSCURED) {
                    tag.setTextOpacity(tag.isSneaking() ? sneakOpacity : (byte) -1);
                }
            }
        }));
        plugin.getTaskScheduler()
                .runTaskAsynchronously(() -> plugin.getPlayerListener().getOnlinePlayers().values().forEach(p -> {
                    setYOffset(p, yOffset);
                    setViewDistance(p, viewDistance);
                    applyBillboardsFromEffectiveNametag(p);
                    applyPreferencesFromPersistentData(p);
                }));
        startTask();
    }

    @SuppressWarnings("UnstableApiUsage")
    public void debug(@NotNull CommandSender audience) {
        audience.sendRichMessage("<red>UnlimitedNameTags v" + plugin.getPluginMeta().getVersion() + " . Compiled: "
                + plugin.getConfigManager().isCompiled());
        final AtomicReference<Component> component = new AtomicReference<>(
                Component.text("Nametags:").colorIfAbsent(TextColor.color(0xFF0000)));
        nameTags.forEach((uuid, tags) -> tags.forEach(display -> {
            final Player player = plugin.getPlayerListener().getPlayer(uuid);

            if (player == null) {
                return;
            }

            final List<String> viewers = display.getViewers().stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .map(Player::getName)
                    .collect(Collectors.toList());

            if (!isEffectiveShowOwnNametag(player)) {
                viewers.remove(player.getName());
            }

            final long lastUpdate = display.getLastUpdate();

            final Component text = getComponent(display, viewers, player, lastUpdate);
            component.set(component.get().append(Component.text("\n")).append(text));
        }));

        plugin.getKyoriManager().sendMessage(audience, component.get());
    }

    @NotNull
    private Component getComponent(@NotNull PacketNameTag display, @NotNull List<String> viewers,
            @NotNull Player player, long lastUpdate) {
        final int seconds = (int) ((System.currentTimeMillis() - lastUpdate) / 1000);
        final Map<String, String> properties = display.properties();
        Component hover = Component.text("Viewers: " + viewers).appendNewline()
                .append(Component.text("Owner: " + paperRow(display).getOwner().getName())).appendNewline()
                .append(Component.text("Visible: " + display.isVisible())).appendNewline()
                .append(Component.text("Last update: " + seconds + "s ago")).appendNewline();

        for (Map.Entry<String, String> entry : properties.entrySet()) {
            hover = hover.append(Component.text(entry.getKey() + ": " + entry.getValue())).appendNewline();
        }

        Component text = Component.text(player.getName() + " -> " + " " + display.getEntityId());
        text = text.color(TextColor.color(0x00FF00));
        text = text.hoverEvent(hover.color(TextColor.color(Color.RED.asRGB())));
        return text;
    }

    private void setYOffset(@NotNull Player player, float yOffset) {
        for (PaperNametagRow row : getPacketDisplays(player)) {
            ((PacketNameTag) row).resetOffset(yOffset);
        }
    }

    private void applyBillboardsFromEffectiveNametag(@NotNull Player player) {
        final Settings.NameTag nameTag = getEffectiveNametag(player);
        final List<Settings.DisplayGroup> groups = nameTag.displayGroups();
        final Settings settings = plugin.getConfigManager().getSettings();
        int i = 0;
        for (PaperNametagRow tag : getPacketDisplays(player)) {
            if (i >= groups.size()) {
                break;
            }
            tag.setBillboard(groups.get(i).effectiveBillboard(settings));
            i++;
        }
    }

    private void setViewDistance(@NotNull Player player, float viewDistance) {
        getPacketDisplays(player).forEach(row -> ((PacketNameTag) row).setViewRange(viewDistance));
    }

    public void vanishPlayer(@NotNull Player player) {
        getPacketDisplays(player).forEach(row -> {
            final PacketNameTag packetNameTag = (PacketNameTag) row;
            final Set<UUID> viewers = new HashSet<>(packetNameTag.getViewers());
            final boolean isVanished = plugin.getVanishManager().isVanished(player);
            viewers.forEach(uuid -> {
                final Player viewer = plugin.getPlayerListener().getPlayer(uuid);
                if (viewer == null || viewer == player) {
                    return;
                }
                if (isVanished && !plugin.getVanishManager().canSee(viewer, player)) {
                    return;
                }
                row.hideFromPlayer(viewer);
            });
        });
    }

    public void unVanishPlayer(@NotNull Player player) {
        getPacketDisplays(player).forEach(row -> {
            final PacketNameTag packetNameTag = (PacketNameTag) row;
            final Set<UUID> viewers = new HashSet<>(packetNameTag.getViewers());
            viewers.forEach(uuid -> {
                final Player viewer = plugin.getPlayerListener().getPlayer(uuid);
                if (viewer == null || viewer == player) {
                    return;
                }
                row.showToPlayer(viewer);
            });
        });
    }

    @NotNull
    public Collection<PaperNametagRow> getPacketDisplays(@NotNull Player player) {
        CopyOnWriteArrayList<PacketNameTag> list = nameTags.get(player.getUniqueId());
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return list.stream().map(NameTagManager::paperRow).toList();
    }

    @Override
    @NotNull
    public Collection<? extends UntNametagDisplay> getPacketDisplayText(@NotNull Player player) {
        return getPacketDisplays(player);
    }

    @NotNull
    public Optional<PaperNametagRow> getPacketDisplayByEntityId(int id) {
        return Optional.ofNullable(entityIdToDisplay.get(id)).map(NameTagManager::paperRow);
    }

    @Override
    @NotNull
    public Optional<? extends UntNametagDisplayCore> getPacketDisplayText(int id) {
        return getPacketDisplayByEntityId(id).map(tag -> tag);
    }

    public void updateDisplay(@NotNull Player player, @NotNull Player target) {
        if (player == target && isEffectiveShowOwnNametag(player)) {
            showToOwner(player);
            return;
        }
        for (PaperNametagRow packetNameTag : getPacketDisplays(target)) {
            packetNameTag.hideFromPlayerSilently(player);
            packetNameTag.showToPlayer(player);
        }
    }

    public void showToOwner(@NotNull Player player) {
        if (!isEffectiveShowOwnNametag(player)) {
            return;
        }
        for (PaperNametagRow row : getPacketDisplays(player)) {
            ((PacketNameTag) row).spawnForOwner();
        }
    }

    public void removeDisplay(@NotNull Player player, @NotNull Player target) {
        if (player == target && !isEffectiveShowOwnNametag(player)) {
            return;
        }
        for (PaperNametagRow packetNameTag : getPacketDisplays(target)) {
            packetNameTag.hideFromPlayer(player);
        }
    }

    public void updateDisplaysForPlayer(@NotNull Player player) {
        nameTags.values().forEach(tags -> tags.forEach(display -> {
            final PaperNametagRow row = paperRow(display);
            final Player owner = row.getOwner();
            if (owner == player) {
                return;
            }
            final Set<UUID> tracked = plugin.getTrackerManager().getTrackedPlayers(owner.getUniqueId());

            if (player.getLocation().getWorld() != owner.getLocation().getWorld()) {
                return;
            }

            if (plugin.getVanishManager().isVanished(owner) && !plugin.getVanishManager().canSee(player, owner)) {
                return;
            }

            if (!tracked.contains(player.getUniqueId())) {
                return;
            }

            display.getBlocked().remove(player.getUniqueId());

            row.hideFromPlayerSilently(player);
            row.showToPlayer(player);
        }));
    }

    public void refreshDisplaysForPlayer(@NotNull Player player) {
        nameTags.values().forEach(tags -> tags.forEach(display -> {
            final PaperNametagRow row = paperRow(display);
            if (!row.canPlayerSee(player)) {
                return;
            }

            row.refreshForPlayer(player, true);
        }));
    }

    public void unBlockForAllPlayers(@NotNull Player player) {
        nameTags.values().forEach(tags -> tags.forEach(display -> display.getBlocked().remove(player.getUniqueId())));
    }

    public void hideOtherNametags(@NotNull Player player) {
        hideNametags.add(player.getUniqueId());
        playerPreferences.writeSeeOthers(player, false);
        hideAllOthersNametagsFromViewer(player);
    }

    public void showOtherNametags(@NotNull Player player) {
        hideNametags.remove(player.getUniqueId());
        playerPreferences.writeSeeOthers(player, true);
        plugin.getTrackerManager().getTrackedPlayers(player.getUniqueId()).forEach(uuid -> {
            final Player tracked = plugin.getPlayerListener().getPlayer(uuid);
            if (tracked == null) {
                return;
            }

            for (PaperNametagRow packetNameTag : getPacketDisplays(tracked)) {
                packetNameTag.showToPlayer(player);
            }
        });
    }

    public boolean isHiddenOtherNametags(@NotNull Player player) {
        return hideNametags.contains(player.getUniqueId());
    }

    public void swapNametag(@NotNull Player player, @NotNull Settings.NameTag nameTag) {
        updateLineCount(player, nameTag);

        final CopyOnWriteArrayList<PacketNameTag> swapTags = nameTags.get(player.getUniqueId());
        if (swapTags == null || swapTags.isEmpty()) {
            return;
        }

        final List<CompletableFuture<ResolvedDisplayRow>> futures = new ArrayList<>(nameTag.displayGroups().size());
        for (int i = 0; i < nameTag.displayGroups().size(); i++) {
            final Settings.DisplayGroup displayGroup = nameTag.displayGroups().get(i);
            replaceDisplayIfNeeded(player, i, displayGroup);
            final PacketNameTag display = swapTags.get(i);

            final List<Player> relationalPlayers = relationalPlayersForRefresh(player, display);

            futures.add(resolveDisplayRow(player, i, displayGroup, display, relationalPlayers, "swap"));
        }

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenRun(() -> {
            final List<ResolvedDisplayRow> rows = collectResolvedRows(futures);
            final float helmetExtraOffset = plugin.getPlaceholderManager().computeHelmetExtraOffset(player);
            runDeferredDisplayBatch(rows, false, () -> {
                for (ResolvedDisplayRow row : rows) {
                    final Component component = row.ownerComponent();
                    if (component == null) {
                        plugin.getLogger().warning(
                                "No nametag component for owner " + player.getName() + "; swap skipped for one row.");
                        continue;
                    }
                    loadDisplay(player, row.index(), component, row.displayGroup(), row.display(), helmetExtraOffset);
                    if (row.displayGroup().resolvedDisplayType() == NametagDisplayType.TEXT) {
                        row.components().forEach((p, c) -> {
                            if (!p.equals(player) && c != null) {
                                paperRow(row.display()).text(p, c);
                            }
                        });
                    }
                }
                applyDisplayGroupStackLayout(player, rows, helmetExtraOffset);
            });
        });

    }

    public void setNametagOverride(@NotNull Player player, @NotNull Settings.NameTag nameTag) {
        setNametagOverride(player, nameTag, false);
    }

    public void setNametagOverride(@NotNull Player player, @NotNull Settings.NameTag nameTag, boolean persist) {
        nameTagOverrides.put(player.getUniqueId(), nameTag);
        if (persist) {
            overrideStorage.writeNametagOverride(player, nameTag);
        }
        swapNametag(player, nameTag);
    }

    public void removeNametagOverride(@NotNull Player player) {
        removeNametagOverride(player, false);
    }

    public void removeNametagOverride(@NotNull Player player, boolean persist) {
        nameTagOverrides.remove(player.getUniqueId());
        if (persist) {
            overrideStorage.clearNametagOverride(player);
        }
        swapNametag(player, getConfigNametag(player));
    }

    @Override
    public void setDisplayGroupGlow(
            @NotNull UUID playerId,
            int groupIndex,
            @Nullable GlowOverride glow,
            boolean persist) {
        setDisplayGroupGlow(playerId, groupIndex, glow, persist, true);
    }

    /**
     * @param reapplyWhenOnline when {@code false}, only updates session/PDC maps (batch writes before one refresh)
     */
    public void setDisplayGroupGlow(
            @NotNull UUID playerId,
            int groupIndex,
            @Nullable GlowOverride glow,
            boolean persist,
            boolean reapplyWhenOnline) {
        if (glow == null) {
            clearDisplayGroupGlow(playerId, groupIndex, persist, reapplyWhenOnline);
            return;
        }
        if (persist) {
            final Map<Integer, GlowOverride> map = new LinkedHashMap<>(
                    persistentGlowOverrides.getOrDefault(playerId, Map.of()));
            map.put(groupIndex, glow);
            persistentGlowOverrides.put(playerId, Map.copyOf(map));
            final Player player = onlinePlayer(playerId);
            if (player != null) {
                glowStorage.write(player, map);
            }
            sessionGlowOverrides.computeIfPresent(playerId, (id, session) -> {
                session.remove(groupIndex);
                return session.isEmpty() ? null : session;
            });
        } else {
            sessionGlowOverrides.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>()).put(groupIndex, glow);
        }
        if (reapplyWhenOnline) {
            final Player player = onlinePlayer(playerId);
            if (player != null) {
                applyGlowOverrideToPlayer(player);
            }
        }
    }

    @Override
    public void clearDisplayGroupGlow(@NotNull UUID playerId, int groupIndex, boolean persist) {
        clearDisplayGroupGlow(playerId, groupIndex, persist, true);
    }

    public void clearDisplayGroupGlow(
            @NotNull UUID playerId,
            int groupIndex,
            boolean persist,
            boolean reapplyWhenOnline) {
        if (persist) {
            final Map<Integer, GlowOverride> map = new LinkedHashMap<>(
                    persistentGlowOverrides.getOrDefault(playerId, Map.of()));
            if (map.remove(groupIndex) != null) {
                if (map.isEmpty()) {
                    persistentGlowOverrides.remove(playerId);
                } else {
                    persistentGlowOverrides.put(playerId, map);
                }
                final Player player = onlinePlayer(playerId);
                if (player != null) {
                    glowStorage.write(player, map);
                }
            }
        }
        sessionGlowOverrides.computeIfPresent(playerId, (id, session) -> {
            session.remove(groupIndex);
            return session.isEmpty() ? null : session;
        });
        if (reapplyWhenOnline) {
            final Player player = onlinePlayer(playerId);
            if (player != null) {
                applyGlowOverrideToPlayer(player);
            }
        }
    }

    @Override
    @NotNull
    public Optional<GlowOverride> getDisplayGroupGlowOverride(@NotNull UUID playerId, int groupIndex) {
        final Map<Integer, GlowOverride> session = sessionGlowOverrides.get(playerId);
        if (session != null && session.containsKey(groupIndex)) {
            return Optional.ofNullable(session.get(groupIndex));
        }
        final Map<Integer, GlowOverride> persistent = persistentGlowOverrides.get(playerId);
        if (persistent != null && persistent.containsKey(groupIndex)) {
            return Optional.ofNullable(persistent.get(groupIndex));
        }
        return Optional.empty();
    }

    @Override
    public void setNametagDisplayGroupAnimation(
            @NotNull UUID playerId,
            int displayGroupIndex,
            @Nullable DisplayAnimation animation,
            boolean persist) {
        if (persist) {
            final Map<Integer, DisplayAnimation> map = new LinkedHashMap<>(
                    persistentDisplayAnimations.getOrDefault(playerId, Map.of()));
            if (animation == null) {
                map.remove(displayGroupIndex);
            } else {
                map.put(displayGroupIndex, animation);
            }
            if (map.isEmpty()) {
                persistentDisplayAnimations.remove(playerId);
            } else {
                persistentDisplayAnimations.put(playerId, map);
            }
            final Player player = onlinePlayer(playerId);
            if (player != null) {
                overrideStorage.writeDisplayAnimations(player, map);
            }
            sessionDisplayAnimations.computeIfPresent(playerId, (id, session) -> {
                session.remove(displayGroupIndex);
                return session.isEmpty() ? null : session;
            });
        } else {
            if (animation == null) {
                sessionDisplayAnimations.computeIfPresent(playerId, (id, session) -> {
                    session.remove(displayGroupIndex);
                    return session.isEmpty() ? null : session;
                });
            } else {
                sessionDisplayAnimations.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>())
                        .put(displayGroupIndex, animation);
            }
        }
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            refresh(player, true);
        }
    }

    private boolean hasNametagRows(@NotNull UUID playerId) {
        final CopyOnWriteArrayList<PacketNameTag> tags = nameTags.get(playerId);
        return tags != null && !tags.isEmpty();
    }

    /**
     * Re-applies PDC-backed glow overrides on every display row (join, reload, or after /unt glow).
     */
    public void applyPersistentGlowOverrides(@NotNull Player player) {
        if (!hasNametagRows(player.getUniqueId())) {
            return;
        }
        final Settings.NameTag nametag = getEffectiveNametag(player);
        final CopyOnWriteArrayList<PacketNameTag> tags = nameTags.get(player.getUniqueId());
        final int count = Math.min(tags.size(), nametag.displayGroups().size());
        for (int i = 0; i < count; i++) {
            applyRowGlow(player, i, tags.get(i), nametag.displayGroups().get(i));
        }
    }

    private void applyGlowOverrideToPlayer(@NotNull Player player) {
        if (hasNametagRows(player.getUniqueId())) {
            refresh(player, false);
            return;
        }
        applyPersistentGlowOverrides(player);
    }

    public void setShiftSystemBlocked(@NotNull Player player, boolean blocked) {
        if (blocked) {
            shiftSystemBlocked.put(player.getUniqueId(), true);
        } else {
            shiftSystemBlocked.remove(player.getUniqueId());
        }
    }

    public boolean isShiftSystemBlocked(@NotNull Player player) {
        return shiftSystemBlocked.getOrDefault(player.getUniqueId(), false);
    }

    public void sendPassengersPacket(@NotNull User user, Player owner) {
        final CopyOnWriteArrayList<PacketNameTag> tags = nameTags.get(owner.getUniqueId());
        plugin.getPacketManager().sendPassengersPacket(user, owner, tags == null ? List.of() : tags);
    }

    @org.jetbrains.annotations.Nullable
    private Player onlinePlayer(@NotNull UUID playerId) {
        return plugin.getPlayerListener().getPlayer(playerId);
    }

    @Override
    public float getScale(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        return player != null ? getScale(player) : 1f;
    }

    @Override
    public void blockPlayer(@NotNull UUID playerId) {
        blocked.add(playerId);
        if (debug) {
            plugin.getLogger().info("Blocked " + playerId);
        }
    }

    @Override
    public void unblockPlayer(@NotNull UUID playerId) {
        blocked.remove(playerId);
        if (debug) {
            plugin.getLogger().info("Unblocked " + playerId);
        }
    }

    @Override
    public boolean hasNametagOverride(@NotNull UUID playerId) {
        return nameTagOverrides.containsKey(playerId);
    }

    @Override
    @NotNull
    public Optional<Settings.NameTag> getNametagOverride(@NotNull UUID playerId) {
        return Optional.ofNullable(nameTagOverrides.get(playerId));
    }

    @Override
    @NotNull
    public Settings.NameTag getEffectiveNametag(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        return player != null ? getEffectiveNametag(player)
                : plugin.getConfigManager().getSettings().resolveNametag(p -> false);
    }

    @Override
    @NotNull
    public Settings.NameTag getConfigNametag(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        return player != null ? getConfigNametag(player)
                : plugin.getConfigManager().getSettings().resolveNametag(p -> false);
    }

    @Override
    public void addPlayer(@NotNull UUID playerId, boolean canBlock) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            addPlayer(player, canBlock);
        }
    }

    @Override
    public void refresh(@NotNull UUID playerId, boolean force) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            refresh(player, force);
        }
    }

    @Override
    public void removePlayer(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            removePlayer(player);
        }
    }

    @Override
    public void removeAllViewers(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            removeAllViewers(player);
        }
    }

    @Override
    public void showToTrackedPlayers(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            showToTrackedPlayers(player);
        }
    }

    @Override
    public void showToTrackedPlayers(@NotNull UUID playerId, @NotNull Collection<UUID> tracked) {
        final Player player = onlinePlayer(playerId);
        if (player == null) {
            return;
        }
        showToTrackedPlayers(player, tracked.stream()
                .map(this::onlinePlayer)
                .filter(Objects::nonNull)
                .collect(Collectors.toList()));
    }

    @Override
    public void hideAllDisplays(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            hideAllDisplays(player);
        }
    }

    @Override
    public void updateSneaking(@NotNull UUID playerId, boolean sneaking) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            updateSneaking(player, sneaking);
        }
    }

    @Override
    public void vanishPlayer(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            vanishPlayer(player);
        }
    }

    @Override
    public void unVanishPlayer(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            unVanishPlayer(player);
        }
    }

    @Override
    public void updateDisplay(@NotNull UUID ownerId, @NotNull UUID targetId) {
        final Player owner = onlinePlayer(ownerId);
        final Player target = onlinePlayer(targetId);
        if (owner != null && target != null) {
            updateDisplay(owner, target);
        }
    }

    @Override
    public void showToOwner(@NotNull UUID ownerId) {
        final Player owner = onlinePlayer(ownerId);
        if (owner != null) {
            showToOwner(owner);
        }
    }

    @Override
    public void removeDisplay(@NotNull UUID ownerId, @NotNull UUID targetId) {
        final Player owner = onlinePlayer(ownerId);
        final Player target = onlinePlayer(targetId);
        if (owner != null && target != null) {
            removeDisplay(owner, target);
        }
    }

    @Override
    public void updateDisplaysForPlayer(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            updateDisplaysForPlayer(player);
        }
    }

    @Override
    public void refreshDisplaysForPlayer(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            refreshDisplaysForPlayer(player);
        }
    }

    @Override
    public void unBlockForAllPlayers(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            unBlockForAllPlayers(player);
        }
    }

    @Override
    public void hideOtherNametags(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            hideOtherNametags(player);
        }
    }

    @Override
    public void showOtherNametags(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            showOtherNametags(player);
        }
    }

    @Override
    public boolean isHiddenOtherNametags(@NotNull UUID playerId) {
        return hideNametags.contains(playerId);
    }

    @Override
    public boolean isEffectiveShowOwnNametag(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        return player != null && isEffectiveShowOwnNametag(player);
    }

    @Override
    public boolean isShowingOwnNametagToSelf(@NotNull UUID playerId) {
        return !hideOwnFromSelf.contains(playerId);
    }

    @Override
    public void setShowingOwnNametagToSelf(@NotNull UUID playerId, boolean show) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            setShowingOwnNametagToSelf(player, show);
        }
    }

    @Override
    public boolean isShowingOwnNametagToOthers(@NotNull UUID playerId) {
        return !hideOwnFromOthers.contains(playerId);
    }

    @Override
    public void setShowingOwnNametagToOthers(@NotNull UUID playerId, boolean show) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            setShowingOwnNametagToOthers(player, show);
        }
    }

    @Override
    public void applyPreferencesFromPersistentData(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            applyPreferencesFromPersistentData(player);
        }
    }

    @Override
    public void syncPlayerPreferenceSetsFromPdc(@NotNull UUID playerId) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            syncPlayerPreferenceSetsFromPdc(player);
        }
    }

    @Override
    public void swapNametag(@NotNull UUID playerId, @NotNull Settings.NameTag nameTag) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            swapNametag(player, nameTag);
        }
    }

    @Override
    public void setNametagOverride(@NotNull UUID playerId, @NotNull Settings.NameTag nameTag) {
        setNametagOverride(playerId, nameTag, false);
    }

    @Override
    public void setNametagOverride(@NotNull UUID playerId, @NotNull Settings.NameTag nameTag, boolean persist) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            setNametagOverride(player, nameTag, persist);
        } else {
            nameTagOverrides.put(playerId, nameTag);
        }
    }

    @Override
    public void removeNametagOverride(@NotNull UUID playerId) {
        removeNametagOverride(playerId, false);
    }

    @Override
    public void removeNametagOverride(@NotNull UUID playerId, boolean persist) {
        final Player player = onlinePlayer(playerId);
        if (player != null) {
            removeNametagOverride(player, persist);
        } else {
            nameTagOverrides.remove(playerId);
        }
    }

    @Override
    public void setShiftSystemBlocked(@NotNull UUID playerId, boolean blocked) {
        if (blocked) {
            shiftSystemBlocked.put(playerId, true);
        } else {
            shiftSystemBlocked.remove(playerId);
        }
    }

    @Override
    public boolean isShiftSystemBlocked(@NotNull UUID playerId) {
        return shiftSystemBlocked.getOrDefault(playerId, false);
    }

    @Override
    @NotNull
    public Attribute getScaleAttribute() {
        return scaleAttribute;
    }

    private static PaperNametagRow paperRow(@NotNull PacketNameTag tag) {
        return (PaperNametagRow) tag;
    }
}
