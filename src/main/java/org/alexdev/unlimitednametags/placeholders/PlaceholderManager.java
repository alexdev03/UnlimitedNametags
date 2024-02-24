package org.alexdev.unlimitednametags.placeholders;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class PlaceholderManager {

    private static final Component EMPTY = Component.text("");
    private final UnlimitedNameTags plugin;
    private final ExecutorService executorService;
    private final PAPIManager papiManager;
    private int index = 16777215;

    public PlaceholderManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.executorService = Executors.newCachedThreadPool(getThreadFactory());
        this.papiManager = new PAPIManager(plugin);
        startIndexTask();
    }

    @NotNull
    private ThreadFactory getThreadFactory() {
        AtomicInteger index = new AtomicInteger(1);
        return r -> {
            final Thread thread = new Thread(r);
            thread.setName("UnlimitedNameTags-PlaceholderManager: " + index.getAndIncrement());
            return thread;
        };
    }

    private void startIndexTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            index -= 1;
            if (index == 0) {
                index = 16777215;
            }
        }, 0, 1);
    }

    public void close() {
        this.executorService.shutdown();
    }


    @NotNull
    public CompletableFuture<Component> applyPlaceholders(@NotNull Player player, @NotNull List<String> lines) {
        return CompletableFuture.supplyAsync(() -> createComponent(player, lines), executorService);
    }

    @NotNull
    private Component createComponent(@NotNull Player player, @NotNull List<String> strings) {
        return Component.join(JoinConfiguration.separator(Component.newline()), strings.stream()
                .map(t -> papiManager.isPAPIEnabled() ? papiManager.setPlaceholders(player, t) : t)
                .filter(s -> !plugin.getConfigManager().getSettings().isRemoveEmptyLines() || !s.isEmpty())
                .map(t -> t.replace("#phase#", String.valueOf(index)))
                .map(t -> format(t, player))
                .filter(c -> !plugin.getConfigManager().getSettings().isRemoveEmptyLines() || !c.equals(EMPTY))
                .toArray(Component[]::new));
    }

    @NotNull
    private Component format(@NotNull String value, @NotNull Player player) {
        return plugin.getConfigManager().getSettings().getFormat().format(plugin, player, value);
    }

}
