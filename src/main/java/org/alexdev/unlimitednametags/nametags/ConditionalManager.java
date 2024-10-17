package org.alexdev.unlimitednametags.nametags;

import me.clip.placeholderapi.PlaceholderAPI;
import net.jodah.expiringmap.ExpiringMap;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ConditionalManager {

    private final UnlimitedNameTags plugin;
    private final JexlEngine jexlEngine;
    private final JexlContext jexlContext;
    private final Map<String, Object> cachedExpressions;


    public ConditionalManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.jexlEngine = createJexlEngine();
        this.jexlContext = createJexlContext();
        this.cachedExpressions = ExpiringMap.builder()
                .expiration(5, TimeUnit.MINUTES)
                .build();
    }

    @NotNull
    private JexlEngine createJexlEngine() {
        return new JexlBuilder().create();
    }


    @NotNull
    private JexlContext createJexlContext() {
        return new MapContext();
    }

    public boolean evaluateExpression(@NotNull Settings.ConditionalModifier modifier, @NotNull Player player) {
        final String entireExpression = PlaceholderAPI.setPlaceholders(player, modifier.getExpression());
        if (cachedExpressions.containsKey(entireExpression)) {
            return (boolean) cachedExpressions.get(entireExpression);
        }

        try {
            final Object result = jexlEngine.createExpression(entireExpression).evaluate(jexlContext);
            final boolean boolResult = result instanceof Boolean bb && bb;
            cachedExpressions.put(entireExpression, boolResult);
            return boolResult;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to evaluate expression: " + entireExpression);
            return false;
        }
    }

}
