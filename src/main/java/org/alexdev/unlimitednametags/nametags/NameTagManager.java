package org.alexdev.unlimitednametags.nametags;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Getter;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@SuppressWarnings("UnstableApiUsage")
@Getter
public class NameTagManager {

    private final UnlimitedNameTags plugin;
    private final Map<UUID, UUID> nameTags;
    private final Map<UUID, UUID> white;
    private final List<UUID> creating;
    private final List<UUID> blocked;
    private final Set<UUID> ejectable;

    public NameTagManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.nameTags = Maps.newConcurrentMap();
        this.creating = Lists.newCopyOnWriteArrayList();
        this.white = Maps.newConcurrentMap();
        this.blocked = Lists.newCopyOnWriteArrayList();
        this.ejectable = Sets.newConcurrentHashSet();
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

        createBlankDisplay(player);

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

    private void createBlankDisplay(@NotNull Player player) {
        final TextDisplay display = player.getWorld().spawn(player.getLocation().clone().add(0, 1.80, 0), TextDisplay.class);
        white.put(player.getUniqueId(), display.getUniqueId());
        display.text(Component.empty());
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setBillboard(Display.Billboard.CENTER);
        player.addPassenger(display);
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
                .filter(p -> p != player && (isVanished || plugin.getVanishManager().canSee(p, player)))
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
            try {
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

                Optional.ofNullable(Bukkit.getEntity(white.remove(player.getUniqueId()))).ifPresent(Entity::remove);
                player.addPassenger(display);

                final boolean isVanished = plugin.getVanishManager().isVanished(player);

                //if player is vanished, hide display for all players except for who can see the player
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p != player)
                        .filter(p -> !isVanished || plugin.getVanishManager().canSee(p, player))
                        .forEach(p -> p.showEntity(plugin, display));
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create nametag for " + player.getName(), e);
            }

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

    public void hideAllDisplays(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> nameTags.forEach((uuid, display) -> {
            final TextDisplay textDisplay = (TextDisplay) Bukkit.getEntity(display);

            if (textDisplay == null) {
                return;
            }

            player.hideEntity(plugin, textDisplay);
        }));
    }

    public void teleportAndApply(@NotNull Player player) {
        final UUID uuid = nameTags.get(player.getUniqueId());
        if (uuid == null) {
            return;
        }
        final TextDisplay display = (TextDisplay) Bukkit.getEntity(uuid);
        if (display == null) {
            return;
        }

        if (player.getPassengers().contains(display) && display.getWorld() == player.getWorld() && display.getLocation().distance(player.getLocation()) < 4) {
            return;
        }

        System.out.println("teleporting " + player.getName() + " to " + display.getLocation());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            display.teleport(player.getLocation().clone().add(0, 1.8, 0));
            applyPassenger(player);
            ejectable.remove(player.getUniqueId());
        }, 1);
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

    @NotNull
    public Optional<TextDisplay> getEntityById(int entityId) {
        for (UUID uuid : nameTags.values()) {
            final Entity entity = Bukkit.getEntity(uuid);
            if (entity == null) continue;
            if (entity.getEntityId() == entityId) {
                return Optional.of((TextDisplay) entity);
            }
        }
        return Optional.empty();
    }
}
