package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import com.google.common.collect.Sets;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import lombok.Getter;
import lombok.Setter;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import me.tofaa.entitylib.wrapper.WrapperPerPlayerEntity;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.api.NametagAnimationTarget;
import org.alexdev.unlimitednametags.api.UntNametagDisplay;
import org.alexdev.unlimitednametags.config.DisplayAnimation;
import org.alexdev.unlimitednametags.config.NametagDisplayType;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.hook.ViaVersionHook;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Packet-backed display entity mounted on a player. Use {@link #create} for the correct implementation
 * ({@link TextPacketNameTag}, {@link ItemPacketNameTag}, {@link BlockPacketNameTag}).
 * <p>
 * Text-specific state (per-viewer lines, forced nametag, through-wall opacity) lives in {@link TextNametagSupport}
 * and is exposed only when {@link #textNametag()} is non-null.
 */
@Getter
public abstract class PacketNameTag implements UntNametagDisplay, NametagAnimationTarget {

    public static @NotNull PacketNameTag create(
            @NotNull UnlimitedNameTags plugin,
            @NotNull Player owner,
            @NotNull Settings.DisplayGroup displayGroup) {
        return switch (displayGroup.resolvedDisplayType()) {
            case TEXT -> new TextPacketNameTag(plugin, owner, displayGroup);
            case ITEM -> new ItemPacketNameTag(plugin, owner, displayGroup);
            case BLOCK -> new BlockPacketNameTag(plugin, owner, displayGroup);
        };
    }

    private final UnlimitedNameTags plugin;
    private final WrapperPerPlayerEntity perPlayerEntity;
    private final int entityId;
    private final UUID entityIdUuid;
    private final Player owner;
    private final Set<UUID> blocked;
    private long lastUpdate;
    @Setter
    private boolean visible;
    private Settings.DisplayGroup displayGroup;
    private float scale;
    private float offset;
    private float increasedOffset;
    private volatile boolean removed;
    @Setter
    private boolean sneaking;

    private float baseTranslationY;
    /**
     * Extra vertical offset (in blocks) added to compensate tall cosmetic helmets.
     * See {@link org.alexdev.unlimitednametags.hook.hat.HatHook} and issue #49: previously the plugin injected
     * empty newlines into the text component which stretched the background; now we push the display up instead.
     */
    private float helmetExtraOffset;
    private float animationTx;
    private float animationTy;
    private float animationTz;
    private Quaternion4f animationLeftQuat = new Quaternion4f(0f, 0f, 0f, 1f);
    private float animationScaleMul = 1f;
    private long animationEpochMs = System.currentTimeMillis();
    /** True while {@link DisplayAnimation#cullBeyondBlocks()} distance culling is pausing the pose. */
    private boolean animationCullDistancePaused;

    /** DVD bounce state; package-private for {@link DisplayAnimationComputer}. */
    boolean animDvdInitialized;
    float animDvdX;
    float animDvdZ;
    float animDvdVx;
    float animDvdVz;

    private final NametagDisplayType createdDisplayType;

    protected PacketNameTag(@NotNull UnlimitedNameTags plugin, @NotNull Player owner, @NotNull Settings.DisplayGroup displayGroup) {
        this.plugin = plugin;
        this.owner = owner;
        this.entityId = plugin.getPacketManager().getEntityIndex();
        this.entityIdUuid = UUID.randomUUID();
        this.displayGroup = displayGroup;
        this.createdDisplayType = displayGroup.resolvedDisplayType();
        this.perPlayerEntity = new WrapperPerPlayerEntity(buildBaseSupplier());
        this.blocked = Sets.newConcurrentHashSet();
        this.lastUpdate = System.currentTimeMillis();
        this.animationLeftQuat = new Quaternion4f(0f, 0f, 0f, 1f);
        this.animationEpochMs = System.currentTimeMillis();
        setScale(plugin.getNametagManager().getScaledDisplayScale(owner, displayGroup.effectiveScale()));
    }

    public void setDisplayGroup(@NotNull Settings.DisplayGroup displayGroup) {
        this.displayGroup = displayGroup;
        resetDisplayAnimationState();
    }

    protected abstract @NotNull Function<User, WrapperEntity> buildBaseSupplier();

    /**
     * After {@code metadata.copyFrom(owner)}, fix per-type fields (text component, item stack, block id).
     */
    protected abstract void applyViewerOwnerMetadata(@NotNull WrapperEntity viewerEntity, @NotNull WrapperEntity ownerEntity);

    protected abstract void appendTypeProperties(@NotNull Map<String, String> properties, @NotNull AbstractDisplayMeta meta);

    /**
     * Refreshes item/block from config; no-op for text.
     */
    public void syncVisualFromGroup(@NotNull Settings.DisplayGroup group) {
    }

    public boolean isTextDisplay() {
        return false;
    }

    public boolean matchesDisplayGroupEntityType(@NotNull Settings.DisplayGroup group) {
        return createdDisplayType == group.resolvedDisplayType();
    }

    protected void touchLastUpdate() {
        lastUpdate = System.currentTimeMillis();
    }

    /**
     * Text-only behaviour; item/block returns {@code null}.
     */
    @Nullable
    protected TextNametagSupport textNametag() {
        return null;
    }

    public boolean text(@NotNull Player player, @NotNull Component text) {
        final TextNametagSupport t = textNametag();
        return t != null && t.text(player, text);
    }

    public void modifyTextForViewer(@Nullable User user, @NotNull Consumer<TextDisplayMeta> consumer) {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.modifyTextForViewer(user, consumer);
        }
    }

    public void modifyTextForOwner(@NotNull Consumer<TextDisplayMeta> consumer) {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.modifyTextForOwner(consumer);
        }
    }

    public void modifyTextAll(@NotNull Consumer<TextDisplayMeta> consumer) {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.modifyTextAll(consumer);
        }
    }

    public void modifyOwnerEntity(@NotNull Consumer<WrapperEntity> consumer) {
        if (removed) {
            return;
        }

        final User ownerUser = PacketEvents.getAPI().getPlayerManager().getUser(this.owner);
        if (ownerUser == null) {
            return;
        }

        modifyEntity(ownerUser, consumer);
    }

    protected void modifyAbstractAll(@NotNull Consumer<AbstractDisplayMeta> consumer) {
        if (removed) {
            return;
        }

        perPlayerEntity.execute(e -> consumer.accept((AbstractDisplayMeta) e.getEntityMeta()));
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
        return displayGroup.effectiveScale();
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
        recomputeBaseTranslationY();
        applyDisplayTransform();
    }

    @Override
    public void setBillboard(@NotNull AbstractDisplayMeta.BillboardConstraints billboard) {
        modifyAbstractAll(meta -> meta.setBillboardConstraints(billboard));
    }

    public void setShadowed(final boolean shadowed) {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.setShadowed(shadowed);
        }
    }

    public void setSeeThrough(final boolean seeThrough) {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.setSeeThrough(seeThrough);
        }
    }

    public void setBackgroundColor(@NotNull final org.bukkit.Color color) {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.setBackgroundColor(color);
        }
    }

    public void resetOffset(float offset) {
        this.offset = offset;
        recomputeBaseTranslationY();
        applyDisplayTransform();
    }

    public void updateYOOffset() {
        recomputeBaseTranslationY();
        applyDisplayTransform();
    }

    private void recomputeBaseTranslationY() {
        this.baseTranslationY = offset + increasedOffset + displayGroup.yOffset() + helmetExtraOffset;
    }

    /**
     * Updates the helmet-height compensation (in blocks). No-op if the value is unchanged.
     * Must be called on the refresh path after the owner's helmet changes; see {@link #recomputeBaseTranslationY}.
     */
    public void setHelmetExtraOffset(float extra) {
        if (Math.abs(this.helmetExtraOffset - extra) < 1e-4f) {
            return;
        }
        this.helmetExtraOffset = extra;
        recomputeBaseTranslationY();
        applyDisplayTransform();
    }

    /**
     * Applies base nametag layout (vertical stack + scale) plus optional {@link DisplayAnimation} offsets.
     */
    private void applyDisplayTransform() {
        if (removed) {
            return;
        }
        final float y = baseTranslationY + animationTy;
        final float tx = animationTx;
        final float tz = animationTz;
        final Quaternion4f lq = new Quaternion4f(
                animationLeftQuat.getX(), animationLeftQuat.getY(), animationLeftQuat.getZ(), animationLeftQuat.getW());
        final float sc = scale * animationScaleMul;
        modifyAbstractAll(meta -> {
            meta.setTranslation(new Vector3f(tx, y, tz));
            meta.setLeftRotation(lq);
            meta.setScale(new Vector3f(sc, sc, sc));
        });
        // EntityLib keeps notifyAboutChanges(false) on display meta; translation/rotation updates otherwise stay server-side only.
        refresh();
    }

    void clearAnimationPose() {
        animationTx = animationTy = animationTz = 0f;
        animationLeftQuat = new Quaternion4f(0f, 0f, 0f, 1f);
        animationScaleMul = 1f;
        animDvdInitialized = false;
        applyDisplayTransform();
    }

    void setAnimationPose(float tx, float ty, float tz, @NotNull Quaternion4f q, float scaleMul) {
        animationTx = tx;
        animationTy = ty;
        animationTz = tz;
        animationLeftQuat = new Quaternion4f(q.getX(), q.getY(), q.getZ(), q.getW());
        animationScaleMul = scaleMul;
        applyDisplayTransform();
    }

    @Override
    public int getNametagDisplayEntityId() {
        return entityId;
    }

    @Override
    public void setLocalPose(
            float translationX,
            float translationY,
            float translationZ,
            float quaternionX,
            float quaternionY,
            float quaternionZ,
            float quaternionW,
            float scaleMultiplier) {
        setAnimationPose(
                translationX,
                translationY,
                translationZ,
                new Quaternion4f(quaternionX, quaternionY, quaternionZ, quaternionW),
                scaleMultiplier);
    }

    @Override
    public void clearLocalPose() {
        clearAnimationPose();
    }

    void resetDisplayAnimationState() {
        animationEpochMs = System.currentTimeMillis();
        animationCullDistancePaused = false;
        clearAnimationPose();
    }

    public void tickDisplayAnimation(long monotonicTick) {
        if (removed) {
            return;
        }
        final DisplayAnimation anim = displayGroup.animation();
        if (anim == null || !anim.isAnimating()) {
            animationCullDistancePaused = false;
            if (hasActiveAnimationResidual()) {
                clearAnimationPose();
            }
            return;
        }
        final int interval = displayGroup.effectiveAnimationTickInterval(plugin.getConfigManager().getSettings());
        if (interval > 1 && monotonicTick % interval != 0) {
            return;
        }
        final double cullBlocks = anim.cullBeyondBlocks();
        if (cullBlocks > 0 && !hasViewerWithinAnimationCullDistanceSq(cullBlocks * cullBlocks)) {
            if (hasActiveAnimationResidual()) {
                clearAnimationPose();
            }
            animationCullDistancePaused = true;
            return;
        }
        if (animationCullDistancePaused) {
            animationEpochMs = System.currentTimeMillis();
            animationCullDistancePaused = false;
        }
        final double elapsed = (System.currentTimeMillis() - animationEpochMs) / 1000.0;
        DisplayAnimationComputer.apply(this, anim, elapsed);
    }

    /**
     * True if at least one nametag viewer exists in the owner's world within {@code maxDistSq} (blocks²).
     */
    private boolean hasViewerWithinAnimationCullDistanceSq(final double maxDistSq) {
        if (!owner.isOnline()) {
            return false;
        }
        final org.bukkit.World ownerWorld = owner.getWorld();
        final Location ownerLoc = owner.getLocation();
        for (final UUID vid : getViewers()) {
            Player p = plugin.getPlayerListener().getPlayer(vid);
            if (p == null) {
                p = Bukkit.getPlayer(vid);
            }
            if (p == null || !p.isOnline()) {
                continue;
            }
            if (p.getWorld() != ownerWorld) {
                continue;
            }
            if (ownerLoc.distanceSquared(p.getLocation()) <= maxDistSq) {
                return true;
            }
        }
        return false;
    }

    private boolean hasActiveAnimationResidual() {
        if (animDvdInitialized) {
            return true;
        }
        if (Math.abs(animationTx) > 1e-5f || Math.abs(animationTy) > 1e-5f || Math.abs(animationTz) > 1e-5f) {
            return true;
        }
        if (Math.abs(animationScaleMul - 1f) > 1e-4f) {
            return true;
        }
        return Math.abs(animationLeftQuat.getX()) > 1e-5f
                || Math.abs(animationLeftQuat.getY()) > 1e-5f
                || Math.abs(animationLeftQuat.getZ()) > 1e-5f
                || Math.abs(animationLeftQuat.getW() - 1f) > 1e-4f;
    }

    public void setViewRange(float range) {
        modifyAbstractAll(meta -> meta.setViewRange(range));
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

        if (isOwner(player) && plugin.getNametagManager().isEffectiveShowOwnNametag(owner)) {
            setOwnerPosition();
        } else {
            setPosition();
        }

        perPlayerEntity.addViewer(getUser(player));

        final TextNametagSupport tn = textNametag();
        if (tn != null) {
            tn.refreshViewerIfCached(player.getUniqueId());
        }

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
                        .info("Player " + player.getName() + " is on a version that does not support display entities (text/item/block).");
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

        if (!player.getUniqueId().equals(owner.getUniqueId())
                && plugin.getNametagManager().isHidingOwnNametagFromOthers(owner)) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger().info("Owner " + owner.getName() + " hides their nametag from others.");
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

        if (player.getUniqueId().equals(owner.getUniqueId())
                && plugin.getNametagManager().isEffectiveShowOwnNametag(owner)) {
            if (plugin.getNametagManager().isDebug()) {
                plugin.getLogger()
                        .info("Player " + player.getName() + " is the owner and their own nametag is shown.");
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
        plugin.getNametagManager().sendPassengersPacket(player, getOwner());
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
        final Location location = getOffsetLocation();
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
        final TextNametagSupport tn = textNametag();
        if (tn != null) {
            tn.onViewerRemoved(player.getUniqueId());
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
        final TextNametagSupport tn = textNametag();
        if (tn != null) {
            tn.onViewerRemoved(player.getUniqueId());
        }
        perPlayerEntity.getEntities().remove(player.getUniqueId());
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

    @Override
    public void refresh() {
        perPlayerEntity.getEntities().forEach((u, e) -> {
            if (blocked.contains(u)) {
                return;
            }

            e.refresh();
        });
    }

    @Override
    public void refreshForPlayer(@NotNull Player player) {
        final User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) {
            return;
        }

        if (blocked.contains(player.getUniqueId())) {
            return;
        }

        final WrapperEntity entity = perPlayerEntity.getEntityOf(user);
        if (entity != null) {
            entity.refresh();
        }
    }

    public void remove() {
        removed = true;
        final TextNametagSupport tn = textNametag();
        if (tn != null) {
            tn.dispose();
        }
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
    }

    public void handleQuit(@NotNull Player player) {
        final TextNametagSupport tn = textNametag();
        if (tn != null) {
            tn.onViewerRemoved(player.getUniqueId());
        }
        perPlayerEntity.getEntities().remove(player.getUniqueId());
        plugin.getPacketManager().removePassenger(player, entityId);
    }

    /**
     * Clears cached per-viewer text opacity / seeThrough when {@link Settings#isObscuredNametagThroughWalls()} is toggled off or on reload.
     */
    public void clearObscuredPresentationTracking() {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.clearObscuredPresentationTracking();
        }
    }

    /**
     * When {@code obscuredNametagThroughWalls} is enabled, updates per-viewer text opacity and seeThrough from line-of-sight (sync periodic task).
     */
    public void applyObscuredLineOfSightPresentation(
            final boolean featureEnabled,
            final byte sneakOpacityByte,
            final byte obscuredOpacityByte,
            final double maxDistanceSq,
            final boolean sneakEffective) {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.applyObscuredLineOfSightPresentation(featureEnabled, sneakOpacityByte, obscuredOpacityByte, maxDistanceSq,
                    sneakEffective);
        }
    }

    public void setTextOpacity(final byte b) {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.setTextOpacity(b);
        }
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
        if (ownerUser == null) {
            return;
        }
        final WrapperEntity ownerEntity = perPlayerEntity.getEntityOf(ownerUser);
        if (ownerEntity == null) {
            return;
        }
        final me.tofaa.entitylib.meta.Metadata metadata = wrapper.getEntityMeta().getMetadata();
        final me.tofaa.entitylib.meta.Metadata ownerMetadata = ownerEntity.getEntityMeta().getMetadata();
        metadata.copyFrom(ownerMetadata);
        applyViewerOwnerMetadata(wrapper, ownerEntity);
        metadata.setNotifyAboutChanges(false);
    }

    @NotNull
    public Map<String, String> properties() {
        final Map<String, String> properties = new LinkedHashMap<>();
        final User ownerUser = PacketEvents.getAPI().getPlayerManager().getUser(owner);
        if (ownerUser == null) {
            return properties;
        }
        final WrapperEntity entity = this.perPlayerEntity.getEntityOf(ownerUser);
        if (entity == null) {
            return properties;
        }
        final AbstractDisplayMeta meta = (AbstractDisplayMeta) entity.getEntityMeta();
        properties.put("displayType", createdDisplayType.name());
        properties.put("billboard", meta.getBillboardConstraints().name());
        properties.put("transformation", meta.getTranslation().toString());
        properties.put("yOffset", String.valueOf(offset));
        properties.put("displayGroupYOffset", String.valueOf(displayGroup.yOffset()));
        properties.put("scale", String.valueOf(meta.getScale()));
        properties.put("increasedOffset", String.valueOf(increasedOffset));
        properties.put("helmetExtraOffset", String.valueOf(helmetExtraOffset));
        properties.put("viewRange", String.valueOf(meta.getViewRange()));
        appendTypeProperties(properties, meta);
        return properties;
    }

    @Override
    public void setForcedNameTag(@NotNull final Component component) {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.setForcedGlobal(component);
        }
    }

    @Override
    public void setForcedNameTag(@NotNull final UUID viewer, @NotNull final Component component) {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.setForcedRelational(viewer, component);
        }
    }

    @Override
    public void clearForcedNameTag() {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.clearForcedGlobal();
        }
    }

    @Override
    public void clearForcedNameTag(@NotNull final UUID viewer) {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.clearForcedRelational(viewer);
        }
    }

    public Set<UUID> getViewers() {
        return Collections.unmodifiableSet(perPlayerEntity.getEntities().keySet());
    }
}
