package org.alexdev.unlimitednametags.nametags;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;

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
        loadAll();
        startTask();
    }

    private void loadAll() {
        Bukkit.getOnlinePlayers().forEach(this::addPlayer);
    }

    private void startTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            Bukkit.getOnlinePlayers().forEach(this::refreshPlayer);
        }, 10, plugin.getConfigManager().getSettings().getTaskInterval());
    }


    public void addPlayer(Player player) {
        final Settings.NameTag nametag = plugin.getConfigManager().getSettings().getNametag(player);

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.lines())
                .thenAccept(lines -> createDisplay(player, lines))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), throwable);
                    return null;
                })
        ;
    }

    public void refreshPlayer(Player player) {
        final Settings.NameTag nametag = plugin.getConfigManager().getSettings().getNametag(player);
        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.lines())
                .thenAccept(lines -> editDisplay(player, lines))
                .exceptionally(throwable -> {
                    throwable.printStackTrace();
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
//            player.hideEntity(plugin, display);

            final boolean isVanished = plugin.getVanishManager().isVanished(player);

            //show to all players except the player itself after tp
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> isVanished && plugin.getVanishManager().canSee(p, player))
                    .forEach(p -> p.showEntity(plugin, display));

            player.addPassenger(display);
        });
    }

    @SuppressWarnings("deprecation")
    private void createDisplay(Player player, Component component) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final TextDisplay display = player.getLocation().getWorld().spawn(player.getLocation(), TextDisplay.class);
            display.text(component);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(false);
            display.setSeeThrough(true);
            display.setBackgroundColor(Color.BLACK.setAlpha(0));
            display.setVisibleByDefault(false);

            final Transformation transformation = display.getTransformation();
            transformation.getTranslation().add(0, 0.3f, 0);
            display.setTransformation(transformation);

            player.addPassenger(display);

            final boolean isVanished = plugin.getVanishManager().isVanished(player);

            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> isVanished && plugin.getVanishManager().canSee(p, player))
                    .forEach(p -> p.showEntity(plugin, display));

            nameTags.put(player.getUniqueId(), display);
        }, 1);
    }

    public void removePlayer(Player player) {
        final TextDisplay display = nameTags.remove(player.getUniqueId());
        if (display != null) {
            player.removePassenger(display);
            display.remove();
        }
    }

    public void removeAll() {
        nameTags.forEach((uuid, display) -> {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.removePassenger(display);
            }
            display.remove();
        });

        nameTags.clear();
    }


    public @NotNull TextDisplay removePassenger(Player player) {
        final TextDisplay display = nameTags.get(player.getUniqueId());
        player.removePassenger(display);
        return display;
    }

    public void updateSneaking(Player player, boolean sneaking) {
        final TextDisplay display = nameTags.get(player.getUniqueId());
        if (display != null) {
            display.setSeeThrough(!sneaking);
            display.setTextOpacity((byte) (sneaking ? plugin.getConfigManager().getSettings().getSneakOpacity() : -1));
        }
    }

    public void reload() {
        Bukkit.getOnlinePlayers().forEach(this::refreshPlayer);
    }

    public void debug(Audience audience) {
        audience.sendMessage(Component.text("Nametags:"));
        nameTags.forEach((uuid, display) -> {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                audience.sendMessage(Component.text(player.getName() + " -> " + player.getPassengers().stream().map(entity -> entity.getType().name()).reduce((a, b) -> a + ", " + b).orElse("")));
            }
        });
    }


    public void vanishPlayer(Player player) {
        final TextDisplay display = nameTags.get(player.getUniqueId());

        List<? extends Player> canSee = Bukkit.getOnlinePlayers()
                .stream()
                .filter(p -> plugin.getVanishManager().canSee(p, player))
                .toList();

        List<? extends Player> cannotSee = Bukkit.getOnlinePlayers()
                .stream()
                .filter(p -> !canSee.contains(p))
                .toList();

        canSee.forEach(p -> p.hideEntity(plugin, display));
    }

    public void unVanishPlayer(Player player) {
        final TextDisplay display = nameTags.get(player.getUniqueId());

        Bukkit.getOnlinePlayers()
                .stream()
                .filter(p -> p != player)
                .forEach(p -> p.showEntity(plugin, display));
    }
}
