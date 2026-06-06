package org.alexdev.unlimitednametags.data;

import de.exlll.configlib.YamlConfigurations;
import org.alexdev.unlimitednametags.config.UntYamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serializes ConfigLib {@code @Configuration} objects to YAML strings for player PDC storage.
 */
public final class NametagPdcYamlCodec {

    private NametagPdcYamlCodec() {
    }

    @NotNull
    public static <T> String encode(@NotNull T object, @NotNull Class<T> type) throws IOException {
        final Path temp = Files.createTempFile("unt-pdc-", ".yml");
        try {
            YamlConfigurations.save(temp, type, object, UntYamlConfiguration.PROPERTIES);
            return Files.readString(temp, StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Nullable
    public static <T> T decode(@Nullable String yaml, @NotNull Class<T> type) throws IOException {
        if (yaml == null || yaml.isBlank()) {
            return null;
        }
        final Path temp = Files.createTempFile("unt-pdc-", ".yml");
        try {
            Files.writeString(temp, yaml, StandardCharsets.UTF_8);
            return YamlConfigurations.load(temp, type, UntYamlConfiguration.PROPERTIES);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
