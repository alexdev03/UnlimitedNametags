package org.alexdev.unlimitednametags;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.google.common.collect.Maps;
import com.jonahseguin.drink.CommandService;
import com.jonahseguin.drink.Drink;
import lombok.Getter;
import org.alexdev.unlimitednametags.api.UNTAPI;
import org.alexdev.unlimitednametags.commands.MainCommand;
import org.alexdev.unlimitednametags.config.ConfigManager;
import org.alexdev.unlimitednametags.events.PacketEventsListener;
import org.alexdev.unlimitednametags.events.PlayerListener;
import org.alexdev.unlimitednametags.hook.*;
import org.alexdev.unlimitednametags.nametags.NameTagManager;
import org.alexdev.unlimitednametags.packet.PacketManager;
import org.alexdev.unlimitednametags.placeholders.PlaceholderManager;
import org.alexdev.unlimitednametags.vanish.VanishManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
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
    private Map<Class<? extends Hook>, Hook> hooks;
    private TaskScheduler taskScheduler;

    @Override
    public void onLoad() {
        hooks = Maps.newConcurrentMap();
    }

    @Override
    public void onEnable() {
        taskScheduler = UniversalScheduler.getScheduler(this);
        configManager = new ConfigManager(this);
        nametagManager = new NameTagManager(this);
        placeholderManager = new PlaceholderManager(this);
        vanishManager = new VanishManager(this);
        packetManager = new PacketManager(this);

        if (!loadConfig()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadCommands();
        loadListeners();
        loadHooks();

        UNTAPI.register(this);
        getLogger().info("API registered");
        getLogger().info("UnlimitedNameTags has been enabled!");
    }

    private boolean loadConfig() {
        final Optional<Throwable> error = configManager.loadConfigs();
        if (error.isPresent()) {
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to load configuration", error.get());
            return false;
        }
        return true;
    }

    private void loadListeners() {
        playerListener = new PlayerListener(this);
        Bukkit.getPluginManager().registerEvents(playerListener, this);

        getLogger().info("PacketEvents found, hooking into it");
        packetEventsListener = new PacketEventsListener(this);
        packetEventsListener.onEnable();
    }

    private void loadHooks() {
        if (Bukkit.getPluginManager().isPluginEnabled("Floodgate")) {
            hooks.put(FloodgateHook.class, new FloodgateHook(this));
            getLogger().info("Floodgate found, hooking into it");
        }
        if (Bukkit.getPluginManager().isPluginEnabled("TypeWriter")) {
            hooks.put(TypeWriterListener.class, new TypeWriterListener(this));
            getLogger().info("TypeWriter found, hooking into it");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")) {
            hooks.put(MiniPlaceholdersHook.class, new MiniPlaceholdersHook(this));
            getLogger().info("MiniPlaceholders found, hooking into it");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("Oraxen")) {
            hooks.put(OraxenHook.class, new OraxenHook(this));
            getLogger().info("Oraxen found, hooking into it");
        }

        hooks.values().forEach(Hook::onEnable);
    }

    private void loadCommands() {
        final CommandService drink = Drink.get(this);

        drink.register(new MainCommand(this), "unt", "unlimitednametags");
        drink.registerCommands();
    }

    public <H extends Hook> Optional<H> getHook(@NotNull Class<H> hookType) {
        return Optional.ofNullable(hooks.get(hookType)).map(hookType::cast);
    }

    @NotNull
    public Optional<FloodgateHook> getFloodgateHook() {
        return getHook(FloodgateHook.class);
    }

    @Override
    public void onDisable() {
        UNTAPI.unregister();

        hooks.values().forEach(Hook::onDisable);

        playerListener.onDisable();
        packetEventsListener.onDisable();
        nametagManager.removeAll();
        placeholderManager.close();
        packetManager.close();
        taskScheduler.cancelTasks();
    }

}
