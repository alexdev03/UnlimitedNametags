package org.alexdev.unlimitednametags.nametags;

import com.github.retrooper.packetevents.util.Vector3f;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.packet.PacketDisplayText;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Getter
public class NameTagManager {

    private final UnlimitedNameTags plugin;
    private final Map<UUID, PacketDisplayText> nameTags;
    private final Map<UUID, UUID> white;
    private final List<UUID> creating;
    private final List<UUID> blocked;
    private int task;

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
        if (task != 0) {
            Bukkit.getScheduler().cancelTask(task);
        }
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                () -> Bukkit.getOnlinePlayers().forEach(this::refreshPlayer),
                10, plugin.getConfigManager().getSettings().getTaskInterval()).getTaskId();
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

        creating.add(player.getUniqueId());
        final Settings.NameTag nametag = plugin.getConfigManager().getSettings().getNametag(player);

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.lines())
                .thenAccept(lines -> createDisplay(player, lines, nametag))
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
                .thenAccept(lines -> editDisplay(player, lines, nametag, update))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to edit nametag for " + player.getName(), throwable);
                    return null;
                });
    }

    public void refreshPlayer(@NotNull Player player) {
        refresh(player, true);
    }

    private void editDisplay(@NotNull Player player, @NotNull Component component, @NotNull Settings.NameTag nameTag, boolean update) {
        getPacketDisplayText(player).ifPresent(packetDisplayText -> {
            packetDisplayText.text(component);
            packetDisplayText.setBackgroundColor(nameTag.background().getColor());
            packetDisplayText.setShadowed(nameTag.background().shadowed());
            packetDisplayText.setSeeThrough(nameTag.background().seeThrough());
            if (update) {
                packetDisplayText.refresh();
            }
        });
    }

    private void createDisplay(@NotNull Player player, @NotNull Component component, @NotNull Settings.NameTag nameTag) {
        try {
            final Location location = player.getLocation().clone();
            //add 1.80 to make a perfect tp animation
            location.setY(location.getY() + 1.80);

            final PacketDisplayText display = new PacketDisplayText(plugin, player);
            nameTags.put(player.getUniqueId(), display);
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

            final boolean isVanished = plugin.getVanishManager().isVanished(player);
            display.spawn(player);

            //if player is vanished, hide display for all players except for who can see the player
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p != player)
                    .filter(p -> p.getLocation().getWorld() == player.getLocation().getWorld())
                    .filter(p -> !isVanished || plugin.getVanishManager().canSee(p, player))
                    .filter(p -> p.getLocation().distance(player.getLocation()) <= 100)
                    .forEach(display::showToPlayer);
            
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), e);
        }
    }


    public void removePlayer(@NotNull Player player, boolean quit) {
        final PacketDisplayText packetDisplayText = nameTags.remove(player.getUniqueId());
        if (packetDisplayText != null) {
            packetDisplayText.remove();
        }

        nameTags.forEach((uuid, display) -> {
            if (quit) {
                display.handleQuit(player);
            } else {
                display.hideFromPlayerSilenty(player);
            }
            display.getBlocked().remove(player.getUniqueId());
        });
    }

    public void removePlayer(@NotNull Player player) {
        removePlayer(player, false);
    }

    public void removePlayerDisplay(@NotNull Player player) {
        final PacketDisplayText packetDisplayText = nameTags.remove(player.getUniqueId());
        if (packetDisplayText != null) {
            packetDisplayText.remove();
        }
    }

    public void hideAllDisplays(@NotNull Player player) {
        nameTags.forEach((uuid, display) -> {
            display.hideFromPlayer(player);
            display.getBlocked().add(player.getUniqueId());
        });
    }

    public void removeAll() {
        nameTags.forEach((uuid, display) -> display.remove());

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


        plugin.getServer().getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(p -> {
            setYOffset(p, yOffset);
            setViewDistance(p, viewDistance);
            refreshPlayer(p);
        }));
        startTask();
    }

    public void debug(@NotNull CommandSender audience) {
        audience.sendMessage(("Nametags:"));
        nameTags.forEach((uuid, display) -> {
            final Player player = Bukkit.getPlayer(uuid);

            if (player == null) {
                return;
            }

            audience.sendMessage((player.getName() + " -> " + display.getUniqueId() + " " + display.getEntity().getEntityId() + " " + display.getEntity().getViewers()));
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


    public Optional<PacketDisplayText> getPacketDisplayText(@NotNull Player player) {
        return Optional.ofNullable(nameTags.get(player.getUniqueId()));
    }

    public Optional<PacketDisplayText> getPacketDisplayText(int id) {
        return nameTags.values().stream().filter(display -> display.getEntity().getEntityId() == id).findFirst();
    }

    public void updateDisplay(@NotNull Player player, @NotNull Player target) {
        if (player == target) {
            return;
        }
        getPacketDisplayText(target).ifPresent(packetDisplayText -> {
            packetDisplayText.hideFromPlayerSilenty(player);
            if (!packetDisplayText.canPlayerSee(player)) {
                packetDisplayText.showToPlayer(player);
            }
        });
    }

    public void removeDisplay(@NotNull Player player, @NotNull Player target) {
        if (player == target) {
            return;
        }
        getPacketDisplayText(target).ifPresent(packetDisplayText -> {
            if (packetDisplayText.canPlayerSee(player)) {
                packetDisplayText.hideFromPlayer(player);
            }
        });
    }

    public void updateDisplaysForPlayer(Player player) {
        nameTags.forEach((uuid, display) -> {
            final Player owner = display.getOwner();

            if(player.getLocation().getWorld() != owner.getLocation().getWorld()) {
                return;
            }

            if (plugin.getVanishManager().isVanished(owner) && !plugin.getVanishManager().canSee(player, owner)) {
                return;
            }

            display.getBlocked().remove(player.getUniqueId());

            display.hideFromPlayerSilenty(player);
            display.showToPlayer(player);
        });
    }
}
