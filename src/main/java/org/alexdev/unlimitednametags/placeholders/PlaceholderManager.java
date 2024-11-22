package org.alexdev.unlimitednametags.placeholders;

import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Getter
public class PlaceholderManager {

    private static final Component EMPTY = Component.text("");
    private static final int maxIndex = 16777215;
    private static final int maxMIndex = 10;
    private static final double minMGIndex = -1.0;
    private static final int MORE_LINES = 15;
    private final UnlimitedNameTags plugin;
    private final ExecutorService executorService;
    private final PAPIManager papiManager;
    private int index = maxIndex;
    private int mmIndex = maxMIndex;
    private double mGIndex = 1.0;
    private DecimalFormat decimalFormat;

    public PlaceholderManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.executorService = Executors.newFixedThreadPool(3, getThreadFactory());
        this.papiManager = new PAPIManager(plugin);
        startIndexTask();
        createDecimalFormat();
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

    private void createDecimalFormat() {
        decimalFormat = new DecimalFormat("#.#");
        decimalFormat.setRoundingMode(RoundingMode.DOWN);
    }

    private void startIndexTask() {
        plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> {
            index -= 1;
            if (index == 0) {
                index = 16777215;
            }
        }, 0, 1);
        plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> {
            mmIndex -= 1;
            if (mmIndex == 1) {
                mmIndex = maxMIndex;
            }
        }, 0, 2);
        plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> {
            mGIndex += 0.1;
            if (mGIndex >= 1d) {
                mGIndex = minMGIndex;
            }
        }, 0, 2);
    }

    public void close() {
        this.executorService.shutdown();
    }


    @NotNull
    public CompletableFuture<Map<Player, Component>> applyPlaceholders(@NotNull Player player, @NotNull List<Settings.LinesGroup> lines,
                                                                       @NotNull List<Player> relationalPlayers) {
        return getCheckedLines(player, lines).thenApply(strings -> createComponent(player, strings, relationalPlayers));
    }

    @NotNull
    private CompletableFuture<List<String>> getCheckedLines(@NotNull Player player, @NotNull List<Settings.LinesGroup> lines) {
        return CompletableFuture.supplyAsync(() -> lines.stream()
                .filter(l -> l.modifiers().stream().allMatch(m -> m.isVisible(player, plugin)))
                .map(Settings.LinesGroup::lines)
                .flatMap(List::stream)
                .toList(), executorService);
    }

    @NotNull
    public Component applyRelationalPlaceholders(@NotNull Player whosees, @NotNull Player target, @NotNull List<String> strings) {
        final PAPIManager papiManager = plugin.getPlaceholderManager().getPapiManager();
        if (!papiManager.isPAPIEnabled()) {
            return Component.join(JoinConfiguration.separator(Component.newline()), strings.stream()
                    .map(s -> replacePlaceholders(s, target))
                    .map(s -> format(s, target))
                    .toList());
        }

        return Component.join(JoinConfiguration.separator(Component.newline()), strings.stream()
                        .map(t -> papiManager.setRelationalPlaceholders(whosees, target, t))
                        .map(t -> format(t, whosees))
                        .toList())
                .compact();
    }

    @NotNull
    private Map<Player, Component> createComponent(@NotNull Player player, @NotNull List<String> strings, @NotNull List<Player> relationalPlayers) {
        final double moreLines = plugin.getHatHooks().stream()
                .mapToDouble(h -> h.getHigh(player))
                .filter(h -> h > 0)
                .findFirst()
                .orElse(0d);

        if (moreLines > 0) {
            strings = new ArrayList<>(strings);
            int lines = (int) (moreLines / MORE_LINES);
            for (int i = 0; i < lines; i++) {
                strings.add(" ");
            }
        }

        final List<String> stringsCopy = strings.stream()
                .map(s -> replacePlaceholders(s, player))
                .toList();
        return relationalPlayers.stream()
                .map(r -> Map.entry(r, Component.join(JoinConfiguration.separator(Component.newline()), stringsCopy.stream()
                                .map(t -> papiManager.isPAPIEnabled() ? papiManager.setRelationalPlaceholders(r, player, t) : t)
                                .filter(s -> !plugin.getConfigManager().getSettings().isRemoveEmptyLines() || !s.isEmpty())
                                .map(this::formatPhases)
                                .map(t -> format(t, player))
                                .filter(c -> !plugin.getConfigManager().getSettings().isRemoveEmptyLines() || !c.equals(EMPTY))
                                .toList())
                        .compact()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @NotNull
    private String formatPhases(@NotNull String value) {
        return value.replaceAll("#phase-md#", String.valueOf(index)).replaceAll("#phase-mm#", Integer.toString(mmIndex))
                .replaceAll("#phase-mm-g#", decimalFormat.format(mGIndex));
    }

    @NotNull
    private Component format(@NotNull String value, @NotNull Player player) {
        return plugin.getConfigManager().getSettings().getFormat().format(plugin, player, value);
    }

    @NotNull
    private String replacePlaceholders(@NotNull String string, @NotNull Player player) {
        for (Map.Entry<String, List<Settings.PlaceholderReplacement>> entry : plugin.getConfigManager().getSettings().getPlaceholdersReplacements().entrySet()) {
            if (!string.contains(entry.getKey())) {
                continue;
            }

            final String replaced = papiManager.setPlaceholders(player, entry.getKey());
            final Optional<Settings.PlaceholderReplacement> replacement = entry.getValue().stream()
                    .filter(r -> r.placeholder().equals(replaced))
                    .findFirst();

            if (replacement.isPresent()) {
                string = string.replace(entry.getKey(), replacement.get().replacement());
            }

        }

        if (papiManager.isPAPIEnabled()) {
            return papiManager.setPlaceholders(player, string);
        }

        return string;
    }

}
