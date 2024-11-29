package org.alexdev.unlimitednametags.hook;

import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.th0rgal.oraxen.api.OraxenItems;
import io.th0rgal.oraxen.api.events.OraxenItemsLoadedEvent;
import io.th0rgal.oraxen.items.ItemBuilder;
import io.th0rgal.oraxen.items.OraxenMeta;
import net.kyori.adventure.key.Key;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

public class OraxenHook extends Hook implements Listener, HatHook {

    private static Method getCustomModelData1 = null;
    private static Method getCustomModelData2 = null;
    private static Method getModelName1 = null;
    private static Method getModelName2 = null;

    static {
//        try {
//            getCustomModelData1 = OraxenMeta.class.getDeclaredMethod("getCustomModelData");
//            getCustomModelData1.setAccessible(true);
//        } catch (NoSuchMethodException e) {
//        }

        if(getCustomModelData1 == null) {
            try {
                getCustomModelData2 = OraxenMeta.class.getDeclaredMethod("customModelData");
                getCustomModelData2.setAccessible(true);
            } catch (NoSuchMethodException e) {
            }
        }

//        try {
//            getModelName1 = OraxenMeta.class.getDeclaredMethod("getModelName");
//            getModelName1.setAccessible(true);
//        } catch (NoSuchMethodException e) {
//        }

        if(getModelName1 == null) {
            try {
                getModelName2 = OraxenMeta.class.getDeclaredMethod("modelKey");
                getModelName2.setAccessible(true);
            } catch (NoSuchMethodException e) {
            }
        }
    }

    private static final File ORAXEN_FOLDER = new File("plugins/Oraxen/pack/models");
    private static final File ORAXEN_FOLDER2 = new File("plugins/Oraxen/pack/assets/minecraft/models");

    private final Map<String, Double> height;
    private final JsonParser jsonParser;

    public OraxenHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
        this.height = Maps.newConcurrentMap();
        this.jsonParser = new JsonParser();
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

    private int getCustomModelData(OraxenMeta meta) {
        if (getCustomModelData1 != null) {
            try {
                return (int) getCustomModelData1.invoke(meta);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        else if (getCustomModelData2 != null) {
            try {
                return (int) getCustomModelData2.invoke(meta);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }

    private String getModelName(OraxenMeta meta) {
        if (getModelName1 != null) {
            try {
                return (String) getModelName1.invoke(meta);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        } else if (getModelName2 != null) {
            try {
                return ((Key) getModelName2.invoke(meta)).value();
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return "";
    }

    private double getHigh(@NotNull String model) {
        if (height.containsKey(model)) {
            return height.get(model);
        }
        final File file = new File(getModelName1 != null ? ORAXEN_FOLDER : ORAXEN_FOLDER2, model);
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
            return jsonParser.parse(reader).getAsJsonObject();
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
