package org.alexdev.unlimitednametags;

import com.jonahseguin.drink.CommandService;
import com.jonahseguin.drink.Drink;
import lombok.Getter;
import org.alexdev.unlimitednametags.api.UNTAPI;
import org.alexdev.unlimitednametags.commands.MainCommand;
import org.alexdev.unlimitednametags.config.ConfigManager;
import org.alexdev.unlimitednametags.events.PacketEventsListener;
import org.alexdev.unlimitednametags.events.PlayerListener;
import org.alexdev.unlimitednametags.events.TypeWriterListener;
import org.alexdev.unlimitednametags.hook.FloodgateHook;
import org.alexdev.unlimitednametags.nametags.NameTagManager;
import org.alexdev.unlimitednametags.packet.PacketManager;
import org.alexdev.unlimitednametags.placeholders.PlaceholderManager;
import org.alexdev.unlimitednametags.vanish.VanishManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Getter
public final class UnlimitedNameTags extends JavaPlugin {

    private ConfigManager configManager;
    private NameTagManager nametagManager;
    private PlaceholderManager placeholderManager;
    private VanishManager vanishManager;
    private PacketEventsListener packetEventsListener;
    private PacketManager packetManager;
    private PlayerListener playerListener;
    private FloodgateHook floodgateHook;

    @Override
    public void onLoad() {
        getLogger().info("PacketEvents found, hooking into it");
        packetEventsListener = new PacketEventsListener(this);
        packetEventsListener.onLoad();
    }

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        placeholderManager = new PlaceholderManager(this);
        nametagManager = new NameTagManager(this);
        vanishManager = new VanishManager(this);

        loadCommands();
        loadListeners();

        packetManager = new PacketManager(this);
        UNTAPI.register(this);
        getLogger().info("API registered");
        getLogger().info("UnlimitedNameTags has been enabled!");
    }

    private void loadListeners() {
        playerListener = new PlayerListener(this);
        Bukkit.getPluginManager().registerEvents(playerListener, this);

        if (Bukkit.getPluginManager().isPluginEnabled("TypeWriter")) {
            Bukkit.getPluginManager().registerEvents(new TypeWriterListener(this), this);
            getLogger().info("TypeWriter found, hooking into it");
        }

        packetEventsListener.onEnable();
    }

    private void loadHooks() {
        if (Bukkit.getPluginManager().isPluginEnabled("Floodgate")) {
            floodgateHook = new FloodgateHook();
            getLogger().info("Floodgate found, hooking into it");
        }
    }

    private void loadCommands() {
        final CommandService drink = Drink.get(this);

        drink.register(new MainCommand(this), "unt", "unlimitednametags");
        drink.registerCommands();
    }

    @NotNull
    public Optional<FloodgateHook> getFloodgateHook() {
        return Optional.ofNullable(floodgateHook);
    }

    @Override
    public void onDisable() {
        UNTAPI.unregister();

        packetEventsListener.onDisable();
        nametagManager.removeAll();
        placeholderManager.close();
        Bukkit.getScheduler().cancelTasks(this);
    }

}
