package org.alexdev.unlimitednametags.commands;

import com.jonahseguin.drink.annotation.Command;
import com.jonahseguin.drink.annotation.Require;
import com.jonahseguin.drink.annotation.Sender;
import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.command.CommandSender;

@RequiredArgsConstructor
@SuppressWarnings("unused")
public class MainCommand {

    private final UnlimitedNameTags plugin;

    @Command(name = "reload", desc = "Reloads the plugin", usage = "/unt reload")
    @Require("unt.reload")
    public void onReload(@Sender CommandSender sender) {
        plugin.getConfigManager().reload();
        plugin.getNametagManager().reload();
        sender.sendMessage("§cPlugin reloaded!");
    }

    @Command(name = "debug", desc = "Debugs the plugin", usage = "/unt debug")
    @Require("unt.debug")
    public void onDebug(@Sender CommandSender sender) {
        plugin.getNametagManager().debug(sender);
    }
}
