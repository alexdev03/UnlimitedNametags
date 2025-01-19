package org.alexdev.unlimitednametags.hook;

import com.google.common.collect.Maps;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@SuppressWarnings("DuplicatedCode")
public class ItemsAdderHook extends Hook implements Listener, HatHook {

    private final Map<String, Double> height;

    public ItemsAdderHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
        this.height = Maps.newConcurrentMap();
    }

    public double getHigh(@NotNull Player player) {
        return 0;
    }

    @EventHandler
    public void onEnable(ItemsAdderLoadDataEvent event) {
        height.clear();
        plugin.getLogger().info("ItemsAdder items loaded, clearing cache");
    }

    @Override
    public void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {

    }
}
