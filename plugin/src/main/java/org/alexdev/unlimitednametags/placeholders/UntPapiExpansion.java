package org.alexdev.unlimitednametags.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

public class UntPapiExpansion extends PlaceholderExpansion {

    private final UnlimitedNameTags plugin;

    public UntPapiExpansion(UnlimitedNameTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "unt";
    }

    @Override
    public @NotNull String getAuthor() {
        return "AlexDev";
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
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
            case "see_others", "pref_see_others" -> boolPref(player, () ->
                    !plugin.getNametagManager().isHiddenOtherNametags(player));
            case "show_own_self", "pref_show_own_self" -> boolPref(player,
                    () -> plugin.getNametagManager().isShowingOwnNametagToSelf(player));
            case "show_own_to_others", "pref_show_own_to_others" -> boolPref(player,
                    () -> plugin.getNametagManager().isShowingOwnNametagToOthers(player));
            case "effective_show_own" -> boolPref(player,
                    () -> plugin.getNametagManager().isEffectiveShowOwnNametag(player));
            default -> null;
        };
    }

    /**
     * @return {@code "true"} or {@code "false"}, or empty string when no player context
     */
    private static @NotNull String boolPref(@Nullable Player player, @NotNull BooleanSupplier valueIfPlayer) {
        if (player == null) {
            return "";
        }
        return Boolean.toString(valueIfPlayer.getAsBoolean());
    }

    // Placeholders (boolean as "true"/"false" strings):
    // %unt_see_others% / %unt_pref_see_others% — player sees other players' nametags (not "hide others").
    // %unt_show_own_self% / %unt_pref_show_own_self% — preference to show own nametag to self (PDC + runtime).
    // %unt_show_own_to_others% / %unt_pref_show_own_to_others% — preference to show own nametag to other viewers.
    // %unt_effective_show_own% — whether own nametag is actually shown above the player (global + prefs).
}
