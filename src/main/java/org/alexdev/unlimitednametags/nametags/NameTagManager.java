package org.alexdev.unlimitednametags.nametags;

import com.github.retrooper.packetevents.util.Vector3f;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Getter;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.packet.PacketDisplayText;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Getter
public class NameTagManager {

    private final UnlimitedNameTags plugin;
    private final Map<UUID, PacketDisplayText> nameTags;
    private final Map<UUID, UUID> white;
    private final List<UUID> creating;
    private final List<UUID> blocked;

    public NameTagManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.nameTags = Maps.newConcurrentMap();
        this.creating = Lists.newCopyOnWriteArrayList();
        this.white = Maps.newConcurrentMap();
        this.blocked = Lists.newCopyOnWriteArrayList();
        this.loadAll();
        this.startTask();
    }

    private void loadAll() {
        Bukkit.getOnlinePlayers().forEach(this::addPlayer);
    }

    private void startTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                () -> Bukkit.getOnlinePlayers().forEach(this::refreshPlayer),
                10, plugin.getConfigManager().getSettings().getTaskInterval());
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

        creating.add(player.getUniqueId());
        final Settings.NameTag nametag = plugin.getConfigManager().getSettings().getNametag(player);

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.lines())
                .thenAccept(lines -> createDisplay(player, lines))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), throwable);
                    creating.remove(player.getUniqueId());
                    return null;
                });
    }

    public void refresh(@NotNull Player player, boolean update) {
        final Settings.NameTag nametag = plugin.getConfigManager().getSettings().getNametag(player);

        if (!nameTags.containsKey(player.getUniqueId())) {
            return;
        }

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.lines())
                .thenAccept(lines -> editDisplay(player, lines, update))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), throwable);
                    return null;
                });
    }

    public void refreshPlayer(@NotNull Player player) {
        refresh(player, true);
    }

    private void editDisplay(Player player, Component component, boolean update) {
        getPacketDisplayText(player).ifPresent(packetDisplayText -> {
            packetDisplayText.text(component);
            if (update) {
                packetDisplayText.refresh();
            }
        });
    }

    private void createDisplay(Player player, Component component) {
        try {
            final Location location = player.getLocation().clone();
            //add 1.80 to make a perfect tp animation
            location.setY(location.getY() + 1.80);

            final PacketDisplayText display = new PacketDisplayText(plugin, player);
            nameTags.put(player.getUniqueId(), display);
            creating.remove(player.getUniqueId());
            display.text(component);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(false);
            display.setSeeThrough(true);
            //invisible background
            display.setBackgroundColor(Color.BLACK.setAlpha(0));
            display.setTransformation(new Vector3f(0, plugin.getConfigManager().getSettings().getYOffset(), 0));

            display.setViewRange(plugin.getConfigManager().getSettings().getViewDistance());

//                Optional.ofNullable(Bukkit.getEntity(white.remove(player.getUniqueId()))).ifPresent(Entity::remove);

            final boolean isVanished = plugin.getVanishManager().isVanished(player);
            display.spawn(player);

            //if player is vanished, hide display for all players except for who can see the player
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p != player)
                    .filter(p -> !isVanished || plugin.getVanishManager().canSee(p, player))
                    .forEach(display::showToPlayer);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), e);
        }
    }

    public void removePlayer(@NotNull Player player) {
        final PacketDisplayText packetDisplayText = nameTags.remove(player.getUniqueId());
        if (packetDisplayText == null) {
            return;
        }
        packetDisplayText.remove();
        nameTags.forEach((uuid, display) -> {
            display.getBlocked().remove(player.getUniqueId());
            display.hideFromPlayerSilenty(player);
        });
    }

    public void hideAllDisplays(Player player) {
        nameTags.forEach((uuid, display) -> {
            display.hideFromPlayer(player);
            display.getBlocked().add(player.getUniqueId());
        });
    }

    public void teleportAndApply(@NotNull Player player, @NotNull Location location) {
        getPacketDisplayText(player).ifPresent(d -> new HashSet<>(d.getEntity().getViewers()).stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != player)
                .filter(Objects::nonNull)
                .filter(p -> location.getWorld() != player.getWorld())
                .forEach(d::hideFromPlayer));

        nameTags.forEach((uuid, display) -> {
            if (display.getOwner() == player) {
                return;
            }
            if (display.getOwner().getWorld() != location.getWorld()) {
                if (display.canPlayerSee(player)) {
                    System.out.println("Hiding entity " + display.getEntity().getEntityId() + " for " + player.getName());
                    display.hideFromPlayer(player);
                }
                return;
            }

            if (display.getOwner().getLocation().distance(location) > plugin.getConfigManager().getSettings().getViewDistance() * 160) {
                if (display.canPlayerSee(player)) {
                    System.out.println("Hiding entity " + display.getEntity().getEntityId() + " for " + player.getName());
                    display.hideFromPlayerSilenty(player);
                }
                return;
            }
            display.showToPlayer(player);
            System.out.println("Showing entity " + display.getEntity().getEntityId() + " for " + player.getName());
        });
//        final UUID uuid = nameTags.get(player.getUniqueId());
//        if (uuid == null) {
//            return;
//        }
//        final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
//        if (display == null) {
//            return;
//        }
//
//        if (player.getPassengers().contains(display) && display.getWorld() == player.getWorld() && display.getLocation().distance(player.getLocation()) < 4) {
//            return;
//        }
//
//        Bukkit.getScheduler().runTaskLater(plugin, () -> {
//            display.teleport(player.getLocation().clone().add(0, 1.8, 0));
//            applyPassenger(player);
//            ejectable.remove(player.getUniqueId());
//        }, 1);
    }


    public void removeAll() {
        nameTags.forEach((uuid, display) -> display.remove());

        nameTags.clear();
    }


    public void updateSneaking(@NotNull Player player, boolean sneaking) {
//        final UUID uuid = nameTags.get(player.getUniqueId());
//        if (uuid == null) return;
//        final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
//        if (display == null) return;
//
//        display.setSeeThrough(!sneaking);
//        display.setTextOpacity((byte) (sneaking ? plugin.getConfigManager().getSettings().getSneakOpacity() : -1));
        getPacketDisplayText(player).ifPresent(packetDisplayText -> {
            packetDisplayText.setSeeThrough(!sneaking);
            packetDisplayText.setTextOpacity((byte) (sneaking ? plugin.getConfigManager().getSettings().getSneakOpacity() : -1));
            packetDisplayText.refresh();
        });
    }

    public void reload() {
        final float yOffset = plugin.getConfigManager().getSettings().getYOffset();
        final float viewDistance = plugin.getConfigManager().getSettings().getViewDistance();


        plugin.getServer().getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(p -> {
            refreshPlayer(p);
            setYOffset(p, yOffset);
            setViewDistance(p, viewDistance);
        }));
    }

    public void debug(@NotNull Audience audience) {
        audience.sendMessage(Component.text("Nametags:"));
        nameTags.forEach((uuid, display) -> {
            final Player player = Bukkit.getPlayer(uuid);

            if (player == null) {
                return;
            }

            audience.sendMessage(Component.text(player.getName() + " -> " + display.getUniqueId() + " " + display.getEntity().getEntityId()));
        });
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
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            //TODO: fix this
//            final UUID uuid = nameTags.get(player.getUniqueId());
//            if (uuid == null) return;
//            final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
//            if (display == null) return;
//
//            List<? extends Player> canSee = Bukkit.getOnlinePlayers()
//                    .stream()
//                    .filter(p -> plugin.getVanishManager().canSee(p, player))
//                    .toList();
//
//            List<? extends Player> cannotSee = Bukkit.getOnlinePlayers()
//                    .stream()
//                    .filter(p -> !canSee.contains(p))
//                    .toList();
//
//            cannotSee.forEach(p -> p.hideEntity(plugin, display));
        }, 1);
    }

    public void unVanishPlayer(@NotNull Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            //TODO: fix this
//            final UUID uuid = nameTags.get(player.getUniqueId());
//            if (uuid == null) return;
//            final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
//            if (display == null) return;
//
//            Bukkit.getOnlinePlayers()
//                    .stream()
//                    .filter(p -> p != player)
//                    .forEach(p -> p.showEntity(plugin, display));
        }, 1);
    }

    public Optional<PacketDisplayText> getPacketDisplayText(Player player) {
        return Optional.ofNullable(nameTags.get(player.getUniqueId()));
    }

    public void updateDisplaysForPlayer(Player player) {
        nameTags.forEach((uuid, display) -> {
            display.getBlocked().remove(player.getUniqueId());
            display.showToPlayer(player);
        });
    }
}
