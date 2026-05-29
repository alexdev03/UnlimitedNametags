package org.alexdev.unlimitednametags.api;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
}
