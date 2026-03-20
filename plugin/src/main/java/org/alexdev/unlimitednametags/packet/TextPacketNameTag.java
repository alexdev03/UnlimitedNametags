package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.player.User;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Text display entity; all per-viewer text state lives in {@link TextNametagSupport}.
 */
final class TextPacketNameTag extends PacketNameTag {

    private final TextNametagSupport textSupport = new TextNametagSupport(this);

    TextPacketNameTag(@NotNull final UnlimitedNameTags plugin, @NotNull final Player owner, @NotNull final Settings.DisplayGroup displayGroup) {
        super(plugin, owner, displayGroup);
    }

    @Override
    @Nullable
    protected TextNametagSupport textNametag() {
        return textSupport;
    }

    @Override
    protected @NotNull Function<User, WrapperEntity> buildBaseSupplier() {
        return user -> {
            final WrapperEntity w = new WrapperEntity(getEntityId(), getEntityIdUuid(), EntityTypes.TEXT_DISPLAY);
            final TextDisplayMeta meta = (TextDisplayMeta) w.getEntityMeta();
            meta.setLineWidth(1000);
            meta.setNotifyAboutChanges(false);
            return w;
        };
    }

    @Override
    protected void applyViewerOwnerMetadata(@NotNull final WrapperEntity viewerEntity, @NotNull final WrapperEntity ownerEntity) {
        final Optional<EntityData<?>> component = viewerEntity.getEntityMeta().entityData()
                .stream()
                .filter(e -> e.getType() == EntityDataTypes.ADV_COMPONENT)
                .findFirst();
        component.ifPresent(entityData -> ((TextDisplayMeta) viewerEntity.getEntityMeta())
                .setText((Component) entityData.getValue()));
    }

    @Override
    protected void appendTypeProperties(@NotNull final Map<String, String> properties, @NotNull final AbstractDisplayMeta meta) {
        if (meta instanceof TextDisplayMeta textMeta) {
            properties.put("text", MiniMessage.miniMessage().serialize(textMeta.getText()));
            properties.put("shadowed", String.valueOf(textMeta.isShadow()));
            properties.put("seeThrough", String.valueOf(textMeta.isSeeThrough()));
            properties.put("backgroundColor", String.valueOf(textMeta.getBackgroundColor()));
        }
    }

    @Override
    public boolean isTextDisplay() {
        return true;
    }
}
