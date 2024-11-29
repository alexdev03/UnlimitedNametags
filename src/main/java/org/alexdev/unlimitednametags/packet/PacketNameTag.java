package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3f;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import me.tofaa.entitylib.meta.Metadata;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import me.tofaa.entitylib.wrapper.WrapperPerPlayerEntity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.hook.ViaVersionHook;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

@Getter
public class PacketNameTag {

    private final UnlimitedNameTags plugin;
    private final WrapperPerPlayerEntity perPlayerEntity;
    private final Set<UUID> viewers;
    private final int entityId;
    private final UUID entityIdUuid;
    private final Player owner;
    private final Set<UUID> blocked;
    @NotNull
    private final Map<UUID, Component> relationalCache;
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
        this.relationalCache = Maps.newConcurrentMap();
        this.entityId = plugin.getPacketManager().getEntityIndex();
        this.entityIdUuid = UUID.randomUUID();
        this.perPlayerEntity = new WrapperPerPlayerEntity(this.getBaseSupplier());
        this.viewers = Sets.newConcurrentHashSet();
//        this.entity = new WrapperEntity(randomId, UUID.randomUUID(), EntityTypes.TEXT_DISPLAY);
//        this.meta = (TextDisplayMeta) entity.getEntityMeta();
        this.blocked = Sets.newConcurrentHashSet();
//        this.meta.setLineWidth(1000);
//        this.meta.setNotifyAboutChanges(false);
        this.lastUpdate = System.currentTimeMillis();
        this.nameTag = nameTag;
        this.scale = plugin.getNametagManager().getScale(owner) ;
        this.setScale(scale);
    }

    private Function<User, WrapperEntity> getBaseSupplier() {
        return user -> {
            final WrapperEntity wrapper = new WrapperEntity(entityId, entityIdUuid, EntityTypes.TEXT_DISPLAY);
            final TextDisplayMeta meta = (TextDisplayMeta) wrapper.getEntityMeta();
            meta.setLineWidth(1000);
            meta.setNotifyAboutChanges(false);
            return wrapper;
        };
    }

    public boolean text(@NotNull Player player, @NotNull Component text) {
        if (text.equals(relationalCache.get(player.getUniqueId()))) {
            return false;
        }

        relationalCache.put(player.getUniqueId(), text);
        final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            return false;
        }

        modify(user, meta -> meta.setText(text));
        lastUpdate = System.currentTimeMillis();
        return true;
    }

    public void modify(User user, Consumer<TextDisplayMeta> consumer) {
        perPlayerEntity.modify(user, e -> {
            final TextDisplayMeta meta = (TextDisplayMeta) e.getEntityMeta();
            consumer.accept(meta);
        });
    }

    public void modify(Consumer<TextDisplayMeta> consumer) {
        perPlayerEntity.execute(e -> {
            final TextDisplayMeta meta = (TextDisplayMeta) e.getEntityMeta();
            consumer.accept(meta);
        });
    }

    public void modifyEntity(User user, Consumer<WrapperEntity> consumer) {
        perPlayerEntity.modify(user, consumer);
    }

    public void modifyEntity(Consumer<WrapperEntity> consumer) {
        perPlayerEntity.execute(consumer);
    }

    public float getDefaultScale() {
        return nameTag.scale();
    }

    public boolean checkScale() {
        final AttributeInstance attribute = owner.getAttribute(Attribute.GENERIC_SCALE);
        if (attribute == null) {
            if (scale != getDefaultScale()) {
                setScale(getDefaultScale());
                return true;
            }
            return false;
        }
        final double playerScale = attribute.getValue() * getDefaultScale();
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
        modify(meta -> meta.setScale(new Vector3f(scale, scale, scale)));
    }

    public void setBillboard(@NotNull Display.Billboard billboard) {
        final AbstractDisplayMeta.BillboardConstraints billboardConstraints = AbstractDisplayMeta.BillboardConstraints.valueOf(billboard.name());
        modify(meta -> meta.setBillboardConstraints(billboardConstraints));
    }

    public void setBillboard(@NotNull AbstractDisplayMeta.BillboardConstraints billboard) {
        modify(meta -> meta.setBillboardConstraints(billboard));
    }

    public void setShadowed(boolean shadowed) {
        modify(meta -> meta.setShadow(shadowed));
    }

    public void setSeeThrough(boolean seeThrough) {
        modify(meta -> meta.setSeeThrough(seeThrough));
    }

    public void setBackgroundColor(@NotNull Color color) {
        modify(meta -> meta.setBackgroundColor(color.asARGB()));
    }

    public void setTransformation(@NotNull Vector3f vector3f) {
        modify(meta -> meta.setTranslation(vector3f));
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
        modify(meta -> meta.setViewRange(range));
    }

    public void showToPlayer(@NotNull Player player) {
        if (!visible) {
            return;
        }

        if (plugin.getHook(ViaVersionHook.class).map(h -> h.hasNotTextDisplays(player)).orElse(false)) {
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

        if (viewers.contains(player.getUniqueId())) {
            return;
        }

        if(!player.getUniqueId().equals(owner.getUniqueId())) {
            applyOwnerData(perPlayerEntity.getEntityOf(PacketEvents.getAPI().getPlayerManager().getUser(player)));
        }

        spawn(player);

        setPosition();

        final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            return;
        }
        viewers.add(user.getUUID());
        perPlayerEntity.addViewer(user);

        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            sendPassengersPacket(player);
        }, 1);
    }

    public void spawnForOwner() {
        this.visible = true;
        final User user = PacketEvents.getAPI().getPlayerManager().getUser(owner);
        if (user == null) {
            return;
        }
        modifyEntity(user, e -> {
            e.despawn();
            e.spawn(SpigotConversionUtil.fromBukkitLocation(getOffsetLocation()));
        });

        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            sendPassengersPacket(owner);
        }, 1);
    }

    public void sendPassengersPacket(@NotNull Player player) {
        plugin.getPacketManager().sendPassengersPacket(player, this);
    }

    public void sendPassengerPacketToViewers() {
        if (!visible) {
            return;
        }

        viewers.forEach(u -> {
            final Player player = Bukkit.getPlayer(u);
            if (player != null) {
                sendPassengersPacket(player);
            }
        });
    }

    private void setPosition() {
        final Location location = getOffsetLocation();
        modifyEntity(meta -> meta.setLocation(SpigotConversionUtil.fromBukkitLocation(location)));
    }

    public Location getOffsetLocation() {
        final Location location = owner.getLocation().clone();
        location.setPitch(0);
        location.setYaw(0);
        location.setY(location.getY() + (1.8) * scale);
        return location;
    }

    public void hideFromPlayer(@NotNull Player player) {
        if (blocked.contains(player.getUniqueId())) {
            return;
        }
        viewers.remove(player.getUniqueId());
        final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            return;
        }
        perPlayerEntity.removeViewer(user);
        if(!player.getUniqueId().equals(owner.getUniqueId())) {
            perPlayerEntity.getEntities().remove(user.getUUID());
        }
        relationalCache.remove(player.getUniqueId());

        plugin.getPacketManager().removePassenger(player, entityId);
    }

    public void clearViewers() {
        viewers.forEach(u -> {
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
        viewers.remove(player.getUniqueId());
        perPlayerEntity.getEntities().remove(player.getUniqueId());
        relationalCache.remove(player.getUniqueId());
    }

    public boolean canPlayerSee(@NotNull Player player) {
        return viewers.contains(player.getUniqueId());
    }

    public void spawn(@NotNull Player player) {
        this.visible = true;
        final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            return;
        }
        modifyEntity(user, e -> e.spawn(SpigotConversionUtil.fromBukkitLocation(getOffsetLocation())));
    }

    public void refresh() {
        perPlayerEntity.execute(WrapperEntity::refresh);
    }

    public void refreshForPlayer(@NotNull Player player) {
        final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            return;
        }
        perPlayerEntity.getEntityOf(user).refresh();
    }

    public void remove() {
        viewers.forEach(u -> {
            final Player player = Bukkit.getPlayer(u);
            if (player == null) {
                return;
            }
            final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user != null) {
                perPlayerEntity.removeViewer(user);
            }
        });
        viewers.clear();
        plugin.getPacketManager().removePassenger(entityId);
        relationalCache.clear();
    }

    public void handleQuit(@NotNull Player player) {
        viewers.remove(player.getUniqueId());
        clearCache(player.getUniqueId());
        plugin.getPacketManager().removePassenger(player, entityId);
    }

    @SneakyThrows
    private void clearCache(@NotNull UUID uuid) {
//        final Field mapField = WrapperPerPlayerEntity.class.getDeclaredField("entities");
//        mapField.setAccessible(true);
//        final Map<UUID, WrapperEntity> entities = (Map<UUID, WrapperEntity>) mapField.get(perPlayerEntity);
//        entities.remove(uuid);
        perPlayerEntity.getEntities().remove(uuid);
    }

    public void setTextOpacity(byte b) {
        modify(meta -> meta.setTextOpacity(b));
    }

    public void hideForOwner() {
        hideFromPlayer(owner);
        blocked.add(owner.getUniqueId());
    }

    public void showForOwner() {
        blocked.remove(owner.getUniqueId());
        showToPlayer(owner);
    }

    public Optional<TextDisplayMeta> getTextDisplayMeta(@NotNull Player player) {
        final User ownerUser = PacketEvents.getAPI().getPlayerManager().getUser(owner);
        if (ownerUser == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((TextDisplayMeta) perPlayerEntity.getEntityOf(ownerUser).getEntityMeta());
    }

    private void applyOwnerData(@NotNull WrapperEntity wrapper) {
        final User ownerUser = PacketEvents.getAPI().getPlayerManager().getUser(owner);
        final Metadata metadata = wrapper.getEntityMeta().getMetadata();
        final Optional<EntityData> component = wrapper.getEntityMeta().entityData()
                .stream()
                .filter(e -> e.getType() == EntityDataTypes.ADV_COMPONENT)
                .findFirst();
        final Metadata ownerMetadata = perPlayerEntity.getEntityOf(ownerUser).getEntityMeta().getMetadata();
        metadata.copyFrom(ownerMetadata);
        component.ifPresent(entityData -> ((TextDisplayMeta) wrapper.getEntityMeta()).setText((Component) entityData.getValue()));
        metadata.setNotifyAboutChanges(false);
    }

    @NotNull
    public Map<String, String> properties() {
        final Map<String, String> properties = new LinkedHashMap<>();
        final TextDisplayMeta meta = (TextDisplayMeta) this.perPlayerEntity.getEntityOf(PacketEvents.getAPI().getPlayerManager().getUser(owner)).getEntityMeta();
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
