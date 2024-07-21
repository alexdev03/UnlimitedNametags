package org.alexdev.unlimitednametags.commands;

import com.jonahseguin.drink.annotation.Command;
import com.jonahseguin.drink.annotation.Require;
import com.jonahseguin.drink.annotation.Sender;
import lombok.RequiredArgsConstructor;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Formatter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
@SuppressWarnings("unused")
public class MainCommand {

    private final UnlimitedNameTags plugin;

    @Command(name = "unt", desc = "Main command for UnlimitedNameTags", usage = "/unt")
    @SuppressWarnings("deprecation")
    public void onMain(@Sender CommandSender sender) {
        plugin.getKyoriManager().sendMessage(sender, Formatter.LEGACY.format(plugin, sender, "&aUnlimitedNameTags v" + plugin.getDescription().getVersion()));
        plugin.getKyoriManager().sendMessage(sender, Formatter.LEGACY.format(plugin, sender, "&a/unt reload &7- Reloads the plugin"));
        plugin.getKyoriManager().sendMessage(sender, Formatter.LEGACY.format(plugin, sender, "&a/unt debug &7- Debugs the plugin"));
        plugin.getKyoriManager().sendMessage(sender, Formatter.LEGACY.format(plugin, sender, "&a/unt hide <player> &7- Hides the nametag"));
        plugin.getKyoriManager().sendMessage(sender, Formatter.LEGACY.format(plugin, sender, "&a/unt show <player> &7- Shows the nametag"));
    }

    @Command(name = "reload", desc = "Reloads the plugin", usage = "/unt reload")
    @Require(value = "unt.reload", message = "&cYou do not have permission to reload the plugin")
    public void onReload(@Sender CommandSender sender) {
        plugin.getConfigManager().reload();
        plugin.getNametagManager().reload();
        plugin.getKyoriManager().sendMessage(sender, Formatter.LEGACY.format(plugin, sender, "&aUnlimitedNameTags has been reloaded!"));
    }

    @Command(name = "debug", desc = "Debugs the plugin", usage = "/unt debug")
    @Require(value = "unt.debug", message = "&cYou do not have permission to debug the plugin")
    public void onDebug(@Sender CommandSender sender) {
        plugin.getNametagManager().debug(sender);
    }

    @Command(name = "hide", desc = "Hides the nametag", usage = "/unt hide")
    @Require(value = "unt.hide", message = "&cYou do not have permission to hide the nametag")
    public void onHide(@Sender CommandSender sender, Player target) {
        plugin.getNametagManager().removeAllViewers(target);
    }

    @Command(name = "show", desc = "Shows the nametag", usage = "/unt show")
    @Require(value = "unt.show", message = "&cYou do not have permission to show the nametag")
    public void onShow(@Sender CommandSender sender, Player target) {
        plugin.getNametagManager().showToTrackedPlayers(target, plugin.getTrackerManager().getTrackedPlayers(target.getUniqueId()));
    }
}
