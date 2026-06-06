package org.alexdev.unlimitednametags.api;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.alexdev.unlimitednametags.config.GlowOverride;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Paper/Bukkit extension of {@link UnlimitedNameTagsInstance}.
 */
public interface UnlimitedNameTagsInstancePaper extends UnlimitedNameTagsInstance {

    @NotNull
    @Override
    UntNametagManagerPaper getNametagManager();

    @NotNull
    @Override
    UntVanishManagerPaper getVanishManager();

    @NotNull
    @Override
    UntConditionalManagerPaper getConditionalManager();

    @NotNull
    Component formatTextForNametag(@NotNull CommandSender audience, @NotNull String text);

    void registerNametagCustomAnimation(@NotNull String id, @NotNull NametagCustomAnimationHandler handler);

    boolean unregisterNametagCustomAnimation(@NotNull String id);

    @Nullable
    NametagCustomAnimationHandler getNametagCustomAnimationHandler(@NotNull String id);

    void registerNametagGlowAnimation(@NotNull String id, @NotNull GlowOverride glow);

    boolean unregisterNametagGlowAnimation(@NotNull String id);

    @Nullable
    GlowOverride getNametagGlowAnimation(@NotNull String id);

    @NotNull
    Set<String> getNametagGlowAnimationIds();

    /**
     * Union of {@code settings.yml} {@code glowAnimations} keys and API-registered preset ids.
     */
    @NotNull
    Set<String> getKnownGlowAnimationIds();

    void registerNametagCustomGlowHandler(@NotNull String id, @NotNull NametagCustomGlowHandler handler);

    boolean unregisterNametagCustomGlowHandler(@NotNull String id);

    @Nullable
    NametagCustomGlowHandler getNametagCustomGlowHandler(@NotNull String id);

    @NotNull
    Set<String> getNametagCustomGlowHandlerIds();
}
