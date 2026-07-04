package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.util.Quaternion4f;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.Setter;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import me.tofaa.entitylib.wrapper.WrapperPerPlayerEntity;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.config.DisplayAnimation;
import org.alexdev.unlimitednametags.config.GlowOverride;
import org.alexdev.unlimitednametags.config.NametagDisplayType;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.platform.NametagMaterialBridge;
import org.alexdev.unlimitednametags.platform.NametagPassengerSource;
import org.alexdev.unlimitednametags.platform.NametagPlatformBridge;
import org.alexdev.unlimitednametags.platform.NametagRuntime;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Packet-backed display entity mounted on a player ({@link TextPacketNameTag}, {@link ItemPacketNameTag}, {@link BlockPacketNameTag}).
 * <p>
 * Text-specific state (per-viewer lines, forced nametag, through-wall opacity) lives in {@link TextNametagSupport}
 * and is exposed only when {@link #textNametag()} is non-null.
 */
@Getter
public abstract class PacketNameTag implements AnimationPoseTarget, NametagPassengerSource {

    private final NametagRuntime runtime;
    private final NametagPlatformBridge platform;
    protected final NametagMaterialBridge materials;
    private final UUID ownerId;
    private final WrapperPerPlayerEntity perPlayerEntity;
    private final int entityId;
    private final UUID entityIdUuid;
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
    private boolean compactStackLayout;
    private float compactStackYOffset;
    private boolean compactStackHelmetOffset = true;
    /** Per-viewer base translation Y (before animation offsets) when compact stacking is relational. */
    private final ConcurrentHashMap<UUID, Float> perViewerStackBaseY = new ConcurrentHashMap<>();
    /**
     * Extra vertical offset (in blocks) added to compensate tall cosmetic helmets.
     * Set by the plugin from hat-hook integrations (issue #49): previously empty newlines were injected
     * into the text component which stretched the background; now the display is shifted up instead.
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

    @Nullable
    private GlowOverride glowOverride;
    private long glowEpochMs = System.currentTimeMillis();
    @Nullable
    private Integer lastAppliedGlowRgb;
    private boolean glowActiveOnEntity;

    /** DVD bounce state for {@link DisplayAnimationComputer}. */
    private boolean animDvdInitialized;
    private float animDvdX;
    private float animDvdZ;
    private float animDvdVx;
    private float animDvdVz;

    private final NametagDisplayType createdDisplayType;

    /** When true, metadata modifications accumulate without network flush until batch end. */
    private volatile boolean deferMetadataFlush;

    protected PacketNameTag(@NotNull NametagRuntime runtime, @NotNull NametagPlatformBridge platform,
            @NotNull NametagMaterialBridge materials, @NotNull UUID ownerId,
            @NotNull Settings.DisplayGroup displayGroup) {
        this.runtime = runtime;
        this.platform = platform;
        this.materials = materials;
        this.ownerId = ownerId;
        this.entityId = runtime.nextEntityId();
        this.entityIdUuid = UUID.randomUUID();
        this.displayGroup = displayGroup;
        this.createdDisplayType = displayGroup.resolvedDisplayType();
        this.perPlayerEntity = new WrapperPerPlayerEntity(buildBaseSupplier());
        this.blocked = Sets.newConcurrentHashSet();
        this.lastUpdate = System.currentTimeMillis();
        this.animationLeftQuat = new Quaternion4f(0f, 0f, 0f, 1f);
        this.animationEpochMs = System.currentTimeMillis();
        setScale(runtime.scaledDisplayScale(ownerId, displayGroup.effectiveScale()));
    }

    public void setDisplayGroup(@NotNull Settings.DisplayGroup displayGroup) {
        this.displayGroup = displayGroup;
        resetDisplayAnimationState();
        resetGlowAnimationState();
        applyGlowNow(0L);
    }

    public void setGlowOverride(@Nullable GlowOverride glowOverride) {
        this.glowOverride = glowOverride;
        resetGlowAnimationState();
        applyGlowNow(0L);
    }

    @Nullable
    public GlowOverride getGlowOverride() {
        return glowOverride;
    }

    @Nullable
    public GlowOverride effectiveGlow() {
        if (isTextDisplay()) {
            return null;
        }
        final GlowOverride raw = glowOverride != null ? glowOverride : displayGroup.glow();
        if (raw == null) {
            return null;
        }
        return raw.resolve(runtime.settings(), runtime::resolveGlowAnimation);
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
    TextNametagSupport textNametag() {
        return null;
    }

    public boolean text(@NotNull UUID viewerId, @NotNull Component text) {
        final TextNametagSupport t = textNametag();
        return t != null && t.text(viewerId, text);
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

        final User ownerUser = platform.resolveUser(ownerId);
        if (ownerUser == null) {
            return;
        }

        modifyEntity(ownerUser, consumer);
    }

    protected void modifyAbstractAll(@NotNull Consumer<AbstractDisplayMeta> consumer) {
        if (removed) {
            return;
        }

        perPlayerEntity.execute(e -> {
            if (e != null) {
                consumer.accept((AbstractDisplayMeta) e.getEntityMeta());
            }
        });
    }

    public void modifyEntity(@Nullable User user, @NotNull Consumer<WrapperEntity> consumer) {
        if (removed) {
            return;
        }

        final WrapperEntity entity = resolveEntity(user);
        if (entity == null) {
            return;
        }

        consumer.accept(entity);
    }

    public void modifyEntity(@NotNull Consumer<WrapperEntity> consumer) {
        if (removed) {
            return;
        }

        perPlayerEntity.execute(entity -> {
            if (entity != null) {
                consumer.accept(entity);
            }
        });
    }

    @Nullable
    private WrapperEntity resolveEntity(@Nullable final User user) {
        if (user == null || user.getUUID() == null) {
            return null;
        }
        return perPlayerEntity.getEntityOf(user);
    }

    public float getDefaultScale() {
        return displayGroup.effectiveScale();
    }

    public boolean checkScale() {
        final float resolved = platform.resolveDisplayScale(ownerId, getDefaultScale());
        final double diff = Math.abs(resolved - scale);
        if (diff <= 0.01 && diff >= 0) {
            return false;
        }

        setScale(resolved);
        return true;
    }

    private void setScale(float scale) {
        this.scale = scale;
        this.increasedOffset = scale > 1 ? scale / 5 : 0;
        recomputeBaseTranslationY();
        applyDisplayTransform();
    }

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

    public void setBackgroundColor(final int argb) {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.setBackgroundColor(argb);
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

    public void setCompactStackYOffset(float yOffset, boolean includeHelmetOffset) {
        perViewerStackBaseY.clear();
        this.compactStackLayout = true;
        this.compactStackYOffset = yOffset;
        this.compactStackHelmetOffset = includeHelmetOffset;
        recomputeBaseTranslationY();
        applyDisplayTransform();
    }

    public void clearCompactStackYOffset() {
        final boolean hadPerViewer = !perViewerStackBaseY.isEmpty();
        perViewerStackBaseY.clear();
        if (!compactStackLayout && compactStackHelmetOffset && !hadPerViewer) {
            return;
        }
        this.compactStackLayout = false;
        this.compactStackYOffset = 0f;
        this.compactStackHelmetOffset = true;
        recomputeBaseTranslationY();
        applyDisplayTransform();
    }

    public void setPerViewerStackBaseY(@NotNull Map<UUID, Float> baseYByViewer) {
        this.compactStackLayout = false;
        this.compactStackYOffset = 0f;
        this.compactStackHelmetOffset = true;
        perViewerStackBaseY.clear();
        perViewerStackBaseY.putAll(baseYByViewer);
        recomputeBaseTranslationY();
        applyDisplayTransform();
    }

    public void clearPerViewerStackLayout() {
        if (perViewerStackBaseY.isEmpty()) {
            return;
        }
        perViewerStackBaseY.clear();
        recomputeBaseTranslationY();
        applyDisplayTransform();
    }

    private void recomputeBaseTranslationY() {
        final float rowOffset = compactStackLayout ? compactStackYOffset : displayGroup.yOffset();
        final float helmetOffset = !compactStackLayout || compactStackHelmetOffset ? helmetExtraOffset : 0f;
        this.baseTranslationY = offset + increasedOffset + rowOffset + helmetOffset;
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
        final float tx = animationTx;
        final float tz = animationTz;
        final Quaternion4f lq = new Quaternion4f(
                animationLeftQuat.getX(), animationLeftQuat.getY(), animationLeftQuat.getZ(), animationLeftQuat.getW());
        final float sc = scale * animationScaleMul;
        if (!perViewerStackBaseY.isEmpty()) {
            for (final UUID viewerId : new ArrayList<>(perPlayerEntity.getEntities().keySet())) {
                final User user = platform.resolveUser(viewerId);
                if (user == null) {
                    continue;
                }
                final float baseY = perViewerStackBaseY.getOrDefault(viewerId, baseTranslationY);
                final float y = baseY + animationTy;
                modifyEntity(user, w -> {
                    final AbstractDisplayMeta meta = (AbstractDisplayMeta) w.getEntityMeta();
                    meta.setTranslation(new Vector3f(tx, y, tz));
                    meta.setLeftRotation(lq);
                    meta.setScale(new Vector3f(sc, sc, sc));
                });
            }
        } else {
            final float y = baseTranslationY + animationTy;
            modifyAbstractAll(meta -> {
                meta.setTranslation(new Vector3f(tx, y, tz));
                meta.setLeftRotation(lq);
                meta.setScale(new Vector3f(sc, sc, sc));
            });
        }
        flushMetadataIfNeeded();
    }

    public void setDeferMetadataFlush(final boolean deferMetadataFlush) {
        this.deferMetadataFlush = deferMetadataFlush;
    }

    public boolean isDeferMetadataFlush() {
        return deferMetadataFlush;
    }

    public void runWithDeferredFlush(@NotNull final Runnable action) {
        final boolean previous = deferMetadataFlush;
        deferMetadataFlush = true;
        try {
            action.run();
        } finally {
            deferMetadataFlush = previous;
        }
    }

    private void flushMetadataIfNeeded() {
        if (deferMetadataFlush) {
            return;
        }
        flushAllViewers(false);
    }

    /**
     * Pushes pending metadata to one viewer. Delta flush when {@code force} is false; full resync when true.
     *
     * @return {@code true} if a packet was sent
     */
    public boolean flushViewerMetadata(@NotNull final UUID viewerId, final boolean force) {
        if (removed || blocked.contains(viewerId)) {
            return false;
        }

        final User user = platform.resolveUser(viewerId);
        if (user == null) {
            return false;
        }

        final WrapperEntity entity = resolveEntity(user);
        if (entity == null || !entity.isSpawned()) {
            return false;
        }

        if (force) {
            entity.refresh();
            MetadataFlushHelper.clearPending(entity.getEntityMeta().getMetadata());
            return true;
        }

        return MetadataFlushHelper.flushPending(entity);
    }

    public void flushAllViewers(final boolean force) {
        perPlayerEntity.getEntities().forEach((viewerId, entity) -> {
            if (entity == null || blocked.contains(viewerId)) {
                return;
            }
            if (force) {
                if (entity.isSpawned()) {
                    entity.refresh();
                    MetadataFlushHelper.clearPending(entity.getEntityMeta().getMetadata());
                }
            } else {
                MetadataFlushHelper.flushPending(entity);
            }
        });
    }

    @Override
    public void clearAnimationPose() {
        animationTx = animationTy = animationTz = 0f;
        animationLeftQuat = new Quaternion4f(0f, 0f, 0f, 1f);
        animationScaleMul = 1f;
        animDvdInitialized = false;
        applyDisplayTransform();
    }

    @Override
    public void setAnimationPose(float tx, float ty, float tz, @NotNull Quaternion4f q, float scaleMul) {
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
    public long getDvdRandomSeed() {
        return ownerId.getLeastSignificantBits();
    }

    @Override
    public boolean isAnimDvdInitialized() {
        return animDvdInitialized;
    }

    @Override
    public void setAnimDvdInitialized(boolean initialized) {
        this.animDvdInitialized = initialized;
    }

    @Override
    public float getAnimDvdX() {
        return animDvdX;
    }

    @Override
    public void setAnimDvdX(float x) {
        this.animDvdX = x;
    }

    @Override
    public float getAnimDvdZ() {
        return animDvdZ;
    }

    @Override
    public void setAnimDvdZ(float z) {
        this.animDvdZ = z;
    }

    @Override
    public float getAnimDvdVx() {
        return animDvdVx;
    }

    @Override
    public void setAnimDvdVx(float vx) {
        this.animDvdVx = vx;
    }

    @Override
    public float getAnimDvdVz() {
        return animDvdVz;
    }

    @Override
    public void setAnimDvdVz(float vz) {
        this.animDvdVz = vz;
    }

    private @Nullable CustomDisplayAnimationHandler resolveCustomAnimationHandler(@NotNull String id) {
        return runtime.resolveCustomAnimationHandler(id);
    }

    void resetDisplayAnimationState() {
        animationEpochMs = System.currentTimeMillis();
        animationCullDistancePaused = false;
        clearAnimationPose();
    }

    void resetGlowAnimationState() {
        glowEpochMs = System.currentTimeMillis();
        lastAppliedGlowRgb = null;
    }

    private static final int NO_GLOW_COLOR_OVERRIDE = -1;

    public void clearGlow() {
        if (!glowActiveOnEntity && lastAppliedGlowRgb == null) {
            return;
        }
        lastAppliedGlowRgb = null;
        glowActiveOnEntity = false;
        modifyEntity(entity -> {
            entity.getEntityMeta().setGlowing(false);
            if (entity.getEntityMeta() instanceof AbstractDisplayMeta displayMeta) {
                displayMeta.setGlowColorOverride(NO_GLOW_COLOR_OVERRIDE);
            }
        });
        flushMetadataIfNeeded();
    }

    public void applyGlow(int rgb) {
        final int normalized = rgb & 0xFFFFFF;
        if (lastAppliedGlowRgb != null && lastAppliedGlowRgb == normalized) {
            return;
        }
        lastAppliedGlowRgb = normalized;
        glowActiveOnEntity = true;
        modifyEntity(entity -> {
            entity.getEntityMeta().setGlowing(true);
            if (entity.getEntityMeta() instanceof AbstractDisplayMeta displayMeta) {
                displayMeta.setGlowColorOverride(normalized);
            }
        });
        flushMetadataIfNeeded();
    }

    public void applyGlowNow(long monotonicTick) {
        final GlowOverride glow = effectiveGlow();
        if (glow == null || !glow.isActive()) {
            clearGlow();
            return;
        }
        final int interval = displayGroup.effectiveGlowTickInterval(runtime.settings());
        final double elapsed = (System.currentTimeMillis() - glowEpochMs) / 1000.0;
        GlowOverrideComputer.compute(
                        glow,
                        elapsed,
                        monotonicTick,
                        interval,
                        ownerId,
                        runtime,
                        (id, ex) -> runtime.logWarning("Nametag custom glow '" + id + "'", ex))
                .ifPresentOrElse(this::applyGlow, this::clearGlow);
    }

    public void tickGlowAnimation(long monotonicTick) {
        if (removed) {
            return;
        }
        final GlowOverride glow = effectiveGlow();
        if (glow == null || !glow.isActive()) {
            clearGlow();
            return;
        }
        final int interval = displayGroup.effectiveGlowTickInterval(runtime.settings());
        if (interval > 1 && monotonicTick % interval != 0) {
            return;
        }
        applyGlowNow(monotonicTick);
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
        final int interval = displayGroup.effectiveAnimationTickInterval(runtime.settings());
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
        DisplayAnimationComputer.apply(
                this,
                anim,
                elapsed,
                this::resolveCustomAnimationHandler,
                (id, ex) -> runtime.logWarning("Nametag custom animation '" + id + "'", ex));
    }

    /**
     * True if at least one nametag viewer exists in the owner's world within {@code maxDistSq} (blocks²).
     */
    private boolean hasViewerWithinAnimationCullDistanceSq(final double maxDistSq) {
        if (!platform.isOwnerOnline()) {
            return false;
        }
        for (final UUID vid : getViewers()) {
            final double distSq = platform.distanceSquaredSameWorld(ownerId, vid);
            if (distSq >= 0 && distSq <= maxDistSq) {
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

    public void showToViewer(@NotNull UUID viewerId) {
        if (!isEligibleToShow(viewerId)) {
            if (runtime.isNametagDebug()) {
                final String viewerName = platform.playerName(viewerId);
                final String ownerName = platform.playerName(ownerId);
                runtime.logInfo("Player " + viewerName + " is not eligible to show nametag for " + ownerName
                        + ": " + showBlockReason(viewerId));
            }
            return;
        }

        if (!viewerId.equals(ownerId)) {
            final User user = platform.resolveUser(viewerId);
            if (user != null) {
                final WrapperEntity entity = resolveEntity(user);
                if (entity != null) {
                    applyOwnerData(entity);
                }
            }
        }

        spawnViewer(viewerId);

        if (viewerId.equals(ownerId) && platform.isEffectiveShowOwnNametag(ownerId)) {
            setOwnerPosition();
        } else {
            setPosition();
        }

        final User viewerUser = platform.resolveUser(viewerId);
        if (viewerUser != null) {
            final WrapperEntity entity = resolveEntity(viewerUser);
            if (entity != null) {
                entity.addViewer(viewerUser);
            }
        }

        final TextNametagSupport tn = textNametag();
        if (tn != null) {
            tn.refreshViewerIfCached(viewerId);
        }

        runtime.schedulePassengersPacket(viewerId, ownerId);
    }

    private boolean isEligibleToShow(@NotNull UUID viewerId) {
        if (blocked.contains(viewerId)) {
            return false;
        }
        return platform.isEligibleToShow(ownerId, viewerId, visible, getViewers().contains(viewerId));
    }

    @NotNull
    private String showBlockReason(@NotNull UUID viewerId) {
        if (blocked.contains(viewerId)) {
            return "viewer is blocked for this nametag";
        }
        final String reason = platform.nametagShowBlockReason(ownerId, viewerId, visible, getViewers().contains(viewerId));
        return reason != null ? reason : "unknown eligibility failure";
    }

    @Override
    public int displayEntityId() {
        return entityId;
    }

    public void spawnForOwner() {
        this.visible = true;
        final User user = platform.resolveUser(ownerId);
        if (user == null) {
            return;
        }
        final Location location = getOffsetPeLocation();
        if (location == null) {
            return;
        }
        modifyEntity(user, e -> {
            e.despawn();
            e.spawn(location);
        });

        runtime.schedulePassengersPacket(ownerId, ownerId);
    }

    public void sendPassengersPacket(@NotNull User viewerUser) {
        if (removed) {
            return;
        }
        runtime.sendPassengersPacket(viewerUser, ownerId);
    }

    public void sendPassengerPacketToViewers() {
        if (!visible) {
            return;
        }

        getViewers().forEach(viewerId -> runtime.schedulePassengersPacket(viewerId, ownerId));
    }

    private void setPosition() {
        final Location location = getOffsetPeLocation();
        if (location == null) {
            return;
        }
        modifyEntity(meta -> meta.setLocation(location));
    }

    private void setOwnerPosition() {
        final Location location = getOffsetPeLocation();
        if (location == null) {
            return;
        }
        modifyOwnerEntity(meta -> meta.setLocation(location));
    }

    @Nullable
    public Location getOffsetPeLocation() {
        return platform.offsetDisplayLocation(scale);
    }

    public void hideFromViewer(@NotNull UUID viewerId) {
        if (blocked.contains(viewerId)) {
            return;
        }
        final TextNametagSupport tn = textNametag();
        if (tn != null) {
            tn.onViewerRemoved(viewerId);
        }
        final User user = platform.resolveUser(viewerId);
        if (user == null) {
            perPlayerEntity.getEntities().remove(viewerId);
            return;
        }
        final WrapperEntity wrapperEntity = resolveEntity(user);
        if (wrapperEntity != null) {
            wrapperEntity.removeViewer(user);
        }
        if (!viewerId.equals(ownerId)) {
            perPlayerEntity.getEntities().remove(user.getUUID());
        }

        runtime.removePassenger(viewerId, entityId);
    }

    public void clearViewers() {
        getViewers().forEach(this::hideFromViewer);
    }

    public void showToViewers(@NotNull Set<UUID> viewerIds) {
        viewerIds.forEach(this::showToViewer);
    }

    public void refreshForViewer(@NotNull UUID viewerId) {
        refreshForViewer(viewerId, false);
    }

    public void refreshForViewer(@NotNull UUID viewerId, final boolean force) {
        flushViewerMetadata(viewerId, force);
    }

    public void hideFromViewerSilently(@NotNull UUID viewerId) {
        if (blocked.contains(viewerId)) {
            return;
        }
        final TextNametagSupport tn = textNametag();
        if (tn != null) {
            tn.onViewerRemoved(viewerId);
        }
        perPlayerEntity.getEntities().remove(viewerId);
    }

    public boolean canViewerSee(@NotNull UUID viewerId) {
        return getViewers().contains(viewerId);
    }

    public void spawnViewer(@NotNull UUID viewerId) {
        this.visible = true;
        final User user = platform.resolveUser(viewerId);
        if (user == null) {
            return;
        }

        final Location location = getOffsetPeLocation();
        if (location == null) {
            return;
        }
        modifyEntity(user, e -> e.spawn(location));
    }

    public void refresh() {
        flushAllViewers(false);
    }

    public void remove() {
        removed = true;
        final TextNametagSupport tn = textNametag();
        if (tn != null) {
            tn.dispose();
        }
        perPlayerEntity.getEntities().keySet().forEach(viewerId -> {
            final User user = platform.resolveUser(viewerId);
            if (user != null && user.getChannel() != null) {
                final WrapperEntity entity = resolveEntity(user);
                if (entity != null) {
                    entity.removeViewer(user);
                }
            }
        });

        perPlayerEntity.getEntities().values().forEach(WrapperEntity::remove);
        perPlayerEntity.getEntities().clear();

        runtime.removePassengerFromAll(entityId);
    }

    public void handleQuit(@NotNull UUID viewerId) {
        final TextNametagSupport tn = textNametag();
        if (tn != null) {
            tn.onViewerRemoved(viewerId);
        }
        perPlayerEntity.getEntities().remove(viewerId);
        runtime.removePassenger(viewerId, entityId);
    }

    /**
     * Clears cached per-viewer text opacity / seeThrough when {@code visibility.obscuredNametagThroughWalls} ({@link Settings.Visibility}) is toggled off or on reload.
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
        hideFromViewer(ownerId);
        blocked.add(ownerId);
    }

    public void showForOwner() {
        blocked.remove(ownerId);
        showToViewer(ownerId);
    }

    private void applyOwnerData(@NotNull WrapperEntity wrapper) {
        final User ownerUser = platform.resolveUser(ownerId);
        if (ownerUser == null) {
            return;
        }
        final WrapperEntity ownerEntity = resolveEntity(ownerUser);
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
        final User ownerUser = platform.resolveUser(ownerId);
        if (ownerUser == null) {
            return properties;
        }
        final WrapperEntity entity = resolveEntity(ownerUser);
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
        properties.put("glowing", String.valueOf(meta.isGlowing()));
        properties.put("glowColorOverride", String.valueOf(meta.getGlowColorOverride()));
        appendTypeProperties(properties, meta);
        return properties;
    }

    public void setForcedNameTag(@NotNull final Component component) {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.setForcedGlobal(component);
        }
    }

    public void setForcedNameTag(@NotNull final UUID viewer, @NotNull final Component component) {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.setForcedRelational(viewer, component);
        }
    }

    public void clearForcedNameTag() {
        final TextNametagSupport t = textNametag();
        if (t != null) {
            t.clearForcedGlobal();
        }
    }

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
