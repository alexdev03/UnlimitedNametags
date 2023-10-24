package org.alexdev.unlimitednametags;

import com.jonahseguin.drink.CommandService;
import com.jonahseguin.drink.Drink;
import lombok.Getter;
import org.alexdev.unlimitednametags.commands.MainCommand;
import org.alexdev.unlimitednametags.config.ConfigManager;
import org.alexdev.unlimitednametags.events.PlayerListener;
import org.alexdev.unlimitednametags.nametags.NameTagManager;
import org.alexdev.unlimitednametags.placeholders.PlaceholderManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class UnlimitedNametags extends JavaPlugin {

    private ConfigManager configManager;
    private NameTagManager nametagManager;
    private PlaceholderManager placeholderManager;

    @Override
    public void onEnable() {

        configManager = new ConfigManager(this);
        placeholderManager = new PlaceholderManager(this);
        nametagManager = new NameTagManager(this);

        loadCommands();
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        getLogger().info("UnlimitedNametags has been enabled!");
    }

    private void loadCommands() {
        final CommandService drink = Drink.get(this);

        drink.register(new MainCommand(this), "unt", "unlimitednametags");
        drink.registerCommands();
    }

    @Override
    public void onDisable() {
        nametagManager.removeAll();
        placeholderManager.close();
        Bukkit.getScheduler().cancelTasks(this);

    }
}
