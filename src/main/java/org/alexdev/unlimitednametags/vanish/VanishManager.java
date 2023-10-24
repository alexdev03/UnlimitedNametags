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

package org.alexdev.unlimitednametags.vanish;


import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class VanishManager {

    private final UnlimitedNameTags plugin;
    private VanishIntegration integration;

    public VanishManager(@NotNull UnlimitedNameTags plugin) {
        this.plugin = plugin;
        setIntegration(new DefaultVanishIntegration());
    }

    public void setIntegration(@NotNull VanishIntegration integration) {
        this.integration = integration;
    }

    @NotNull
    public VanishIntegration getIntegration() {
        return integration;
    }

    public boolean canSee(@NotNull Player name, @NotNull Player other) {
        return integration.canSee(name, other);
    }

    public boolean isVanished(@NotNull Player name) {
        return integration.isVanished(name);
    }

    public void vanishPlayer(@NotNull Player player) {
        plugin.getNametagManager().vanishPlayer(player);
    }

    public void unVanishPlayer(@NotNull Player player) {
        plugin.getNametagManager().unVanishPlayer(player);
    }
}
