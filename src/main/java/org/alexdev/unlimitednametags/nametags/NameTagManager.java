package org.alexdev.unlimitednametags.nametags;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.github.retrooper.packetevents.util.Vector3f;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.packet.PacketDisplayText;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Getter
public class NameTagManager {

    private final UnlimitedNameTags plugin;
    private final Map<UUID, PacketDisplayText> nameTags;
    private final Map<Integer, PacketDisplayText> entityIdToDisplay;
    private final Set<UUID> creating;
    private final Set<UUID> blocked;
    private MyScheduledTask task;

    public NameTagManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.nameTags = Maps.newConcurrentMap();
        this.entityIdToDisplay = Maps.newConcurrentMap();
        this.creating = Sets.newConcurrentHashSet();
        this.blocked = Sets.newConcurrentHashSet();
        this.loadAll();
    }

    private void loadAll() {
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            Bukkit.getOnlinePlayers().forEach(this::addPlayer);
            this.startTask();
        }, 5);
    }

    private void startTask() {
        if (task != null) {
            task.cancel();
        }
        task = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                () -> Bukkit.getOnlinePlayers().forEach(p -> refresh(p, false)),
                10, plugin.getConfigManager().getSettings().getTaskInterval());

        // Refresh passengers
        plugin.getTaskScheduler().runTaskTimerAsynchronously(() ->
                        Bukkit.getOnlinePlayers().forEach(player ->
                                getPacketDisplayText(player)
                                        .ifPresent(PacketDisplayText::sendPassengerPacketToViewers))
                , 20, 20 * 5L);
    }


    public void blockPlayer(@NotNull Player player) {
        blocked.add(player.getUniqueId());
    }

    public void unblockPlayer(@NotNull Player player) {
        blocked.remove(player.getUniqueId());
    }

    public void addPlayer(@NotNull Player player) {
        if (nameTags.containsKey(player.getUniqueId())) {
            return;
        }

        if (creating.contains(player.getUniqueId())) {
            return;
        }

        if (blocked.contains(player.getUniqueId())) {
            return;
        }

        if (player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            blockPlayer(player);
            return;
        }

        final PacketDisplayText display = new PacketDisplayText(plugin, player);
        display.text(Component.empty());
        display.spawn(player);

        handleVanish(player, display);

        nameTags.put(player.getUniqueId(), display);
        entityIdToDisplay.put(display.getEntity().getEntityId(), display);

        creating.add(player.getUniqueId());
        final Settings.NameTag nametag = plugin.getConfigManager().getSettings().getNametag(player);

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.lines())
                .thenAccept(lines -> loadDisplay(player, lines, nametag, display))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), throwable);
                    creating.remove(player.getUniqueId());
                    return null;
                });
    }

    public void refresh(@NotNull Player player, boolean force) {
        final Settings.NameTag nametag = plugin.getConfigManager().getSettings().getNametag(player);

        if (!nameTags.containsKey(player.getUniqueId())) {
            return;
        }

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.lines())
                .thenAccept(lines -> editDisplay(player, lines, nametag, force))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to edit nametag for " + player.getName(), throwable);
                    return null;
                });
    }

    private void editDisplay(@NotNull Player player, @NotNull Component component,
                             @NotNull Settings.NameTag nameTag, boolean force) {
        getPacketDisplayText(player).ifPresent(packetDisplayText -> {
            final boolean update = packetDisplayText.text(component) || force;
            packetDisplayText.setBackgroundColor(nameTag.background().getColor());
            packetDisplayText.setShadowed(nameTag.background().shadowed());
            packetDisplayText.setSeeThrough(nameTag.background().seeThrough());
            if (update) {
                packetDisplayText.refresh();
            }
        });
    }

    private void loadDisplay(@NotNull Player player, @NotNull Component component,
                             @NotNull Settings.NameTag nameTag,
                             @NotNull PacketDisplayText display) {
        try {
            final Location location = player.getLocation().clone();
            //add 1.80 to make a perfect tp animation
            location.setY(location.getY() + 1.80);

            creating.remove(player.getUniqueId());
            display.getMeta().setUseDefaultBackground(false);
            display.text(component);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(nameTag.background().shadowed());
            display.setSeeThrough(nameTag.background().seeThrough());
            //background color, if disabled, set to transparent
            display.setBackgroundColor(nameTag.background().getColor());

            display.setTransformation(new Vector3f(0, plugin.getConfigManager().getSettings().getYOffset(), 0));

            display.setViewRange(plugin.getConfigManager().getSettings().getViewDistance());

            display.refresh();

            handleVanish(player, display);

        } catch (Throwable e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), e);
        }
    }

    private void handleVanish(@NotNull Player player, PacketDisplayText display) {
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
        final PacketDisplayText packetDisplayText = nameTags.remove(player.getUniqueId());
        if (packetDisplayText != null) {
            packetDisplayText.remove();
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
        final PacketDisplayText packetDisplayText = nameTags.get(player.getUniqueId());
        if (packetDisplayText != null) {
            packetDisplayText.setVisible(false);
            packetDisplayText.clearViewers();
        }
    }

    public void showToTrackedPlayers(@NotNull Player player, @NotNull Collection<UUID> tracked) {
        final PacketDisplayText packetDisplayText = nameTags.get(player.getUniqueId());
        if (packetDisplayText != null) {
            packetDisplayText.setVisible(true);
            packetDisplayText.showToPlayers(tracked.stream()
                    .map(Bukkit::getPlayer)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
        }
    }

    public void hideAllDisplays(@NotNull Player player) {
        nameTags.forEach((uuid, display) -> {
            display.hideFromPlayer(player);
            display.getBlocked().add(player.getUniqueId());
        });
        getPacketDisplayText(player).ifPresent(PacketDisplayText::clearViewers);
    }

    public void removeAll() {
        nameTags.forEach((uuid, display) -> display.remove());

        entityIdToDisplay.clear();
        nameTags.clear();
    }

    public void updateSneaking(@NotNull Player player, boolean sneaking) {
        getPacketDisplayText(player).ifPresent(packetDisplayText -> {
            packetDisplayText.setSeeThrough(!sneaking);
            packetDisplayText.setTextOpacity((byte) (sneaking ? plugin.getConfigManager().getSettings().getSneakOpacity() : -1));
            packetDisplayText.refresh();
        });
    }

    public void reload() {
        final float yOffset = plugin.getConfigManager().getSettings().getYOffset();
        final float viewDistance = plugin.getConfigManager().getSettings().getViewDistance();


        plugin.getTaskScheduler().runTaskAsynchronously(() -> Bukkit.getOnlinePlayers().forEach(p -> {
            setYOffset(p, yOffset);
            setViewDistance(p, viewDistance);
            refresh(p, true);
        }));
        startTask();
    }

    public void debug(@NotNull CommandSender audience) {
        final AtomicReference<Component> component = new AtomicReference<>(Component.text("Nametags:").colorIfAbsent(TextColor.color(0xFF0000)));
        nameTags.forEach((uuid, display) -> {
            final Player player = Bukkit.getPlayer(uuid);

            if (player == null) {
                return;
            }

            final List<String> viewers = display.getEntity().getViewers().stream()
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
    private Component getComponent(@NotNull PacketDisplayText display, @NotNull List<String> viewers,
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

        Component text = Component.text(player.getName() + " -> " + " " + display.getEntity().getEntityId());
        text = text.color(TextColor.color(0x00FF00));
        text = text.hoverEvent(hover.color(TextColor.color(Color.RED.asRGB())));
        return text;
    }

    private void setYOffset(@NotNull Player player, float yOffset) {
        getPacketDisplayText(player).ifPresent(packetDisplayText -> {
            packetDisplayText.setYOffset(yOffset);
        });
    }

    private void setViewDistance(@NotNull Player player, float viewDistance) {
        getPacketDisplayText(player).ifPresent(packetDisplayText -> {
            packetDisplayText.setViewRange(viewDistance);
        });
    }


    public void vanishPlayer(@NotNull Player player) {
        getPacketDisplayText(player).ifPresent(packetDisplayText -> {
            final Set<UUID> viewers = new HashSet<>(packetDisplayText.getEntity().getViewers());
            final boolean isVanished = plugin.getVanishManager().isVanished(player);
            viewers.forEach(uuid -> {
                final Player viewer = Bukkit.getPlayer(uuid);
                if (viewer == null || viewer == player) {
                    return;
                }
                if (isVanished && !plugin.getVanishManager().canSee(viewer, player)) {
                    return;
                }
                packetDisplayText.hideFromPlayer(viewer);
            });
        });
    }

    public void unVanishPlayer(@NotNull Player player) {
        getPacketDisplayText(player).ifPresent(packetDisplayText -> {
            final Set<UUID> viewers = new HashSet<>(packetDisplayText.getEntity().getViewers());
            viewers.forEach(uuid -> {
                final Player viewer = Bukkit.getPlayer(uuid);
                if (viewer == null || viewer == player) {
                    return;
                }
                packetDisplayText.showToPlayer(viewer);
            });
        });
    }


    @NotNull
    public Optional<PacketDisplayText> getPacketDisplayText(@NotNull Player player) {
        return Optional.ofNullable(nameTags.get(player.getUniqueId()));
    }

    @NotNull
    public Optional<PacketDisplayText> getPacketDisplayText(int id) {
        return Optional.ofNullable(entityIdToDisplay.get(id));
    }

    public void updateDisplay(@NotNull Player player, @NotNull Player target) {
        if (player == target) {
            return;
        }
        getPacketDisplayText(target).ifPresent(packetDisplayText -> {
            packetDisplayText.hideFromPlayerSilently(player);
            packetDisplayText.showToPlayer(player);
        });
    }

    public void removeDisplay(@NotNull Player player, @NotNull Player target) {
        if (player == target) {
            return;
        }
        getPacketDisplayText(target).ifPresent(packetDisplayText -> {
            packetDisplayText.hideFromPlayer(player);
        });
    }

    public void updateDisplaysForPlayer(@NotNull Player player) {
        nameTags.forEach((uuid, display) -> {
            final Player owner = display.getOwner();

            if (player.getLocation().getWorld() != owner.getLocation().getWorld()) {
                return;
            }

            if (plugin.getVanishManager().isVanished(owner) && !plugin.getVanishManager().canSee(player, owner)) {
                return;
            }

            display.getBlocked().remove(player.getUniqueId());

            display.hideFromPlayerSilently(player);
            display.showToPlayer(player);
        });
    }
}
