package org.alexdev.unlimitednametags.hook;

import io.github.miniplaceholders.api.MiniPlaceholders;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.jetbrains.annotations.NotNull;

public class MiniPlaceholdersHook extends Hook {

    public MiniPlaceholdersHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
    }

    @NotNull
    public Component format(@NotNull String text, @NotNull Audience player) {
        return MiniMessage.miniMessage().deserialize(text, player, MiniPlaceholders.audienceGlobalPlaceholders());
    }

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }
}
