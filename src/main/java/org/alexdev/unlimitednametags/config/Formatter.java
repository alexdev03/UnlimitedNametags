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
import lombok.Getter;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.MiniPlaceholdersHook;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Different formatting markup options for the TAB list
 */
@SuppressWarnings("unused")
public enum Formatter {

    MINEDOWN(
            (plugin, player, text) -> new MineDown(text).toComponent(),
            "MineDown"
    ),
    MINIMESSAGE(
            (plugin, player, text)  -> plugin.getHook(MiniPlaceholdersHook.class)
                    .map(hook -> hook.format(text, player))
                    .orElse(MiniMessage.miniMessage().deserialize(text)),
            "MiniMessage"
    ),
    LEGACY(
            (plugin, player, text)  -> LegacyComponentSerializer.legacyAmpersand().deserialize(text),
            "Legacy Text"
    ),
    UNIVERSAL(
            (plugin, player, text)  -> {
                if (text == null) {
                    return null;
                }
                long start = System.currentTimeMillis();
                text = text.replaceAll("&r", "###RESET###");
                text = getHEX().serialize(getSTUPID().deserialize(text));
                text = replaceHexColorCodes(text);
                text = text.replaceAll("###RESET###", "&r");
                final Component component = MINEDOWN.formatter.apply(plugin, player, text) ;
                final String string = MiniMessage.miniMessage().serialize(component).replace("\\<", "<").replace("\\", "");
                return MINIMESSAGE.formatter.apply(plugin, player, string);
            },
            "Universal"
    );

    @NotNull
    private static String replaceHexColorCodes(@NotNull String text) {
        final Matcher matcher = getHEX_PATTERN().matcher(text);
        final StringBuilder valueBuffer = new StringBuilder();
        while (matcher.find()) {
            final String hex = matcher.group();
            final int start = matcher.start();
            final int end = matcher.end();
            matcher.appendReplacement(valueBuffer, hex + "&");
        }
        matcher.appendTail(valueBuffer);
        return valueBuffer.toString();
    }

    /**
     * Name of the formatter
     */
    private final String name;

    /**
     * Function to apply formatting to a string
     */
    private final TriFunction<UnlimitedNameTags, Audience, String, Component> formatter;

    @Getter
    private final static Pattern HEX_PATTERN = Pattern.compile("&#[a-fA-F0-9]{6}");
    @Getter
    private final static LegacyComponentSerializer STUPID = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .useUnusualXRepeatedCharacterHexFormat()
            .hexColors()
            .build();
    @Getter
    private final static LegacyComponentSerializer HEX = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .build();


    Formatter(@NotNull TriFunction<UnlimitedNameTags, Audience, String, Component> formatter, @NotNull String name) {
        this.formatter = formatter;
        this.name = name;
    }

    /**
     * Apply formatting to a string
     *
     * @param text the string to format
     * @return the formatted string
     */
    public Component format(@NotNull UnlimitedNameTags plugin, @NotNull Audience audience,  @NotNull String text) {
        return formatter.apply(plugin, audience, text) ;
    }


    @NotNull
    public String getName() {
        return name;
    }

}
