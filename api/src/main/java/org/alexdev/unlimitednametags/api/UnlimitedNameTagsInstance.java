package org.alexdev.unlimitednametags.api;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Platform-neutral plugin facade (UUID / Adventure). Paper/Bukkit extensions ship in the {@code api-paper} module.
 */
public interface UnlimitedNameTagsInstance {

    @NotNull
    UntNametagManager getNametagManager();

    @NotNull
    UntVanishManager getVanishManager();

    @NotNull
    UntConditionalManager getConditionalManager();

    @NotNull
    List<HatHook> getHatHooks();

    /**
     * Formats raw nametag text for display (MiniPlaceholders when available on Paper, else MiniMessage).
     */
    @NotNull
    Component formatTextForNametag(@NotNull Audience audience, @NotNull String text);
}
