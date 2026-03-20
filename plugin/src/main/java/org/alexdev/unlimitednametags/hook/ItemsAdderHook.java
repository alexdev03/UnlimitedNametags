package org.alexdev.unlimitednametags.hook;

import com.google.common.collect.Maps;
import dev.lone.itemsadder.api.Events.ItemsAdderPackCompressedEvent;
import lombok.Getter;
import net.kyori.adventure.key.Key;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.creative.CreativeHook;
import org.alexdev.unlimitednametags.hook.creative.CustomMinecraftResourcePackReaderImpl;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import team.unnamed.creative.ResourcePack;
import team.unnamed.creative.model.Model;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;

@Getter
public class ItemsAdderHook extends Hook implements Listener, HatHook, CreativeHook {

    private static final Path generatedPath = new File(Bukkit.getPluginsFolder(),"ItemsAdder" + File.separator + "output" + File.separator + "generated.zip").toPath();

    private final Map<Key, Map<Integer, Model>> cmdCache;
    private ResourcePack resourcePack;

    public ItemsAdderHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
        this.cmdCache = Maps.newConcurrentMap();
        loadTexture();
    }

    public double getHigh(@NotNull Player player) {
        return CreativeHook.super.getHigh(player);
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
            plugin.getLogger().info("ItemsAdder's resource pack loaded from " + generated.getAbsolutePath());
        } catch (Throwable e) {
            resourcePack = null;
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load ItemsAdder resource pack for file at " + generated.getAbsolutePath(), e);
        }
    }
}
