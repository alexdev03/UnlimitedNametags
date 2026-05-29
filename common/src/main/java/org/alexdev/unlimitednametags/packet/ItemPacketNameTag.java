package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.User;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.ItemDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.platform.NametagMaterialBridge;
import org.alexdev.unlimitednametags.platform.NametagPlatformBridge;
import org.alexdev.unlimitednametags.platform.NametagRuntime;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

class ItemPacketNameTag extends PacketNameTag {

    public ItemPacketNameTag(@NotNull NametagRuntime runtime, @NotNull NametagPlatformBridge platform,
            @NotNull NametagMaterialBridge materials, @NotNull UUID ownerId,
            @NotNull Settings.DisplayGroup displayGroup) {
        super(runtime, platform, materials, ownerId, displayGroup);
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
        if (!getRuntime().isDisplayGroupActive(getOwnerId(), group)) {
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
        final String expanded = getRuntime().expandPlaceholdersForOwner(getOwnerId(), raw);
        final Object resolved = getMaterials().resolveItemStack(getOwnerId(), expanded);
        final ItemStack peStack = resolved instanceof ItemStack stack ? stack : ItemStack.EMPTY;
        final ItemDisplayMeta.DisplayType displayType = parseItemDisplayMode(group.itemDisplayMode());
        getPerPlayerEntity().execute(e -> {
            final ItemDisplayMeta meta = (ItemDisplayMeta) e.getEntityMeta();
            meta.setItem(peStack);
            meta.setDisplayType(displayType);
        });
    }
}
