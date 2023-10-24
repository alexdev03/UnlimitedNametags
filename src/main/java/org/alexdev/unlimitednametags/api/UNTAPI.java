/*
 * This file is part of Velocitab, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package org.alexdev.unlimitednametags.api;


import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.vanish.VanishIntegration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("unused")

/**
 * The UnlimitedNameTags API class.
 * Retrieve an instance of the API class via {@link #getInstance()}.
 */ public class UNTAPI {

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


    static final class NotRegisteredException extends IllegalStateException {

        private static final String MESSAGE = """
                Could not access the Velocitab API as it has not yet been registered. This could be because:
                1) Velocitab has failed to enable successfully
                2) You are attempting to access Velocitab on plugin construction/before your plugin has enabled.
                3) You have shaded Velocitab into your plugin jar and need to fix your maven/gradle/build script
                   to only include Velocitab as a dependency and not as a shaded dependency.""";

        NotRegisteredException() {
            super(MESSAGE);
        }

    }
}
