package org.alexdev.unlimitednametags.hook;

import io.github.miniplaceholders.api.MiniPlaceholders;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class MiniPlaceholdersHook extends Hook {

    public MiniPlaceholdersHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
    }

    @NotNull
    public Component format(@NotNull String text, @NotNull Audience player, List<TagResolver> tagResolvers) {
        final List<TagResolver> resolvers = List.of(MiniPlaceholders.getAudienceGlobalPlaceholders(player), MiniPlaceholders.getAudienceGlobalPlaceholders(player));
        return MiniMessage.miniMessage().deserialize(text, resolvers.toArray(new TagResolver[0]));
    }

    @NotNull
    public Component format(@NotNull Component component, @NotNull Audience player, List<TagResolver> tagResolvers) {
        return format(MiniMessage.miniMessage().serialize(component), player, tagResolvers);
    }

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }
}
