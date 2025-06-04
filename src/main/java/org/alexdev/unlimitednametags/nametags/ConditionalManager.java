package org.alexdev.unlimitednametags.nametags;

import me.clip.placeholderapi.PlaceholderAPI;
import net.jodah.expiringmap.ExpiringMap;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.apache.commons.jexl3.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

public class ConditionalManager {

    private final UnlimitedNameTags plugin;
    private final JexlEngine jexlEngine;
    private final JexlContext jexlContext;
    private final ExpiringMap<String, JexlExpression> expressionCache;

    public ConditionalManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        // Create a single reusable JexlEngine instance
        this.jexlEngine = new JexlBuilder().debug(false).create();
        // Shared evaluation context
        this.jexlContext = new MapContext();
        // Cache for parsed expressions with size and time limits
        this.expressionCache = ExpiringMap.builder()
                .maxSize(1000)
                .expiration(30, TimeUnit.MINUTES)
                .build();
    }

    public boolean evaluateExpression(@NotNull Settings.ConditionalModifier modifier, @NotNull Player player) {
        // Resolve placeholders if PlaceholderAPI is enabled
        final String expressionString = plugin.getPlaceholderManager().getPapiManager().isPapiEnabled()
                ? PlaceholderAPI.setPlaceholders(player, modifier.getExpression())
                : modifier.getExpression();

        // Try to get pre-parsed expression from cache
        JexlExpression expression = expressionCache.get(expressionString);
        
        // Parse and cache new expressions
        if (expression == null) {
            try {
                expression = jexlEngine.createExpression(expressionString);
                expressionCache.put(expressionString, expression);
            } catch (JexlException e) {
                plugin.getLogger().warning("Parse error in expression: " + expressionString + ": " + e.getMessage());
                return false;
            }
        }

        // Evaluate the expression
        try {
            Object result = expression.evaluate(jexlContext);
            return result instanceof Boolean b && b;
        } catch (JexlException e) {
            plugin.getLogger().warning("Evaluation error for: " + expressionString + ": " + e.getMessage());
            return false;
        }
    }
}
