package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3f;
import com.google.common.collect.Sets;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import lombok.Getter;
import lombok.SneakyThrows;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.entity.WrapperEntity;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import me.tofaa.entitylib.meta.types.DisplayMeta;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@SuppressWarnings("unused")
public class PacketDisplayText {

    private final UnlimitedNameTags plugin;
    private final WrapperEntity entity;
    private final TextDisplayMeta meta;
    private final Player owner;
    private final Set<UUID> blocked;


    public PacketDisplayText(@NotNull UnlimitedNameTags plugin, @NotNull Player owner) {
        this.plugin = plugin;
        this.owner = owner;
        this.entity = EntityLib.createEntity(UUID.randomUUID(), EntityTypes.TEXT_DISPLAY);
        this.meta = (TextDisplayMeta) entity.getMeta();
        this.blocked = Sets.newConcurrentHashSet();
        this.setConcurrent();
    }

    @SneakyThrows
    private void setConcurrent() {
        Field field = entity.getClass().getDeclaredField("viewers");
        field.setAccessible(true);
        field.set(entity, Sets.newConcurrentHashSet());
    }

    public void text(@NotNull Component text) {
        fixViewers();
        try {
            meta.setText(text);
        } catch (Exception ignored) {

        }
    }

    public void setBillboard(@NotNull DisplayMeta.BillboardConstraints billboard) {
        meta.setBillboardConstraints(billboard);
    }

    public void setBillboard(@NotNull Display.Billboard billboard) {
        meta.setBillboardConstraints(DisplayMeta.BillboardConstraints.valueOf(billboard.name()));
    }

    public void setShadowed(boolean shadowed) {
        meta.setShadow(shadowed);
    }

    public void setSeeThrough(boolean seeThrough) {
        meta.setSeeThrough(seeThrough);
    }

    public void setBackgroundColor(int color) {
        meta.setBackgroundColor(color);
    }

    public void setBackgroundColor(@NotNull Color color) {
        meta.setBackgroundColor(color.asRGB());
    }

    public void setInvisibleBackground() {
        setBackgroundColor(Color.BLACK.setAlpha(0).asRGB());
    }

    public void setTransformation(@NotNull Vector3f vector3f) {
        meta.setTranslation(vector3f);
    }

    public void setYOffset(float offset) {
        this.setTransformation(new Vector3f(0, offset, 0));
    }

    public void setViewRange(float range) {
        meta.setViewRange(range);
    }

    public void showToPlayer(@NotNull Player player) {
        if (blocked.contains(player.getUniqueId())) {
            return;
        }
        final boolean result = entity.addViewer(player.getUniqueId());
        if(!result) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            plugin.getPacketManager().sendPassengersPacket(player, this);
        }, 3); //min is 3
    }

    public void hideFromPlayer(@NotNull Player player) {
        if (blocked.contains(player.getUniqueId())) {
            return;
        }
        boolean result = entity.getViewers().contains(player.getUniqueId());
        entity.removeViewer(player.getUniqueId());

        plugin.getPacketManager().removePassenger(player, entity.getEntityId());
    }

    public void hideFromPlayerSilenty(@NotNull Player player) {
        if (blocked.contains(player.getUniqueId())) {
            return;
        }
        entity.removeViewerSilently(player.getUniqueId());
    }

    public void showFromPlayerSilenty(@NotNull Player player) {
        if (blocked.contains(player.getUniqueId())) {
            return;
        }
        entity.addViewerSilently(player.getUniqueId());
    }

    public boolean canPlayerSee(@NotNull Player player) {
        return entity.getViewers().contains(player.getUniqueId());
    }

    public void spawn(@NotNull Player player) {
        entity.spawn(SpigotConversionUtil.fromBukkitLocation(player.getLocation()));
    }

    @NotNull
    public UUID getUniqueId() {
        return entity.getUuid();
    }

    public void refresh() {
        fixViewers();
        entity.refresh();
    }

    private void fixViewers() {
        entity.getViewers().stream().filter(u -> Bukkit.getPlayer(u) == null).forEach(entity::removeViewerSilently);
    }

    public void remove() {
        entity.remove();
        plugin.getPacketManager().removePassenger(entity.getEntityId());
    }

    public void handleQuit(@NotNull Player player) {
        entity.removeViewerSilently(player.getUniqueId());
        plugin.getPacketManager().removePassenger(player, entity.getEntityId());
    }

    public void setTextOpacity(byte b) {
        meta.setTextOpacity(b);
    }

    public Set<Player> findNearbyPlayers() {
        final float viewDistance = plugin.getConfigManager().getSettings().getViewDistance() * 160;
        return owner.getNearbyEntities(viewDistance, viewDistance, viewDistance).stream()
                .filter(e -> e instanceof Player)
                .map(e -> (Player) e)
                .collect(Collectors.toSet());
    }
}
