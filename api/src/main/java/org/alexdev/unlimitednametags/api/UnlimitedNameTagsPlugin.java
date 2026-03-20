package org.alexdev.unlimitednametags.api;

import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Core plugin surface for {@link UNTAPI} and config types (e.g. {@link org.alexdev.unlimitednametags.config.Settings.Modifier}).
 */
public interface UnlimitedNameTagsPlugin {

    @NotNull
    UntNametagManager getNametagManager();

    @NotNull
    UntVanishManager getVanishManager();

    @NotNull
    UntConditionalManager getConditionalManager();

    @NotNull
    List<HatHook> getHatHooks();

    /**
     * Formats raw nametag text for display (MiniPlaceholders when available, else MiniMessage).
     */
    @NotNull
    Component formatTextForNametag(@NotNull CommandSender audience, @NotNull String text);
}
