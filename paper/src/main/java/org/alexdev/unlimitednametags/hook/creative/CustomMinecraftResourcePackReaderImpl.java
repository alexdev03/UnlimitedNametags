/*
 * This file is part of creative, licensed under the MIT license
 *
 * Copyright (c) 2021-2025 Unnamed Team
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.alexdev.unlimitednametags.hook.creative;

import com.google.gson.JsonElement;
import com.google.gson.stream.JsonReader;
import net.kyori.adventure.key.Key;
import org.intellij.lang.annotations.Subst;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import team.unnamed.creative.ResourcePack;
import team.unnamed.creative.base.Writable;
import team.unnamed.creative.metadata.Metadata;
import team.unnamed.creative.overlay.Overlay;
import team.unnamed.creative.overlay.ResourceContainer;
import team.unnamed.creative.part.ResourcePackPart;
import team.unnamed.creative.serialize.minecraft.*;
import team.unnamed.creative.serialize.minecraft.fs.FileTreeReader;
import team.unnamed.creative.serialize.minecraft.io.BinaryResourceDeserializer;
import team.unnamed.creative.serialize.minecraft.io.JsonResourceDeserializer;
import team.unnamed.creative.serialize.minecraft.io.ResourceDeserializer;
import team.unnamed.creative.serialize.minecraft.metadata.MetadataSerializer;
import team.unnamed.creative.serialize.minecraft.sound.SoundRegistrySerializer;
import team.unnamed.creative.texture.Texture;
import team.unnamed.creative.util.Keys;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;

import static java.util.Objects.requireNonNull;

public final class CustomMinecraftResourcePackReaderImpl implements MinecraftResourcePackReader {
    public static final CustomMinecraftResourcePackReaderImpl INSTANCE = new CustomMinecraftResourcePackReaderImpl(false);

    private static final String METADATA_EXTENSION = ".mcmeta";
    private static final String PACK_METADATA_FILE = "pack.mcmeta";
    private static final String PACK_ICON_FILE = "pack.png";
    private static final String ASSETS_FOLDER = "assets";
    private static final String SOUNDS_FILE = "sounds.json";
    private static final String TEXTURES_FOLDER = "textures";
    public static final String OVERLAYS_FOLDER = "overlays";

    private final boolean lenient;

    private CustomMinecraftResourcePackReaderImpl(
            final boolean lenient
    ) {
        this.lenient = lenient;
    }

    @Override
    @SuppressWarnings("PatternValidation")
    public @NotNull ResourcePack read(final @NotNull FileTreeReader reader) {
        ResourcePack resourcePack = ResourcePack.resourcePack();

        // textures that are waiting for metadata, or metadata
        // waiting for textures (because we can't know the order
        // they come in)
        // (null key means it is root resource pack)
        Map<@Nullable String, Map<Key, Texture>> incompleteTextures = new LinkedHashMap<>();

        while (reader.hasNext()) {
            String path = reader.next();

            // tokenize path in sections, e.g.: [ assets, minecraft, textures, ... ]
            Queue<String> tokens = tokenize(path);

            if (tokens.isEmpty()) {
                // this should never happen
                throw new IllegalStateException("Token collection is empty!");
            }

            // single token means the file is on the
            // root level (top level files) so it may be:
            // - pack.mcmeta
            // - pack.png
            if (tokens.size() == 1) {
                switch (tokens.poll()) {
                    case PACK_METADATA_FILE: {
                        try {
                            // found pack.mcmeta file, deserialize and add
                            Metadata metadata = MetadataSerializer.INSTANCE.readFromTree(parseJson(reader.stream()));
                            resourcePack.metadata(metadata);

                        } catch (Throwable ignored) {
                            // Some ItemsAdder generated packs contain modern or malformed metadata
                            // that older Creative releases cannot deserialize. Metadata is not
                            // required for model height lookup, so keep reading the pack.
                        }
                        continue;
                    }
                    case PACK_ICON_FILE: {
                        // found pack.png file, add
                        resourcePack.icon(reader.content().asWritable());
                        continue;
                    }
                    default: {
                        // unknown top level file
                        resourcePack.unknownFile(path, reader.content().asWritable());
                        continue;
                    }
                }
            }

            // the container to use, it is initially the default resource-pack,
            // but it may change if the file is inside an overlay folder
            @Subst("dir")
            @Nullable String overlayDir = null;

            // the file path, relative to the container
            String containerPath = path;
            ResourceContainer container = resourcePack;

            // if there are two or more tokens, it means the
            // file is inside a folder, in a Minecraft resource
            // pack, the first folder is always "assets"
            String folder = tokens.poll();

            if (folder.equals(OVERLAYS_FOLDER)) {
                // gets the overlay name, set after the
                // "overlays" folder, e.g. "overlays/foo",
                // or "overlays/bar"
                overlayDir = tokens.poll();
                if (tokens.isEmpty()) {
                    // this means that there is a file directly
                    // inside the "overlays" folder, this is illegal
                    resourcePack.unknownFile(containerPath, reader.content().asWritable());
                    continue;
                }

                Overlay overlay = resourcePack.overlay(overlayDir);
                if (overlay == null) {
                    // first occurrence, register overlay
                    overlay = Overlay.overlay(overlayDir);
                    resourcePack.overlay(overlay);
                }

                container = overlay;
                folder = tokens.poll();
                containerPath = path.substring((OVERLAYS_FOLDER + '/' + overlayDir + '/').length());
            }

            // null check to make ide happy
            if (folder == null || !folder.equals(ASSETS_FOLDER) || tokens.isEmpty()) {
                // not assets! this is an unknown file
                container.unknownFile(containerPath, reader.content().asWritable());
                continue;
            }

            // inside "assets", we should always have a folder
            // with any name, which is a namespace, e.g. "minecraft"
            String namespace = tokens.poll();

            if (!Keys.isValidNamespace(namespace)) {
                // invalid namespace found
                container.unknownFile(containerPath, reader.content().asWritable());
                continue;
            }

            if (tokens.isEmpty()) {
                // found a file directly inside "assets", like
                // assets/<file>, it is not allowed
                container.unknownFile(containerPath, reader.content().asWritable());
                continue;
            }

            // so we already have "assets/<namespace>/", most files inside
            // the namespace folder always have a "category", e.g. textures,
            // lang, font, etc. But not always! There is sounds.json file and
            // gpu_warnlist.json file
            String categoryName = tokens.poll();

            if (tokens.isEmpty()) {
                // this means "category" is a file
                // (remember: last tokens are always files)
                if (categoryName.equals(SOUNDS_FILE)) {
                    // found a sound registry!
                    container.soundRegistry(SoundRegistrySerializer.INSTANCE.readFromTree(
                            parseJson(reader.stream()),
                            namespace
                    ));
                    continue;
                } else {
                    // TODO: gpu_warnlist.json?
                    container.unknownFile(containerPath, reader.content().asWritable());
                    continue;
                }
            }

            // so "category" is actually a category like "textures",
            // "lang", "font", etc. next we can compute the relative
            // path inside the category
            String categoryPath = path(tokens);

            if (categoryName.equals(TEXTURES_FOLDER)) {
                String keyOfMetadata = withoutExtension(categoryPath, METADATA_EXTENSION);
                if (keyOfMetadata != null) {
                    // found metadata for texture
                    Key key = Key.key(namespace, keyOfMetadata);
                    Metadata metadata;
                    try {
                        metadata = MetadataSerializer.INSTANCE.readFromTree(parseJson(reader.stream()));
                    } catch (RuntimeException e) {
                        // ItemsAdder generated packs may contain non-object .mcmeta sidecar files
                        // (for example a plain hash string). Texture metadata is optional for the
                        // model-height lookup, so skip only this sidecar instead of aborting the
                        // whole resource-pack load.
                        continue;
                    }

                    Map<Key, Texture> incompleteTexturesThisContainer = incompleteTextures.computeIfAbsent(overlayDir, k -> new LinkedHashMap<>());
                    Texture texture = incompleteTexturesThisContainer.remove(key);
                    if (texture == null) {
                        // metadata was found first, put
                        incompleteTexturesThisContainer.put(key, Texture.texture(key, Writable.EMPTY, metadata));
                    } else {
                        // texture was found before the metadata, nice!
                        container.texture(texture.meta(metadata));
                    }
                } else {
                    Key key = Key.key(namespace, categoryPath);
                    Writable data = reader.content().asWritable();
                    Map<Key, Texture> incompleteTexturesThisContainer = incompleteTextures.computeIfAbsent(overlayDir, k -> new LinkedHashMap<>());
                    Texture waiting = incompleteTexturesThisContainer.remove(key);

                    if (waiting == null) {
                        // found texture before metadata
                        incompleteTexturesThisContainer.put(key, Texture.texture(key, data));
                    } else {
                        // metadata was found first
                        container.texture(Texture.texture(
                                key,
                                data,
                                waiting.meta()
                        ));
                    }
                }
            } else {
                ResourceCategory<?> category = categoryByFolder(categoryName);
                if (category == null) {
                    // unknown category
                    container.unknownFile(containerPath, reader.content().asWritable());
                    continue;
                }
                String keyValue = withoutExtension(categoryPath, categoryExtension(category));
                if (keyValue == null) {
                    // wrong extension
                    container.unknownFile(containerPath, reader.content().asWritable());
                    continue;
                }

                if (keyValue.startsWith("equipment/")) {
                    // skip trying to load "namespace:equipment/X.json" as a normal model
                    container.unknownFile(containerPath, reader.content().asWritable());
                    continue;
                }

                Key key = Key.key(namespace, keyValue);
                try {
                    @SuppressWarnings({"rawtypes", "unchecked"})
                    ResourceDeserializer deserializer = category.deserializer();
                    Object resource = deserializeResource(deserializer, reader, key);
                    ((ResourcePackPart) resource).addTo(container);
                } catch (Throwable e) {
//                    throw new UncheckedIOException("Failed to deserialize resource at: '" + path + "'", e);
                }
            }
        }

        for (Map.Entry<String, Map<Key, Texture>> entry : incompleteTextures.entrySet()) {
            @Subst("dir")
            @Nullable String overlayDir = entry.getKey();
            Map<Key, Texture> incompleteTexturesThisContainer = entry.getValue();
            ResourceContainer container;

            if (overlayDir == null) {
                // root
                container = resourcePack;
            } else {
                // from an overlay
                container = resourcePack.overlay(overlayDir);
                requireNonNull(container, "container"); // should never happen, but make ide happy
            }

            for (Texture texture : incompleteTexturesThisContainer.values()) {
                if (texture.data() != Writable.EMPTY) {
                    container.texture(texture);
                }
            }
        }
        return resourcePack;
    }

    private static Queue<String> tokenize(String path) {
        return new ArrayDeque<>(Arrays.asList(path.split("/")));
    }

    private static String path(Iterable<String> tokens) {
        StringBuilder builder = new StringBuilder();
        for (String token : tokens) {
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(token);
        }
        return builder.toString();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object deserializeResource(ResourceDeserializer deserializer, FileTreeReader reader, Key key) throws Exception {
        if (deserializer instanceof BinaryResourceDeserializer) {
            return ((BinaryResourceDeserializer) deserializer).deserializeBinary(reader.content().asWritable(), key);
        }
        if (deserializer instanceof JsonResourceDeserializer) {
            JsonElement json = parseJson(reader.stream());
            try {
                Method method = deserializer.getClass().getMethod("deserializeFromJson", JsonElement.class, Key.class);
                return method.invoke(deserializer, json, key);
            } catch (NoSuchMethodException ignored) {
                Class<?> packFormat = Class.forName("team.unnamed.creative.metadata.pack.PackFormat");
                Object unknown = packFormat.getField("UNKNOWN").get(null);
                Method method = deserializer.getClass().getMethod("deserializeFromJson", JsonElement.class, Key.class, packFormat);
                return method.invoke(deserializer, json, key, unknown);
            }
        }
        return deserializer.deserialize(reader.stream(), key);
    }

    private static ResourceCategory<?> categoryByFolder(String folder) {
        try {
            Method getByFolder = ResourceCategories.class.getMethod("getByFolder", String.class);
            return (ResourceCategory<?>) getByFolder.invoke(null, folder);
        } catch (NoSuchMethodException ignored) {
            // Creative 1.9.x and some shaded copies build a folder map per pack format.
        } catch (ReflectiveOperationException e) {
            return null;
        }

        try {
            Method build = ResourceCategories.class.getMethod("buildCategoryMapByFolder", int.class);
            @SuppressWarnings("unchecked")
            Map<String, ResourceCategory<?>> categories = (Map<String, ResourceCategory<?>>) build.invoke(null, -1);
            return categories.get(folder);
        } catch (NoSuchMethodException ignored) {
            // Newer Nexo-shaded Creative uses PackFormat instead of int.
        } catch (ReflectiveOperationException e) {
            return null;
        }

        try {
            Class<?> packFormat = Class.forName("team.unnamed.creative.metadata.pack.PackFormat");
            Object unknown = packFormat.getField("UNKNOWN").get(null);
            Method build = ResourceCategories.class.getMethod("buildCategoryMapByFolder", packFormat);
            @SuppressWarnings("unchecked")
            Map<String, ResourceCategory<?>> categories = (Map<String, ResourceCategory<?>>) build.invoke(null, unknown);
            return categories.get(folder);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String categoryExtension(ResourceCategory<?> category) {
        try {
            return (String) category.getClass().getMethod("extension").invoke(category);
        } catch (NoSuchMethodException ignored) {
            // Creative 1.9.x and some shaded copies require a pack format.
        } catch (ReflectiveOperationException e) {
            return ".json";
        }

        try {
            return (String) category.getClass().getMethod("extension", int.class).invoke(category, -1);
        } catch (NoSuchMethodException ignored) {
            // Newer Nexo-shaded Creative uses PackFormat instead of int.
        } catch (ReflectiveOperationException e) {
            return ".json";
        }

        try {
            Class<?> packFormat = Class.forName("team.unnamed.creative.metadata.pack.PackFormat");
            Object unknown = packFormat.getField("UNKNOWN").get(null);
            return (String) category.getClass().getMethod("extension", packFormat).invoke(category, unknown);
        } catch (ReflectiveOperationException e) {
            return ".json";
        }
    }

    private static @Nullable String withoutExtension(String string, String extension) {
        if (string.endsWith(extension)) {
            return string.substring(0, string.length() - extension.length());
        } else {
            // string doesn't end with extension
            return null;
        }
    }

    private @NotNull JsonElement parseJson(final @NotNull InputStream input) {
        try (final JsonReader jsonReader = new JsonReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            jsonReader.setLenient(lenient);
            return GsonUtil.parseReader(jsonReader);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to close JSON reader", e);
        }
    }
}
