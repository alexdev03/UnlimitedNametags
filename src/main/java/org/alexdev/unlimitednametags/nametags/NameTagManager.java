package org.alexdev.unlimitednametags.nametags;

import com.github.retrooper.packetevents.util.Vector3f;
import com.google.common.collect.*;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
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
import java.util.concurrent.atomic.AtomicReference;

@Getter
public class NameTagManager {

    private final UnlimitedNameTags plugin;
    private final Map<UUID, PacketDisplayText> nameTags;
    private final List<UUID> creating;
    private final List<UUID> blocked;
    private final Multimap<UUID, Runnable> pending;
    private int task;

    public NameTagManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.nameTags = Maps.newConcurrentMap();
        this.creating = Lists.newCopyOnWriteArrayList();
        this.blocked = Lists.newCopyOnWriteArrayList();
        this.pending = Multimaps.synchronizedMultimap(Multimaps.newSetMultimap(Maps.newConcurrentMap(), Sets::newConcurrentHashSet));
        this.loadAll();
    }

    private void loadAll() {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            Bukkit.getOnlinePlayers().forEach(p -> addPlayer(p, true));
            this.startTask();
        }, 5);
    }

    private void startTask() {
        if (task != 0) {
            Bukkit.getScheduler().cancelTask(task);
        }
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                () -> Bukkit.getOnlinePlayers().forEach(this::refreshPlayer),
                10, plugin.getConfigManager().getSettings().getTaskInterval()).getTaskId();
    }

    public void addPending(@NotNull Player player, @NotNull Runnable runnable) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> pending.put(player.getUniqueId(), runnable));
    }

    public void blockPlayer(@NotNull Player player) {
        blocked.add(player.getUniqueId());
    }

    public void unblockPlayer(@NotNull Player player) {
        blocked.remove(player.getUniqueId());
    }

    public void addPlayer(@NotNull Player player, boolean startup) {
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

        creating.add(player.getUniqueId());
        final Settings.NameTag nametag = plugin.getConfigManager().getSettings().getNametag(player);

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.lines())
                .thenAccept(lines -> loadDisplay(player, lines, nametag, startup, display))
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

    private void loadDisplay(@NotNull Player player, @NotNull Component component,
                             @NotNull Settings.NameTag nameTag, boolean startup,
                             @NotNull PacketDisplayText display) {
        try {
            final Location location = player.getLocation().clone();
            //add 1.80 to make a perfect tp animation
            location.setY(location.getY() + 1.80);

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

            display.refresh();

            creating.remove(player.getUniqueId());

            pending.removeAll(player.getUniqueId()).forEach(Runnable::run);

//            if(!startup) {
//                return;
//            }

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

        nameTags.forEach((uuid, display) -> {
            if (quit) {
                display.handleQuit(player);
            } else {
                display.hideFromPlayerSilently(player);
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
        getPacketDisplayText(player).ifPresent(PacketDisplayText::clearViewers);
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

            Component text = Component.text(player.getName() + " -> " + " " + display.getEntity().getEntityId());
            text = text.color(TextColor.color(0x00FF00));
            text = text.hoverEvent(Component.text("Viewers: " + viewers));

            component.set(component.get().append(Component.text("\n")).append(text));

        });

        audience.sendMessage(component.get());
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
        return nameTags.values().stream().filter(display -> display.getEntity().getEntityId() == id).findFirst();
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

            if(player.getLocation().getWorld() != owner.getLocation().getWorld()) {
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
