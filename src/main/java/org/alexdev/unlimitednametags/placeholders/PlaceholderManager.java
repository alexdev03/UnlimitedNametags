package org.alexdev.unlimitednametags.placeholders;

import com.google.common.collect.Maps;
import lombok.Getter;
import net.jodah.expiringmap.ExpiringMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Settings;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class PlaceholderManager {

    public static final String PHASE_MD_KEY = "phase-md";
    public static final String PHASE_MM_KEY = "phase-mm";
    public static final String PHASE_MM_G_KEY = "phase-mm-g";
    public static final String NEG_PHASE_MD_KEY = "-phase-md";
    public static final String NEG_PHASE_MM_KEY = "-phase-mm";
    public static final String NEG_PHASE_MM_G_KEY = "-phase-mm-g";

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%.*?%", Pattern.DOTALL);
    private static final JoinConfiguration JOIN_CONFIGURATION = JoinConfiguration.separator(Component.newline());

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

    private BigDecimal miniGradientIndexBD = new BigDecimal("-1.0");
    private final BigDecimal stepBD = new BigDecimal("0.1");
    private final BigDecimal one = new BigDecimal("1.0");
    private final BigDecimal minusOne = new BigDecimal("-1.0");
    private final Map<String, Component> cachedComponents;
    private Map<UUID, Map<String, String>> cachedPlaceholders;
    private final Map<String, String> formattedPhaseValues;
    private final Map<String, Map<String, String>> placeholdersReplacements;

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
        this.placeholdersReplacements = Maps.newConcurrentMap();
        reloadPlaceholdersReplacements();
        createDecimalFormat();
        reload();
        startIndexTask();
    }

    public void reload() {
        cachedComponents.clear();
        this.cachedPlaceholders = Maps.newConcurrentMap();
        plugin.getPlayerListener().getOnlinePlayers().values().forEach(p -> cachedPlaceholders.put(p.getUniqueId(), ExpiringMap.builder()
                .expiration(plugin.getConfigManager().getSettings().getPlaceholderCacheTime() * 50L, TimeUnit.MILLISECONDS) // Adjusted unit based on previous code
                .build()));
        reloadPlaceholdersReplacements();
    }

    private void reloadPlaceholdersReplacements() {
        placeholdersReplacements.clear();
        plugin.getConfigManager().getSettings().getPlaceholdersReplacements().forEach((key, value) -> {
            final Map<String, String> replacements = Maps.newHashMapWithExpectedSize(value.size());
            value.forEach((pr) -> replacements.put(pr.placeholder().toLowerCase(Locale.ROOT), pr.replacement()));
            placeholdersReplacements.put(key.toLowerCase(Locale.ROOT), replacements);
        });
    }

    public void removePlayer(@NotNull Player player) {
        cachedPlaceholders.remove(player.getUniqueId());
    }

    private void createDecimalFormat() {
        decimalFormat = new DecimalFormat("#.#", DecimalFormatSymbols.getInstance(Locale.US));
        decimalFormat.setRoundingMode(RoundingMode.DOWN);

        final DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setDecimalSeparator('.');
        decimalFormat.setDecimalFormatSymbols(symbols);
    }

    private void startIndexTask() {
        plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> {
            index -= 1;
            if (index == 0) {
                index = maxIndex;
            }
        }, 0, 1);
        plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> {
            mmIndex -= 1;
            if (mmIndex == 1) {
                mmIndex = maxMIndex;
            }
        }, 0, 1);
        plugin.getTaskScheduler().runTaskTimerAsynchronously(() -> {
            miniGradientIndexBD = miniGradientIndexBD.add(stepBD);
            if (miniGradientIndexBD.compareTo(one) > 0) {
                miniGradientIndexBD = minusOne;
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
        papiManager.close();
    }


    @NotNull
    public CompletableFuture<Map<Player, Component>> applyPlaceholders(@NotNull Player player, @NotNull List<Settings.LinesGroup> lines,
                                                                       @NotNull List<Player> relationalPlayers) {
        return getCheckedLines(player, lines).thenApplyAsync(strings -> createComponent(player, strings, relationalPlayers), executorService);
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
            int linesCount = moreLinesInt / MORE_LINES;
            for (int i = 0; i < linesCount; i++) {
                emptyLines = emptyLines.append(Component.newline());
            }
        }

        final Component hatLines = emptyLines;
        final Settings settings = plugin.getConfigManager().getSettings();
        final boolean removeEmptyLines = settings.isRemoveEmptyLines();
        final boolean enableRelationalPlaceholders = settings.isEnableRelationalPlaceholders();

        final List<String> baseStrings = papiManager.isPapiEnabled() ?
                strings.stream()
                        .map(s -> replacePlaceholders(s, player, null))
                        .toList()
                : strings;

        if (enableRelationalPlaceholders) {
            final Map<Player, Component> result = Maps.newHashMapWithExpectedSize(relationalPlayers.size());
            for (Player viewer : relationalPlayers) {
                List<Component> processedLines = baseStrings.stream()
                        .map(line -> replacePlaceholders(line, player, viewer))
                        .filter(s -> !removeEmptyLines || !s.isEmpty())
                        .map(this::formatPhases)
                        .map(line -> format(line, player))
                        .filter(c -> !removeEmptyLines || !c.equals(Component.empty()))
                        .toList();
                Component finalComponent = joinLines(processedLines).append(hatLines);
                result.put(viewer, finalComponent);
            }
            return result;
        } else {
            List<Component> processedLines = baseStrings.stream()
                    .map(line -> replacePlaceholders(line, player, null))
                    .filter(s -> !removeEmptyLines || !s.isEmpty())
                    .map(this::formatPhases)
                    .map(line -> format(line, player))
                    .filter(c -> !removeEmptyLines || !c.equals(Component.empty()))
                    .toList();

            Component finalComponent = joinLines(processedLines).append(hatLines);

            final Map<Player, Component> result = Maps.newHashMapWithExpectedSize(relationalPlayers.size());
            for (Player viewer : relationalPlayers) {
                result.put(viewer, finalComponent);
            }
            return result;
        }
    }

    private Component joinLines(List<Component> lines) {
        return Component.join(JOIN_CONFIGURATION, lines);
    }

    @NotNull
    private String formatPhases(@NotNull String value) {
        if (value.indexOf('#') == -1) {
            return value;
        }

        String result = value.replace("#phase-mm-g#", formattedPhaseValues.getOrDefault(PHASE_MM_G_KEY, ""));
        if (result.indexOf('#') == -1) return result;

        result = result.replace("#-phase-mm-g#", formattedPhaseValues.getOrDefault(NEG_PHASE_MM_G_KEY, ""));
        if (result.indexOf('#') == -1) return result;

        result = result.replace("#phase-md#", formattedPhaseValues.getOrDefault(PHASE_MD_KEY, ""));
        if (result.indexOf('#') == -1) return result;

        result = result.replace("#phase-mm#", formattedPhaseValues.getOrDefault(PHASE_MM_KEY, ""));
        if (result.indexOf('#') == -1) return result;

        result = result.replace("#-phase-md#", formattedPhaseValues.getOrDefault(NEG_PHASE_MD_KEY, ""));
        if (result.indexOf('#') == -1) return result;

        return result.replace("#-phase-mm#", formattedPhaseValues.getOrDefault(NEG_PHASE_MM_KEY, ""));
    }

    @NotNull
    public String getFormattedPhases(@NotNull String text) {
        return formattedPhaseValues.getOrDefault(text, "");
    }

    private boolean containsAnyPlaceholders(String text) {
        return text != null && text.indexOf('%') != -1;
    }

    @NotNull
    private Component format(@NotNull String value, @NotNull Player player) {
        if (plugin.getConfigManager().getSettings().isComponentCaching()) {
            return cachedComponents.computeIfAbsent(value, v ->
                    plugin.getConfigManager().getSettings().getFormat().format(plugin, player, v)
            );
        }

        return plugin.getConfigManager().getSettings().getFormat().format(plugin, player, value);
    }

    @NotNull
    private String replacePlaceholders(@NotNull String string, @NotNull Player player, @Nullable Player viewer) {
        if (!containsAnyPlaceholders(string)) {
            return string;
        }

        final StringBuilder builder = new StringBuilder(string.length() + 32); // Pre-allocate extra space
        final Matcher matcher = PLACEHOLDER_PATTERN.matcher(string);
        int lastAppendPosition = 0;

        while (matcher.find()) {
            builder.append(string, lastAppendPosition, matcher.start());
            final String placeholder = matcher.group();
            final String value = viewer == null ? getCachedPlaceholder(player, placeholder)
                    : papiManager.setRelationalPlaceholders(viewer, player, placeholder);
            final String customReplacement = getReplacement(placeholder, value);
            builder.append(customReplacement != null ? customReplacement : value);
            lastAppendPosition = matcher.end();
        }

        builder.append(string, lastAppendPosition, string.length());

        final String intermediateResult = builder.toString();

        if (papiManager.isPapiEnabled()) {
            return viewer == null ? papiManager.setPlaceholders(player, intermediateResult)
                    : papiManager.setRelationalPlaceholders(viewer, player, intermediateResult);
        }

        return intermediateResult;
    }

    @Nullable
    private String getReplacement(@NotNull String placeholder, @NotNull String value) {
        var replacements = placeholdersReplacements.get(placeholder.toLowerCase(Locale.ROOT));
        if (replacements == null) {
            return null;
        }

        final String replacement = replacements.get(value.toLowerCase(Locale.ROOT));
        if (replacement != null) {
            return replacement;
        }

        return replacements.get(ELSE_PLACEHOLDER.toLowerCase(Locale.ROOT));
    }


    private Map<String, String> getCachedPlaceholders(@NotNull Player player) {
        return cachedPlaceholders.computeIfAbsent(player.getUniqueId(), u -> ExpiringMap.builder()
                .expiration(plugin.getConfigManager().getSettings().getPlaceholderCacheTime() * 50L, TimeUnit.MILLISECONDS)
                .build());
    }

    @NotNull
    public String getCachedPlaceholder(@NotNull Player player, @NotNull String placeholder) {
        final Map<String, String> playerCache = getCachedPlaceholders(player);
        return playerCache.computeIfAbsent(placeholder, p -> papiManager.setPlaceholders(player, p));
    }

}
