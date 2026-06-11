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
import org.alexdev.unlimitednametags.hook.creative.JsonModelHeightResolver;
import org.alexdev.unlimitednametags.hook.hat.HatHookPaper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import team.unnamed.creative.ResourcePack;
import team.unnamed.creative.model.Model;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

@Getter
public class NexoHook extends Hook implements Listener, CreativeHook, HatHookPaper {

    private final Map<Key, Map<Integer, Model>> cmdCache;
    private ResourcePack resourcePack;
    private JsonModelHeightResolver jsonModelHeightResolver;

    public NexoHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
        this.cmdCache = Maps.newConcurrentMap();
        loadTexture();
    }

    public void loadTexture() {
        resourcePack = NexoPack.resourcePack();
        File packZip = new File(plugin.getDataFolder().getParentFile(), "Nexo" + File.separator + "pack" + File.separator + "pack.zip");
        jsonModelHeightResolver = new JsonModelHeightResolver(packZip);
    }

    @Override
    public double getHigh(@NotNull UUID playerId) {
        final Player player = plugin.getPlayerListener().getPlayer(playerId);
        if (player == null) {
            return 0;
        }
        return getHigh(player);
    }

    @Override
    public double getHigh(@NotNull Player player) {
        final double v = CreativeHook.super.getHigh(player);
        if (HelmetDebugContext.isVerbose()) {
            plugin.getLogger().info("[UNT helmet dbg] NexoHook: getHigh=" + v);
        }
        return v;
    }

    @Override
    public double getHigh(@NotNull Player player, @Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        final double v = getHigh(item);
        if (HelmetDebugContext.isVerbose()) {
            final Optional<Model> resolved = findModel(item);
            plugin.getLogger().info("[UNT helmet dbg] NexoHook: source item getHigh=" + v
                    + " findModelResolved=" + resolved.isPresent());
        }
        return v;
    }

    @Override
    public double getHigh(@NotNull ItemStack item) {
        final double creativeHeight = CreativeHook.super.getHigh(item);
        if (creativeHeight > 0 || jsonModelHeightResolver == null) {
            return creativeHeight;
        }
        final Optional<ItemBuilder> optionalItemBuilder = Optional.ofNullable(NexoItems.builderFromItem(item));
        if (optionalItemBuilder.isEmpty()) {
            return creativeHeight;
        }
        final NexoMeta meta = optionalItemBuilder.get().getNexoMeta();
        OptionalDouble height = jsonModelHeightResolver.heightForKey(meta.getModel());
        if (height.isPresent()) {
            return height.getAsDouble();
        }
        final Key itemModelKey = itemModelKey(meta);
        if (itemModelKey != null) {
            height = jsonModelHeightResolver.heightForKey(itemModelKey);
            if (height.isPresent()) {
                return height.getAsDouble();
            }
        }
        return creativeHeight;
    }

    private Key itemModelKey(@NotNull NexoMeta meta) {
        try {
            Object itemModel = meta.getClass().getMethod("getItemModel").invoke(meta);
            if (itemModel == null) {
                return null;
            }
            Object key = itemModel.getClass().getMethod("getKey").invoke(itemModel);
            return key instanceof Key ? (Key) key : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
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
    public void onLoad(NexoItemsLoadedEvent event) {
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
