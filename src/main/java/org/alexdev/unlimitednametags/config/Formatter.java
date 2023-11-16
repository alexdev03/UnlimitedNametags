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

package org.alexdev.unlimitednametags.config;

import de.themoep.minedown.adventure.MineDown;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Different formatting markup options for the TAB list
 */
@SuppressWarnings("unused")
public enum Formatter {
    MINEDOWN(
            (text) -> new MineDown(text).toComponent(),
            "MineDown"
    ),
    MINIMESSAGE(
            (text) -> MiniMessage.miniMessage().deserialize(text),
            "MiniMessage"
    ),
    LEGACY(
            (text) -> LegacyComponentSerializer.legacyAmpersand().deserialize(text),
            "Legacy Text"
    );

    /**
     * Name of the formatter
     */
    private final String name;

    /**
     * Function to apply formatting to a string
     */
    private final Function<String, Component> formatter;

    Formatter(@NotNull Function<String, Component> formatter, @NotNull String name) {
        this.formatter = formatter;
        this.name = name;
    }

    /**
     * Apply formatting to a string
     *
     * @param text the string to format
     * @return the formatted string
     */
    public Component format(@NotNull String text) {
        return formatter.apply(text);
    }


    @NotNull
    public String getName() {
        return name;
    }

}
