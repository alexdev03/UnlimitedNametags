package org.alexdev.unlimitednametags.api;

import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.alexdev.unlimitednametags.vanish.VanishIntegration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Platform-neutral UnlimitedNameTags API (UUID-based). Paper/Bukkit extensions: {@code UNTPaperAPI} in the api-paper module.
 */
@SuppressWarnings("unused")
public abstract class UNTAPI {

    private static UNTAPI instance;

    protected final UnlimitedNameTagsInstance plugin;

    protected UNTAPI(@NotNull UnlimitedNameTagsInstance plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /**
     * @return the registered API instance (on Paper, cast to {@code UNTPaperAPI} or use {@code UNTPaperAPI.getInstance()})
     */
    @NotNull
    public static UNTAPI getInstance() {
        if (instance == null) {
            throw new NotRegisteredException();
        }
        return instance;
    }

    @ApiStatus.Internal
    protected static void setInstance(@Nullable UNTAPI api) {
        instance = api;
    }

    @NotNull
    protected UnlimitedNameTagsInstance plugin() {
        return plugin;
    }

    @NotNull
    public UntNametagManager nametagManager() {
        return plugin.getNametagManager();
    }

    @NotNull
    public UntVanishManager vanishManager() {
        return plugin.getVanishManager();
    }

    @NotNull
    public UntConditionalManager conditionalManager() {
        return plugin.getConditionalManager();
    }

    public void setVanishIntegration(@NotNull VanishIntegration vanishIntegration) {
        plugin.getVanishManager().setIntegration(vanishIntegration);
    }

    @NotNull
    public VanishIntegration getVanishIntegration() {
        return plugin.getVanishManager().getIntegration();
    }

    public void vanishPlayer(@NotNull UUID playerId) {
        plugin.getVanishManager().vanishPlayer(playerId);
    }

    public void unVanishPlayer(@NotNull UUID playerId) {
        plugin.getVanishManager().unVanishPlayer(playerId);
    }

    public void hideNametag(@NotNull UUID playerId) {
        plugin.getNametagManager().removeAllViewers(playerId);
    }

    public void showNametag(@NotNull UUID playerId) {
        plugin.getNametagManager().showToTrackedPlayers(playerId);
    }

    public void addHatHook(@NotNull HatHook hook) {
        plugin.getHatHooks().add(hook);
    }

    public void removeHatHook(@NotNull HatHook hook) {
        plugin.getHatHooks().remove(hook);
    }

    public void setNametagOverride(@NotNull UUID playerId, @NotNull Settings.NameTag nameTag) {
        plugin.getNametagManager().setNametagOverride(playerId, nameTag);
    }

    public void modifyNametagProperty(
            @NotNull UUID playerId,
            @NotNull Function<Settings.NameTag, Settings.NameTag> modifier) {
        final Settings.NameTag current = plugin.getNametagManager().getEffectiveNametag(playerId);
        plugin.getNametagManager().setNametagOverride(playerId, modifier.apply(current));
    }

    public void removeNametagOverride(@NotNull UUID playerId) {
        plugin.getNametagManager().removeNametagOverride(playerId);
    }

    public boolean hasNametagOverride(@NotNull UUID playerId) {
        return plugin.getNametagManager().hasNametagOverride(playerId);
    }

    @NotNull
    public Optional<Settings.NameTag> getNametagOverride(@NotNull UUID playerId) {
        return plugin.getNametagManager().getNametagOverride(playerId);
    }

    @NotNull
    public Settings.NameTag getEffectiveNametag(@NotNull UUID playerId) {
        return plugin.getNametagManager().getEffectiveNametag(playerId);
    }

    @NotNull
    public Settings.NameTag getConfigNametag(@NotNull UUID playerId) {
        return plugin.getNametagManager().getConfigNametag(playerId);
    }

    public void setShiftSystemBlocked(@NotNull UUID playerId, boolean blocked) {
        plugin.getNametagManager().setShiftSystemBlocked(playerId, blocked);
    }

    public boolean isShiftSystemBlocked(@NotNull UUID playerId) {
        return plugin.getNametagManager().isShiftSystemBlocked(playerId);
    }

    public void forceRefresh(@NotNull UUID playerId) {
        plugin.getNametagManager().refresh(playerId, true);
    }

    public void forceRefresh(@NotNull UUID playerId, boolean force) {
        plugin.getNametagManager().refresh(playerId, force);
    }

    public void setNametagScale(@NotNull UUID playerId, float scale) {
        final Settings.NameTag current = plugin.getNametagManager().getEffectiveNametag(playerId);
        plugin.getNametagManager().setNametagOverride(playerId, current.withScale(scale));
    }

    public void setNametagBackground(@NotNull UUID playerId, @NotNull Settings.Background background) {
        final Settings.NameTag current = plugin.getNametagManager().getEffectiveNametag(playerId);
        plugin.getNametagManager().setNametagOverride(playerId, current.withBackground(background));
    }

    public void setNametagDisplayGroups(@NotNull UUID playerId, @NotNull List<Settings.DisplayGroup> displayGroups) {
        final Settings.NameTag current = plugin.getNametagManager().getEffectiveNametag(playerId);
        plugin.getNametagManager().setNametagOverride(playerId, current.withDisplayGroups(displayGroups));
    }

    protected static final class NotRegisteredException extends IllegalStateException {

        private static final String MESSAGE = """
                Could not access the UnlimitedNameTags API as it has not yet been registered. This could be because:
                1) UnlimitedNameTags has failed to enable successfully
                2) You are attempting to access UnlimitedNameTags on plugin construction/before your plugin has enabled.
                3) You have shaded UnlimitedNameTags into your plugin jar and need to fix your maven/gradle/build script
                   to only include UnlimitedNameTags as a dependency and not as a shaded dependency.""";

        NotRegisteredException() {
            super(MESSAGE);
        }
    }
}
