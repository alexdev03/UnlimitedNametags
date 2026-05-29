package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.BlockDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.platform.NametagMaterialBridge;
import org.alexdev.unlimitednametags.platform.NametagPlatformBridge;
import org.alexdev.unlimitednametags.platform.NametagRuntime;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

class BlockPacketNameTag extends PacketNameTag {

    public BlockPacketNameTag(@NotNull NametagRuntime runtime, @NotNull NametagPlatformBridge platform,
            @NotNull NametagMaterialBridge materials, @NotNull UUID ownerId,
            @NotNull Settings.DisplayGroup displayGroup) {
        super(runtime, platform, materials, ownerId, displayGroup);
    }

    @Override
    protected @NotNull Function<User, WrapperEntity> buildBaseSupplier() {
        return user -> {
            final WrapperEntity w = new WrapperEntity(getEntityId(), getEntityIdUuid(), EntityTypes.BLOCK_DISPLAY);
            w.getEntityMeta().setNotifyAboutChanges(false);
            return w;
        };
    }

    @Override
    protected void applyViewerOwnerMetadata(@NotNull WrapperEntity viewerEntity, @NotNull WrapperEntity ownerEntity) {
        final BlockDisplayMeta ownerMeta = (BlockDisplayMeta) ownerEntity.getEntityMeta();
        final BlockDisplayMeta viewerMeta = (BlockDisplayMeta) viewerEntity.getEntityMeta();
        viewerMeta.setBlockId(ownerMeta.getBlockId());
    }

    @Override
    protected void appendTypeProperties(@NotNull Map<String, String> properties, @NotNull AbstractDisplayMeta meta) {
        if (meta instanceof BlockDisplayMeta blockMeta) {
            properties.put("blockState", String.valueOf(blockMeta.getBlockId()));
        }
    }

    @Override
    public void syncVisualFromGroup(@NotNull Settings.DisplayGroup group) {
        if (!getRuntime().isDisplayGroupActive(getOwnerId(), group)) {
            getPerPlayerEntity().execute(e -> ((BlockDisplayMeta) e.getEntityMeta())
                    .setBlockState(resolveBlockState("AIR", "STONE")));
            touchLastUpdate();
            return;
        }
        applyBlockFromGroup(group);
        touchLastUpdate();
    }

    @NotNull
    private WrappedBlockState resolveBlockState(@NotNull String materialKey, @NotNull String fallbackKey) {
        final Object resolved = getMaterials().resolveBlockState(getOwnerId(), materialKey);
        if (resolved instanceof WrappedBlockState state) {
            return state;
        }
        final Object fallback = getMaterials().resolveBlockState(getOwnerId(), fallbackKey);
        if (fallback instanceof WrappedBlockState fallbackState) {
            return fallbackState;
        }
        throw new IllegalStateException("Unable to resolve block state for " + materialKey);
    }

    private void applyBlockFromGroup(@NotNull Settings.DisplayGroup group) {
        String raw = group.blockMaterial();
        if (raw == null || raw.isBlank()) {
            raw = "STONE";
        }
        final String expanded = getRuntime().expandPlaceholdersForOwner(getOwnerId(), raw);
        final Object resolved = getMaterials().resolveBlockState(getOwnerId(), expanded);
        final WrappedBlockState state = resolved instanceof WrappedBlockState blockState
                ? blockState
                : resolveBlockState("STONE", "STONE");
        getPerPlayerEntity().execute(e -> {
            final BlockDisplayMeta meta = (BlockDisplayMeta) e.getEntityMeta();
            meta.setBlockState(state);
        });
    }
}
