package org.alexdev.unlimitednametags.nametags;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings("UnstableApiUsage")
public class NameTagManager {

    private final UnlimitedNameTags plugin;
    private final Map<UUID, UUID> nameTags;
    private final List<UUID> creating;

    public NameTagManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.nameTags = new ConcurrentHashMap<>();
        this.creating = new CopyOnWriteArrayList<>();
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

    public void addPlayer(@NotNull Player player) {
        if (nameTags.containsKey(player.getUniqueId())) {
            return;
        }

        if (creating.contains(player.getUniqueId())) {
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
                })
        ;
    }

    public void refreshPlayer(@NotNull Player player) {
        final Settings.NameTag nametag = plugin.getConfigManager().getSettings().getNametag(player);

        if (!nameTags.containsKey(player.getUniqueId())) {
            return;
        }

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.lines())
                .thenAccept(lines -> editDisplay(player, lines))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), throwable);
                    return null;
                });
    }

    private void editDisplay(Player player, Component component) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            final UUID uuid = nameTags.get(player.getUniqueId());
            if (uuid == null) return;
            final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
            if (display == null) return;

            display.text(component);
        });
    }

    private void applyPassenger(Player player) {
        final UUID uuid = nameTags.get(player.getUniqueId());
        if (uuid == null) return;
        final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
        if (display == null) return;

        final boolean isVanished = plugin.getVanishManager().isVanished(player);

        //show to all players except the player itself after tp
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> isVanished && plugin.getVanishManager().canSee(p, player))
                .forEach(p -> p.showEntity(plugin, display));

        player.addPassenger(display);
    }

    public void updateDisplaysForPlayer(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> nameTags.forEach((uuid, display) -> {
            final Player p = Bukkit.getPlayer(uuid);

            //player is offline
            if (p == null) {
                return;
            }

            TextDisplay textDisplay = (TextDisplay) Bukkit.getEntity(display);

            if (textDisplay == null) {
                return;
            }

            if (!plugin.getVanishManager().isVanished(p) || plugin.getVanishManager().canSee(player, p)) {
                player.showEntity(plugin, textDisplay);
            }
        }));
    }

    @SuppressWarnings("deprecation")
    private void createDisplay(Player player, Component component) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final Location location = player.getLocation().clone();
            //add 1.80 to make a perfect tp animation
            location.setY(location.getY() + 1.80);

            final TextDisplay display = location.getWorld().spawn(location, TextDisplay.class);
            nameTags.put(player.getUniqueId(), display.getUniqueId());
            creating.remove(player.getUniqueId());
            display.text(component);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(false);
            display.setSeeThrough(true);
            //invisible background
            display.setBackgroundColor(Color.BLACK.setAlpha(0));
            display.setVisibleByDefault(false);
            display.setMetadata("nametag", new FixedMetadataValue(plugin, player.getUniqueId()));

            final Transformation transformation = display.getTransformation();
            transformation.getTranslation().add(0, plugin.getConfigManager().getSettings().getYOffset(), 0);
            display.setTransformation(transformation);

            display.setViewRange(plugin.getConfigManager().getSettings().getViewDistance());

            player.addPassenger(display);

            final boolean isVanished = plugin.getVanishManager().isVanished(player);

            //if player is vanished, hide display for all players except for who can see the player
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p != player)
                    .filter(p -> !isVanished || plugin.getVanishManager().canSee(p, player))
                    .forEach(p -> p.showEntity(plugin, display));

        }, 5);
    }

    public void removePlayer(@NotNull Player player, boolean removePassenger) {
        final UUID uuid = nameTags.get(player.getUniqueId());
        if (uuid == null) return;
        final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
        if (display == null) return;

        if (removePassenger) {
            player.removePassenger(display);
        }
        display.remove();
        nameTags.remove(player.getUniqueId());
    }

    public void removeDeadDisplay(TextDisplay textDisplay) {
        for (Map.Entry<UUID, UUID> entry : nameTags.entrySet()) {
            if (entry.getValue().equals(textDisplay.getUniqueId())) {
                nameTags.remove(entry.getKey(), entry.getValue());
                Optional.ofNullable(Bukkit.getPlayer(entry.getKey())).ifPresent(this::addPlayer);
                break;
            }
        }
    }

//    public void hideNameTag(@NotNull Player player) {
//        final UUID uuid = nameTags.get(player.getUniqueId());
//        if (uuid == null) return;
//        final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
//        if (display == null) return;
//
//        Bukkit.getOnlinePlayers()
////                .filter(p -> p != player)
//                .forEach(p -> p.hideEntity(plugin, display));
//    }
//
//    public void showNameTag(@NotNull Player player) {
//        final UUID uuid = nameTags.get(player.getUniqueId());
//        if (uuid == null) return;
//        final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
//        if (display == null) return;
//
//        final boolean isVanished = plugin.getVanishManager().isVanished(player);
//
//        //if player is vanished, hide display for all players except for who can see the player
//        Bukkit.getOnlinePlayers().stream()
//                .filter(p -> p != player)
//                .filter(p -> !isVanished || plugin.getVanishManager().canSee(p, player))
//                .forEach(p -> p.showEntity(plugin, display));
//    }


    public void teleportAndApply(@NotNull Player player) {
        final UUID uuid = nameTags.get(player.getUniqueId());
        if (uuid == null) return;
        final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
        if (display == null) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            display.teleport(player.getLocation().clone().add(0, 1.8, 0));
            applyPassenger(player);
        });
    }


    public void removeAll() {
        nameTags.forEach((uuid, display) -> {
            Optional.ofNullable(Bukkit.getEntity(display)).ifPresent(entity -> {
                entity.removeMetadata("nametag", plugin);
                entity.remove();
            });
        });

        nameTags.clear();
    }


    public void updateSneaking(@NotNull Player player, boolean sneaking) {
        final UUID uuid = nameTags.get(player.getUniqueId());
        if (uuid == null) return;
        final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
        if (display == null) return;

        display.setSeeThrough(!sneaking);
        display.setTextOpacity((byte) (sneaking ? plugin.getConfigManager().getSettings().getSneakOpacity() : -1));
    }

    public void reload() {
        final float yOffset = plugin.getConfigManager().getSettings().getYOffset();
        final float viewDistance = plugin.getConfigManager().getSettings().getViewDistance();


        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Bukkit.getOnlinePlayers().forEach(p -> {
                refreshPlayer(p);
                setYOffset(p, yOffset);
                setViewDistance(p, viewDistance);
            });
        });
    }

    public void debug(@NotNull Audience audience) {
        audience.sendMessage(Component.text("Nametags:"));
        nameTags.forEach((uuid, display) -> {
            final Player player = Bukkit.getPlayer(uuid);

            if (player == null) {
                return;
            }

            audience.sendMessage(Component.text(player.getName() + " -> " + player.getPassengers().stream().map(entity -> entity.getType().name()).reduce((a, b) -> a + ", " + b).orElse("")));
        });
    }

    private void setYOffset(@NotNull Player player, float yOffset) {
        final UUID uuid = nameTags.get(player.getUniqueId());
        if (uuid == null) return;
        final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
        if (display == null) return;
        final Transformation transformation = display.getTransformation();
        transformation.getTranslation().set(0, yOffset, 0);
        display.setTransformation(transformation);
    }

    private void setViewDistance(@NotNull Player player, float viewDistance) {
        final TextDisplay display = (TextDisplay) Bukkit.getEntity(nameTags.get(player.getUniqueId()));
        if (display == null) return;
        display.setViewRange(viewDistance);
    }


    public void vanishPlayer(@NotNull Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final UUID uuid = nameTags.get(player.getUniqueId());
            if (uuid == null) return;
            final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
            if (display == null) return;

            List<? extends Player> canSee = Bukkit.getOnlinePlayers()
                    .stream()
                    .filter(p -> plugin.getVanishManager().canSee(p, player))
                    .toList();

            List<? extends Player> cannotSee = Bukkit.getOnlinePlayers()
                    .stream()
                    .filter(p -> !canSee.contains(p))
                    .toList();

            cannotSee.forEach(p -> p.hideEntity(plugin, display));
        }, 1);
    }

    public void unVanishPlayer(@NotNull Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final UUID uuid = nameTags.get(player.getUniqueId());
            if (uuid == null) return;
            final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
            if (display == null) return;

            Bukkit.getOnlinePlayers()
                    .stream()
                    .filter(p -> p != player)
                    .forEach(p -> p.showEntity(plugin, display));
        }, 1);
    }
}
