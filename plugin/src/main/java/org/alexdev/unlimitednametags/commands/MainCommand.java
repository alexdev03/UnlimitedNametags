package org.alexdev.unlimitednametags.commands;

import com.jonahseguin.drink.annotation.*;
import lombok.RequiredArgsConstructor;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
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
        plugin.getKyoriManager().sendMessage(sender, Formatter.LEGACY.format(plugin, sender, "&a/unt debugger <true/false> &7- Enable or disable the debugger"));
    }

    @Command(name = "reload", desc = "Reloads the plugin", usage = "reload")
    @Require(value = "unt.reload", message = "&cYou do not have permission to reload the plugin")
    public void onReload(@Sender CommandSender sender) {
        plugin.getConfigManager().reload();
        plugin.getNametagManager().reload();
        plugin.getPlaceholderManager().reload();
        plugin.getKyoriManager().sendMessage(sender, Formatter.LEGACY.format(plugin, sender, "&aUnlimitedNameTags has been reloaded!"));
    }

    @Command(name = "debugger", desc = "Enables/Disables the debug mode", usage = "debugger")
    @Require(value = "unt.debug", message = "&cYou do not have permission to debug the plugin")
    public void onDebug(@Sender CommandSender sender, boolean debug) {
        plugin.getNametagManager().setDebug(debug);
        plugin.getKyoriManager().sendMessage(sender, Formatter.LEGACY.format(plugin, sender, "&aUnlimitedNameTags debug mode set to " + debug));
    }

    @Command(name = "debug", desc = "Debugs the plugin", usage = "debug")
    @Require(value = "unt.debug", message = "&cYou do not have permission to debug the plugin")
    public void onDebug(@Sender CommandSender sender) {
        plugin.getNametagManager().debug(sender);
    }

    @Command(name = "hide", desc = "Hides the nametag", usage = "hide")
    @Require(value = "unt.hide", message = "&cYou do not have permission to hide the nametag")
    public void onHide(@Sender CommandSender sender, Player target) {
        plugin.getNametagManager().removeAllViewers(target);
    }

    @Command(name = "show", desc = "Shows the nametag", usage = "show")
    @Require(value = "unt.show", message = "&cYou do not have permission to show the nametag")
    public void onShow(@Sender CommandSender sender, Player target) {
        plugin.getNametagManager().showToTrackedPlayers(target);
    }

    @Command(name = "refresh", desc = "Refreshes the nametag of a player for you", usage = "refresh")
    @Require(value = "unt.refresh", message = "&cYou do not have permission to refresh the nametag")
    public void onRefresh(@Sender Player sender, Player target) {
        plugin.getNametagManager().getPacketDisplayText(target).forEach(packetDisplayText -> {
            packetDisplayText.refreshForPlayer(sender);
        });
    }

    @Command(name = "billboard", desc = "Sets the default billboard", usage = "billboard")
    @Require(value = "unt.billboard", message = "&cYou do not have permission to set the default billboard")
    public void onBillboard(@Sender CommandSender sender, AbstractDisplayMeta.BillboardConstraints billboardConstraints) {
        plugin.getConfigManager().getSettings().setDefaultBillboard(billboardConstraints);
        plugin.getConfigManager().save();
        plugin.getNametagManager().reload();
        plugin.getKyoriManager().sendMessage(sender, Formatter.LEGACY.format(plugin, sender, "&aDefault billboard set to " + billboardConstraints.name()));
    }

    @Command(name = "formatter", desc = "Sets the default formatter", usage = "formatter")
    @Require(value = "unt.formatter", message = "&cYou do not have permission to set the default formatter")
    public void onFormatter(@Sender CommandSender sender, Formatter formatter) {
        plugin.getConfigManager().getSettings().setFormat(formatter);
        plugin.getConfigManager().save();
        plugin.getNametagManager().reload();
        plugin.getKyoriManager().sendMessage(sender, Formatter.LEGACY.format(plugin, sender, "&aDefault formatter set to " + formatter.name()));
    }

    @Command(name = "hideOtherNametags", desc = "Hides other nametags", usage = "hideOtherNametags [-h]")
    @Require(value = "unt.hideOtherNametags", message = "&cYou do not have permission to hide other nametags")
    public void onHideOtherNametags(@Sender Player sender, @OptArg(value = "false") boolean hideMessage) {
        if (!plugin.getNametagManager().isHiddenOtherNametags(sender)) {
            plugin.getNametagManager().hideOtherNametags(sender);
            if (!hideMessage) {
                plugin.getKyoriManager().sendMessage(sender, Formatter.LEGACY.format(plugin, sender, "&aOther nametags hidden"));
            }
        }
    }

    @Command(name = "showOtherNametags", desc = "Shows other nametags", usage = "showOtherNametags [-h]")
    @Require(value = "unt.showOtherNametags", message = "&cYou do not have permission to show other nametags")
    public void onShowOtherNametags(@Sender Player sender, @OptArg(value = "false") boolean showMessage) {
        if (plugin.getNametagManager().isHiddenOtherNametags(sender)) {
            plugin.getNametagManager().showOtherNametags(sender);
            if (!showMessage) {
                plugin.getKyoriManager().sendMessage(sender, Formatter.LEGACY.format(plugin, sender, "&aOther nametags shown"));
            }
        }
    }
}
