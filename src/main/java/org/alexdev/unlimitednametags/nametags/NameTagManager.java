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
import java.util.concurrent.atomic.AtomicBoolean;
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
        this.loadAll();
        this.scaleAttribute = loadScaleAttribute();
    }

    private void loadAll() {
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            Bukkit.getOnlinePlayers().forEach(this::addPlayer);
            this.startTask();
        }, 5);
    }

    private void startTask() {
        tasks.forEach(MyScheduledTask::cancel);
        final MyScheduledTask refresh = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                () -> Bukkit.getOnlinePlayers().forEach(p -> refresh(p, false)),
                10, plugin.getConfigManager().getSettings().getTaskInterval());

        // Refresh passengers
        final MyScheduledTask passengers = plugin.getTaskScheduler().runTaskTimerAsynchronously(() ->
                        Bukkit.getOnlinePlayers().forEach(player ->
                                getPacketDisplayText(player)
                                        .ifPresent(PacketNameTag::sendPassengerPacketToViewers))
                , 20, 20 * 5L);

        // Scale task
        if (isScalePresent()) {
            final MyScheduledTask scale = plugin.getTaskScheduler().runTaskTimerAsynchronously(() ->
                            Bukkit.getOnlinePlayers().forEach(player ->
                                    getPacketDisplayText(player)
                                            .filter(PacketNameTag::checkScale)
                                            .ifPresent(PacketNameTag::refresh))
                    , 20, 10);
            tasks.add(scale);
        }

        if (plugin.getConfigManager().getSettings().isShowWhileLooking()) {
            final boolean current = plugin.getConfigManager().getSettings().isShowCurrentNameTag();
            final MyScheduledTask point = plugin.getTaskScheduler().runTaskTimerAsynchronously(() ->
                            Bukkit.getOnlinePlayers().forEach(player1 -> {
                                final Optional<PacketNameTag> display = getPacketDisplayText(player1);
                                if (display.isEmpty()) {
                                    return;
                                }
                                Bukkit.getOnlinePlayers().forEach(player2 -> {
                                    if (plugin.getHook(ViaVersionHook.class).map(h -> h.hasNotTextDisplays(player2)).orElse(false)) {
                                        return;
                                    }

                                    if (player1.getWorld() != player2.getWorld()) {
                                        return;
                                    }

                                    final boolean isPointing = isPlayerPointingAt(player2, player1) || (current && player1 == player2);
                                    if (display.get().canPlayerSee(player2) && !isPointing) {
                                        display.get().hideFromPlayer(player2);
                                    } else if (!display.get().canPlayerSee(player2) && isPointing) {
                                        display.get().showToPlayer(player2);
                                    }

                                });
                            })

                    , 5, 5);
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

        if(PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_20_5)) {
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
    }

    public void addPlayer(@NotNull Player player) {
        if (nameTags.containsKey(player.getUniqueId())) {
            if (debug) {
                plugin.getLogger().info("Player " + player.getName() + " already has a nametag");
            }
            return;
        }

        if (creating.contains(player.getUniqueId())) {
            if (debug) {
                plugin.getLogger().info("Player " + player.getName() + " is already creating a nametag");
            }
            return;
        }

        if (blocked.contains(player.getUniqueId())) {
            if (debug) {
                plugin.getLogger().info("Player " + player.getName() + " is blocked");
            }
            return;
        } else {
            if (debug) {
                plugin.getLogger().info("Player " + player.getName() + " is not blocked");
            }
        }

        if (PacketEvents.getAPI().getPlayerManager().getUser(player) == null) {
            if (debug) {
                plugin.getLogger().info("Player " + player.getName() + " is not loaded");
            }
            return;
        }

        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            if (debug) {
                plugin.getLogger().info("Player " + player.getName() + " has invisibility potion effect, blocking");
            }
            blockPlayer(player);
            return;
        }
        final Settings.NameTag nametag = plugin.getConfigManager().getSettings().getNametag(player);
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

        creating.add(player.getUniqueId());

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.linesGroups(), List.of(player))
                .thenAccept(lines -> loadDisplay(player, lines.get(player), nametag, display))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), throwable);
                    creating.remove(player.getUniqueId());
                    return null;
                });
    }

    public void refresh(@NotNull Player player, boolean force) {
        final Settings.NameTag nametag = plugin.getConfigManager().getSettings().getNametag(player);

        if(PacketEvents.getAPI().getPlayerManager().getUser(player) == null) {
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

        final List<Player> relationalPlayers = display.getViewers().stream()
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .toList();

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.linesGroups(), relationalPlayers)
                .thenAccept(lines -> editDisplay(player, lines, nametag, force))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to edit nametag for " + player.getName(), throwable);
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
            components.forEach((p, c) -> {
                final AtomicBoolean update = new AtomicBoolean(false);
                update.set(packetNameTag.text(p, c) || force);
                final User user = PacketEvents.getAPI().getPlayerManager().getUser(p);
                if (user == null) {
                    return;
                }
                packetNameTag.modify(user, m -> {

                    if(force) {
                        m.setShadow(nameTag.background().shadowed());
                        m.setSeeThrough(nameTag.background().seeThrough());
                        m.setBackgroundColor(nameTag.background().getColor().asARGB());
                        update.set(true);
                    } else {
                        if(m.isShadow() != nameTag.background().shadowed()) {
                            m.setShadow(nameTag.background().shadowed());
                            update.set(true);
                        }
                        if(m.isSeeThrough() != nameTag.background().seeThrough()) {
                            m.setSeeThrough(nameTag.background().seeThrough());
                            update.set(true);
                        }
                    }


                });

                if (update.get()) {
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
            //add 1.80 to make a perfect tp animation
            location.setY(location.getY() + 1.80);

            creating.remove(player.getUniqueId());
            display.modify(m -> m.setUseDefaultBackground(false));
            display.text(player, component);
            display.setBillboard(plugin.getConfigManager().getSettings().getDefaultBillboard());
            display.setShadowed(nameTag.background().shadowed());
            display.setSeeThrough(nameTag.background().seeThrough());
            //background color, if disabled, set to transparent
            display.setBackgroundColor(nameTag.background().getColor());

//            display.setTransformation(new Vector3f(0, plugin.getConfigManager().getSettings().getYOffset(), 0));
            display.resetOffset(plugin.getConfigManager().getSettings().getYOffset());

            display.setViewRange(plugin.getConfigManager().getSettings().getViewDistance());

            plugin.getTaskScheduler().runTaskLater(() -> display.modifyOwner(meta -> meta.setText(component)), 1);

            display.refresh();

            handleVanish(player, display);

        } catch (Throwable e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), e);
        }
    }

    private void handleVanish(@NotNull Player player, @NotNull PacketNameTag display) {
        final boolean isVanished = plugin.getVanishManager().isVanished(player);

        //if player is vanished, hide display for all players except for who can see the player
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p != player)
                .filter(p -> p.getLocation().getWorld() == player.getLocation().getWorld())
                .filter(p -> !isVanished || plugin.getVanishManager().canSee(p, player))
                .filter(p -> p.getLocation().distance(player.getLocation()) <= 250)
                .filter(p -> !display.canPlayerSee(p))
                .forEach(display::showToPlayer);
    }


    public void removePlayer(@NotNull Player player, boolean quit) {
        final PacketNameTag packetNameTag = nameTags.remove(player.getUniqueId());
        if (packetNameTag != null) {
            packetNameTag.remove();
        }

        entityIdToDisplay.remove(player.getEntityId());

        nameTags.forEach((uuid, display) -> {
            if (quit) {
                display.handleQuit(player);
            } else {
                display.hideFromPlayerSilently(player);
            }
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

    public void showToTrackedPlayers(@NotNull Player player, @NotNull Collection<UUID> tracked) {
        final PacketNameTag packetNameTag = nameTags.get(player.getUniqueId());
        if (packetNameTag != null) {
            packetNameTag.setVisible(true);
            packetNameTag.showToPlayers(tracked.stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
            return;
        }

        addPlayer(player);
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
        getPacketDisplayText(player).ifPresent(packetNameTag -> {
            if (packetNameTag.getNameTag().background().seeThrough()) {
                packetNameTag.setSeeThrough(!sneaking);
            }
            packetNameTag.setTextOpacity((byte) (sneaking ? plugin.getConfigManager().getSettings().getSneakOpacity() : -1));
            packetNameTag.refresh();
        });
    }

    public void reload() {
        final float yOffset = plugin.getConfigManager().getSettings().getYOffset();
        final float viewDistance = plugin.getConfigManager().getSettings().getViewDistance();
        final AbstractDisplayMeta.BillboardConstraints billboard = plugin.getConfigManager().getSettings().getDefaultBillboard();

        plugin.getTaskScheduler().runTaskAsynchronously(() -> Bukkit.getOnlinePlayers().forEach(p -> {
            setYOffset(p, yOffset);
            setViewDistance(p, viewDistance);
            setBillBoard(p, billboard);
            refresh(p, true);
        }));
        startTask();
    }

    @SuppressWarnings("UnstableApiUsage")
    public void debug(@NotNull CommandSender audience) {
        audience.sendRichMessage("<red>UnlimitedNameTags v" + plugin.getPluginMeta().getVersion() + " . Compiled: " + plugin.getConfigManager().isCompiled());
        final AtomicReference<Component> component = new AtomicReference<>(Component.text("Nametags:").colorIfAbsent(TextColor.color(0xFF0000)));
        nameTags.forEach((uuid, display) -> {
            final Player player = Bukkit.getPlayer(uuid);

            if (player == null) {
                return;
            }

            final List<String> viewers = display.getViewers().stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .map(Player::getName)
                    .toList();
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
                final Player viewer = Bukkit.getPlayer(uuid);
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
                final Player viewer = Bukkit.getPlayer(uuid);
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
            final Player tracked = Bukkit.getPlayer(uuid);
            if (tracked == null) {
                return;
            }
            getPacketDisplayText(tracked).ifPresent(display -> display.showToPlayer(player));
        });
    }

    public boolean isHiddenOtherNametags(@NotNull Player player) {
        return hideNametags.contains(player.getUniqueId());
    }
}
