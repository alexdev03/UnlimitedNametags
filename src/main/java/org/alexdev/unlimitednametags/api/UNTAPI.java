package org.alexdev.unlimitednametags.api;


import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.vanish.VanishIntegration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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
     * @return instance of the HuskSync API
     * @since 1.5.2
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
     * @since 3.0
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
     */
    public void hideNametag(@NotNull Player player) {
        plugin.getNametagManager().removeAllViewers(player);
    }

    /**
     * Shows the nametag of the specified player to the tracked players.
     *
     * @param player the player whose nametag should be shown
     * @throws IllegalArgumentException if player is null
     */
    public void showNametag(@NotNull Player player) {
        plugin.getNametagManager().showToTrackedPlayers(player, plugin.getTrackerManager().getTrackedPlayers(player.getUniqueId()));
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
