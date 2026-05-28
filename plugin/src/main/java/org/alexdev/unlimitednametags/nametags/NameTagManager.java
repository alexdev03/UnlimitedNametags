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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.api.UntNametagManager;
import org.alexdev.unlimitednametags.config.NametagDisplayType;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.data.NametagPlayerPreferences;
import org.alexdev.unlimitednametags.hook.HMCCosmeticsHook;
import org.alexdev.unlimitednametags.hook.ViaVersionHook;
import org.alexdev.unlimitednametags.packet.PacketNameTag;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Getter
public class NameTagManager implements UntNametagManager {

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
    private final Map<UUID, Settings.NameTag> nameTagOverrides;
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
        this.nameTagOverrides = Maps.newConcurrentMap();
        this.shiftSystemBlocked = Maps.newConcurrentMap();
        this.loadAll();
        this.scaleAttribute = loadScaleAttribute();
    }

    private void loadAll() {
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            plugin.getPlayerListener().getOnlinePlayers().values().forEach(p -> addPlayer(p, true));
            this.startTask();
        }, 5);
    }

    private void startTask() {
        tasks.forEach(MyScheduledTask::cancel);
        tasks.clear();

        final MyScheduledTask displayAnimations = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                () -> {
                    final long t = displayAnimationMonotonicTick.incrementAndGet();
                    nameTags.values().forEach(tags -> tags.forEach(tag -> tag.tickDisplayAnimation(t)));
                },
                1L,
                1L);
        tasks.add(displayAnimations);

        final MyScheduledTask refresh = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                () -> {
                    if (plugin.isPaper() && plugin.getServer().isStopping()) {
                        return;
                    }
                    nameTags.values().forEach(tags -> tags.forEach(tag -> refresh(tag.getOwner(), false)));
                },
                10, plugin.getConfigManager().getSettings().getBehavior().getTaskInterval());
        tasks.add(refresh);

        // Refresh passengers
        final MyScheduledTask passengers = plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> nameTags.values()
                .stream()
                .flatMap(Collection::stream)
                .map(PacketNameTag::getOwner)
                .distinct()
                .filter(p -> plugin.getHook(HMCCosmeticsHook.class).map(h -> !h.hasBackpack(p)).orElse(true))
                .forEach(player -> getPacketDisplayText(player)
                        .forEach(PacketNameTag::sendPassengerPacketToViewers)),
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
                    final Player targetOwner = tag.getOwner();
                    final List<Player> viewers = plugin.getTrackerManager().getWhoTracks(targetOwner);

                    for (Player viewer : viewers) {
                        if (plugin.getHook(ViaVersionHook.class).map(h -> h.hasNotTextDisplays(viewer)).orElse(false)) {
                            continue;
                        }

                        final boolean isPointing = isPlayerPointingAt(viewer, targetOwner);

                        if (tag.canPlayerSee(viewer) && !isPointing) {
                            tag.hideFromPlayer(viewer);
                        } else if (!tag.canPlayerSee(viewer) && isPointing) {
                            tag.showToPlayer(viewer);
                        }
                    }
                }));
            }, 5, 5);
            tasks.add(point);
        }

        if (plugin.getConfigManager().getSettings().getVisibility().isObscuredNametagThroughWalls()) {
            final int obscuredInterval = Math.max(1, plugin.getConfigManager().getSettings().getVisibility().getObscuredNametagCheckInterval());
            final MyScheduledTask obscured = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                    this::tickObscuredNametagThroughWalls,
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

    private void tickObscuredNametagThroughWalls() {
        if (!plugin.getConfigManager().getSettings().getVisibility().isObscuredNametagThroughWalls()) {
            return;
        }
        final Settings s = plugin.getConfigManager().getSettings();
        final byte sneakB = clampMcTextOpacity(s.getVisibility().getSneakOpacity());
        final byte obscB = clampMcTextOpacity(s.getVisibility().getObscuredNametagOpacity());
        final double maxSq = s.getVisibility().getObscuredNametagMaxDistance() * s.getVisibility().getObscuredNametagMaxDistance();
        for (final CopyOnWriteArrayList<PacketNameTag> tags : nameTags.values()) {
            for (final PacketNameTag tag : tags) {
                if (!tag.isTextDisplay()) {
                    continue;
                }
                final Player owner = tag.getOwner();
                final boolean shiftBlocked = shiftSystemBlocked.getOrDefault(owner.getUniqueId(), false);
                final boolean sneakEff = tag.isSneaking() && !shiftBlocked;
                tag.applyObscuredLineOfSightPresentation(true, sneakB, obscB, maxSq, sneakEff);
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
        shiftSystemBlocked.remove(uuid);
    }

    @NotNull
    public NametagPlayerPreferences getPlayerPreferences() {
        return playerPreferences;
    }

    /**
     * Whether this player should see their own nametag above them (global setting + per-player preference).
     */
    @Override
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

    @Override
    public boolean isShowingOwnNametagToSelf(@NotNull Player player) {
        return !hideOwnFromSelf.contains(player.getUniqueId());
    }

    @Override
    public void setShowingOwnNametagToSelf(@NotNull Player player, boolean show) {
        playerPreferences.writeShowOwnSelf(player, show);
        if (show) {
            hideOwnFromSelf.remove(player.getUniqueId());
        } else {
            hideOwnFromSelf.add(player.getUniqueId());
        }
        refresh(player, true);
    }

    @Override
    public boolean isShowingOwnNametagToOthers(@NotNull Player player) {
        return !hideOwnFromOthers.contains(player.getUniqueId());
    }

    @Override
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
                for (PacketNameTag tag : getPacketDisplayText(player)) {
                    tag.hideFromPlayer(viewer);
                }
            }
        }
        refresh(player, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
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
                for (PacketNameTag tag : getPacketDisplayText(player)) {
                    tag.hideFromPlayer(viewer);
                }
            }
        }
        if (!isEffectiveShowOwnNametag(player)) {
            for (PacketNameTag tag : getPacketDisplayText(player)) {
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
            if (display.getOwner().getUniqueId().equals(viewer.getUniqueId())) {
                return;
            }
            if (display.canPlayerSee(viewer)) {
                display.hideFromPlayer(viewer);
            }
        }));
    }

    /**
     * Loads PDC into runtime sets and reapplies visibility (call when the player is online and nametags exist).
     */
    @Override
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
                for (PacketNameTag packetNameTag : getPacketDisplayText(tracked)) {
                    packetNameTag.showToPlayer(player);
                }
            });
        }

        if (!showOwnToOthers) {
            for (Player viewer : plugin.getTrackerManager().getWhoTracks(player)) {
                if (viewer.getUniqueId().equals(player.getUniqueId())) {
                    continue;
                }
                for (PacketNameTag tag : getPacketDisplayText(player)) {
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
        return nameTagOverrides.getOrDefault(player.getUniqueId(),
                plugin.getConfigManager().getSettings().getNametag(player));
    }

    @NotNull
    public Settings.NameTag getConfigNametag(@NotNull Player player) {
        return plugin.getConfigManager().getSettings().getNametag(player);
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
            for (ResolvedDisplayRow row : rows) {
                final Component resolved = row.ownerComponent();
                if (resolved == null) {
                    plugin.getLogger().warning(
                            "No nametag component for owner " + player.getName() + "; skipping display row.");
                    finishRowCreation(player.getUniqueId());
                    continue;
                }
                loadDisplay(player, resolved, row.displayGroup(), row.display(), helmetExtraOffset);
            }
            applyDisplayGroupStackLayout(player, rows, helmetExtraOffset);
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
            final PacketNameTag display = playerTags.get(i);

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

            final List<Player> relationalPlayers = relationalPlayersForRefresh(player, display);

            futures.add(resolveDisplayRow(player, i, displayGroup, display, relationalPlayers, "edit"));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).thenRun(() -> {
            final List<ResolvedDisplayRow> rows = collectResolvedRows(futures);
            final float helmetExtraOffset = plugin.getPlaceholderManager().computeHelmetExtraOffset(player);
            rows.forEach(row -> editDisplay(row.display(), row.components(), row.displayGroup(), force, helmetExtraOffset));
            applyDisplayGroupStackLayout(player, rows, helmetExtraOffset);
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
                final PacketNameTag display = PacketNameTag.create(plugin, player, displayGroup);
                if (displayGroup.resolvedDisplayType() == NametagDisplayType.TEXT) {
                    display.text(player, Component.empty());
                }
                display.spawn(player);

                if (isEffectiveShowOwnNametag(player)) {
                    display.showToPlayer(player);
                }

                handleVanish(player, display);

                list.add(display);
                if (debug) {
                    plugin.getLogger().info("Added nametag for " + player.getName());
                }
                entityIdToDisplay.put(display.getEntityId(), display);
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

    private void editDisplay(PacketNameTag packetNameTag, Map<Player, Component> components,
            @NotNull Settings.DisplayGroup displayGroup, boolean force, float helmetExtraOffset) {
        if (!packetNameTag.getDisplayGroup().equals(displayGroup)) {
            packetNameTag.setDisplayGroup(displayGroup);
        }
        final Settings cfg = plugin.getConfigManager().getSettings();
        packetNameTag.setBillboard(displayGroup.effectiveBillboard(cfg));
        packetNameTag.setHelmetExtraOffset(helmetExtraOffset);
        if (displayGroup.resolvedDisplayType() != NametagDisplayType.TEXT) {
            packetNameTag.syncVisualFromGroup(displayGroup);
            if (force && isScalePresent()) {
                packetNameTag.checkScale();
            }
            if (force) {
                packetNameTag.updateYOOffset();
            }
            for (Player p : components.keySet()) {
                packetNameTag.refreshForPlayer(p);
            }
            return;
        }
        if (force && isScalePresent()) {
            packetNameTag.checkScale();
        }
        if (force) {
            packetNameTag.updateYOOffset();
        }

        final boolean shadowed = displayGroup.effectiveBackground().shadowed();
        final boolean seeThrough = displayGroup.effectiveBackground().seeThrough() && !packetNameTag.isSneaking();
        final int backgroundColor = displayGroup.effectiveBackground().getColor().asARGB();
        final boolean wallOpacity = cfg.getVisibility().isObscuredNametagThroughWalls();

        components.forEach((p, c) -> {
            if (c == null) {
                return;
            }
            final boolean[] updateRef = { packetNameTag.text(p, c) || force };
            final User user = PacketEvents.getAPI().getPlayerManager().getUser(p);
            if (user == null) {
                return;
            }
            packetNameTag.modifyTextForViewer(user, m -> {

                if (force) {
                    m.setShadow(shadowed);
                    if (!wallOpacity) {
                        m.setSeeThrough(seeThrough);
                    }
                    m.setBackgroundColor(backgroundColor);
                } else {
                    if (m.isShadow() != shadowed) {
                        m.setShadow(shadowed);
                        updateRef[0] = true;
                    }
                    if (m.getBackgroundColor() != backgroundColor) {
                        m.setBackgroundColor(backgroundColor);
                        updateRef[0] = true;
                    }
                    if (!wallOpacity && m.isSeeThrough() != seeThrough) {
                        m.setSeeThrough(seeThrough);
                        updateRef[0] = true;
                    }
                }

            });

            if (updateRef[0]) {
                packetNameTag.refreshForPlayer(p);
            }
        });
    }

    private void finishRowCreation(@NotNull UUID uuid) {
        final AtomicInteger pending = pendingRowCreations.get(uuid);
        if (pending != null && pending.decrementAndGet() <= 0) {
            creating.remove(uuid);
            pendingRowCreations.remove(uuid);
        }
    }

    private void loadDisplay(@NotNull Player player, @NotNull Component component,
            @NotNull Settings.DisplayGroup displayGroup,
            @NotNull PacketNameTag display,
            float helmetExtraOffset) {
        try {
            finishRowCreation(player.getUniqueId());
            final NametagDisplayType dt = displayGroup.resolvedDisplayType();
            if (dt == NametagDisplayType.TEXT) {
                display.modifyTextAll(m -> m.setUseDefaultBackground(false));
                display.text(player, component);
            } else {
                display.syncVisualFromGroup(displayGroup);
            }
            display.setBillboard(displayGroup.effectiveBillboard(plugin.getConfigManager().getSettings()));
            if (dt == NametagDisplayType.TEXT) {
                display.setShadowed(displayGroup.effectiveBackground().shadowed());
                if (!plugin.getConfigManager().getSettings().getVisibility().isObscuredNametagThroughWalls()) {
                    display.setSeeThrough(displayGroup.effectiveBackground().seeThrough() && !display.isSneaking());
                }
                display.setBackgroundColor(displayGroup.effectiveBackground().getColor());
            }

            display.resetOffset(plugin.getConfigManager().getSettings().getBehavior().getYOffset());
            display.setHelmetExtraOffset(helmetExtraOffset);

            display.setViewRange(plugin.getConfigManager().getSettings().getBehavior().getViewDistance());

            if (dt == NametagDisplayType.TEXT) {
                plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> display.modifyTextForOwner(meta -> meta.setText(component)),
                        1);
            }

            display.refresh();

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
        final PacketNameTag neu = PacketNameTag.create(plugin, player, displayGroup);
        if (displayGroup.resolvedDisplayType() == NametagDisplayType.TEXT) {
            neu.text(player, Component.empty());
        }
        neu.spawn(player);
        if (isEffectiveShowOwnNametag(player)) {
            neu.showToPlayer(player);
        }
        handleVanish(player, neu);
        list.set(index, neu);
        entityIdToDisplay.put(neu.getEntityId(), neu);
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

        final boolean perViewerNeeded = rows.stream().anyMatch(r -> r.displayGroup().relationalConditions());
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
                if (!visible) {
                    rowCompactY = hasVisibleRow ? nextYOffset : row.displayGroup().yOffset();
                    helmetForRow = false;
                } else {
                    rowCompactY = hasVisibleRow ? nextYOffset : row.displayGroup().yOffset();
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
        final boolean relationalText = row.displayGroup().relationalConditions()
                && row.displayGroup().resolvedDisplayType() == NametagDisplayType.TEXT;
        if (!plugin.getPlaceholderManager().isDisplayGroupActive(owner, row.displayGroup(),
                relationalText ? viewer : null)) {
            return false;
        }

        return switch (row.displayGroup().resolvedDisplayType()) {
            case TEXT -> hasRenderableText(row.components().get(viewer));
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
            case TEXT -> hasRenderableText(row.ownerComponent());
            case ITEM -> isMaterialVisible(player, row.displayGroup().itemMaterial(), true);
            case BLOCK -> isMaterialVisible(player, row.displayGroup().blockMaterial(), false);
        };
    }

    private boolean isMaterialVisible(@NotNull Player player, String rawMaterial, boolean item) {
        String raw = rawMaterial;
        if (raw == null || raw.isBlank()) {
            raw = "STONE";
        }
        final String expanded = plugin.getPlaceholderManager().expandForOwner(player, raw).trim();
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
                .filter(p -> !display.canPlayerSee(p))
                .forEach(display::showToPlayer);
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
            display.handleQuit(player);
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
        final CopyOnWriteArrayList<PacketNameTag> tagList = nameTags.get(player.getUniqueId());
        final List<PacketNameTag> packetNameTags = tagList == null ? List.of() : tagList;
        for (PacketNameTag packetNameTag : packetNameTags) {
            packetNameTag.setVisible(true);
            final Set<Player> players = tracked.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            players.add(packetNameTag.getOwner());
            packetNameTag.showToPlayers(players);
            if (debug) {
                plugin.getLogger().info("Showing nametag of " + player.getName() + " to tracked players: " +
                        players.stream().map(Player::getName).collect(Collectors.joining(", ")));
            }
        }

        addPlayer(player, false);
    }

    public void hideAllDisplays(@NotNull Player player) {
        nameTags.values().forEach(tags -> tags.forEach(display -> {
            display.hideFromPlayer(player);
            display.getBlocked().add(player.getUniqueId());
        }));
        for (PacketNameTag packetNameTag : getPacketDisplayText(player)) {
            packetNameTag.clearViewers();
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
        final boolean wall = settings.getVisibility().isObscuredNametagThroughWalls();
        final byte sneakB = clampMcTextOpacity(settings.getVisibility().getSneakOpacity());
        final byte obscB = clampMcTextOpacity(settings.getVisibility().getObscuredNametagOpacity());
        final double maxSq = settings.getVisibility().getObscuredNametagMaxDistance() * settings.getVisibility().getObscuredNametagMaxDistance();

        for (PacketNameTag packetNameTag : getPacketDisplayText(player)) {
            packetNameTag.setSneaking(sneaking);
            if (packetNameTag.isTextDisplay()) {
                if (wall) {
                    packetNameTag.applyObscuredLineOfSightPresentation(true, sneakB, obscB, maxSq, sneaking);
                } else {
                    if (packetNameTag.getDisplayGroup().effectiveBackground().seeThrough()) {
                        packetNameTag.setSeeThrough(!sneaking);
                    }
                    packetNameTag.setTextOpacity(sneaking ? sneakB : (byte) -1);
                }
            }
            packetNameTag.refresh();
        }
    }

    public void reload() {
        final float yOffset = plugin.getConfigManager().getSettings().getBehavior().getYOffset();
        final float viewDistance = plugin.getConfigManager().getSettings().getBehavior().getViewDistance();
        if (!plugin.getConfigManager().getSettings().getVisibility().isObscuredNametagThroughWalls()) {
            nameTags.values().forEach(tags -> tags.forEach(PacketNameTag::clearObscuredPresentationTracking));
        }
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
                .append(Component.text("Owner: " + display.getOwner().getName())).appendNewline()
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
        for (PacketNameTag packetNameTag : getPacketDisplayText(player)) {
            packetNameTag.resetOffset(yOffset);
        }
    }

    private void applyBillboardsFromEffectiveNametag(@NotNull Player player) {
        final Settings.NameTag nameTag = getEffectiveNametag(player);
        final List<Settings.DisplayGroup> groups = nameTag.displayGroups();
        final Settings settings = plugin.getConfigManager().getSettings();
        int i = 0;
        for (PacketNameTag tag : getPacketDisplayText(player)) {
            if (i >= groups.size()) {
                break;
            }
            tag.setBillboard(groups.get(i).effectiveBillboard(settings));
            i++;
        }
    }

    private void setViewDistance(@NotNull Player player, float viewDistance) {
        getPacketDisplayText(player).forEach(packetNameTag -> packetNameTag.setViewRange(viewDistance));
    }

    public void vanishPlayer(@NotNull Player player) {
        getPacketDisplayText(player).forEach(packetNameTag -> {
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
                packetNameTag.hideFromPlayer(viewer);
            });
        });
    }

    public void unVanishPlayer(@NotNull Player player) {
        getPacketDisplayText(player).forEach(packetNameTag -> {
            final Set<UUID> viewers = new HashSet<>(packetNameTag.getViewers());
            viewers.forEach(uuid -> {
                final Player viewer = plugin.getPlayerListener().getPlayer(uuid);
                if (viewer == null || viewer == player) {
                    return;
                }
                packetNameTag.showToPlayer(viewer);
            });
        });
    }

    @NotNull
    public Collection<PacketNameTag> getPacketDisplayText(@NotNull Player player) {
        CopyOnWriteArrayList<PacketNameTag> list = nameTags.get(player.getUniqueId());
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(list);
    }

    @NotNull
    public Optional<PacketNameTag> getPacketDisplayText(int id) {
        return Optional.ofNullable(entityIdToDisplay.get(id));
    }

    public void updateDisplay(@NotNull Player player, @NotNull Player target) {
        if (player == target && isEffectiveShowOwnNametag(player)) {
            showToOwner(player);
            return;
        }
        for (PacketNameTag packetNameTag : getPacketDisplayText(target)) {
            packetNameTag.hideFromPlayerSilently(player);
            packetNameTag.showToPlayer(player);
        }
    }

    public void showToOwner(@NotNull Player player) {
        if (!isEffectiveShowOwnNametag(player)) {
            return;
        }
        for (PacketNameTag packetNameTag : getPacketDisplayText(player)) {
            packetNameTag.spawnForOwner();
        }
    }

    public void removeDisplay(@NotNull Player player, @NotNull Player target) {
        if (player == target && !isEffectiveShowOwnNametag(player)) {
            return;
        }
        for (PacketNameTag packetNameTag : getPacketDisplayText(target)) {
            packetNameTag.hideFromPlayer(player);
        }
    }

    public void updateDisplaysForPlayer(@NotNull Player player) {
        nameTags.values().forEach(tags -> tags.forEach(display -> {
            final Player owner = display.getOwner();
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

            display.hideFromPlayerSilently(player);
            display.showToPlayer(player);
        }));
    }

    public void refreshDisplaysForPlayer(@NotNull Player player) {
        nameTags.values().forEach(tags -> tags.forEach(display -> {
            if (!display.canPlayerSee(player)) {
                return;
            }

            display.refreshForPlayer(player);
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

            for (PacketNameTag packetNameTag : getPacketDisplayText(tracked)) {
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
            for (ResolvedDisplayRow row : rows) {
                final Component component = row.ownerComponent();
                if (component == null) {
                    plugin.getLogger().warning(
                            "No nametag component for owner " + player.getName() + "; swap skipped for one row.");
                    continue;
                }
                loadDisplay(player, component, row.displayGroup(), row.display(), helmetExtraOffset);
                if (row.displayGroup().resolvedDisplayType() == NametagDisplayType.TEXT) {
                    row.components().forEach((p, c) -> {
                        if (!p.equals(player) && c != null) {
                            row.display().text(p, c);
                        }
                    });
                }
            }
            applyDisplayGroupStackLayout(player, rows, helmetExtraOffset);
        });

    }

    public void setNametagOverride(@NotNull Player player, @NotNull Settings.NameTag nameTag) {
        nameTagOverrides.put(player.getUniqueId(), nameTag);

        swapNametag(player, nameTag);
    }

    public void removeNametagOverride(@NotNull Player player) {
        nameTagOverrides.remove(player.getUniqueId());

        final Settings.NameTag configNametag = getConfigNametag(player);
        swapNametag(player, configNametag);
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
        plugin.getPacketManager().sendPassengersPacket(user, owner, getPacketDisplayText(owner));
    }
}
