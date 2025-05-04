package org.alexdev.unlimitednametags.hook;

import com.google.common.collect.Maps;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.api.NexoPack;
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import com.nexomc.nexo.items.ItemBuilder;
import com.nexomc.nexo.items.NexoMeta;
import lombok.Getter;
import net.kyori.adventure.key.Key;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.creative.CreativeHook;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import team.unnamed.creative.ResourcePack;
import team.unnamed.creative.model.Model;

import java.util.Map;
import java.util.Optional;

@Getter
public class NexoHook extends Hook implements Listener, CreativeHook, HatHook {

    private final Map<Key, Map<Integer, Model>> cmdCache;
    private ResourcePack resourcePack;

    public NexoHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
        this.cmdCache = Maps.newConcurrentMap();
        loadTexture();
    }

    public void loadTexture() {
        resourcePack = NexoPack.resourcePack();
    }

    @Override
    public double getHigh(@NotNull Player player) {
        return CreativeHook.super.getHigh(player);
    }

    public Optional<Model> findModel(@NotNull ItemStack item) {
        final Optional<ItemBuilder> optionalItemBuilder = Optional.ofNullable(NexoItems.builderFromItem(item));
        if (optionalItemBuilder.isPresent()) {
            final Optional<Model> optionalModel = optionalItemBuilder.map(ItemBuilder::getNexoMeta)
                    .map(NexoMeta::getModel)
                    .map(key -> resourcePack.model(key));
            if (optionalModel.isPresent()) {
                return optionalModel;
            }
        }

        return CreativeHook.super.findModel(item);
    }

    @EventHandler
    public void onEnable(NexoItemsLoadedEvent event) {
        cmdCache.clear();
        loadTexture();
        plugin.getLogger().info("Nexo items loaded, clearing cache");
    }

    @Override
    public void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {
        cmdCache.clear();
    }
}
