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
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

@Getter
public class PacketNameTag {

    private final UnlimitedNameTags plugin;
    private final WrapperPerPlayerEntity perPlayerEntity;
    private final int entityId;
    private final UUID entityIdUuid;
    private final Player owner;
    private final Set<UUID> blocked;
    @NotNull
    private final Map<UUID, Component> relationalCache;
    private final Map<UUID, Component> calculatedTextCache = Maps.newConcurrentMap();
    private final Map<UUID, Component> forcedRelationalText = Maps.newConcurrentMap();
    private volatile Component forcedGlobalText = null;
    private long lastUpdate;
    @Setter
    private boolean visible;
    @Setter
    private Settings.NameTag nameTag;
    private float scale;
    private float offset;
    private float increasedOffset;
    private volatile boolean removed;
    @Setter
    private boolean sneaking;

    public PacketNameTag(@NotNull UnlimitedNameTags plugin, @NotNull Player owner, @NotNull Settings.NameTag nameTag) {
        this.plugin = plugin;
        this.owner = owner;
        this.relationalCache = Maps.newConcurrentMap();
        this.entityId = plugin.getPacketManager().getEntityIndex();
        this.entityIdUuid = UUID.randomUUID();
        this.perPlayerEntity = new WrapperPerPlayerEntity(this.getBaseSupplier());
        this.blocked = Sets.newConcurrentHashSet();
        this.lastUpdate = System.currentTimeMillis();
        this.nameTag = nameTag;
        this.scale = plugin.getNametagManager().getScale(owner);
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
        if (removed) {
            return false;
        }

        calculatedTextCache.put(player.getUniqueId(), text);

        if (forcedRelationalText.containsKey(player.getUniqueId()) || forcedGlobalText != null) {
            return false;
        }

        return applyText(player, text);
    }

    private boolean applyText(@NotNull Player player, @NotNull Component text) {
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

    public void modify(@Nullable User user, @NotNull Consumer<TextDisplayMeta> consumer) {
        if (removed) {
            return;
        }

        if (user == null) {
            return;
        }

        perPlayerEntity.modify(user, e -> {
            final TextDisplayMeta meta = (TextDisplayMeta) e.getEntityMeta();
            consumer.accept(meta);
        });
    }

    public void modifyOwner(@NotNull Consumer<TextDisplayMeta> consumer) {
        if (removed) {
            return;
        }

        final User owner = PacketEvents.getAPI().getPlayerManager().getUser(this.owner);
        if (owner == null) {
            return;
        }

        modify(owner, consumer);
    }

    public void modifyOwnerEntity(@NotNull Consumer<WrapperEntity> consumer) {
        if (removed) {
            return;
        }

        final User owner = PacketEvents.getAPI().getPlayerManager().getUser(this.owner);
        if (owner == null) {
            return;
        }

        modifyEntity(owner, consumer);
    }

    public void modify(@NotNull Consumer<TextDisplayMeta> consumer) {
        if (removed) {
            return;
        }

        perPlayerEntity.execute(e -> {
            final TextDisplayMeta meta = (TextDisplayMeta) e.getEntityMeta();
            consumer.accept(meta);
        });
    }

    public void modifyEntity(@Nullable User user, @NotNull Consumer<WrapperEntity> consumer) {
        if (removed) {
            return;
        }

        perPlayerEntity.modify(user, consumer);
    }

    public void modifyEntity(@NotNull Consumer<WrapperEntity> consumer) {
        if (removed) {
            return;
        }

        perPlayerEntity.execute(consumer);
    }

    public float getDefaultScale() {
        return nameTag.scale();
    }

    public boolean checkScale() {
        final AttributeInstance attribute = owner.getAttribute(plugin.getNametagManager().getScaleAttribute());
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
        if (!isEligibleToShow(player)) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger()
                        .info("Player " + player.getName() + " is not eligible to show nametag for " + owner.getName());
            }
            return;
        }

        if (!player.getUniqueId().equals(owner.getUniqueId())) {
            WrapperEntity entity = perPlayerEntity.getEntityOf(getUser(player));
            if (entity != null) {
                applyOwnerData(entity);
            }
        }

        spawn(player);

        if (isOwner(player) && plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
            setOwnerPosition();
        } else {
            setPosition();
        }

        perPlayerEntity.addViewer(getUser(player));

        updateViewer(player.getUniqueId());

        plugin.getTaskScheduler().runTaskLaterAsynchronously(() -> {
            final User user = getUser(player);
            if (user == null) {
                return;
            }

            sendPassengersPacket(user);
        }, 1);
    }

    private boolean isEligibleToShow(@NotNull Player player) {
        if (!visible) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger().info("Nametag is not visible for " + owner.getName());
            }
            return false;
        }

        if (plugin.getHook(ViaVersionHook.class).map(h -> h.hasNotTextDisplays(player)).orElse(false)) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger()
                        .info("Player " + player.getName() + " is on a version that does not support text displays.");
            }
            return false;
        }

        if (blocked.contains(player.getUniqueId())) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger()
                        .info("Player " + player.getName() + " is blocked from seeing nametag of " + owner.getName());
            }
            return false;
        }

        if (!player.canSee(owner)) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger().info("Player " + player.getName() + " cannot see owner " + owner.getName());
            }
            return false;
        }

        if (player.getWorld() != owner.getWorld()) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger()
                        .info("Player " + player.getName() + " is in a different world than owner " + owner.getName());
            }
            return false;
        }

        if (isPlayerChannelNotValid(player) || isPlayerChannelNotValid(owner)) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger().info("Player " + player.getName() + " or owner " + owner.getName()
                        + " has an invalid channel/user.");
            }
            return false;
        }

        if (!hasRequiredPermissions(player)) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger().info("Player " + player.getName()
                        + " does not have required permissions to see nametag of " + owner.getName());
            }
            return false;
        }

        if (plugin.getNametagManager().isHiddenOtherNametags(player)) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger().info("Player " + player.getName() + " has other nametags hidden.");
            }
            return false;
        }

        if (plugin.getConfigManager().getSettings().isShowWhileLooking() &&
                !plugin.getNametagManager().isPlayerPointingAt(player, owner)) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger().info("Player " + player.getName() + " is not looking at owner " + owner.getName());
            }
            return false;
        }

        if (plugin.getConfigManager().getSettings().isShowCurrentNameTag()
                && player.getUniqueId() == owner.getUniqueId()) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger()
                        .info("Player " + player.getName() + " is the owner and show current nametag is enabled.");
            }
            return true;
        }

        if (plugin.getNametagManager().isDebug() && !getViewers().contains(player.getUniqueId())) {
            plugin.getLogger().info("Player " + player.getName() + " is eligible to see nametag of " + owner.getName());
        }
        return !getViewers().contains(player.getUniqueId());
    }

    private boolean isPlayerChannelNotValid(@NotNull Player player) {
        return PacketEvents.getAPI().getPlayerManager().getChannel(player) == null ||
                PacketEvents.getAPI().getPlayerManager().getUser(player) == null;
    }

    private User getUser(@NotNull Player player) {
        return PacketEvents.getAPI().getPlayerManager().getUser(player);
    }

    private boolean isOwner(@NotNull Player player) {
        return player.getUniqueId().equals(owner.getUniqueId());
    }

    private boolean hasRequiredPermissions(@NotNull Player player) {
        final boolean isOwner = isOwner(player);
        if (!isOwner && !player.hasPermission("unt.shownametags")) {
            return false;
        }

        return !isOwner || player.hasPermission("unt.showownnametag");
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
            final User ownerUser = getUser(owner);
            if (ownerUser == null) {
                return;
            }
            sendPassengersPacket(ownerUser);
        }, 1);
    }

    public void sendPassengersPacket(@NotNull User player) {
        if (removed) {
            return;
        }
        plugin.getPacketManager().sendPassengersPacket(player, this);
    }

    public void sendPassengerPacketToViewers() {
        if (!visible) {
            return;
        }

        getViewers().forEach(u -> {
            final Player player = plugin.getPlayerListener().getPlayer(u);
            if (player == null) {
                return;
            }
            final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user != null) {
                sendPassengersPacket(user);
            }
        });
    }

    private void setPosition() {
        final Location location = getOffsetLocation();
        modifyEntity(meta -> meta.setLocation(SpigotConversionUtil.fromBukkitLocation(location)));
    }

    private void setOwnerPosition() {
        final Location location = getOffsetLocation(); // .add(0, 0.25, 0)
        modifyOwnerEntity(meta -> meta.setLocation(SpigotConversionUtil.fromBukkitLocation(location)));
    }

    public Location getOffsetLocation() {
        final Location location = owner.getLocation();
        location.setPitch(0);
        location.setYaw(-180);
        location.setY(location.getY() + (1.8) * scale);
        return location;
    }

    public void hideFromPlayer(@NotNull Player player) {
        if (blocked.contains(player.getUniqueId())) {
            return;
        }
        final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            perPlayerEntity.getEntities().remove(player.getUniqueId());
            return;
        }
        final WrapperEntity wrapperEntity = perPlayerEntity.getEntityOf(user);
        if (wrapperEntity != null) {
            wrapperEntity.removeViewer(user);
        }
        if (!player.getUniqueId().equals(owner.getUniqueId())) {
            perPlayerEntity.getEntities().remove(user.getUUID());

        }
        relationalCache.remove(player.getUniqueId());
        calculatedTextCache.remove(player.getUniqueId());

        plugin.getPacketManager().removePassenger(player, entityId);
    }

    public void clearViewers() {
        getViewers().forEach(u -> {
            final Player player = plugin.getPlayerListener().getPlayer(u);
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
        perPlayerEntity.getEntities().remove(player.getUniqueId());
        relationalCache.remove(player.getUniqueId());
        calculatedTextCache.remove(player.getUniqueId());
    }

    public boolean canPlayerSee(@NotNull Player player) {
        return getViewers().contains(player.getUniqueId());
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
        perPlayerEntity.getEntities().forEach((u, e) -> {
            if (blocked.contains(u)) {
                return;
            }

            e.refresh();
        });
    }

    public void refreshForPlayer(@NotNull Player player) {
        final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            return;
        }

        if (blocked.contains(player.getUniqueId())) {
            return;
        }

        perPlayerEntity.getEntityOf(user).refresh();
    }

    public void remove() {
        removed = true;
        perPlayerEntity.getEntities().keySet().forEach(u -> {
            final Player player = plugin.getPlayerListener().getPlayer(u);
            if (player == null) {
                return;
            }
            final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            if (user != null && user.getChannel() != null) {
                perPlayerEntity.removeViewer(user);
            }
        });

        perPlayerEntity.getEntities().values().forEach(WrapperEntity::remove);
        perPlayerEntity.getEntities().clear();

        plugin.getPacketManager().removePassenger(entityId);
        relationalCache.clear();
        calculatedTextCache.clear();
        forcedRelationalText.clear();
        forcedGlobalText = null;
    }

    public void handleQuit(@NotNull Player player) {
        perPlayerEntity.getEntities().remove(player.getUniqueId());
        plugin.getPacketManager().removePassenger(player, entityId);
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

    private void applyOwnerData(@NotNull WrapperEntity wrapper) {
        final User ownerUser = PacketEvents.getAPI().getPlayerManager().getUser(owner);
        final Metadata metadata = wrapper.getEntityMeta().getMetadata();
        final Optional<EntityData<?>> component = wrapper.getEntityMeta().entityData()
                .stream()
                .filter(e -> e.getType() == EntityDataTypes.ADV_COMPONENT)
                .findFirst();
        final Metadata ownerMetadata = perPlayerEntity.getEntityOf(ownerUser).getEntityMeta().getMetadata();
        metadata.copyFrom(ownerMetadata);
        component.ifPresent(
                entityData -> ((TextDisplayMeta) wrapper.getEntityMeta()).setText((Component) entityData.getValue()));
        metadata.setNotifyAboutChanges(false);
    }

    @NotNull
    public Map<String, String> properties() {
        final Map<String, String> properties = new LinkedHashMap<>();
        final TextDisplayMeta meta = (TextDisplayMeta) this.perPlayerEntity
                .getEntityOf(PacketEvents.getAPI().getPlayerManager().getUser(owner)).getEntityMeta();
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

    public void setForcedNameTag(@NotNull Component component) {
        this.forcedGlobalText = component;
        updateAllViewers();
    }

    public void setForcedNameTag(@NotNull UUID viewer, @NotNull Component component) {
        this.forcedRelationalText.put(viewer, component);
        updateViewer(viewer);
    }

    public void clearForcedNameTag() {
        this.forcedGlobalText = null;
        updateAllViewers();
    }

    public void clearForcedNameTag(@NotNull UUID viewer) {
        this.forcedRelationalText.remove(viewer);
        updateViewer(viewer);
    }

    private void updateAllViewers() {
        for (final UUID uuid : getViewers()) {
            updateViewer(uuid);
        }
    }

    private void updateViewer(@NotNull UUID uuid) {
        final Player player = plugin.getPlayerListener().getPlayer(uuid);
        if (player == null)
            return;

        Component textToUse = calculatedTextCache.get(uuid);

        if (forcedRelationalText.containsKey(uuid)) {
            textToUse = forcedRelationalText.get(uuid);
        } else if (forcedGlobalText != null) {
            textToUse = forcedGlobalText;
        }

        if (textToUse != null) {
            if (applyText(player, textToUse)) {
                refreshForPlayer(player);
            }
        }
    }

    /**
     * Returns an unmodifiable set containing the viewers of the nametag.
     *
     * @return the viewers of the nametag
     */
    public Set<UUID> getViewers() {
        return Collections.unmodifiableSet(perPlayerEntity.getEntities().keySet());
    }
}