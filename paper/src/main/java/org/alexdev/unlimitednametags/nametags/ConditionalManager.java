package org.alexdev.unlimitednametags.nametags;

import me.clip.placeholderapi.PlaceholderAPI;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.api.UntConditionalManagerPaper;
import org.alexdev.unlimitednametags.condition.JexlConditionEvaluator;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ConditionalManager implements UntConditionalManagerPaper {

    private final UnlimitedNameTags plugin;
    private final JexlConditionEvaluator jexlConditionEvaluator;

    public ConditionalManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.jexlConditionEvaluator = new JexlConditionEvaluator(msg -> plugin.getLogger().warning(msg));
    }

    @Override
    public boolean evaluateCondition(@NotNull String expression, @NotNull UUID playerId) {
        final Player player = plugin.getPlayerListener().getPlayer(playerId);
        if (player == null) {
            return false;
        }
        final String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        final String entireExpression = plugin.getPlaceholderManager().getPapiManager().isPapiEnabled() ?
                PlaceholderAPI.setPlaceholders(player, trimmed) :
                trimmed;

        return jexlConditionEvaluator.evaluate(entireExpression);
    }

    @Override
    public boolean evaluateCondition(@NotNull String expression, @NotNull UUID viewerId, @NotNull UUID ownerId) {
        final Player viewer = plugin.getPlayerListener().getPlayer(viewerId);
        final Player owner = plugin.getPlayerListener().getPlayer(ownerId);
        if (viewer == null || owner == null) {
            return false;
        }
        final String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        final String entireExpression = plugin.getPlaceholderManager().getPapiManager().isPapiEnabled() ?
                PlaceholderAPI.setRelationalPlaceholders(viewer, owner, trimmed) :
                trimmed;

        return jexlConditionEvaluator.evaluate(entireExpression);
    }
}
