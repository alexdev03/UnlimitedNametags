package org.alexdev.unlimitednametags.hook;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import com.nexomc.nexo.items.ItemBuilder;
import com.nexomc.nexo.items.NexoMeta;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
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
public class NexoHook extends Hook implements Listener, HatHook {

    private static final File NEXO_FOLDER = new File("plugins/Nexo/pack/assets/minecraft/models");

    private final Map<String, Double> height;
    private final Gson jsonParser;

    public NexoHook(@NotNull UnlimitedNameTags plugin) {
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
        final Optional<ItemBuilder> optionalItemBuilder = findItem(helmet);
        return optionalItemBuilder.map(itemBuilder -> getHigh(getModelName(itemBuilder.getNexoMeta()) + ".json")).orElse(0.0);
    }

    private Optional<ItemBuilder> findItem(@NotNull ItemStack item) {
        return Optional.ofNullable(NexoItems.builderFromItem(item));
    }

    private String getModelName(@NotNull NexoMeta meta) {
        return meta.getModelKey().value();
    }

    private double getHigh(@NotNull String model) {
        if (height.containsKey(model)) {
            return height.get(model);
        }
        final File file = new File(NEXO_FOLDER, model);
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
    public void onEnable(NexoItemsLoadedEvent event) {
        height.clear();
        plugin.getLogger().info("Nexo items loaded, clearing cache");
    }

    @Override
    public void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {

    }
}
