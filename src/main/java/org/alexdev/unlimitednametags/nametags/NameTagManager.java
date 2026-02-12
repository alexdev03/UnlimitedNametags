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
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Getter
public class NameTagManager {

    private final UnlimitedNameTags plugin;
    private final Map<UUID, PacketNameTag> nameTags;
    private final Map<Integer, PacketNameTag> entityIdToDisplay;
    private final Set<UUID> creating;
    private final Set<UUID> blocked;
    private final Set<UUID> hideNametags;
    private final Map<UUID, Settings.NameTag> nameTagOverrides;
    private final Map<UUID, Boolean> shiftSystemBlocked;
    private final List<MyScheduledTask> tasks;
    @Setter
    private boolean debug = false;
    private final Attribute scaleAttribute;

    public NameTagManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.nameTags = Maps.newConcurrentMap();
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
        final MyScheduledTask refresh = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                () -> {
                    if (plugin.isPaper() && plugin.getServer().isStopping()) {
                        return;
                    }
                    nameTags.values().forEach(tag -> refresh(tag.getOwner(), false));
                },
                10, plugin.getConfigManager().getSettings().getTaskInterval());

        // Refresh passengers
        final MyScheduledTask passengers = plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> nameTags.values()
                .stream()
                .map(PacketNameTag::getOwner)
                .filter(p -> plugin.getHook(HMCCosmeticsHook.class).map(h -> !h.hasBackpack(p)).orElse(true))
                .forEach(player -> getPacketDisplayText(player)
                        .ifPresent(PacketNameTag::sendPassengerPacketToViewers)),
                20, 20 * 5L);

        // Scale task
        if (isScalePresent()) {
            final MyScheduledTask scale = plugin.getTaskScheduler()
                    .runTaskTimerAsynchronously(() -> nameTags.values().forEach(tag -> {
                        if (tag.checkScale()) {
                            tag.refresh();
                        }
                    }), 20, 10);
            tasks.add(scale);
        }

        if (plugin.getConfigManager().getSettings().isShowWhileLooking()) {
            final MyScheduledTask point = plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> {
                nameTags.values().forEach(tag -> {
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
                });
            }, 5, 5);
            tasks.add(point);
        }

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
        if (nameTags.containsKey(player.getUniqueId())) {
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
        final PacketNameTag display = new PacketNameTag(plugin, player, nametag);
        display.text(player, Component.empty());
        display.spawn(player);

        if (plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
            display.showToPlayer(player);
        }

        handleVanish(player, display);

        nameTags.put(player.getUniqueId(), display);
        if (debug) {
            plugin.getLogger().info("Added nametag for " + player.getName());
        }
        entityIdToDisplay.put(display.getEntityId(), display);

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.linesGroups(), List.of(player))
                .thenAccept(lines -> loadDisplay(player, lines.get(player), nametag, display))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Failed to create nametag for " + player.getName(), throwable);
                    creating.remove(player.getUniqueId());
                    return null;
                });
    }

    public void refresh(@NotNull Player player, boolean force) {
        final Settings.NameTag nametag = getEffectiveNametag(player);

        if (PacketEvents.getAPI().getPlayerManager().getUser(player) == null) {
            return;
        }

        if (!nameTags.containsKey(player.getUniqueId())) {
            return;
        }

        final PacketNameTag display = nameTags.get(player.getUniqueId());
        if (display == null) {
            return;
        }

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

        final List<Player> relationalPlayers = display.getViewers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .toList();

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.linesGroups(), relationalPlayers)
                .thenAccept(lines -> editDisplay(player, lines, nametag, force))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Failed to edit nametag for " + player.getName(), throwable);
                    return null;
                });
    }

    private void editDisplay(@NotNull Player player, Map<Player, Component> components,
            @NotNull Settings.NameTag nameTag, boolean force) {
        getPacketDisplayText(player).ifPresent(packetNameTag -> {
            if (!packetNameTag.getNameTag().equals(nameTag)) {
                packetNameTag.setNameTag(nameTag);
            }
            if (force && isScalePresent()) {
                packetNameTag.checkScale();
            }

            final boolean shadowed = nameTag.background().shadowed();
            final boolean seeThrough = nameTag.background().seeThrough();
            final int backgroundColor = nameTag.background().getColor().asARGB();

            components.forEach((p, c) -> {
                final boolean[] updateRef = { packetNameTag.text(p, c) || force };
                final User user = PacketEvents.getAPI().getPlayerManager().getUser(p);
                if (user == null) {
                    return;
                }
                packetNameTag.modify(user, m -> {

                    if (force) {
                        m.setShadow(shadowed);
                        m.setSeeThrough(seeThrough);
                        m.setBackgroundColor(backgroundColor);
                    } else {
                        if (m.isShadow() != shadowed) {
                            m.setShadow(shadowed);
                            updateRef[0] = true;
                        }
                        if (m.isSeeThrough() != seeThrough) {
                            m.setSeeThrough(seeThrough);
                            updateRef[0] = true;
                        }
                    }

                });

                if (updateRef[0]) {
                    packetNameTag.refreshForPlayer(p);
                }
            });
        });
    }

    private void loadDisplay(@NotNull Player player, @NotNull Component component,
            @NotNull Settings.NameTag nameTag,
            @NotNull PacketNameTag display) {
        try {
            final Location location = player.getLocation().clone();
            // add 1.80 to make a perfect tp animation
            location.setY(location.getY() + 1.80);

            creating.remove(player.getUniqueId());
            display.modify(m -> m.setUseDefaultBackground(false));
            display.text(player, component);
            display.setBillboard(plugin.getConfigManager().getSettings().getDefaultBillboard());
            display.setShadowed(nameTag.background().shadowed());
            display.setSeeThrough(nameTag.background().seeThrough());
            // background color, if disabled, set to transparent
            display.setBackgroundColor(nameTag.background().getColor());

            display.resetOffset(plugin.getConfigManager().getSettings().getYOffset());

            display.setViewRange(plugin.getConfigManager().getSettings().getViewDistance());

            plugin.getTaskScheduler().runTaskLater(() -> display.modifyOwner(meta -> meta.setText(component)), 1);

            display.refresh();

            handleVanish(player, display);

        } catch (Throwable e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(),
                    e);
        }
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
        final PacketNameTag packetNameTag = nameTags.remove(player.getUniqueId());
        if (packetNameTag != null) {
            packetNameTag.remove();
            entityIdToDisplay.remove(packetNameTag.getEntityId());
        }

        nameTags.forEach((uuid, display) -> {
            display.handleQuit(player);
            display.getBlocked().remove(player.getUniqueId());
        });
    }

    public void removeAllViewers(@NotNull Player player) {
        final PacketNameTag packetNameTag = nameTags.get(player.getUniqueId());
        if (packetNameTag != null) {
            packetNameTag.setVisible(false);
            packetNameTag.clearViewers();
        }
    }

    public void showToTrackedPlayers(@NotNull Player player) {
        showToTrackedPlayers(player, plugin.getTrackerManager().getWhoTracks(player));
    }

    public void showToTrackedPlayers(@NotNull Player player, @NotNull Collection<Player> tracked) {
        final PacketNameTag packetNameTag = nameTags.get(player.getUniqueId());
        if (packetNameTag != null) {
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
            return;
        }

        addPlayer(player, false);
    }

    public void hideAllDisplays(@NotNull Player player) {
        nameTags.forEach((uuid, display) -> {
            display.hideFromPlayer(player);
            display.getBlocked().add(player.getUniqueId());
        });
        getPacketDisplayText(player).ifPresent(PacketNameTag::clearViewers);
    }

    public void removeAll() {
        nameTags.forEach((uuid, display) -> display.remove());

        entityIdToDisplay.clear();
        nameTags.clear();
    }

    public void updateSneaking(@NotNull Player player, boolean sneaking) {
        if (shiftSystemBlocked.getOrDefault(player.getUniqueId(), false)) {
            return;
        }

        getPacketDisplayText(player).ifPresent(packetNameTag -> {
            if (packetNameTag.getNameTag().background().seeThrough()) {
                packetNameTag.setSeeThrough(!sneaking);
            }

            packetNameTag.setSneaking(sneaking);
            packetNameTag
                    .setTextOpacity((byte) (sneaking ? plugin.getConfigManager().getSettings().getSneakOpacity() : -1));
            packetNameTag.refresh();
        });
    }

    public void reload() {
        final float yOffset = plugin.getConfigManager().getSettings().getYOffset();
        final float viewDistance = plugin.getConfigManager().getSettings().getViewDistance();
        final AbstractDisplayMeta.BillboardConstraints billboard = plugin.getConfigManager().getSettings()
                .getDefaultBillboard();

        plugin.getTaskScheduler()
                .runTaskAsynchronously(() -> plugin.getPlayerListener().getOnlinePlayers().values().forEach(p -> {
                    setYOffset(p, yOffset);
                    setViewDistance(p, viewDistance);
                    setBillBoard(p, billboard);
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
        nameTags.forEach((uuid, display) -> {
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
        });

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
        getPacketDisplayText(player).ifPresent(packetNameTag -> {
            packetNameTag.resetOffset(yOffset);
        });
    }

    private void setBillBoard(@NotNull Player player, AbstractDisplayMeta.BillboardConstraints billboard) {
        getPacketDisplayText(player).ifPresent(packetNameTag -> packetNameTag.setBillboard(billboard));
    }

    private void setViewDistance(@NotNull Player player, float viewDistance) {
        getPacketDisplayText(player).ifPresent(packetNameTag -> packetNameTag.setViewRange(viewDistance));
    }

    public void vanishPlayer(@NotNull Player player) {
        getPacketDisplayText(player).ifPresent(packetNameTag -> {
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
        getPacketDisplayText(player).ifPresent(packetNameTag -> {
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
    public Optional<PacketNameTag> getPacketDisplayText(@NotNull Player player) {
        return Optional.ofNullable(nameTags.get(player.getUniqueId()));
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
        getPacketDisplayText(target).ifPresent(packetNameTag -> {
            packetNameTag.hideFromPlayerSilently(player);
            packetNameTag.showToPlayer(player);
        });
    }

    public void showToOwner(@NotNull Player player) {
        if (!plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
            return;
        }
        getPacketDisplayText(player).ifPresent(PacketNameTag::spawnForOwner);
    }

    public void removeDisplay(@NotNull Player player, @NotNull Player target) {
        if (player == target && !plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
            return;
        }
        getPacketDisplayText(target).ifPresent(packetNameTag -> packetNameTag.hideFromPlayer(player));
    }

    public void updateDisplaysForPlayer(@NotNull Player player) {
        nameTags.forEach((uuid, display) -> {
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
        });
    }

    public void refreshDisplaysForPlayer(@NotNull Player player) {
        nameTags.forEach((uuid, display) -> {
            if (!display.canPlayerSee(player)) {
                return;
            }

            display.refreshForPlayer(player);
        });
    }

    public void unBlockForAllPlayers(@NotNull Player player) {
        nameTags.forEach((uuid, display) -> display.getBlocked().remove(player.getUniqueId()));
    }

    public void hideOtherNametags(@NotNull Player player) {
        hideNametags.add(player.getUniqueId());
        nameTags.forEach((uuid, display) -> {
            if (display.canPlayerSee(player)) {
                display.hideFromPlayer(player);
            }
        });
    }

    public void showOtherNametags(@NotNull Player player) {
        hideNametags.remove(player.getUniqueId());
        plugin.getTrackerManager().getTrackedPlayers(player.getUniqueId()).forEach(uuid -> {
            final Player tracked = plugin.getPlayerListener().getPlayer(uuid);
            if (tracked == null) {
                return;
            }

            getPacketDisplayText(tracked).ifPresent(display -> display.showToPlayer(player));
        });
    }

    public boolean isHiddenOtherNametags(@NotNull Player player) {
        return hideNametags.contains(player.getUniqueId());
    }

    public void swapNametag(@NotNull Player player, @NotNull Settings.NameTag nameTag) {
        final PacketNameTag display = nameTags.get(player.getUniqueId());
        if (display == null) {
            return;
        }

        display.setNameTag(nameTag);

        final List<Player> relationalPlayers = display.getViewers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .toList();

        plugin.getPlaceholderManager().applyPlaceholders(player, nameTag.linesGroups(), relationalPlayers)
                .thenAccept(lines -> {
                    final Component component = lines.get(player);
                    display.text(player, component);
                    display.setBillboard(plugin.getConfigManager().getSettings().getDefaultBillboard());
                    display.setShadowed(nameTag.background().shadowed());
                    display.setSeeThrough(nameTag.background().seeThrough());
                    display.setBackgroundColor(nameTag.background().getColor());
                    display.resetOffset(plugin.getConfigManager().getSettings().getYOffset());
                    display.setViewRange(plugin.getConfigManager().getSettings().getViewDistance());

                    plugin.getTaskScheduler().runTaskLater(() -> display.modifyOwner(meta -> meta.setText(component)),
                            1);

                    lines.forEach((p, c) -> {
                        if (!p.equals(player)) {
                            display.text(p, c);
                        }
                    });

                    display.refresh();
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Failed to swap nametag for " + player.getName(), throwable);
                    return null;
                });
    }

    public void setNametagOverride(@NotNull Player player, @NotNull Settings.NameTag nameTag) {
        nameTagOverrides.put(player.getUniqueId(), nameTag);

        final PacketNameTag display = nameTags.get(player.getUniqueId());
        if (display != null) {
            swapNametag(player, nameTag);
        }
    }

    public void removeNametagOverride(@NotNull Player player) {
        nameTagOverrides.remove(player.getUniqueId());

        final PacketNameTag display = nameTags.get(player.getUniqueId());
        if (display != null) {
            final Settings.NameTag configNametag = getConfigNametag(player);
            swapNametag(player, configNametag);
        }
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
}
