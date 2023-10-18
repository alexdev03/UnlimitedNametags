package org.alexdev.unlimitednametags;

import lombok.Getter;
import org.alexdev.unlimitednametags.config.ConfigManager;
import org.alexdev.unlimitednametags.events.JoinQuitListener;
import org.alexdev.unlimitednametags.nametags.NametagManager;
import org.alexdev.unlimitednametags.placeholders.PlaceholderManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class UnlimitedNametags extends JavaPlugin {

    private ConfigManager configManager;
    private NametagManager nametagManager;
    private PlaceholderManager placeholderManager;

    @Override
    public void onEnable() {

        configManager = new ConfigManager(this);
        placeholderManager = new PlaceholderManager(this);
        nametagManager = new NametagManager(this);

        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);

        getLogger().info("UnlimitedNametags has been enabled!");
    }

    @Override
    public void onDisable() {
        nametagManager.removeAll();
        placeholderManager.close();
        Bukkit.getScheduler().cancelTasks(this);

    }
}
