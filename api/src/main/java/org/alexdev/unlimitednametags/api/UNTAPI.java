package org.alexdev.unlimitednametags.api;


import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.config.DisplayAnimation;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.alexdev.unlimitednametags.vanish.VanishIntegration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * The UnlimitedNameTags API class.
 * Retrieve an instance of the API class via {@link #getInstance()}.
 */
@SuppressWarnings("unused")
public class UNTAPI {

    private final UnlimitedNameTagsPlugin plugin;
    private static UNTAPI instance;

    @ApiStatus.Internal
    protected UNTAPI(@NotNull UnlimitedNameTagsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Entrypoint to the {@link UNTAPI} API - returns an instance of the API
     *
     * @return instance of the UnlimitedNameTags API
     */
    @NotNull
    public static UNTAPI getInstance() {
        if (instance == null) {
            throw new NotRegisteredException();
        }
        return instance;
    }

    /**
     * <b>(Internal use only)</b> - Register the API.
     *
     * @param plugin the plugin instance
     */
    @ApiStatus.Internal
    public static void register(@NotNull UnlimitedNameTagsPlugin plugin) {
        instance = new UNTAPI(plugin);
    }

    /**
     * <b>(Internal use only)</b> - Unregister the API.
     */
    @ApiStatus.Internal
    public static void unregister() {
        instance = null;
    }

    /**
     * Sets the VanishIntegration for the UNTAPI.
     *
     * @param vanishIntegration the VanishIntegration to set
     */
    public void setVanishIntegration(@NotNull VanishIntegration vanishIntegration) {
        plugin.getVanishManager().setIntegration(vanishIntegration);
    }

    /**
     * Retrieves the VanishIntegration associated with the UNTAPI instance.
     * This integration allows checking if a player can see another player and if a player is vanished.
     *
     * @return The VanishIntegration instance associated with the UNTAPI
     */
    @NotNull
    public VanishIntegration getVanishIntegration() {
        return plugin.getVanishManager().getIntegration();
    }

    /**
     * Vanishes the player by hiding them from the tab list and scoreboard if enabled.
     *
     * @param player The player to vanish
     */
    public void vanishPlayer(@NotNull Player player) {
        plugin.getVanishManager().vanishPlayer(player);
    }

    /**
     * Un-vanishes the given player by showing them in the tab list and scoreboard if enabled.
     *
     * @param player The player to unvanish
     */
    public void unVanishPlayer(@NotNull Player player) {
        plugin.getVanishManager().unVanishPlayer(player);
    }

    /**
     * Hides the nametag of the specified player.
     *
     * @param player The player whose nametag should be hidden
     * @throws IllegalArgumentException if player is null
     * @since 1.5
     */
    public void hideNametag(@NotNull Player player) {
        plugin.getNametagManager().removeAllViewers(player);
    }

    /**
     * Shows the nametag of the specified player to the tracked players.
     *
     * @param player the player whose nametag should be shown
     * @throws IllegalArgumentException if player is null
     * @since 1.5
     */
    public void showNametag(@NotNull Player player) {
        plugin.getNametagManager().showToTrackedPlayers(player);
    }

    /**
     * Adds a {@link HatHook} to the plugin.
     * This hook is used to determine the height of the hat for a player.
     *
     * @param hook the hook to add
     */
    public void addHatHook(@NotNull HatHook hook) {
        plugin.getHatHooks().add(hook);
    }

    /**
     * Retrieves the display text packet for the specified player.
     *
     * @param player the player to retrieve the display text packet for
     * @return the display text packet for the player
     */
    public Collection<? extends UntNametagDisplay> getPacketDisplayText(@NotNull Player player) {
        return plugin.getNametagManager().getPacketDisplayText(player);
    }

    /**
     * Removes a {@link HatHook} from the plugin.
     *
     * @param hook the hook to remove
     */
    public void removeHatHook(@NotNull HatHook hook) {
        plugin.getHatHooks().remove(hook);
    }

    /**
     * Sets a complete nametag override for the specified player.
     * If the player already has a nametag, it will be swapped immediately.
     * If the player doesn't have a nametag yet, it will be cached and applied when the nametag is created.
     *
     * @param player the player to set the override for
     * @param nameTag the nametag override to set
     */
    public void setNametagOverride(@NotNull Player player, @NotNull Settings.NameTag nameTag) {
        plugin.getNametagManager().setNametagOverride(player, nameTag);
    }

    /**
     * Modifies a single property of the player's nametag by cloning the current nametag (override or config)
     * and applying the modification, then saving it as an override.
     *
     * @param player the player whose nametag should be modified
     * @param modifier a function that takes the current nametag and returns a modified nametag
     */
    public void modifyNametagProperty(@NotNull Player player, @NotNull Function<Settings.NameTag, Settings.NameTag> modifier) {
        final Settings.NameTag current = plugin.getNametagManager().getEffectiveNametag(player);
        final Settings.NameTag modified = modifier.apply(current);
        plugin.getNametagManager().setNametagOverride(player, modified);
    }

    /**
     * Removes the nametag override for the specified player, reverting to the config nametag.
     *
     * @param player the player to remove the override for
     */
    public void removeNametagOverride(@NotNull Player player) {
        plugin.getNametagManager().removeNametagOverride(player);
    }

    /**
     * Checks if the specified player has an active nametag override.
     *
     * @param player the player to check
     * @return true if the player has an override, false otherwise
     */
    public boolean hasNametagOverride(@NotNull Player player) {
        return plugin.getNametagManager().hasNametagOverride(player);
    }

    /**
     * Gets the nametag override for the specified player, if present.
     *
     * @param player the player to get the override for
     * @return an Optional containing the override if present, empty otherwise
     */
    @NotNull
    public Optional<Settings.NameTag> getNametagOverride(@NotNull Player player) {
        return plugin.getNametagManager().getNametagOverride(player);
    }

    /**
     * Gets the effective nametag for the specified player (override if present, otherwise config).
     *
     * @param player the player to get the effective nametag for
     * @return the effective nametag
     */
    @NotNull
    public Settings.NameTag getEffectiveNametag(@NotNull Player player) {
        return plugin.getNametagManager().getEffectiveNametag(player);
    }

    /**
     * Gets the config nametag for the specified player (without any override).
     *
     * @param player the player to get the config nametag for
     * @return the config nametag
     */
    @NotNull
    public Settings.NameTag getConfigNametag(@NotNull Player player) {
        return plugin.getNametagManager().getConfigNametag(player);
    }

    /**
     * Sets whether the shift system (sneak opacity) is blocked for the specified player.
     *
     * @param player the player to set the block for
     * @param blocked true to block the shift system, false to allow it
     */
    public void setShiftSystemBlocked(@NotNull Player player, boolean blocked) {
        plugin.getNametagManager().setShiftSystemBlocked(player, blocked);
    }

    /**
     * Checks if the shift system is blocked for the specified player.
     *
     * @param player the player to check
     * @return true if the shift system is blocked, false otherwise
     */
    public boolean isShiftSystemBlocked(@NotNull Player player) {
        return plugin.getNametagManager().isShiftSystemBlocked(player);
    }

    /**
     * Forces an immediate refresh of the player's nametag.
     *
     * @param player the player to refresh
     */
    public void forceRefresh(@NotNull Player player) {
        plugin.getNametagManager().refresh(player, true);
    }

    /**
     * Forces an immediate refresh of the player's nametag with the specified force option.
     *
     * @param player the player to refresh
     * @param force whether to force the refresh
     */
    public void forceRefresh(@NotNull Player player, boolean force) {
        plugin.getNametagManager().refresh(player, force);
    }

    /**
     * Sets the scale of the player's nametag by cloning the current nametag and applying the new scale.
     *
     * @param player the player whose nametag scale should be modified
     * @param scale the new scale value
     */
    public void setNametagScale(@NotNull Player player, float scale) {
        final Settings.NameTag current = plugin.getNametagManager().getEffectiveNametag(player);
        final Settings.NameTag modified = current.withScale(scale);
        plugin.getNametagManager().setNametagOverride(player, modified);
    }

    /**
     * Sets the background of the player's nametag by cloning the current nametag and applying the new background.
     *
     * @param player the player whose nametag background should be modified
     * @param background the new background
     */
    public void setNametagBackground(@NotNull Player player, @NotNull Settings.Background background) {
        final Settings.NameTag current = plugin.getNametagManager().getEffectiveNametag(player);
        final Settings.NameTag modified = current.withBackground(background);
        plugin.getNametagManager().setNametagOverride(player, modified);
    }

    /**
     * Sets the display groups of the player's nametag by cloning the current nametag and applying the new list.
     *
     * @param player the player whose nametag should be modified
     * @param displayGroups the new display groups (stacked text/item/block slots)
     */
    public void setNametagDisplayGroups(@NotNull Player player, @NotNull List<Settings.DisplayGroup> displayGroups) {
        final Settings.NameTag current = plugin.getNametagManager().getEffectiveNametag(player);
        final Settings.NameTag modified = current.withDisplayGroups(displayGroups);
        plugin.getNametagManager().setNametagOverride(player, modified);
    }

    /**
     * Sets or clears the {@link DisplayAnimation} on one stacked display group (0-based index), using a nametag override
     * cloned from {@link UntNametagManager#getEffectiveNametag(Player)}. Refreshes the player afterwards.
     *
     * @param player            target player
     * @param displayGroupIndex index into {@link Settings.NameTag#displayGroups()}
     * @param animation         new animation, or {@code null} to remove
     * @throws IllegalArgumentException if the index is out of range
     */
    public void setNametagDisplayGroupAnimation(@NotNull Player player, int displayGroupIndex, @Nullable DisplayAnimation animation) {
        final Settings.NameTag current = plugin.getNametagManager().getEffectiveNametag(player);
        final List<Settings.DisplayGroup> groups = new ArrayList<>(current.displayGroups());
        if (displayGroupIndex < 0 || displayGroupIndex >= groups.size()) {
            throw new IllegalArgumentException(
                    "displayGroupIndex " + displayGroupIndex + " out of range (size " + groups.size() + ")");
        }
        groups.set(displayGroupIndex, groups.get(displayGroupIndex).withAnimation(animation));
        final Settings.NameTag modified = new Settings.NameTag(current.permission(), List.copyOf(groups));
        plugin.getNametagManager().setNametagOverride(player, modified);
        plugin.getNametagManager().refresh(player, true);
    }

    /**
     * Removes the animation from the display group at the given index (same as {@link #setNametagDisplayGroupAnimation(Player, int, DisplayAnimation)} with {@code null}).
     */
    public void clearNametagDisplayGroupAnimation(@NotNull Player player, int displayGroupIndex) {
        setNametagDisplayGroupAnimation(player, displayGroupIndex, null);
    }

    /**
     * Registers a handler for {@link DisplayAnimation.CustomDisplayAnimation} ({@code type: custom} + {@code id} in YAML).
     *
     * @see UnlimitedNameTagsPlugin#registerNametagCustomAnimation
     */
    public void registerNametagCustomAnimation(@NotNull String id, @NotNull NametagCustomAnimationHandler handler) {
        plugin.registerNametagCustomAnimation(id, handler);
    }

    public boolean unregisterNametagCustomAnimation(@NotNull String id) {
        return plugin.unregisterNametagCustomAnimation(id);
    }

    @Nullable
    public NametagCustomAnimationHandler getNametagCustomAnimationHandler(@NotNull String id) {
        return plugin.getNametagCustomAnimationHandler(id);
    }

    /**
     * @deprecated Renamed to {@link #setNametagDisplayGroups(Player, List)}.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public void setNametagLines(@NotNull Player player, @NotNull List<Settings.DisplayGroup> displayGroups) {
        setNametagDisplayGroups(player, displayGroups);
    }

    /**
     * Sets the billboard constraints of the player's nametag.
     * Note: This modifies the display directly and doesn't require cloning the nametag.
     *
     * @param player the player whose nametag billboard should be modified
     * @param billboard the new billboard constraints
     */
    public void setNametagBillboard(@NotNull Player player, @NotNull AbstractDisplayMeta.BillboardConstraints billboard) {
        plugin.getNametagManager().getPacketDisplayText(player).forEach(packetNameTag -> {
            packetNameTag.setBillboard(billboard);
            packetNameTag.refresh();
        });
    }

    /**
     * Sets the shadowed property of the player's nametag background by cloning the current nametag and applying the modification.
     *
     * @param player the player whose nametag shadowed property should be modified
     * @param shadowed the new shadowed value
     */
    public void setNametagShadowed(@NotNull Player player, boolean shadowed) {
        final Settings.NameTag current = plugin.getNametagManager().getEffectiveNametag(player);
        Settings.NameTag modified = current.withDisplayGroups(group -> {
            final Settings.Background bg = group.effectiveBackground();
            Settings.Background newBg;
            if (bg instanceof Settings.IntegerBackground intBg) {
                newBg = new Settings.IntegerBackground(bg.enabled(), intBg.getRed(), intBg.getGreen(), intBg.getBlue(), bg.opacity(), shadowed, bg.seeThrough());
            } else if (bg instanceof Settings.HexBackground hexBg) {
                newBg = new Settings.HexBackground(bg.enabled(), hexBg.getHex(), bg.opacity(), shadowed, bg.seeThrough());
            } else {
                return group;
            }
            return new Settings.DisplayGroup(group.lines(), newBg, group.scale(), group.yOffset(), group.when(),
                    group.displayType(), group.itemMaterial(), group.blockMaterial(), group.itemDisplayMode(), group.animation(), group.animationInterval(), group.billboard());
        });

        plugin.getNametagManager().setNametagOverride(player, modified);
    }

    /**
     * Sets the seeThrough property of the player's nametag background by cloning the current nametag and applying the modification.
     *
     * @param player the player whose nametag seeThrough property should be modified
     * @param seeThrough the new seeThrough value
     */
    public void setNametagSeeThrough(@NotNull Player player, boolean seeThrough) {
        final Settings.NameTag current = plugin.getNametagManager().getEffectiveNametag(player);
        final Settings.NameTag modified = current.withDisplayGroups(group -> {
            final Settings.Background bg = group.effectiveBackground();
            Settings.Background newBg;
            if (bg instanceof Settings.IntegerBackground intBg) {
                newBg = new Settings.IntegerBackground(bg.enabled(), intBg.getRed(), intBg.getGreen(), intBg.getBlue(), bg.opacity(), bg.shadowed(), seeThrough);
            } else if (bg instanceof Settings.HexBackground hexBg) {
                newBg = new Settings.HexBackground(bg.enabled(), hexBg.getHex(), bg.opacity(), bg.shadowed(), seeThrough);
            } else {
                return group;
            }
            return new Settings.DisplayGroup(group.lines(), newBg, group.scale(), group.yOffset(), group.when(),
                    group.displayType(), group.itemMaterial(), group.blockMaterial(), group.itemDisplayMode(), group.animation(), group.animationInterval(), group.billboard());
        });
        plugin.getNametagManager().setNametagOverride(player, modified);
    }

    /**
     * Forces a specific component as the nametag for a player.
     * This overrides any existing nametag configuration or overrides.
     *
     * @param player the player whose nametag should be forced
     * @param component the component to display as the nametag
     */
    public void setForcedNametag(@NotNull Player player, @NotNull Component component) {
        plugin.getNametagManager().getPacketDisplayText(player).stream().findFirst().ifPresent(packetNameTag -> {
            packetNameTag.setForcedNameTag(component);
            packetNameTag.refresh();
        });
    }

    /**
     * Forces a specific component as the nametag for a player, visible only to a specific viewer.
     * This overrides any existing nametag configuration or overrides for that specific viewer.
     *
     * @param player the player whose nametag should be forced
     * @param viewer the player who will see the forced nametag
     * @param component the component to display as the nametag
     */
    public void setForcedNametag(@NotNull Player player, @NotNull Player viewer, @NotNull Component component) {
        plugin.getNametagManager().getPacketDisplayText(player).stream().findFirst().ifPresent(packetNameTag -> {
            packetNameTag.setForcedNameTag(viewer.getUniqueId(), component);
            packetNameTag.refreshForPlayer(viewer);
        });
    }

    /**
     * Clears the forced nametag for a player.
     *
     * @param player the player whose forced nametag should be cleared
     */
    public void clearForcedNametag(@NotNull Player player) {
        plugin.getNametagManager().getPacketDisplayText(player).stream().findFirst().ifPresent(packetNameTag -> {
            packetNameTag.clearForcedNameTag();
            packetNameTag.refresh();
        });
    }

    /**
     * Clears the forced nametag for a player, for a specific viewer.
     *
     * @param player the player whose forced nametag should be cleared
     * @param viewer the player who was seeing the forced nametag
     */
    public void clearForcedNametag(@NotNull Player player, @NotNull Player viewer) {
        plugin.getNametagManager().getPacketDisplayText(player).stream().findFirst().ifPresent(packetNameTag -> {
            packetNameTag.clearForcedNameTag(viewer.getUniqueId());
            packetNameTag.refreshForPlayer(viewer);
        });
    }

    static final class NotRegisteredException extends IllegalStateException {

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
