package org.alexdev.unlimitednametags.nametags;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.User;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Getter
public class NameTagManager {

    private final UnlimitedNameTags plugin;
    private final ConcurrentMap<UUID, PacketNameTag> nameTags;
    private final ConcurrentMap<Integer, PacketNameTag> entityIdToDisplay;
    private final Set<UUID> creating;
    private final Set<UUID> blocked;
    private final Set<UUID> hideNametags;
    private final List<MyScheduledTask> tasks;
    @Setter
    private boolean debug = false;
    private final Attribute scaleAttribute;

    // Cache for computational optimization
    private final Map<UUID, Vector> playerDirections = new ConcurrentHashMap<>();
    private final Map<UUID, Location> playerEyeLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> viaVersionCache = new ConcurrentHashMap<>();

    public NameTagManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.nameTags = Maps.newConcurrentMap();
        this.entityIdToDisplay = Maps.newConcurrentMap();
        this.tasks = new ArrayList<>();
        this.creating = Sets.newConcurrentHashSet();
        this.blocked = Sets.newConcurrentHashSet();
        this.hideNametags = Sets.newConcurrentHashSet();
        this.scaleAttribute = loadScaleAttribute();
        this.loadAll();
    }

    private void loadAll() {
        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            Bukkit.getOnlinePlayers().forEach(p -> addPlayer(p, true));
            this.startTask();
        }, 5);
    }

    private void startTask() {
        tasks.forEach(MyScheduledTask::cancel);
        tasks.clear();

        final MyScheduledTask refresh = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                this::refreshAllPlayers,
                10, plugin.getConfigManager().getSettings().getTaskInterval());

        final MyScheduledTask passengers = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                this::updatePassengers,
                20, 20 * 5L);

        if (isScalePresent()) {
            final MyScheduledTask scale = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                    this::updateScales,
                    20, 10);
            tasks.add(scale);
        }

        if (plugin.getConfigManager().getSettings().isShowWhileLooking()) {
            final MyScheduledTask point = plugin.getTaskScheduler().runTaskTimerAsynchronously(
                    this::processPlayerPointing,
                    5, 5);
            tasks.add(point);
        }

        tasks.add(refresh);
        tasks.add(passengers);
    }

    // Optimized group operations
    private void refreshAllPlayers() {
        if (plugin.isPaper() && plugin.getServer().isStopping()) return;
        Bukkit.getOnlinePlayers().forEach(p -> refresh(p, false));
    }

    private void updatePassengers() {
        Bukkit.getOnlinePlayers().forEach(player ->
            getPacketDisplayText(player).ifPresent(PacketNameTag::sendPassengerPacketToViewers));
    }

    private void updateScales() {
        Bukkit.getOnlinePlayers().forEach(player ->
            getPacketDisplayText(player)
                .filter(PacketNameTag::checkScale)
                .ifPresent(PacketNameTag::refresh));
    }

    private void processPlayerPointing() {
        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        if (onlinePlayers.isEmpty()) return;

        Map<World, List<Player>> playersByWorld = onlinePlayers.stream()
            .collect(Collectors.groupingBy(Player::getWorld));

        for (List<Player> players : playersByWorld.values()) {
            int size = players.size();
            if (size < 2) continue;

            for (int i = 0; i < size; i++) {
                Player player1 = players.get(i);
                Optional<PacketNameTag> displayOpt = getPacketDisplayText(player1);
                if (displayOpt.isEmpty()) continue;

                PacketNameTag display = displayOpt.get();
                Location loc1 = player1.getLocation();

                for (int j = 0; j < size; j++) {
                    if (i == j) continue;

                    Player player2 = players.get(j);
                    if (!isCompatibleVersion(player2)) continue;
                    if (player1.equals(player2)) continue;

                    Location loc2 = player2.getLocation();
                    if (loc1.distanceSquared(loc2) > 2500) continue; // 50^2 blocks

                    boolean isPointing = isPlayerPointingAt(player2, player1);
                    boolean canSee = display.canPlayerSee(player2);

                    if (canSee && !isPointing) {
                        display.hideFromPlayer(player2);
                    } else if (!canSee && isPointing) {
                        display.showToPlayer(player2);
                    }
                }
            }
        }
    }

    private boolean isCompatibleVersion(Player player) {
        return viaVersionCache.computeIfAbsent(player.getUniqueId(), 
            k -> plugin.getHook(ViaVersionHook.class)
                .map(h -> h.hasNotTextDisplays(player))
                .orElse(false));
    }

    // Optimized directional calculations
    public boolean isPlayerPointingAt(Player viewer, Player target) {
        Location eyeLoc = viewer.getEyeLocation();
        Location targetLoc = target.getEyeLocation();

        if (!eyeLoc.getWorld().equals(targetLoc.getWorld())) return false;
        if (eyeLoc.distanceSquared(targetLoc) < 25) return true; // 5^2 blocks

        Vector direction = playerDirections.computeIfAbsent(viewer.getUniqueId(), 
            k -> viewer.getEyeLocation().getDirection());

        Vector toTarget = targetLoc.toVector().subtract(eyeLoc.toVector());
        double distance = toTarget.length();
        if (distance < 1e-7) return false;

        toTarget.normalize();
        return direction.dot(toTarget) > 0.90;
    }

    // Optimization of work with attributes
    public float getScale(@NotNull Player player) {
        if (!isScalePresent()) return 1.0f;
        
        AttributeInstance attribute = player.getAttribute(scaleAttribute);
        return attribute != null ? (float) attribute.getValue() : 1.0f;
    }

    // Simplified logic for adding a player
    public void addPlayer(@NotNull Player player, boolean canBlock) {
        if (!preAddChecks(player, canBlock)) return;

        creating.add(player.getUniqueId());
        plugin.getTaskScheduler().runTaskAsynchronously(() -> {
            try {
                Settings.NameTag nametag = plugin.getConfigManager().getSettings().getNametag(player);
                PacketNameTag display = new PacketNameTag(plugin, player, nametag);
                
                plugin.getPlaceholderManager().applyPlaceholders(player, nametag.linesGroups(), List.of(player))
                    .thenAccept(lines -> finalizeNameTagCreation(player, display, nametag, lines.get(player)))
                    .exceptionally(throwable -> {
                        handleNameTagError(player, throwable);
                        return null;
                    });
            } catch (Exception e) {
                handleNameTagError(player, e);
            }
        });
    }

    private void finalizeNameTagCreation(Player player, PacketNameTag display, 
                                        Settings.NameTag nametag, Component component) {
        plugin.getTaskScheduler().runTask(() -> {
            try {
                display.initialize(player, component, nametag);
                nameTags.put(player.getUniqueId(), display);
                entityIdToDisplay.put(display.getEntityId(), display);
                
                if (plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
                    display.showToPlayer(player);
                }
                handleVanish(player, display);
                
                if (debug) plugin.getLogger().info("Added nametag for " + player.getName());
            } catch (Throwable e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, 
                    "Failed to create nametag for " + player.getName(), e);
            } finally {
                creating.remove(player.getUniqueId());
            }
        });
    }

    private void handleNameTagError(Player player, Throwable throwable) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE, 
            "Failed to create nametag for " + player.getName(), throwable);
        creating.remove(player.getUniqueId());
    }

    // Optimize display updates
    private void editDisplay(@NotNull Player player, Map<Player, Component> components,
                             @NotNull Settings.NameTag nameTag, boolean force) {
        getPacketDisplayText(player).ifPresent(packetNameTag -> {
            if (!packetNameTag.getNameTag().equals(nameTag)) {
                packetNameTag.setNameTag(nameTag);
            }
            
            components.forEach((viewer, component) -> {
                boolean needsUpdate = packetNameTag.text(viewer, component) || force;
                
                if (force || needsUpdate) {
                    packetNameTag.modify(viewer, meta -> {
                        meta.setShadow(nameTag.background().shadowed());
                        meta.setSeeThrough(nameTag.background().seeThrough());
                        meta.setBackgroundColor(nameTag.background().getColor().asARGB());
                    });
                    packetNameTag.refreshForPlayer(viewer);
                }
            });
            
            if (force && isScalePresent()) {
                packetNameTag.checkScale();
            }
        });
    }

    // Optimization of vanish processing
    private void handleVanish(@NotNull Player player, @NotNull PacketNameTag display) {
        boolean isVanished = plugin.getVanishManager().isVanished(player);
        World world = player.getWorld();
        Location playerLoc = player.getLocation();
        double maxDistance = 250 * 250; // Distance squared for optimization

        Bukkit.getOnlinePlayers().parallelStream()
            .filter(p -> p != player)
            .filter(p -> p.getWorld() == world)
            .filter(p -> p.getLocation().distanceSquared(playerLoc) <= maxDistance)
            .filter(p -> !isVanished || plugin.getVanishManager().canSee(p, player))
            .filter(p -> !display.canPlayerSee(p))
            .forEach(display::showToPlayer);
    }

    // Other methods with minimal optimizations
    public void removePlayer(@NotNull Player player) {
        PacketNameTag display = nameTags.remove(player.getUniqueId());
        if (display != null) {
            display.remove();
            entityIdToDisplay.remove(display.getEntityId());
        }
        
        UUID playerId = player.getUniqueId();
        nameTags.values().forEach(d -> {
            d.handleQuit(player);
            d.getBlocked().remove(playerId);
        });
    }

    public void reload() {
        Settings settings = plugin.getConfigManager().getSettings();
        float yOffset = settings.getYOffset();
        float viewDistance = settings.getViewDistance();
        AbstractDisplayMeta.BillboardConstraints billboard = settings.getDefaultBillboard();

        plugin.getTaskScheduler().runTaskAsynchronously(() -> 
            Bukkit.getOnlinePlayers().forEach(p -> {
                setYOffset(p, yOffset);
                setViewDistance(p, viewDistance);
                setBillBoard(p, billboard);
                refresh(p, true);
            })
        );
        startTask();
    }

    // Additional optimizations
    private Attribute loadScaleAttribute() {
        ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
        if (version.isOlderThan(ServerVersion.V_1_20_5)) return null;
        return Attribute.SCALE;
    }

    public boolean isScalePresent() {
        return PacketEvents.getAPI().getServerManager().getVersion()
            .isNewerThanOrEquals(ServerVersion.V_1_20_5);
    }
}
