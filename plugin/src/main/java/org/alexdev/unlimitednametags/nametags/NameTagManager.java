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
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.api.UntNametagManager;
import org.alexdev.unlimitednametags.config.NametagDisplayType;
import org.alexdev.unlimitednametags.config.Settings;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Getter
public class NameTagManager implements UntNametagManager {

    private final UnlimitedNameTags plugin;
    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<PacketNameTag>> nameTags;
    private final Map<Integer, PacketNameTag> entityIdToDisplay;
    private final Set<UUID> creating;
    private final Set<UUID> blocked;
    private final Set<UUID> hideNametags;
    private final Map<UUID, Settings.NameTag> nameTagOverrides;
    private final Map<UUID, Boolean> shiftSystemBlocked;
    private final List<MyScheduledTask> tasks;
    private final AtomicLong displayAnimationMonotonicTick = new AtomicLong();
    @Setter
    private boolean debug = false;
    private final Attribute scaleAttribute;

    public NameTagManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.nameTags = new ConcurrentHashMap<>();
        this.entityIdToDisplay = Maps.newConcurrentMap();
        this.tasks = Lists.newCopyOnWriteArrayList();
        this.creating = Sets.newConcurrentHashSet();
        this.blocked = Sets.newConcurrentHashSet();
        this.hideNametags = Sets.newConcurrentHashSet();
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
        final MyScheduledTask displayAnimations = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                () -> {
                    final long t = displayAnimationMonotonicTick.incrementAndGet();
                    nameTags.values().forEach(tags -> tags.forEach(tag -> tag.tickDisplayAnimation(t)));
                },
                1L,
                1L);

        final MyScheduledTask refresh = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                () -> {
                    if (plugin.isPaper() && plugin.getServer().isStopping()) {
                        return;
                    }
                    nameTags.values().forEach(tags -> tags.forEach(tag -> refresh(tag.getOwner(), false)));
                },
                10, plugin.getConfigManager().getSettings().getTaskInterval());

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

        if (plugin.getConfigManager().getSettings().isShowWhileLooking()) {
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

        if (plugin.getConfigManager().getSettings().isObscuredNametagThroughWalls()) {
            final int obscuredInterval = Math.max(1, plugin.getConfigManager().getSettings().getObscuredNametagCheckInterval());
            final MyScheduledTask obscured = plugin.getTaskScheduler().runTaskTimer(
                    this::tickObscuredNametagThroughWalls,
                    obscuredInterval,
                    obscuredInterval);
            tasks.add(obscured);
        }

        tasks.add(displayAnimations);
        tasks.add(refresh);
        tasks.add(passengers);
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
        if (!plugin.getConfigManager().getSettings().isObscuredNametagThroughWalls()) {
            return;
        }
        final Settings s = plugin.getConfigManager().getSettings();
        final byte sneakB = clampMcTextOpacity(s.getSneakOpacity());
        final byte obscB = clampMcTextOpacity(s.getObscuredNametagOpacity());
        final double maxSq = s.getObscuredNametagMaxDistance() * s.getObscuredNametagMaxDistance();
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

    public float getScale(@NotNull Player player) {
        if (!isScalePresent()) {
            return 1;
        }

        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_20_5)) {
            return 1;
        }

        final AttributeInstance attribute = player.getAttribute(scaleAttribute);

        if (attribute == null) {
            return 1;
        }

        return (int) attribute.getValue();
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
        nameTagOverrides.remove(uuid);
        shiftSystemBlocked.remove(uuid);
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
        for (int i = 0; i < nametag.displayGroups().size(); i++) {
            final Settings.DisplayGroup displayGroup = nametag.displayGroups().get(i);
            final PacketNameTag display = createdTags.get(i);
            plugin.getPlaceholderManager().applyPlaceholders(player, displayGroup, List.of(player))
                    .thenAccept(lines -> loadDisplay(player, lines.get(player), displayGroup, display))
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                "Failed to create nametag for " + player.getName(), throwable);
                        creating.remove(player.getUniqueId());
                        return null;
                    });
        }

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

        for (int i = 0; i < nametag.displayGroups().size(); i++) {
            final Settings.DisplayGroup displayGroup = nametag.displayGroups().get(i);
            replaceDisplayIfNeeded(player, i, displayGroup);
            final PacketNameTag display = playerTags.get(i);

            final boolean show = plugin.getConfigManager().getSettings().isShowCurrentNameTag();
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

            plugin.getPlaceholderManager().applyPlaceholders(player, displayGroup, relationalPlayers)
                    .thenAccept(lines -> editDisplay(display, lines, displayGroup, force))
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                "Failed to edit nametag for " + player.getName(), throwable);
                        return null;
                    });
        }
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

                if (plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
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
            @NotNull Settings.DisplayGroup displayGroup, boolean force) {
        if (!packetNameTag.getDisplayGroup().equals(displayGroup)) {
            packetNameTag.setDisplayGroup(displayGroup);
        }
        final Settings cfg = plugin.getConfigManager().getSettings();
        packetNameTag.setBillboard(displayGroup.effectiveBillboard(cfg));
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
        final boolean wallOpacity = cfg.isObscuredNametagThroughWalls();

        components.forEach((p, c) -> {
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

    private void loadDisplay(@NotNull Player player, @NotNull Component component,
            @NotNull Settings.DisplayGroup displayGroup,
            @NotNull PacketNameTag display) {
        try {
            final Location location = player.getLocation().clone();
            // add 1.80 to make a perfect tp animation
            location.setY(location.getY() + 1.80);

            creating.remove(player.getUniqueId());
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
                if (!plugin.getConfigManager().getSettings().isObscuredNametagThroughWalls()) {
                    display.setSeeThrough(displayGroup.effectiveBackground().seeThrough() && !display.isSneaking());
                }
                display.setBackgroundColor(displayGroup.effectiveBackground().getColor());
            }

            display.resetOffset(plugin.getConfigManager().getSettings().getYOffset());

            display.setViewRange(plugin.getConfigManager().getSettings().getViewDistance());

            if (dt == NametagDisplayType.TEXT) {
                plugin.getTaskScheduler().runTaskLater(() -> display.modifyTextForOwner(meta -> meta.setText(component)),
                        1);
            }

            display.refresh();

            if (dt == NametagDisplayType.TEXT && plugin.getConfigManager().getSettings().isObscuredNametagThroughWalls()) {
                plugin.getTaskScheduler().runTask(this::tickObscuredNametagThroughWalls);
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
        final PacketNameTag neu = PacketNameTag.create(plugin, player, displayGroup);
        if (displayGroup.resolvedDisplayType() == NametagDisplayType.TEXT) {
            neu.text(player, Component.empty());
        }
        neu.spawn(player);
        if (plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
            neu.showToPlayer(player);
        }
        handleVanish(player, neu);
        list.set(index, neu);
        entityIdToDisplay.put(neu.getEntityId(), neu);
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
        final boolean wall = settings.isObscuredNametagThroughWalls();
        final byte sneakB = clampMcTextOpacity(settings.getSneakOpacity());
        final byte obscB = clampMcTextOpacity(settings.getObscuredNametagOpacity());
        final double maxSq = settings.getObscuredNametagMaxDistance() * settings.getObscuredNametagMaxDistance();

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
        final float yOffset = plugin.getConfigManager().getSettings().getYOffset();
        final float viewDistance = plugin.getConfigManager().getSettings().getViewDistance();
        if (!plugin.getConfigManager().getSettings().isObscuredNametagThroughWalls()) {
            nameTags.values().forEach(tags -> tags.forEach(PacketNameTag::clearObscuredPresentationTracking));
        }
        plugin.getTaskScheduler()
                .runTaskAsynchronously(() -> plugin.getPlayerListener().getOnlinePlayers().values().forEach(p -> {
                    setYOffset(p, yOffset);
                    setViewDistance(p, viewDistance);
                    applyBillboardsFromEffectiveNametag(p);
                    refresh(p, true);
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

            if (!plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
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
        if (player == target && plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
            showToOwner(player);
            return;
        }
        for (PacketNameTag packetNameTag : getPacketDisplayText(target)) {
            packetNameTag.hideFromPlayerSilently(player);
            packetNameTag.showToPlayer(player);
        }
    }

    public void showToOwner(@NotNull Player player) {
        if (!plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
            return;
        }
        for (PacketNameTag packetNameTag : getPacketDisplayText(player)) {
            packetNameTag.spawnForOwner();
        }
    }

    public void removeDisplay(@NotNull Player player, @NotNull Player target) {
        if (player == target && !plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
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
        nameTags.values().forEach(tags -> tags.forEach(display -> {
            if (display.canPlayerSee(player)) {
                display.hideFromPlayer(player);
            }
        }));
    }

    public void showOtherNametags(@NotNull Player player) {
        hideNametags.remove(player.getUniqueId());
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

        for (int i = 0; i < nameTag.displayGroups().size(); i++) {
            final Settings.DisplayGroup displayGroup = nameTag.displayGroups().get(i);
            replaceDisplayIfNeeded(player, i, displayGroup);
            final PacketNameTag display = swapTags.get(i);

            final List<Player> relationalPlayers = relationalPlayersForRefresh(player, display);

            plugin.getPlaceholderManager().applyPlaceholders(player, displayGroup, relationalPlayers)
                    .thenAccept(lines -> {
                        final Component component = lines.get(player);
                        final NametagDisplayType dt = displayGroup.resolvedDisplayType();
                        if (dt == NametagDisplayType.TEXT) {
                            display.text(player, component);
                        } else {
                            display.syncVisualFromGroup(displayGroup);
                        }
                        display.setBillboard(displayGroup.effectiveBillboard(plugin.getConfigManager().getSettings()));
                        if (dt == NametagDisplayType.TEXT) {
                            display.setShadowed(displayGroup.effectiveBackground().shadowed());
                            if (!plugin.getConfigManager().getSettings().isObscuredNametagThroughWalls()) {
                                display.setSeeThrough(displayGroup.effectiveBackground().seeThrough() && !display.isSneaking());
                            }
                            display.setBackgroundColor(displayGroup.effectiveBackground().getColor());
                        }
                        display.resetOffset(plugin.getConfigManager().getSettings().getYOffset());
                        display.setViewRange(plugin.getConfigManager().getSettings().getViewDistance());

                        if (dt == NametagDisplayType.TEXT) {
                            plugin.getTaskScheduler()
                                    .runTaskLater(() -> display.modifyTextForOwner(meta -> meta.setText(component)), 1);
                        }

                        lines.forEach((p, c) -> {
                            if (!p.equals(player) && dt == NametagDisplayType.TEXT) {
                                display.text(p, c);
                            }
                        });

                        display.refresh();

                        if (dt == NametagDisplayType.TEXT && plugin.getConfigManager().getSettings().isObscuredNametagThroughWalls()) {
                            plugin.getTaskScheduler().runTask(this::tickObscuredNametagThroughWalls);
                        }
                    })
                    .exceptionally(throwable -> {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                "Failed to swap nametag for " + player.getName(), throwable);
                        return null;
                    });
        }

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
