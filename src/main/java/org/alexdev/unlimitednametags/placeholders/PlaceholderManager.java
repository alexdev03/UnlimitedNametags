package org.alexdev.unlimitednametags.placeholders;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaceholderManager {

    private final UnlimitedNameTags plugin;
    private final ExecutorService executorService;
    private int index = 16777215;
    private final PAPIManager papiManager;

    public PlaceholderManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.executorService = Executors.newCachedThreadPool();
        this.papiManager = new PAPIManager(plugin);
        startIndexTask();
    }

    private void startIndexTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            index -= 1;
            if (index == 0) {
                index = 16777215;
//                index = 10000;
            }
        }, 0, 1);
    }

    public void close() {
        this.executorService.shutdown();
    }


    public CompletableFuture<Component> applyPlaceholders(Player player, List<String> lines) {
        return CompletableFuture.supplyAsync(() -> createComponent(player, lines), executorService);
    }

    private Component createComponent(Player player, List<String> strings) {
        return Component.join(JoinConfiguration.separator(Component.newline()), strings.stream()
                .map(t -> papiManager.isPAPIEnabled() ? papiManager.setPlaceholders(player, t) : t)
                .map(t -> t.replace("#val#", String.valueOf(index)))
                .map(this::format)
                .toArray(Component[]::new));
    }

    private Component format(String value) {
        return plugin.getConfigManager().getSettings().getFormat().format(value);
    }


}
