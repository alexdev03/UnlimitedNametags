package org.alexdev.unlimitednametags.hook;

import com.google.common.collect.Maps;
import dev.lone.itemsadder.api.Events.ItemsAdderPackCompressedEvent;
import lombok.Getter;
import net.kyori.adventure.key.Key;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.creative.CreativeHook;
import org.alexdev.unlimitednametags.hook.creative.CustomMinecraftResourcePackReaderImpl;
import org.alexdev.unlimitednametags.hook.creative.JsonModelHeightResolver;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;
import team.unnamed.creative.ResourcePack;
import team.unnamed.creative.model.Model;
import dev.lone.itemsadder.api.CustomStack;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalDouble;

@Getter
public class ItemsAdderHook extends Hook implements Listener, HatHook, CreativeHook {

    private static final Path generatedPath = new File(Bukkit.getPluginsFolder(),"ItemsAdder" + File.separator + "output" + File.separator + "generated.zip").toPath();

    private final Map<Key, Map<Integer, Model>> cmdCache;
    private ResourcePack resourcePack;
    private JsonModelHeightResolver jsonModelHeightResolver;

    public ItemsAdderHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
        this.cmdCache = Maps.newConcurrentMap();
        loadTexture();
    }

    @Override
    public double getHigh(@NotNull UUID playerId) {
        final Player player = plugin.getPlayerListener().getPlayer(playerId);
        if (player == null) {
            return 0;
        }
        return CreativeHook.super.getHigh(player);
    }

    public Optional<Model> findModel(@NotNull ItemStack item) {
        final CustomStack stack = CustomStack.byItemStack(item);
        if (stack != null) {
            final String modelPath = stack.getModelPath();
            if (modelPath != null) {
                final net.kyori.adventure.key.Key key = net.kyori.adventure.key.Key.key(stack.getNamespace(), modelPath);
                final Model model = resourcePack.model(key);
                if (model != null) {
                    return Optional.of(model);
                }
            }
        }
        return CreativeHook.super.findModel(item);
    }

    @Override
    public double getHigh(@NotNull ItemStack helmet) {
        final double creativeHeight = CreativeHook.super.getHigh(helmet);
        if (creativeHeight > 0 || jsonModelHeightResolver == null) {
            return creativeHeight;
        }
        final OptionalDouble height = jsonModelHeightResolver.heightForItem(helmet);
        return height.orElse(creativeHeight);
    }

    @EventHandler
    public void onLoad(ItemsAdderPackCompressedEvent event) {
        cmdCache.clear();
        loadTexture();
        plugin.getLogger().info("ItemsAdder items loaded, clearing cache");
    }

    @Override
    public void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {
        cmdCache.clear();
    }

    @Override
    public void loadTexture() {
        final File generated = generatedPath.toFile();
        if (!generated.exists()) {
            plugin.getLogger().warning("ItemsAdder generated.zip not found, skipping");
            return;
        }

        try {
//            resourcePack = MinecraftResourcePackReader.builder().lenient(true).build().readFromZipFile(generated);
            resourcePack = CustomMinecraftResourcePackReaderImpl.INSTANCE.readFromZipFile(generated);
            jsonModelHeightResolver = new JsonModelHeightResolver(generated);
            plugin.getLogger().info("ItemsAdder's resource pack loaded from " + generated.getAbsolutePath());
        } catch (Throwable e) {
            resourcePack = null;
            jsonModelHeightResolver = new JsonModelHeightResolver(generated);
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load ItemsAdder resource pack for file at " + generated.getAbsolutePath(), e);
        }
    }
}
