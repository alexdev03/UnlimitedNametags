package org.alexdev.unlimitednametags.nametags;

import me.clip.placeholderapi.PlaceholderAPI;
import net.jodah.expiringmap.ExpiringMap;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.apache.commons.jexl3.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ConditionalManager {

    private final UnlimitedNameTags plugin;
    private final BlockingQueue<JexlEngine> jexlEnginePool;
    private final JexlContext jexlContext;
    private final Map<String, Object> cachedExpressions;

    public ConditionalManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.jexlEnginePool = createJexlEnginePool();
        this.jexlContext = createJexlContext();
        this.cachedExpressions = ExpiringMap.builder()
                .expiration(5, TimeUnit.MINUTES)
                .build();
    }

    @NotNull
    private BlockingQueue<JexlEngine> createJexlEnginePool() {
        final BlockingQueue<JexlEngine> pool = new LinkedBlockingQueue<>(10);
        for (int i = 0; i < 10; i++) {
            pool.add(new JexlBuilder().debug(false).create());
        }
        return pool;
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

        JexlEngine jexlEngine = null;
        try {
            jexlEngine = jexlEnginePool.poll(1, TimeUnit.SECONDS);
            if (jexlEngine == null) {
                jexlEngine = new JexlBuilder().debug(false).create();
            }

            final Object result = jexlEngine.createExpression(entireExpression).evaluate(jexlContext);
            final boolean boolResult = result instanceof Boolean bb && bb;

            cachedExpressions.put(entireExpression, boolResult);

            return boolResult;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to evaluate expression: " + entireExpression);
            return false;
        } finally {
            if (jexlEngine != null && !jexlEnginePool.offer(jexlEngine)) {
                plugin.getLogger().warning("JexlEngine pool is full. Discarding engine.");
            }
        }
    }
}
