package org.alexdev.unlimitednametags.hook;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.api.events.OraxenItemsLoadedEvent;
import io.th0rgal.oraxen.items.ItemBuilder;
import io.th0rgal.oraxen.items.OraxenMeta;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("DuplicatedCode")
public class OraxenHook extends Hook implements Listener, HatHook {

    private static final File ORAXEN_FOLDER = new File("plugins/Oraxen/pack/models");

    private final Map<String, Double> height;
    private final Gson jsonParser;

    public OraxenHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
        this.height = Maps.newConcurrentMap();
        this.jsonParser = new Gson();
    }

    public double getHigh(@NotNull Player player) {
        final ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null) {
            return 0;
        }

        if (!helmet.hasItemMeta() || !helmet.getItemMeta().hasCustomModelData()) {
            return 0;
        }
        final int customModelData = helmet.getItemMeta().getCustomModelData();
        final Optional<ItemBuilder> optionalItemBuilder = findItem(customModelData, helmet.getType());
        return optionalItemBuilder.map(itemBuilder -> getHigh(getModelName(itemBuilder.getOraxenMeta()) + ".json")).orElse(0.0);
    }

    private Optional<ItemBuilder> findItem(int customModelData, @NotNull Material material) {
        return OraxenItems.getItems().stream()
                .filter(item -> getCustomModelData(item.getOraxenMeta()) == customModelData)
                .filter(item -> item.getType() == material)
                .findFirst();
    }

    private int getCustomModelData(@NotNull OraxenMeta meta) {
        try {
            if (meta.getCustomModelData() == null) {
                return 0;
            }

            return meta.getCustomModelData();
        } catch (NoSuchMethodError | NullPointerException e) {
            // Compatibility with older Oraxen versions
            return 0;
        }
    }

    private String getModelName(OraxenMeta meta) {
        return meta.getModelName();
    }

    private double getHigh(@NotNull String model) {
        if (height.containsKey(model)) {
            return height.get(model);
        }
        final File file = new File(ORAXEN_FOLDER, model);
        if (!file.exists()) {
            return -1;
        }

        final JsonObject jsonObject = parseFile(file);
        final JsonArray elements = jsonObject.getAsJsonArray("elements");
        if (elements.size() == 0) {
            return -1;
        }

        double highest = 0;
        for (int i = 0; i < elements.size(); i++) {
            final JsonObject element = elements.get(i).getAsJsonObject();
            final double to = element.getAsJsonArray("to").get(1).getAsDouble();
            if (to > highest) {
                highest = to;
            }
        }

        final JsonObject display = jsonObject.getAsJsonObject("display");
        if (!display.has("head")) {
            return -1;
        }

        final JsonObject head = display.getAsJsonObject("head");
        final double scale = head.has("scale") ? head.getAsJsonArray("scale").get(1).getAsDouble() : 1;
        highest *= scale;
        final double translation = head.has("translation") ? head.getAsJsonArray("translation").get(1).getAsDouble() : 0;


        final double value = highest + translation;
        height.put(model, value);
        return value;
    }

    @NotNull
    private JsonObject parseFile(@NotNull File file) {
        try (final FileReader reader = new FileReader(file)) {
            return jsonParser.fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse file: " + file, e);
        }
    }

    @EventHandler
    public void onEnable(OraxenItemsLoadedEvent event) {
        height.clear();
        plugin.getLogger().info("Oraxen items loaded, clearing cache");
    }

    @Override
    public void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {

    }
}
