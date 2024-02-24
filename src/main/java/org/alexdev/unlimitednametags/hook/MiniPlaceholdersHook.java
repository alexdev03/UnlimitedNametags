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

package org.alexdev.unlimitednametags.hook;

import io.github.miniplaceholders.api.MiniPlaceholders;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.jetbrains.annotations.NotNull;

public class MiniPlaceholdersHook extends Hook {

    public MiniPlaceholdersHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
    }

    @NotNull
    public Component format(@NotNull String text, @NotNull Audience player) {
        return MiniMessage.miniMessage().deserialize(text, MiniPlaceholders.getAudienceGlobalPlaceholders(player));
    }

    public Component format(@NotNull Component component, @NotNull Audience player) {
        return format(MiniMessage.miniMessage().serialize(component), player);
    }

    @Override
    public void onEnable() {

    }

    @Override
    public void onDisable() {

    }
}
