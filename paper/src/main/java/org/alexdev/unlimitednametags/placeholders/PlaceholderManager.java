package org.alexdev.unlimitednametags.placeholders;

import com.google.common.collect.Maps;
import lombok.Getter;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.TextReplacementConfig;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Advanced;
import org.alexdev.unlimitednametags.config.Formatter;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.hook.HelmetDebugContext;
import org.alexdev.unlimitednametags.hook.HelmetRuleDebugThrottle;
import org.alexdev.unlimitednametags.hook.HelmetRuleDiagnostics;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
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

    private static final Pattern RELATIONAL_PATTERN = Pattern.compile("%(rel|relational)_[a-zA-Z0-9_]+%");

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("%.*?%", Pattern.DOTALL);
    private static final JoinConfiguration JOIN_CONFIGURATION = JoinConfiguration.separator(Component.newline());

    private static final String ELSE_PLACEHOLDER = "ELSE";
    private static final int maxIndex = 16777215;
    private static final int maxMIndex = 10;
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
    private Map<UUID, ExpiringMap<String, String>> cachedPlaceholders;
    private Map<String, Long> perPlaceholderTtlMs;
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
        updateFormattedPhaseValues(); // Populate immediately to avoid race: gradient with #-phase-mm-g# fails when map is empty
        startIndexTask();
    }

    public void reload() {
        cachedComponents.clear();
        this.cachedPlaceholders = Maps.newConcurrentMap();
        plugin.getPlayerListener().getOnlinePlayers().values().forEach(p ->
                cachedPlaceholders.put(p.getUniqueId(), buildExpiringMap()));
        reloadPlaceholdersReplacements();
        reloadPerPlaceholderTtls();
    }

    private ExpiringMap<String, String> buildExpiringMap() {
        return ExpiringMap.builder()
                .variableExpiration()
                .build();
    }

    private void reloadPerPlaceholderTtls() {
        final Map<String, Long> map = Maps.newConcurrentMap();
        final long taskMs = plugin.getConfigManager().getSettings().getBehavior().getTaskInterval() * 50L;
        plugin.getConfigManager().getSettings().getPerformance().getPlaceholderUpdateRates()
                .forEach((ph, ticks) -> map.put(ph.toLowerCase(Locale.ROOT), Math.max(taskMs, ticks * 50L)));
        this.perPlaceholderTtlMs = map;
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
        HelmetRuleDebugThrottle.remove(player.getUniqueId());
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
    public CompletableFuture<Map<Player, Component>> applyPlaceholders(@NotNull Player player, @NotNull Settings.DisplayGroup group,
                                                                       @NotNull List<Player> relationalPlayers) {
        if (requiresRelationalEvaluation(group)) {
            return CompletableFuture.supplyAsync(() -> {
                final Map<Player, Component> result = Maps.newHashMapWithExpectedSize(relationalPlayers.size());
                for (Player viewer : relationalPlayers) {
                    final List<String> strings = collectActiveLineTexts(player, viewer, group);
                    result.put(viewer, buildComponentForViewer(player, viewer, strings));
                }
                return result;
            }, executorService);
        }
        return getCheckedLines(player, group).thenApplyAsync(strings -> createComponent(player, strings, relationalPlayers), executorService);
    }

    /**
     * Expands placeholders in a raw string for the given player (non-relational).
     */
    @NotNull
    public String expandForOwner(@NotNull Player player, @NotNull String raw) {
        return replacePlaceholders(raw, player, null);
    }

    /**
     * Same visibility as checked lines resolution: when {@code false}, text lines are empty and item/block displays should hide content.
     * When {@code relationalViewer} is non-null and the group has {@link Settings.DisplayGroup#relationalConditions()},
     * the group {@code when} is evaluated with relational placeholders (viewer → owner).
     */
    public boolean isDisplayGroupActive(@NotNull Player owner, @NotNull Settings.DisplayGroup group, @Nullable Player relationalViewer) {
        if (group.when() == null || group.when().isBlank()) {
            return true;
        }
        final String expr = group.when().trim();
        final boolean isRelational = group.relationalConditions() || containsRelationalPlaceholders(expr);
        if (!isRelational || relationalViewer == null) {
            return plugin.getConditionalManager().evaluateCondition(expr, owner);
        }
        return plugin.getConditionalManager().evaluateCondition(expr, relationalViewer, owner);
    }

    /**
     * Same as {@link #isDisplayGroupActive(Player, Settings.DisplayGroup, Player)} with {@code relationalViewer = null} (owner-based).
     */
    public boolean isDisplayGroupActive(@NotNull Player player, @NotNull Settings.DisplayGroup group) {
        return isDisplayGroupActive(player, group, null);
    }

    @NotNull
    private List<String> collectActiveLineTexts(@NotNull Player owner, @NotNull Player viewer, @NotNull Settings.DisplayGroup group) {
        final boolean isGroupRelational = requiresRelationalEvaluation(group);
        if (!isDisplayGroupActive(owner, group, isGroupRelational ? viewer : null)) {
            return List.of();
        }
        return group.lines().stream()
                .filter(line -> line.when() == null
                        || line.when().isBlank()
                        || evaluateLineWhen(owner, viewer, line, isGroupRelational))
                .map(Settings.NametagLine::text)
                .toList();
    }

    private boolean evaluateLineWhen(@NotNull Player owner, @NotNull Player viewer, @NotNull Settings.NametagLine line, boolean relationalGroup) {
        if (line.when() == null || line.when().isBlank()) {
            return true;
        }
        final String expr = line.when().trim();
        final boolean isRelational = relationalGroup || containsRelationalPlaceholders(expr);
        if (!isRelational) {
            return plugin.getConditionalManager().evaluateCondition(expr, owner);
        }
        return plugin.getConditionalManager().evaluateCondition(expr, viewer, owner);
    }

    @NotNull
    private Component buildComponentForViewer(@NotNull Player owner, @NotNull Player viewer, @NotNull List<String> strings) {
        final Settings settings = plugin.getConfigManager().getSettings();
        final boolean removeEmptyLines = settings.getBehavior().isRemoveEmptyLines();

        final List<String> baseStrings = papiManager.isPapiEnabled() ?
                strings.stream()
                        .map(s -> replacePlaceholders(s, owner, null))
                        .toList()
                : strings;

        final List<Component> baseComponents = baseStrings.stream()
                .map(this::formatPhases)
                .map(line -> format(line, owner))
                .toList();

        final List<Component> processedLines = baseComponents.stream()
                .map(component -> resolveRelationalPlaceholdersInComponent(component, owner, viewer))
                .filter(c -> !removeEmptyLines || !c.equals(Component.empty()))
                .toList();

        return joinLines(processedLines);
    }

    @NotNull
    private Component resolveRelationalPlaceholdersInComponent(@NotNull Component component, @NotNull Player owner, @NotNull Player viewer) {
        if (!papiManager.isPapiEnabled()) {
            return component;
        }
        return component.replaceText(TextReplacementConfig.builder()
                .match(RELATIONAL_PATTERN)
                .replacement((matchResult, builder) -> {
                    final String placeholder = matchResult.group();
                    final String resolved = papiManager.setRelationalPlaceholders(viewer, owner, placeholder);
                    return format(resolved, owner);
                })
                .build());
    }

    @NotNull
    private CompletableFuture<List<String>> getCheckedLines(@NotNull Player player, @NotNull Settings.DisplayGroup group) {
        return CompletableFuture.supplyAsync(() -> collectActiveLineTexts(player, player, group), executorService);
    }

    @NotNull
    private Map<Player, Component> createComponent(@NotNull Player player, @NotNull List<String> strings, @NotNull List<Player> relationalPlayers) {
        // Issue #49: helmet height compensation used to be done by prepending empty newlines to the text component,
        // which stretched the text-display background. The vertical offset is now applied via PacketNameTag#setHelmetExtraOffset
        // in NameTagManager, so the component only carries the real text here.
        final Settings settings = plugin.getConfigManager().getSettings();
        final boolean removeEmptyLines = settings.getBehavior().isRemoveEmptyLines();
        final boolean enableRelationalPlaceholders = settings.getPerformance().isEnableRelationalPlaceholders();

        final List<String> baseStrings = papiManager.isPapiEnabled() ?
                strings.stream()
                        .map(s -> replacePlaceholders(s, player, null))
                        .toList()
                : strings;

        final List<Component> baseComponents = baseStrings.stream()
                .map(this::formatPhases)
                .map(line -> format(line, player))
                .toList();

        if (enableRelationalPlaceholders) {
            final Map<Player, Component> result = Maps.newHashMapWithExpectedSize(relationalPlayers.size());
            for (Player viewer : relationalPlayers) {
                final List<Component> processedLines = baseComponents.stream()
                        .map(component -> resolveRelationalPlaceholdersInComponent(component, player, viewer))
                        .filter(c -> !removeEmptyLines || !c.equals(Component.empty()))
                        .toList();
                result.put(viewer, joinLines(processedLines));
            }
            return result;
        } else {
            final List<Component> processedLines = baseComponents.stream()
                    .filter(c -> !removeEmptyLines || !c.equals(Component.empty()))
                    .toList();

            final Component finalComponent = joinLines(processedLines);

            final Map<Player, Component> result = Maps.newHashMapWithExpectedSize(relationalPlayers.size());
            for (Player viewer : relationalPlayers) {
                result.put(viewer, finalComponent);
            }
            return result;
        }
    }

    /**
     * Computes the vertical translation (in blocks) to apply to a nametag display in order to clear the player's
     * cosmetic helmet. Returns 0 when no hat hook reports a height. Replaces the old newline-based stretching
     * to keep text-display backgrounds tight around the actual text (issue #49).
     */
    public float computeHelmetExtraOffset(@NotNull Player player) {
        final Advanced advanced = plugin.getConfigManager().getAdvanced();
        final boolean dbgEnabled = advanced.isHelmetRulesDebug();
        final boolean verbose = dbgEnabled && HelmetRuleDebugThrottle.tryConsume(player.getUniqueId(), advanced.getHelmetRulesDebugCooldownMs());

        try {
            HelmetDebugContext.setVerbose(verbose);
            if (verbose) {
                HelmetRuleDiagnostics.logEquippedHelmet(plugin, player);
                plugin.getLogger().info("[UNT helmet dbg] computing offset (hat hooks=" + plugin.getHatHooks().size() + ")");
            }

            double rawHeight = 0d;
            HatHook winner = null;
            for (final HatHook hook : plugin.getHatHooks()) {
                final double v = hook.getHigh(player.getUniqueId());
                if (verbose) {
                    plugin.getLogger().info("[UNT helmet dbg]   " + hook.getClass().getSimpleName() + ".getHigh -> " + v);
                }
                if (v > 0d && rawHeight <= 0d) {
                    rawHeight = v;
                    winner = hook;
                    break;
                }
            }

            if (verbose) {
                plugin.getLogger().info("[UNT helmet dbg] first hook with height>0: "
                        + (winner == null ? "<none>" : winner.getClass().getSimpleName())
                        + " rawHeight=" + rawHeight);
            }

            if (rawHeight <= 0d) {
                if (verbose) {
                    plugin.getLogger().info("[UNT helmet dbg] final helmetExtraOffset=0 (no positive raw height)");
                }
                return 0f;
            }
            final float multiplier = advanced.getHelmetHeightYOffsetMultiplier();
            final float offset = (float) rawHeight * multiplier;
            if (verbose) {
                plugin.getLogger().info("[UNT helmet dbg] multiplier=" + multiplier + " final helmetExtraOffset=" + offset);
            }
            return offset;
        } finally {
            HelmetDebugContext.clear();
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

        String result = value.replace("#phase-mm-g#", formattedPhaseValues.getOrDefault(PHASE_MM_G_KEY, "0"));
        if (result.indexOf('#') == -1) return result;

        result = result.replace("#-phase-mm-g#", formattedPhaseValues.getOrDefault(NEG_PHASE_MM_G_KEY, "0"));
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

    public static boolean containsRelationalPlaceholders(@Nullable String text) {
        if (text == null) {
            return false;
        }
        return text.contains("%rel_") || text.contains("%relational_");
    }

    public boolean requiresRelationalEvaluation(@NotNull Settings.DisplayGroup group) {
        if (group.relationalConditions()) {
            return true;
        }
        if (containsRelationalPlaceholders(group.when())) {
            return true;
        }
        for (Settings.NametagLine line : group.lines()) {
            if (containsRelationalPlaceholders(line.when())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAnyPlaceholders(String text) {
        return text != null && text.indexOf('%') != -1;
    }

    @NotNull
    private Component format(@NotNull String value, @NotNull Player player) {
        if (plugin.getConfigManager().getSettings().getPerformance().isComponentCaching()) {
            return cachedComponents.computeIfAbsent(value, v ->
                    Formatter.from(plugin.getConfigManager().getSettings().getBehavior().getFormat()).format(plugin, player, v)
            );
        }

        return Formatter.from(plugin.getConfigManager().getSettings().getBehavior().getFormat()).format(plugin, player, value);
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


    private ExpiringMap<String, String> getCachedPlaceholders(@NotNull Player player) {
        return cachedPlaceholders.computeIfAbsent(player.getUniqueId(), u -> buildExpiringMap());
    }

    private long effectiveTtlMs(@NotNull String placeholder) {
        final Map<String, Long> rates = perPlaceholderTtlMs;
        if (rates != null) {
            final Long custom = rates.get(placeholder.toLowerCase(Locale.ROOT));
            if (custom != null) return custom;
        }
        return Math.max(1L, plugin.getConfigManager().getSettings().getPerformance().getPlaceholderCacheTime()) * 50L;
    }

    @NotNull
    public String getCachedPlaceholder(@NotNull Player player, @NotNull String placeholder) {
        final ExpiringMap<String, String> playerCache = getCachedPlaceholders(player);
        final String cached = playerCache.get(placeholder);
        if (cached != null) return cached;
        final String fresh = papiManager.setPlaceholders(player, placeholder);
        playerCache.put(placeholder, fresh, ExpirationPolicy.CREATED, effectiveTtlMs(placeholder), TimeUnit.MILLISECONDS);
        return fresh;
    }

}
