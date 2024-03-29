package org.alexdev.unlimitednametags.commands;

import com.jonahseguin.drink.annotation.Command;
import com.jonahseguin.drink.annotation.Require;
import com.jonahseguin.drink.annotation.Sender;
import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Formatter;
import org.bukkit.command.CommandSender;

@RequiredArgsConstructor
@SuppressWarnings("unused")
public class MainCommand {

    private final UnlimitedNameTags plugin;

    @Command(name = "reload", desc = "Reloads the plugin", usage = "/unt reload")
    @Require(value = "unt.reload", message = "&cYou do not have permission to reload the plugin")
    public void onReload(@Sender CommandSender sender) {
        plugin.getConfigManager().reload();
        plugin.getNametagManager().reload();
        sender.sendMessage(Formatter.LEGACY.format(plugin, sender, "&aUnlimitedNameTags has been reloaded!"));
    }

    @Command(name = "debug", desc = "Debugs the plugin", usage = "/unt debug")
    @Require(value = "unt.debug", message = "&cYou do not have permission to debug the plugin")
    public void onDebug(@Sender CommandSender sender) {
        plugin.getNametagManager().debug(sender);
    }
}
