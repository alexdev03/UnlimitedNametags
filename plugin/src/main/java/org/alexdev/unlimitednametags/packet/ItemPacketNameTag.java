package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.User;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.ItemDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

final class ItemPacketNameTag extends PacketNameTag {

    ItemPacketNameTag(@NotNull UnlimitedNameTags plugin, @NotNull Player owner, @NotNull Settings.DisplayGroup displayGroup) {
        super(plugin, owner, displayGroup);
    }

    @Override
    protected @NotNull Function<User, WrapperEntity> buildBaseSupplier() {
        return user -> {
            final WrapperEntity w = new WrapperEntity(getEntityId(), getEntityIdUuid(), EntityTypes.ITEM_DISPLAY);
            w.getEntityMeta().setNotifyAboutChanges(false);
            return w;
        };
    }

    @Override
    protected void applyViewerOwnerMetadata(@NotNull WrapperEntity viewerEntity, @NotNull WrapperEntity ownerEntity) {
        final ItemDisplayMeta ownerMeta = (ItemDisplayMeta) ownerEntity.getEntityMeta();
        final ItemDisplayMeta viewerMeta = (ItemDisplayMeta) viewerEntity.getEntityMeta();
        viewerMeta.setItem(ownerMeta.getItem());
        viewerMeta.setDisplayType(ownerMeta.getDisplayType());
    }

    @Override
    protected void appendTypeProperties(@NotNull Map<String, String> properties, @NotNull AbstractDisplayMeta meta) {
        if (meta instanceof ItemDisplayMeta itemMeta) {
            properties.put("itemDisplayMode", itemMeta.getDisplayType().name());
        }
    }

    @Override
    public void syncVisualFromGroup(@NotNull Settings.DisplayGroup group) {
        if (!getPlugin().getPlaceholderManager().isDisplayGroupActive(getOwner(), group)) {
            getPerPlayerEntity().execute(e -> ((ItemDisplayMeta) e.getEntityMeta()).setItem(ItemStack.EMPTY));
            touchLastUpdate();
            return;
        }
        applyItemFromGroup(group);
        touchLastUpdate();
    }

    private static ItemDisplayMeta.DisplayType parseItemDisplayMode(@Nullable String mode) {
        if (mode == null || mode.isBlank()) {
            return ItemDisplayMeta.DisplayType.HEAD;
        }
        try {
            return ItemDisplayMeta.DisplayType.valueOf(mode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ItemDisplayMeta.DisplayType.HEAD;
        }
    }

    private void applyItemFromGroup(@NotNull Settings.DisplayGroup group) {
        String raw = group.itemMaterial();
        if (raw == null || raw.isBlank()) {
            raw = "STONE";
        }
        final String expanded = getPlugin().getPlaceholderManager().expandForOwner(getOwner(), raw).trim();
        final Material mat = Material.matchMaterial(expanded, false);
        final ItemStack peStack;
        if (mat != null && mat.isItem()) {
            peStack = SpigotConversionUtil.fromBukkitItemStack(new org.bukkit.inventory.ItemStack(mat));
        } else {
            peStack = ItemStack.EMPTY;
        }
        final ItemDisplayMeta.DisplayType displayType = parseItemDisplayMode(group.itemDisplayMode());
        getPerPlayerEntity().execute(e -> {
            final ItemDisplayMeta meta = (ItemDisplayMeta) e.getEntityMeta();
            meta.setItem(peStack);
            meta.setDisplayType(displayType);
        });
    }
}
