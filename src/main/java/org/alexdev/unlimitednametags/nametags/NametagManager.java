package org.alexdev.unlimitednametags.nametags;

import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.UnlimitedNametags;
import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NametagManager {

    private final UnlimitedNametags plugin;
    private final Map<UUID, TextDisplay> nametags;

    public NametagManager(UnlimitedNametags plugin) {
        this.plugin = plugin;
        this.nametags = new HashMap<>();
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
        Settings.Nametag nametag = plugin.getConfigManager().getSettings().getNametag(player);

        plugin.getPlaceholderManager().applyPlaceholders(player, nametag.lines())
                .thenAccept(lines -> createDisplay(player, lines))
                .exceptionally(throwable -> {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), throwable);
                    return null;
                })
        ;
    }

    public void refreshPlayer(Player player) {
        plugin.getPlaceholderManager().applyPlaceholders(player, plugin.getConfigManager().getSettings().getNametag(player).lines())
                .thenAccept(lines -> editDisplay(player, lines))
                .exceptionally(throwable -> {
                    throwable.printStackTrace();
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), throwable);
                    return null;
                });
    }

    private void editDisplay(Player player, Component component) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            TextDisplay display = nametags.get(player.getUniqueId());
            if (display != null) {
                display.text(component);

                if(!player.getPassengers().contains(display)) {
                    player.addPassenger(display);
                }
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void createDisplay(Player player, Component component) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            TextDisplay display = player.getLocation().getWorld().spawn(player.getLocation(), TextDisplay.class);
            display.text(component);
            display.setInvulnerable(true);
            display.setPersistent(false);
            display.setBillboard(Display.Billboard.CENTER);
            display.setShadowed(false);
            display.setBackgroundColor(Color.BLACK.setAlpha(0));

            Transformation transformation = display.getTransformation();
            transformation.getTranslation().add(0, 0.3f, 0);
            display.setTransformation(transformation);

            player.addPassenger(display);
            player.hideEntity(plugin, display);
            nametags.put(player.getUniqueId(), display);
        }, 15);
    }

    public void removePlayer(Player player) {
        TextDisplay display = nametags.remove(player.getUniqueId());
        if (display != null) {
            player.removePassenger(display);
            display.remove();
        }
    }

    public void removeAll() {
        nametags.forEach((uuid, display) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.removePassenger(display);
            }
            display.remove();
        });

        nametags.clear();
    }


}
