package org.alexdev.unlimitednametags;

import com.jonahseguin.drink.CommandService;
import com.jonahseguin.drink.Drink;
import lombok.Getter;
import org.alexdev.unlimitednametags.api.UNTAPI;
import org.alexdev.unlimitednametags.commands.MainCommand;
import org.alexdev.unlimitednametags.config.ConfigManager;
import org.alexdev.unlimitednametags.events.PacketEventsListener;
import org.alexdev.unlimitednametags.events.PlayerListener;
import org.alexdev.unlimitednametags.events.PurpurListener;
import org.alexdev.unlimitednametags.events.TypeWriterListener;
import org.alexdev.unlimitednametags.nametags.NameTagManager;
import org.alexdev.unlimitednametags.placeholders.PlaceholderManager;
import org.alexdev.unlimitednametags.vanish.VanishManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class UnlimitedNameTags extends JavaPlugin {

    private ConfigManager configManager;
    private NameTagManager nametagManager;
    private PlaceholderManager placeholderManager;
    private VanishManager vanishManager;
    private PacketEventsListener packetEventsListener;

    @Override
    public void onLoad() {
        if (Bukkit.getPluginManager().isPluginEnabled("PacketEvents")) {
            getLogger().info("PacketEvents found, hooking into it");
            packetEventsListener = new PacketEventsListener(this);
            packetEventsListener.onLoad();
        }
    }

    @Override
    public void onEnable() {

        configManager = new ConfigManager(this);
        placeholderManager = new PlaceholderManager(this);
        nametagManager = new NameTagManager(this);
        vanishManager = new VanishManager(this);

        loadCommands();
        loadListeners();

        UNTAPI.register(this);

        getLogger().info("API registered");

        getLogger().info("UnlimitedNameTags has been enabled!");
    }

    private void loadListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);

        if (isPurpur()) {
            Bukkit.getPluginManager().registerEvents(new PurpurListener(this), this);
            getLogger().info("Purpur found, teleporting with passengers will work");
        } else {
            getLogger().warning("Purpur not found, teleporting with passengers will not work. This could create problems with teleports");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("TypeWriter")) {
            Bukkit.getPluginManager().registerEvents(new TypeWriterListener(this), this);
            getLogger().info("TypeWriter found, hooking into it");
        }

        if (packetEventsListener != null) {
            packetEventsListener.onEnable();
        }
    }

    private boolean isPurpur() {
        try {
            Class.forName("org.purpurmc.purpur.event.entity.EntityTeleportHinderedEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void loadCommands() {
        final CommandService drink = Drink.get(this);

        drink.register(new MainCommand(this), "unt", "unlimitednametags");
        drink.registerCommands();
    }

    @Override
    public void onDisable() {
        UNTAPI.unregister();

        nametagManager.removeAll();
        placeholderManager.close();
        Bukkit.getScheduler().cancelTasks(this);
    }

}
