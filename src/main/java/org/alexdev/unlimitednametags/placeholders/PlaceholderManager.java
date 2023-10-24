package org.alexdev.unlimitednametags.placeholders;

import de.themoep.minedown.adventure.MineDown;
import me.clip.placeholderapi.PlaceholderAPI;
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

    public PlaceholderManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.executorService = Executors.newCachedThreadPool();
        startIndexTask();
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


    public CompletableFuture<Component> applyPlaceholders(Player player, List<String> lines) {
        return CompletableFuture.supplyAsync(() -> createComponent(player, lines), executorService);
    }

    private Component createComponent(Player player, List<String> strings) {
        return Component.join(JoinConfiguration.separator(Component.newline()), strings.stream()
                .map(text -> PlaceholderAPI.setPlaceholders(player, text))
                .map(t -> t.replace("#val#", String.valueOf(index)))
                .map(MineDown::parse)
                .toArray(Component[]::new));
    }

}
