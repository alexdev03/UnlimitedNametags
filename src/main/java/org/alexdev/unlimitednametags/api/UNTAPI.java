package org.alexdev.unlimitednametags.api;


import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.alexdev.unlimitednametags.packet.PacketNameTag;
import org.alexdev.unlimitednametags.vanish.VanishIntegration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * The UnlimitedNameTags API class.
 * Retrieve an instance of the API class via {@link #getInstance()}.
 */
@SuppressWarnings("unused")
public class UNTAPI {

    // Instance of the plugin
    private final UnlimitedNameTags plugin;
    private static UNTAPI instance;

    @ApiStatus.Internal
    protected UNTAPI(@NotNull UnlimitedNameTags plugin) {
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
    public static void register(@NotNull UnlimitedNameTags plugin) {
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
        plugin.getNametagManager().showToTrackedPlayers(player, plugin.getTrackerManager().getTrackedPlayers(player.getUniqueId()));
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
    public Optional<PacketNameTag> getPacketDisplayText(@NotNull Player player) {
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
