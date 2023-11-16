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
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("UnstableApiUsage")
public class NameTagManager {

    private final UnlimitedNameTags plugin;
    private final Map<UUID, TextDisplay> nameTags;

    public NameTagManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.nameTags = new HashMap<>();
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
        if(nameTags.containsKey(player.getUniqueId())) {
            return;
        }

        final Settings.NameTag nametag = plugin.getConfigManager().getSettings().getNametag(player);

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.lines())
                .thenAccept(lines -> createDisplay(player, lines))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), throwable);
                    return null;
                })
        ;
    }

    public void refreshPlayer(@NotNull Player player) {
        final Settings.NameTag nametag = plugin.getConfigManager().getSettings().getNametag(player);

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.lines())
                .thenAccept(lines -> editDisplay(player, lines))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), throwable);
                    return null;
                });
    }

    private void editDisplay(Player player, Component component) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            final TextDisplay display = nameTags.get(player.getUniqueId());
            if (display != null) {
                display.text(component);
            }
        });
    }

    public void applyPassenger(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            final TextDisplay display = nameTags.get(player.getUniqueId());

            final boolean isVanished = plugin.getVanishManager().isVanished(player);

            //show to all players except the player itself after tp
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> isVanished && plugin.getVanishManager().canSee(p, player))
                    .forEach(p -> p.showEntity(plugin, display));

            player.addPassenger(display);
        });
    }

    public void updateDisplaysForPlayer(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> nameTags.forEach((uuid, display) -> {
            final Player p = Bukkit.getPlayer(uuid);

            //player is offline
            if (p == null) {
                return;
            }

            if (!plugin.getVanishManager().isVanished(p) || plugin.getVanishManager().canSee(player, p)) {
                player.showEntity(plugin, display);
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
            nameTags.put(player.getUniqueId(), display);
            display.text(component);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(false);
            display.setSeeThrough(true);
            //invisible background
            display.setBackgroundColor(Color.BLACK.setAlpha(0));
            display.setVisibleByDefault(false);
            display.setMetadata("nametag", new FixedMetadataValue(plugin, true));

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

    public void removePlayer(@NotNull Player player) {
        final TextDisplay display = nameTags.remove(player.getUniqueId());

        if (display != null) {
            System.out.println("Removing display");
            display.remove();
//            player.removePassenger(display);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (display != null) {
                System.out.println("Removing display");
                display.remove();
            }
        }, 1);
    }

    public void removeAll() {
        nameTags.forEach((uuid, display) -> {
//            final Player player = Bukkit.getPlayer(uuid);
//
//            if (player != null) {
//                player.removePassenger(display);
//            }

            display.remove();
        });

        nameTags.clear();
    }


    @Nullable
    public TextDisplay removePassenger(@NotNull Player player) {
        final TextDisplay display = nameTags.get(player.getUniqueId());

        if (display == null) return null;

        player.removePassenger(display);
        return display;
    }

    public void updateSneaking(@NotNull Player player, boolean sneaking) {
        final TextDisplay display = nameTags.get(player.getUniqueId());
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
        final TextDisplay display = nameTags.get(player.getUniqueId());
        final Transformation transformation = display.getTransformation();
        transformation.getTranslation().set(0, yOffset, 0);
        display.setTransformation(transformation);
    }

    private void setViewDistance(@NotNull Player player, float viewDistance) {
        final TextDisplay display = nameTags.get(player.getUniqueId());
        display.setViewRange(viewDistance);
    }


    public void vanishPlayer(@NotNull Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final TextDisplay display = nameTags.get(player.getUniqueId());

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
            final TextDisplay display = nameTags.get(player.getUniqueId());

            Bukkit.getOnlinePlayers()
                    .stream()
                    .filter(p -> p != player)
                    .forEach(p -> p.showEntity(plugin, display));
        }, 1);
    }
}
