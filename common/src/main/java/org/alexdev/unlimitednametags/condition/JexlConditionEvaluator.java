package org.alexdev.unlimitednametags.condition;

import net.jodah.expiringmap.ExpiringMap;
import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlContext;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class JexlConditionEvaluator {

  private final BlockingQueue<JexlEngine> jexlEnginePool;
  private final JexlContext jexlContext;
  private final Map<String, Object> cachedExpressions;
  private final Consumer<String> warningLogger;

  public JexlConditionEvaluator(@NotNull Consumer<String> warningLogger) {
    this.warningLogger = warningLogger;
    this.jexlEnginePool = createJexlEnginePool();
    this.jexlContext = new MapContext();
    this.cachedExpressions = ExpiringMap.builder()
        .expiration(5, TimeUnit.MINUTES)
        .build();
  }

  @NotNull
  private static BlockingQueue<JexlEngine> createJexlEnginePool() {
    final BlockingQueue<JexlEngine> pool = new LinkedBlockingQueue<>(10);
    for (int i = 0; i < 10; i++) {
      pool.add(new JexlBuilder().debug(false).create());
    }
    return pool;
  }

  public boolean evaluate(@NotNull String entireExpression) {
    final Object cachedVal = cachedExpressions.get(entireExpression);
    if (cachedVal instanceof Boolean b) {
      return b;
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
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    } catch (Exception e) {
      warningLogger.accept("Failed to evaluate expression: " + entireExpression);
      return false;
    } finally {
      if (jexlEngine != null && !jexlEnginePool.offer(jexlEngine)) {
        warningLogger.accept("JexlEngine pool is full. Discarding engine.");
      }
    }
  }
}
