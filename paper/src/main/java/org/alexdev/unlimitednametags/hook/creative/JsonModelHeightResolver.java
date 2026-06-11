package org.alexdev.unlimitednametags.hook.creative;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.OptionalDouble;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class JsonModelHeightResolver {
    private static final double MULTIPLIER = 1.1;

    private final File zipFile;

    public JsonModelHeightResolver(@NotNull File zipFile) {
        this.zipFile = zipFile;
    }

    public OptionalDouble heightForItem(@NotNull ItemStack item) {
        if (!zipFile.exists() || !item.hasItemMeta()) {
            return OptionalDouble.empty();
        }

        final ItemMeta meta = item.getItemMeta();
        if (meta.hasCustomModelData()) {
            final int cmd = meta.getCustomModelData();
            final Key itemModelKey = Key.key(item.getType().getKey().namespace(), "item/" + item.getType().getKey().value());
            final Key modelKey = findLegacyOverride(itemModelKey, cmd);
            if (modelKey != null) {
                return heightForModel(modelKey);
            }
        }

        if (!PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_21_3)
                && meta.hasEquippable()
                && meta.getEquippable().getModel() != null) {
            OptionalDouble height = heightForKey(toKey(meta.getEquippable().getModel()));
            if (height.isPresent()) {
                return height;
            }
        }

        if (meta.hasItemModel() && meta.getItemModel() != null) {
            return heightForKey(toKey(meta.getItemModel()));
        }

        return OptionalDouble.empty();
    }

    public OptionalDouble heightForKey(@Nullable Key key) {
        if (key == null || !zipFile.exists()) {
            return OptionalDouble.empty();
        }

        OptionalDouble directModel = heightForModel(key);
        if (directModel.isPresent()) {
            return directModel;
        }

        OptionalDouble directItem = heightForItemModel(key);
        if (directItem.isPresent()) {
            return directItem;
        }

        final String value = key.value();
        if (value.startsWith("item/")) {
            return heightForItemModel(Key.key(key.namespace(), value.substring("item/".length())));
        }
        return OptionalDouble.empty();
    }

    private OptionalDouble heightForItemModel(@NotNull Key itemKey) {
        final JsonObject json = readJson("assets/" + itemKey.namespace() + "/items/" + itemKey.value() + ".json");
        if (json == null || !json.has("model") || !json.get("model").isJsonObject()) {
            return OptionalDouble.empty();
        }
        final Key model = extractFirstReference(json.getAsJsonObject("model"));
        if (model == null) {
            return OptionalDouble.empty();
        }
        return heightForModel(model);
    }

    private Key extractFirstReference(@NotNull JsonObject modelNode) {
        final String type = string(modelNode, "type");
        if ("model".equals(type) && modelNode.has("model")) {
            return parseKey(modelNode.get("model").getAsString(), "minecraft");
        }
        if ("range_dispatch".equals(type)) {
            JsonArray entries = modelNode.getAsJsonArray("entries");
            if (entries != null && entries.size() > 0) {
                JsonObject entry = entries.get(0).getAsJsonObject();
                if (entry.has("model") && entry.get("model").isJsonObject()) {
                    Key nested = extractFirstReference(entry.getAsJsonObject("model"));
                    if (nested != null) return nested;
                }
            }
            if (modelNode.has("fallback") && modelNode.get("fallback").isJsonObject()) {
                return extractFirstReference(modelNode.getAsJsonObject("fallback"));
            }
        }
        if ("select".equals(type)) {
            JsonArray cases = modelNode.getAsJsonArray("cases");
            if (cases != null && cases.size() > 0) {
                JsonObject c = cases.get(0).getAsJsonObject();
                if (c.has("model") && c.get("model").isJsonObject()) {
                    Key nested = extractFirstReference(c.getAsJsonObject("model"));
                    if (nested != null) return nested;
                }
            }
            if (modelNode.has("fallback") && modelNode.get("fallback").isJsonObject()) {
                return extractFirstReference(modelNode.getAsJsonObject("fallback"));
            }
        }
        if ("composite".equals(type)) {
            JsonArray models = modelNode.getAsJsonArray("models");
            if (models != null) {
                for (JsonElement element : models) {
                    if (element.isJsonObject()) {
                        Key nested = extractFirstReference(element.getAsJsonObject());
                        if (nested != null) return nested;
                    }
                }
            }
        }
        return null;
    }

    private Key findLegacyOverride(@NotNull Key itemModelKey, int cmd) {
        final JsonObject json = readJson("assets/" + itemModelKey.namespace() + "/models/" + itemModelKey.value() + ".json");
        if (json == null || !json.has("overrides")) {
            return null;
        }
        final String asString = Integer.toString(cmd);
        for (JsonElement element : json.getAsJsonArray("overrides")) {
            if (!element.isJsonObject()) continue;
            JsonObject override = element.getAsJsonObject();
            if (!override.has("predicate") || !override.has("model")) continue;
            JsonObject predicate = override.getAsJsonObject("predicate");
            JsonElement customModelData = predicate.get("custom_model_data");
            if (customModelData == null) continue;
            if (customModelData.getAsString().equals(asString)) {
                return parseKey(override.get("model").getAsString(), itemModelKey.namespace());
            }
        }
        return null;
    }

    private OptionalDouble heightForModel(@NotNull Key modelKey) {
        final JsonObject model = readJson("assets/" + modelKey.namespace() + "/models/" + modelKey.value() + ".json");
        if (model == null) {
            return OptionalDouble.empty();
        }
        if (model.has("parent") && (!model.has("elements") || model.getAsJsonArray("elements").size() == 0)) {
            final Key parent = parseKey(model.get("parent").getAsString(), modelKey.namespace());
            OptionalDouble parentHeight = heightForModel(parent);
            if (parentHeight.isPresent()) {
                return parentHeight;
            }
        }
        if (!model.has("elements") || !model.has("display")) {
            return OptionalDouble.empty();
        }
        JsonObject display = model.getAsJsonObject("display");
        if (!display.has("head")) {
            return OptionalDouble.of(-1.0);
        }
        double highest = 0.0;
        for (JsonElement element : model.getAsJsonArray("elements")) {
            JsonArray to = element.getAsJsonObject().getAsJsonArray("to");
            if (to != null && to.size() > 1) {
                highest = Math.max(highest, to.get(1).getAsDouble());
            }
        }
        JsonObject head = display.getAsJsonObject("head");
        double scaleY = arrayValue(head, "scale", 1, 1.0);
        double translationY = arrayValue(head, "translation", 1, 0.0);
        return OptionalDouble.of(highest * scaleY * MULTIPLIER + translationY);
    }

    private JsonObject readJson(@NotNull String path) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            ZipEntry entry = zip.getEntry(path);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                String json = new String(readAllLenient(in), StandardCharsets.UTF_8);
                return new JsonParser().parse(json).getAsJsonObject();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private byte[] readAllLenient(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (true) {
            int read;
            try {
                read = in.read(buffer);
            } catch (IOException e) {
                break;
            }
            if (read < 0) break;
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static Key parseKey(String value, String defaultNamespace) {
        if (value.indexOf(':') >= 0) {
            return Key.key(value);
        }
        return Key.key(defaultNamespace, value);
    }

    private static Key toKey(NamespacedKey key) {
        return Key.key(key.namespace(), key.value());
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private static double arrayValue(JsonObject object, String key, int index, double fallback) {
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return fallback;
        }
        JsonArray array = object.getAsJsonArray(key);
        return array.size() > index ? array.get(index).getAsDouble() : fallback;
    }
}
