package org.alexdev.unlimitednametags.api;

import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Core plugin surface for {@link UNTAPI} and config types (e.g. {@link org.alexdev.unlimitednametags.config.Settings}).
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

    /**
     * Registers a handler for {@link org.alexdev.unlimitednametags.config.DisplayAnimation.CustomDisplayAnimation}
     * ({@code animation.type: custom} and matching {@code id}). Replaces any previous registration for the same id.
     */
    void registerNametagCustomAnimation(@NotNull String id, @NotNull NametagCustomAnimationHandler handler);

    /**
     * @return whether a handler was removed
     */
    boolean unregisterNametagCustomAnimation(@NotNull String id);

    @Nullable
    NametagCustomAnimationHandler getNametagCustomAnimationHandler(@NotNull String id);
}
