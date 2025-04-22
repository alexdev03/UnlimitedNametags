package org.alexdev.unlimitednametags.placeholders;

import com.google.common.collect.Maps;
import lombok.Getter;
import net.jodah.expiringmap.ExpiringMap;
import net.kyori.adventure.text.Component;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Getter
public class PlaceholderManager {

    public static final String PHASE_MD_KEY = "phase-md";
    public static final String PHASE_MM_KEY = "phase-mm";
    public static final String PHASE_MM_G_KEY = "phase-mm-g";
    public static final String NEG_PHASE_MD_KEY = "-phase-md";
    public static final String NEG_PHASE_MM_KEY = "-phase-mm";
    public static final String NEG_PHASE_MM_G_KEY = "-phase-mm-g";

    private static final String ELSE_PLACEHOLDER = "ELSE";
    private static final int maxIndex = 16777215;
    private static final int maxMIndex = 10;
    private static final int MORE_LINES = 14;
    private final UnlimitedNameTags plugin;
    private final ExecutorService executorService;
    private final PAPIManager papiManager;
    private int index = maxIndex;
    private int mmIndex = maxMIndex;
    private DecimalFormat decimalFormat;
    private final UntPapiExpansion untPapiExpansion;

    private BigDecimal miniGradientIndexBD = new BigDecimal("-1.0");
    private final BigDecimal stepBD = new BigDecimal("0.1");
    private final BigDecimal one = new BigDecimal("1.0");
    private final BigDecimal minusOne = new BigDecimal("-1.0");
    private final Map<String, Component> cachedComponents;
    private Map<UUID, Map<String, String>> cachedPlaceholders;
    private final Map<String, String> formattedPhaseValues;

    public PlaceholderManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.executorService = Executors.newWorkStealingPool(
                Math.max(2, Runtime.getRuntime().availableProcessors() / 2)
        );
        this.papiManager = new PAPIManager(plugin);
        this.cachedComponents = ExpiringMap.builder()
                .expiration(2, TimeUnit.MINUTES)
                .build();
        this.formattedPhaseValues = Maps.newConcurrentMap();
        this.untPapiExpansion = new UntPapiExpansion(plugin);
        this.untPapiExpansion.register();
        createDecimalFormat();
        reload();
        startIndexTask();
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
            miniGradientIndexBD = miniGradientIndexBD.add(stepBD);
            if (miniGradientIndexBD.compareTo(one) >= 0) {
                miniGradientIndexBD = new BigDecimal("-1.0");
            }

            updateFormattedPhaseValues();
        }, 0, 1);
    }

    private void updateFormattedPhaseValues() {
        final int currentIndex = this.index;
        final int currentMmIndex = this.mmIndex;
        final BigDecimal currentMiniGradientIndexBD = this.miniGradientIndexBD;

        final String indexStr = Integer.toString(currentIndex);
        final String mmIndexStr = Integer.toString(currentMmIndex);
        final String phaseMmGStr = decimalFormat.format(currentMiniGradientIndexBD);
        final String negIndexStr = Integer.toString(maxIndex - currentIndex);
        final String negMmIndexStr = Integer.toString(maxMIndex - currentMmIndex);
        final String negPhaseMmGStr = decimalFormat.format(currentMiniGradientIndexBD.multiply(minusOne));

        formattedPhaseValues.put(PHASE_MD_KEY, indexStr);
        formattedPhaseValues.put(PHASE_MM_KEY, mmIndexStr);
        formattedPhaseValues.put(PHASE_MM_G_KEY, phaseMmGStr);
        formattedPhaseValues.put(NEG_PHASE_MD_KEY, negIndexStr);
        formattedPhaseValues.put(NEG_PHASE_MM_KEY, negMmIndexStr);
        formattedPhaseValues.put(NEG_PHASE_MM_G_KEY, negPhaseMmGStr);
    }

    public void close() {
        this.executorService.shutdown();
        this.untPapiExpansion.unregister();
    }


    @NotNull
    public CompletableFuture<Map<Player, Component>> applyPlaceholders(@NotNull Player player, @NotNull List<Settings.LinesGroup> lines,
                                                                       @NotNull List<Player> relationalPlayers) {
        return getCheckedLines(player, lines).thenApply(strings -> createComponent(player, strings, relationalPlayers));
    }

    @NotNull
    private CompletableFuture<List<String>> getCheckedLines(@NotNull Player player, @NotNull List<Settings.LinesGroup> lines) {
        return CompletableFuture.supplyAsync(() -> lines.stream()
                .filter(l -> l.modifiers() == null || l.modifiers().isEmpty() || l.modifiers().stream().allMatch(m -> m.isVisible(player, plugin)))
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

        final int moreLinesInt = (int) Math.ceil(moreLines);
        Component emptyLines = Component.empty();
        if (moreLinesInt > 0) {
            int lines = moreLinesInt / MORE_LINES;
            for (int i = 0; i < lines; i++) {
                emptyLines = emptyLines.append(Component.newline());
            }
        }

        final Component hatLines = emptyLines;
        final List<String> stringsCopy = papiManager.isPapiEnabled() ?
                strings.stream()
                        .map(s -> replacePlaceholders(s, player, null))
                        .toList()
                : strings;
        return relationalPlayers.stream()
                .map(r -> Map.entry(r, joinLines(stringsCopy.stream()
                        .map(t -> replacePlaceholders(t, player, r))
                        .filter(s -> !plugin.getConfigManager().getSettings().isRemoveEmptyLines() || !s.isEmpty())
                        .map(this::formatPhases)
                        .map(t -> format(t, player))
                        .filter(c -> !plugin.getConfigManager().getSettings().isRemoveEmptyLines() || !c.equals(Component.empty()))
                        .toList()
                )))
                .map(e -> Map.entry(e.getKey(), e.getValue().append(hatLines)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Component joinLines(List<Component> lines) {
        return lines.stream()
                .reduce(Component.empty(), (a, b) -> a.appendNewline().append(b));
    }

    @NotNull
    private String formatPhases(@NotNull String value) {
        return value.replace("#phase-mm-g#", formattedPhaseValues.getOrDefault(PHASE_MM_G_KEY, ""))
                .replace("#-phase-mm-g#", formattedPhaseValues.getOrDefault(NEG_PHASE_MM_G_KEY, ""))
                .replace("#phase-md#", formattedPhaseValues.getOrDefault(PHASE_MD_KEY, ""))
                .replace("#phase-mm#", formattedPhaseValues.getOrDefault(PHASE_MM_KEY, ""))
                .replace("#-phase-md#", formattedPhaseValues.getOrDefault(NEG_PHASE_MD_KEY, ""))
                .replace("#-phase-mm#", formattedPhaseValues.getOrDefault(NEG_PHASE_MM_KEY, ""));
    }

    @NotNull
    public String getFormattedPhases(@NotNull String text) {
        return formattedPhaseValues.getOrDefault(text, "");
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
            if (papiManager.isPapiEnabled()) {
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

        if (papiManager.isPapiEnabled()) {
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
