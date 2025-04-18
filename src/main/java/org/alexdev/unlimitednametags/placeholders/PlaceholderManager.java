package org.alexdev.unlimitednametags.placeholders;

import com.google.common.collect.Maps;
import lombok.Getter;
import net.jodah.expiringmap.ExpiringMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Getter
public class PlaceholderManager {

    private static final String ELSE_PLACEHOLDER = "ELSE";
    private static final int maxIndex = 16777215;
    private static final int maxMIndex = 10;
    private static final double minMGIndex = -1.0;
    private static final int MORE_LINES = 14;
    private final UnlimitedNameTags plugin;
    private final ExecutorService executorService;
    private final PAPIManager papiManager;
    private int index = maxIndex;
    private int mmIndex = maxMIndex;
    private final double miniGradientIndex = 1.0;
    private DecimalFormat decimalFormat;

    private BigDecimal miniGradientIndexBD = new BigDecimal("-1.0");
    private final BigDecimal stepBD = new BigDecimal("0.1");
    private final BigDecimal one = new BigDecimal("1.0");
    private final Map<String, Component> cachedComponents;
    private Map<UUID, Map<String, String>> cachedPlaceholders;

    public PlaceholderManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.executorService = Executors.newWorkStealingPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
        );
        this.papiManager = new PAPIManager(plugin);
        this.cachedComponents = ExpiringMap.builder()
                .expiration(2, TimeUnit.MINUTES)
                .build();
        reload();
        startIndexTask();
        createDecimalFormat();
    }

    public void reload() {
        cachedComponents.clear();
        this.cachedPlaceholders = Maps.newConcurrentMap();
        Bukkit.getOnlinePlayers().forEach(p -> cachedPlaceholders.put(p.getUniqueId(), ExpiringMap.builder()
                .expiration(plugin.getConfigManager().getSettings().getPlaceholderCacheTime() * 50L, TimeUnit.MINUTES)
                .build()));
    }

    public void removePlayer(@NotNull Player player) {
        cachedPlaceholders.remove(player.getUniqueId());
    }

    private void createDecimalFormat() {
        decimalFormat = new DecimalFormat("#.#");
        decimalFormat.setRoundingMode(RoundingMode.DOWN);

        final DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        decimalFormat.setDecimalFormatSymbols(symbols);
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
//            miniGradientIndex += 0.1;
//            if (miniGradientIndex >= 1.0 - 0.000001) {
//                miniGradientIndex = minMGIndex;
//            }
            miniGradientIndexBD = miniGradientIndexBD.add(stepBD);
            if (miniGradientIndexBD.compareTo(one) >= 0) {
                miniGradientIndexBD = new BigDecimal("-1.0");
            }
        }, 0, 1);
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
    private Map<Player, Component> createComponent(@NotNull Player player, @NotNull List<String> strings, @NotNull List<Player> relationalPlayers) {
        final double moreLines = plugin.getHatHooks().stream()
                .mapToDouble(h -> h.getHigh(player))
                .filter(h -> h > 0)
                .findFirst()
                .orElse(0d);

        //integer just greater
        final int moreLinesInt = (int) Math.ceil(moreLines);

        if (moreLinesInt > 0) {
            strings = new ArrayList<>(strings);
            int lines = moreLinesInt / MORE_LINES;
            for (int i = 0; i < lines; i++) {
                strings.add(" ");
            }
        }

        final List<String> stringsCopy = papiManager.isPAPIEnabled() ?
                strings.stream()
                        .map(s -> replacePlaceholders(s, player, null))
                        .toList()
                : strings;
        return relationalPlayers.stream()
                .map(r -> Map.entry(r, Component.join(JoinConfiguration.separator(Component.newline()), stringsCopy.stream()
                                .map(t -> replacePlaceholders(t, player, r))
                                .filter(s -> !plugin.getConfigManager().getSettings().isRemoveEmptyLines() || !s.isEmpty())
                                .map(this::formatPhases)
                                .map(t -> format(t, player))
                                .filter(c -> !plugin.getConfigManager().getSettings().isRemoveEmptyLines() || !c.equals(Component.empty()))
                                .toList())
                        .compact()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @NotNull
    private String formatPhases(@NotNull String value) {
        return value.replace("#phase-md#", Integer.toString(index))
                .replace("#phase-mm#", Integer.toString(mmIndex))
                .replace("#phase-mm-g#", decimalFormat.format(new BigDecimal(miniGradientIndexBD.toPlainString())))
                .replace("#-phase-md#", Integer.toString(maxIndex - index))
                .replace("#-phase-mm#", Integer.toString(maxMIndex - mmIndex))
                .replace("#-phase-mm-g#", decimalFormat.format(new BigDecimal(miniGradientIndexBD.multiply(BigDecimal.valueOf(-1)).toPlainString())));
    }

    private boolean containsAnyPlaceholders(String text) {
        return text.contains("%");
    }

    @NotNull
    private Component format(@NotNull String value, @NotNull Player player) {
        if (plugin.getConfigManager().getSettings().isComponentCaching()) {
            final Component cached = cachedComponents.get(value);
            if (cached != null) {
                return cached;
            }
        }

        final Component component = plugin.getConfigManager().getSettings().getFormat().format(plugin, player, value);
        if (plugin.getConfigManager().getSettings().isComponentCaching()) {
            cachedComponents.put(value, component);
        }

        return component;
    }

    @NotNull
    private String replacePlaceholders(@NotNull String string, @NotNull Player player, @Nullable Player viewer) {
        if (!containsAnyPlaceholders(string)) {
            return string;
        }

        for (Map.Entry<String, List<Settings.PlaceholderReplacement>> entry : plugin.getConfigManager().getSettings().getPlaceholdersReplacements().entrySet()) {
            if (!string.contains(entry.getKey())) {
                continue;
            }

            final String replaced;
            if (papiManager.isPAPIEnabled()) {
                if (viewer == null) {
                    replaced = getCachedPlaceholder(player, entry.getKey());
                } else {
                    replaced = papiManager.setRelationalPlaceholders(viewer, player, entry.getKey());
                }
            } else {
                replaced = "";
            }
            final Optional<Settings.PlaceholderReplacement> replacement = entry.getValue().stream()
                    .filter(r -> r.placeholder().equals(replaced))
                    .findFirst();

            if (replacement.isPresent()) {
                string = string.replace(entry.getKey(), replacement.get().replacement());
            } else {
                final Optional<Settings.PlaceholderReplacement> elseReplacement = entry.getValue().stream()
                        .filter(r -> r.placeholder().equalsIgnoreCase(ELSE_PLACEHOLDER))
                        .findFirst();
                if (elseReplacement.isPresent()) {
                    string = string.replace(entry.getKey(), elseReplacement.get().replacement());
                }
            }

        }

        if (papiManager.isPAPIEnabled()) {
            if (viewer == null) {
                return papiManager.setPlaceholders(player, string);
            }
            return papiManager.setRelationalPlaceholders(viewer, player, string);
        }

        return string;
    }

    private Map<String, String> getCachedPlaceholders(@NotNull Player player) {
        return cachedPlaceholders.computeIfAbsent(player.getUniqueId(), u -> ExpiringMap.builder()
                .expiration(plugin.getConfigManager().getSettings().getPlaceholderCacheTime() * 50L, TimeUnit.MINUTES)
                .build());
    }

    @NotNull
    public String getCachedPlaceholder(@NotNull Player player, @NotNull String placeholder) {
        final Map<String, String> cachedPlaceholders = getCachedPlaceholders(player);
        if (cachedPlaceholders.containsKey(placeholder)) {
            return cachedPlaceholders.get(placeholder);
        }

        final String replaced = papiManager.setPlaceholders(player, placeholder);
        cachedPlaceholders.put(placeholder, replaced);
        return replaced;
    }

}
