package org.alexdev.unlimitednametags.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UntPapiExpansion extends PlaceholderExpansion {

    private final UnlimitedNameTags plugin;

    public UntPapiExpansion(UnlimitedNameTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "unt";
    }

    @Override
    public String getAuthor() {
        return "AlexDev";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return switch (params) {
            case "phase-mm" -> plugin.getPlaceholderManager().getFormattedPhases(PlaceholderManager.PHASE_MM_KEY);
            case "phase-md" -> plugin.getPlaceholderManager().getFormattedPhases(PlaceholderManager.PHASE_MD_KEY);
            case "phase-mm-g" -> plugin.getPlaceholderManager().getFormattedPhases(PlaceholderManager.PHASE_MM_G_KEY);
            case "-phase-mm" -> plugin.getPlaceholderManager().getFormattedPhases(PlaceholderManager.NEG_PHASE_MM_KEY);
            case "-phase-md" -> plugin.getPlaceholderManager().getFormattedPhases(PlaceholderManager.NEG_PHASE_MD_KEY);
            default -> null;
        };
    }

    //Placeholders:
}
