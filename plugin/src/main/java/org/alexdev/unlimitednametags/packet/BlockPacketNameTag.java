package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.protocol.world.states.WrappedBlockState;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.BlockDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Function;

final class BlockPacketNameTag extends PacketNameTag {

    BlockPacketNameTag(@NotNull UnlimitedNameTags plugin, @NotNull Player owner, @NotNull Settings.DisplayGroup displayGroup) {
        super(plugin, owner, displayGroup);
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
        if (!getPlugin().getPlaceholderManager().isDisplayGroupActive(getOwner(), group)) {
            getPerPlayerEntity().execute(e -> ((BlockDisplayMeta) e.getEntityMeta())
                    .setBlockState(SpigotConversionUtil.fromBukkitBlockData(Material.AIR.createBlockData())));
            touchLastUpdate();
            return;
        }
        applyBlockFromGroup(group);
        touchLastUpdate();
    }

    private void applyBlockFromGroup(@NotNull Settings.DisplayGroup group) {
        String raw = group.blockMaterial();
        if (raw == null || raw.isBlank()) {
            raw = "STONE";
        }
        final String expanded = getPlugin().getPlaceholderManager().expandForOwner(getOwner(), raw).trim();
        final Material mat = Material.matchMaterial(expanded, false);
        final WrappedBlockState state;
        if (mat != null && mat.isBlock()) {
            state = SpigotConversionUtil.fromBukkitBlockData(mat.createBlockData());
        } else {
            state = SpigotConversionUtil.fromBukkitBlockData(Material.STONE.createBlockData());
        }
        getPerPlayerEntity().execute(e -> {
            final BlockDisplayMeta meta = (BlockDisplayMeta) e.getEntityMeta();
            meta.setBlockState(state);
        });
    }
}
