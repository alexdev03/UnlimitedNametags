package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3f;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Getter
public class PacketNameTag {

    private final UnlimitedNameTags plugin;
    private final WrapperEntity entity;
    private final TextDisplayMeta meta;
    private final Player owner;
    private final Set<UUID> blocked;
    @NotNull
    private List<String> lines;
    @NotNull
    private Map<UUID, Component> relationalCache;
    @Nullable
    private Component lastText;
    private long lastUpdate;
    @Setter
    private boolean visible;
    @Setter
    private Settings.NameTag nameTag;
    private float scale;
    private float offset;
    private float increasedOffset;

    public PacketNameTag(@NotNull UnlimitedNameTags plugin, @NotNull Player owner, @NotNull Settings.NameTag nameTag) {
        this.plugin = plugin;
        this.owner = owner;
        this.lines = Lists.newCopyOnWriteArrayList();
        this.relationalCache = Maps.newConcurrentMap();
        final int randomId = plugin.getPacketManager().getEntityIndex();
        this.entity = new WrapperEntity(randomId, UUID.randomUUID(), EntityTypes.TEXT_DISPLAY);
        this.meta = (TextDisplayMeta) entity.getEntityMeta();
        this.blocked = Sets.newConcurrentHashSet();
        this.meta.setLineWidth(1000);
        this.meta.setNotifyAboutChanges(false);
        this.lastUpdate = System.currentTimeMillis();
        this.nameTag = nameTag;
        this.scale = plugin.getNametagManager().getScale(owner);
    }

    public boolean text(@NotNull Component text, @NotNull List<String> lines) {
        fixViewers();
        this.lines.clear();
        this.lines.addAll(lines);
        if (lastText != null && lastText.equals(text) &&
                nameTag.linesGroups().stream().noneMatch(l -> l.lines().stream().anyMatch(c -> c.contains("%rel_")))) {
            return false;
        }
        lastText = text;
        meta.setText(text);
        lastUpdate = System.currentTimeMillis();
        return true;
    }

    public boolean checkScale() {
        final AttributeInstance attribute = owner.getAttribute(Attribute.GENERIC_SCALE);
        if (attribute == null) {
            if (scale != 1.0F) {
                setScale(1.0F);
                return true;
            }
            return false;
        }
        final double playerScale = attribute.getValue();
        final double diff = Math.abs(playerScale - scale);
        if (diff <= 0.01 && diff >= 0) {
            return false;
        }

        setScale((float) playerScale);
        return true;
    }

    private void setScale(float scale) {
        this.scale = scale;
        this.increasedOffset = scale > 1 ? scale / 5 : 0;
        updateYOOffset();
        meta.setScale(new Vector3f(scale, scale, scale));
    }

    public void setBillboard(@NotNull Display.Billboard billboard) {
        meta.setBillboardConstraints(AbstractDisplayMeta.BillboardConstraints.valueOf(billboard.name()));
    }

    public void setBillboard(@NotNull AbstractDisplayMeta.BillboardConstraints billboard) {
        meta.setBillboardConstraints(billboard);
    }

    public void setShadowed(boolean shadowed) {
        meta.setShadow(shadowed);
    }

    public void setSeeThrough(boolean seeThrough) {
        meta.setSeeThrough(seeThrough);
    }

    public void setBackgroundColor(@NotNull Color color) {
        meta.setBackgroundColor(color.asARGB());
    }

    public void setTransformation(@NotNull Vector3f vector3f) {
        meta.setTranslation(vector3f);
    }

    public void setYOffset(float offset) {
        this.setTransformation(new Vector3f(0, offset + increasedOffset, 0));
        this.offset = offset;
    }

    public void resetOffset(float offset) {
        this.setTransformation(new Vector3f(0, offset + increasedOffset, 0));
        this.offset = offset;
    }

    public void updateYOOffset() {
        this.setTransformation(new Vector3f(0, offset + increasedOffset, 0));
    }

    public void setViewRange(float range) {
        meta.setViewRange(range);
    }

    public void showToPlayer(@NotNull Player player) {
        if (!visible) {
            return;
        }
        if (blocked.contains(player.getUniqueId())) {
            return;
        }

        if (!player.canSee(owner)) {
            return;
        }

        final boolean same = player.getUniqueId().equals(owner.getUniqueId());
        if (!same && !player.hasPermission("unt.shownametags")) {
            return;
        }

        if (same && !player.hasPermission("unt.showownnametag")) {
            return;
        }

        if (plugin.getNametagManager().isHiddenOtherNametags(player)) {
            return;
        }

        if (plugin.getConfigManager().getSettings().isShowWhileLooking() && !plugin.getNametagManager().isPlayerPointingAt(player, owner)) {
            return;
        }

        setPosition();
        entity.addViewer(player.getUniqueId());

        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            sendPassengersPacket(player);
        }, 2);
    }

    public void sendPassengersPacket(@NotNull Player player) {
        plugin.getPacketManager().sendPassengersPacket(player, this);
    }

    public void sendPassengerPacketToViewers() {
        if (!visible) {
            return;
        }
        entity.getViewers().forEach(u -> {
            final Player player = Bukkit.getPlayer(u);
            if (player != null) {
                sendPassengersPacket(player);
            }
        });
    }

    @SneakyThrows
    private void setPosition() {
        final Location location = owner.getLocation().clone();
        location.setPitch(0);
        location.setYaw(0);
        location.setY(location.getY() + (1.8) * scale);
        entity.setLocation(SpigotConversionUtil.fromBukkitLocation(location));
    }

    public void hideFromPlayer(@NotNull Player player) {
        if (blocked.contains(player.getUniqueId())) {
            return;
        }
        entity.removeViewer(player.getUniqueId());
        relationalCache.remove(player.getUniqueId());

        plugin.getPacketManager().removePassenger(player, entity.getEntityId());
    }

    public void clearViewers() {
        entity.getViewers().forEach(u -> {
            final Player player = Bukkit.getPlayer(u);
            if (player != null) {
                hideFromPlayer(player);
            }
        });
    }

    public void showToPlayers(Set<Player> players) {
        players.forEach(this::showToPlayer);
    }

    public void hideFromPlayerSilently(@NotNull Player player) {
        if (blocked.contains(player.getUniqueId())) {
            return;
        }
        entity.removeViewerSilently(player.getUniqueId());
        relationalCache.remove(player.getUniqueId());
    }

    public boolean canPlayerSee(@NotNull Player player) {
        return entity.getViewers().contains(player.getUniqueId());
    }

    public void spawn(@NotNull Player player) {
        this.visible = true;
        entity.spawn(SpigotConversionUtil.fromBukkitLocation(player.getLocation()));
    }

    public void refresh() {
        fixViewers();
        entity.refresh();
    }

    public void refreshForPlayer(@NotNull Player player) {
        final var packet = meta.createPacket();
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    private void fixViewers() {
        entity.getViewers().stream().filter(u -> {
            final Player player = Bukkit.getPlayer(u);
            if (player == null) {
                return true;
            }
            final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            return user == null || user.getChannel() == null;
        }).forEach(entity::removeViewerSilently);
    }

    public void remove() {
        entity.remove();
        plugin.getPacketManager().removePassenger(entity.getEntityId());
        relationalCache.clear();
    }

    public void handleQuit(@NotNull Player player) {
        entity.removeViewerSilently(player.getUniqueId());
        plugin.getPacketManager().removePassenger(player, entity.getEntityId());
    }

    public void setTextOpacity(byte b) {
        meta.setTextOpacity(b);
    }

    public void hideForOwner() {
        hideFromPlayer(owner);
        blocked.add(owner.getUniqueId());
    }

    public void showForOwner() {
        blocked.remove(owner.getUniqueId());
        showToPlayer(owner);
    }

    @NotNull
    public Map<String, String> properties() {
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("text", MiniMessage.miniMessage().serialize(meta.getText()));
        properties.put("billboard", meta.getBillboardConstraints().name());
        properties.put("shadowed", String.valueOf(meta.isShadow()));
        properties.put("seeThrough", String.valueOf(meta.isSeeThrough()));
        properties.put("backgroundColor", String.valueOf(meta.getBackgroundColor()));
        properties.put("transformation", meta.getTranslation().toString());
        properties.put("yOffset", String.valueOf(offset));
        properties.put("scale", String.valueOf(meta.getScale()));
        properties.put("increasedOffset", String.valueOf(increasedOffset));
        properties.put("viewRange", String.valueOf(meta.getViewRange()));
        return properties;
    }

}
