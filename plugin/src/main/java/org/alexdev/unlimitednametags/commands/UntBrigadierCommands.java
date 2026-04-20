package org.alexdev.unlimitednametags.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.config.Formatter;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Paper lifecycle-registered Brigadier command tree for {@code /unt}.
 */
@SuppressWarnings("UnstableApiUsage")
public final class UntBrigadierCommands {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private UntBrigadierCommands() {
    }

    public static void register(@NotNull UnlimitedNameTags plugin) {
        if (!plugin.isPaper()) {
            plugin.getLogger().severe("UnlimitedNameTags commands require Paper (Brigadier lifecycle API).");
            return;
        }

        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final LiteralCommandNode<CommandSourceStack> root = Commands.literal("unt")
                    .executes(ctx -> {
                        sendHelp(plugin, ctx.getSource().getSender());
                        return Command.SINGLE_SUCCESS;
                    })
                    .then(literalReload(plugin))
                    .then(literalDebug(plugin))
                    .then(literalDebugger(plugin))
                    .then(literalHide(plugin))
                    .then(literalShow(plugin))
                    .then(literalRefresh(plugin))
                    .then(literalBillboard(plugin))
                    .then(literalFormatter(plugin))
                    .then(literalHideOtherNametags(plugin))
                    .then(literalShowOtherNametags(plugin))
                    .then(literalPreferences(plugin))
                    .build();

            event.registrar().register(root, "UnlimitedNametags commands", List.of("unlimitednametags"));
        });
    }

    private static void sendHelp(@NotNull UnlimitedNameTags plugin, @NotNull CommandSender sender) {
        final String v = plugin.getPluginMeta().getVersion();
        msg(plugin, sender, "<green>UnlimitedNameTags v<version></green>", Placeholder.unparsed("version", v));
        msg(plugin, sender, "<green>/unt reload</green> <gray>- Reloads the plugin");
        msg(plugin, sender, "<green>/unt debug</green> <gray>- Debugs the plugin");
        msg(plugin, sender, "<green>/unt hide</green> <gray>- Hides the nametag (target player)</gray>");
        msg(plugin, sender, "<green>/unt show</green> <gray>- Shows the nametag (target player)</gray>");
        msg(plugin, sender, "<green>/unt refresh</green> <gray>- Re-applies nametag for everyone (target player)</gray>");
        msg(plugin, sender, "<green>/unt debugger</green> <gray>- Toggle debugger (true or false)</gray>");
        msg(plugin, sender, "<green>/unt preferences …</green> <gray>- Per-player nametag visibility (see /unt preferences)");
    }

    private static void sendPreferenceLines(@NotNull UnlimitedNameTags plugin, @NotNull CommandSender to,
            @NotNull String headerMiniMessage, @NotNull Player subject) {
        final var nm = plugin.getNametagManager();
        final boolean seeOthers = !nm.isHiddenOtherNametags(subject);
        final boolean showOwnSelf = nm.isShowingOwnNametagToSelf(subject);
        final boolean showOwnToOthers = nm.isShowingOwnNametagToOthers(subject);
        msg(plugin, to, headerMiniMessage, Placeholder.unparsed("name", subject.getName()));
        msg(plugin, to, "  <yellow>seeothers</yellow><gray>:</gray> <white><v></white>",
                Placeholder.unparsed("v", String.valueOf(seeOthers)));
        msg(plugin, to, "  <yellow>showown</yellow><gray>:</gray> <white><v></white>",
                Placeholder.unparsed("v", String.valueOf(showOwnSelf)));
        msg(plugin, to, "  <yellow>showothers</yellow><gray>:</gray> <white><v></white>",
                Placeholder.unparsed("v", String.valueOf(showOwnToOthers)));
    }

    private static void msg(@NotNull UnlimitedNameTags plugin, @NotNull CommandSender sender,
            @NotNull String miniMessageTemplate, TagResolver... resolvers) {
        plugin.getKyoriManager().sendMessage(sender,
                resolvers.length == 0
                        ? MINI_MESSAGE.deserialize(miniMessageTemplate)
                        : MINI_MESSAGE.deserialize(miniMessageTemplate, resolvers));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literalReload(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.literal("reload")
                .requires(stack -> stack.getSender().hasPermission("unt.reload"))
                .executes(ctx -> {
                    plugin.getConfigManager().reload();
                    plugin.getNametagManager().reload();
                    plugin.getPlaceholderManager().reload();
                    msg(plugin, ctx.getSource().getSender(), "<green>UnlimitedNameTags has been reloaded!</green>");
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literalDebug(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.literal("debug")
                .requires(stack -> stack.getSender().hasPermission("unt.debug"))
                .executes(ctx -> {
                    plugin.getNametagManager().debug(ctx.getSource().getSender());
                    return Command.SINGLE_SUCCESS;
                });
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literalDebugger(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.literal("debugger")
                .requires(stack -> stack.getSender().hasPermission("unt.debug"))
                .then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(ctx -> {
                            final boolean debug = BoolArgumentType.getBool(ctx, "enabled");
                            plugin.getNametagManager().setDebug(debug);
                            msg(plugin, ctx.getSource().getSender(),
                                    "<green>UnlimitedNameTags debug mode set to <yellow><state></yellow></green>",
                                    Placeholder.unparsed("state", String.valueOf(debug)));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literalHide(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.literal("hide")
                .requires(stack -> stack.getSender().hasPermission("unt.hide"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            final Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                            if (target == null) {
                                msg(plugin, ctx.getSource().getSender(), "<red>Player not found or not online.</red>");
                                return 0;
                            }
                            plugin.getNametagManager().removeAllViewers(target);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literalShow(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.literal("show")
                .requires(stack -> stack.getSender().hasPermission("unt.show"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            final Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                            if (target == null) {
                                msg(plugin, ctx.getSource().getSender(), "<red>Player not found or not online.</red>");
                                return 0;
                            }
                            plugin.getNametagManager().showToTrackedPlayers(target);
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literalRefresh(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.literal("refresh")
                .requires(stack -> stack.getSender().hasPermission("unt.refresh"))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            final Player target = Bukkit.getPlayerExact(StringArgumentType.getString(ctx, "target"));
                            if (target == null) {
                                msg(plugin, ctx.getSource().getSender(), "<red>Player not found or not online.</red>");
                                return 0;
                            }
                            plugin.getNametagManager().refresh(target, true);
                            msg(plugin, ctx.getSource().getSender(),
                                    "<green>Nametag refreshed for <yellow><name></yellow>.</green>",
                                    Placeholder.unparsed("name", target.getName()));
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literalBillboard(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.literal("billboard")
                .requires(stack -> stack.getSender().hasPermission("unt.billboard"))
                .then(Commands.argument("constraint", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (AbstractDisplayMeta.BillboardConstraints c :
                                    AbstractDisplayMeta.BillboardConstraints.values()) {
                                builder.suggest(c.name());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            final String raw = StringArgumentType.getString(ctx, "constraint");
                            try {
                                final AbstractDisplayMeta.BillboardConstraints c =
                                        AbstractDisplayMeta.BillboardConstraints.valueOf(raw.toUpperCase());
                                plugin.getConfigManager().getSettings().setDefaultBillboard(c);
                                plugin.getConfigManager().save();
                                plugin.getNametagManager().reload();
                                msg(plugin, ctx.getSource().getSender(),
                                        "<green>Default billboard set to <yellow><value></yellow></green>",
                                        Placeholder.unparsed("value", c.name()));
                                return Command.SINGLE_SUCCESS;
                            } catch (IllegalArgumentException e) {
                                msg(plugin, ctx.getSource().getSender(),
                                        "<red>Invalid billboard: <gray><value></gray></red>",
                                        Placeholder.unparsed("value", raw));
                                return 0;
                            }
                        }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literalFormatter(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.literal("formatter")
                .requires(stack -> stack.getSender().hasPermission("unt.formatter"))
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (Formatter f : Formatter.values()) {
                                builder.suggest(f.name());
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            final String raw = StringArgumentType.getString(ctx, "name");
                            try {
                                final Formatter f = Formatter.valueOf(raw.toUpperCase());
                                plugin.getConfigManager().getSettings().setFormat(f);
                                plugin.getConfigManager().save();
                                plugin.getNametagManager().reload();
                                msg(plugin, ctx.getSource().getSender(),
                                        "<green>Default formatter set to <yellow><value></yellow></green>",
                                        Placeholder.unparsed("value", f.name()));
                                return Command.SINGLE_SUCCESS;
                            } catch (IllegalArgumentException e) {
                                msg(plugin, ctx.getSource().getSender(),
                                        "<red>Invalid formatter: <gray><value></gray></red>",
                                        Placeholder.unparsed("value", raw));
                                return 0;
                            }
                        }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literalHideOtherNametags(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.literal("hideOtherNametags")
                .requires(stack -> stack.getSender().hasPermission("unt.hideOtherNametags"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player p)) {
                        msg(plugin, ctx.getSource().getSender(), "<red>Players only.</red>");
                        return 0;
                    }
                    if (!plugin.getNametagManager().isHiddenOtherNametags(p)) {
                        plugin.getNametagManager().hideOtherNametags(p);
                        msg(plugin, p, "<green>Other nametags hidden</green>");
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("silent", BoolArgumentType.bool())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player p)) {
                                msg(plugin, ctx.getSource().getSender(), "<red>Players only.</red>");
                                return 0;
                            }
                            final boolean silent = BoolArgumentType.getBool(ctx, "silent");
                            if (!plugin.getNametagManager().isHiddenOtherNametags(p)) {
                                plugin.getNametagManager().hideOtherNametags(p);
                                if (!silent) {
                                    msg(plugin, p, "<green>Other nametags hidden</green>");
                                }
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literalShowOtherNametags(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.literal("showOtherNametags")
                .requires(stack -> stack.getSender().hasPermission("unt.showOtherNametags"))
                .executes(ctx -> {
                    if (!(ctx.getSource().getSender() instanceof Player p)) {
                        msg(plugin, ctx.getSource().getSender(), "<red>Players only.</red>");
                        return 0;
                    }
                    if (plugin.getNametagManager().isHiddenOtherNametags(p)) {
                        plugin.getNametagManager().showOtherNametags(p);
                        msg(plugin, p, "<green>Other nametags shown</green>");
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("silent", BoolArgumentType.bool())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player p)) {
                                msg(plugin, ctx.getSource().getSender(), "<red>Players only.</red>");
                                return 0;
                            }
                            final boolean silent = BoolArgumentType.getBool(ctx, "silent");
                            if (plugin.getNametagManager().isHiddenOtherNametags(p)) {
                                plugin.getNametagManager().showOtherNametags(p);
                                if (!silent) {
                                    msg(plugin, p, "<green>Other nametags shown</green>");
                                }
                            }
                            return Command.SINGLE_SUCCESS;
                        }));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literalPreferences(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.literal("preferences")
                .requires(stack -> stack.getSender().hasPermission("unt.preferences"))
                .executes(ctx -> {
                    msg(plugin, ctx.getSource().getSender(),
                            "<yellow>/unt preferences get</yellow> <dark_gray>[player]</dark_gray>");
                    msg(plugin, ctx.getSource().getSender(),
                            "<yellow>/unt preferences seeothers</yellow> <gray>true/false</gray> <dark_gray>[player]</dark_gray>");
                    msg(plugin, ctx.getSource().getSender(),
                            "<yellow>/unt preferences showown</yellow> <gray>true/false</gray> <dark_gray>[player]</dark_gray>");
                    msg(plugin, ctx.getSource().getSender(),
                            "<yellow>/unt preferences showothers</yellow> <gray>true/false</gray> <dark_gray>[player]</dark_gray>");
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("get")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player p)) {
                                msg(plugin, ctx.getSource().getSender(),
                                        "<red>Players only (or use /unt preferences get <player>).</red>");
                                return 0;
                            }
                            sendPreferenceLines(plugin, ctx.getSource().getSender(),
                                    "<gray>Your nametag preferences</gray> <dark_gray>(<white><name></white>)</dark_gray>:", p);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.argument("target", StringArgumentType.word())
                                .requires(stack -> stack.getSender().hasPermission("unt.preferences.others"))
                                .suggests((ctx, builder) -> {
                                    Bukkit.getOnlinePlayers().forEach(player -> builder.suggest(player.getName()));
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    final Player target = Bukkit.getPlayerExact(
                                            StringArgumentType.getString(ctx, "target"));
                                    if (target == null) {
                                        msg(plugin, ctx.getSource().getSender(),
                                                "<red>Player not found or not online.</red>");
                                        return 0;
                                    }
                                    sendPreferenceLines(plugin, ctx.getSource().getSender(),
                                            "<gray>Nametag preferences for</gray> <yellow><name></yellow><gray>:</gray>", target);
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("seeothers")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    final boolean v = BoolArgumentType.getBool(ctx, "value");
                                    return execSeeOthersSelf(plugin, ctx.getSource().getSender(), v);
                                })
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .requires(stack -> stack.getSender().hasPermission("unt.preferences.others"))
                                        .suggests((ctx, builder) -> {
                                            Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            final boolean v = BoolArgumentType.getBool(ctx, "value");
                                            final Player target = Bukkit.getPlayerExact(
                                                    StringArgumentType.getString(ctx, "target"));
                                            if (target == null) {
                                                msg(plugin, ctx.getSource().getSender(),
                                                        "<red>Player not found or not online.</red>");
                                                return 0;
                                            }
                                            return applySeeOthers(plugin, ctx.getSource().getSender(), target, v);
                                        }))))
                .then(Commands.literal("showown")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    final boolean v = BoolArgumentType.getBool(ctx, "value");
                                    return execShowOwnSelf(plugin, ctx.getSource().getSender(), v);
                                })
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .requires(stack -> stack.getSender().hasPermission("unt.preferences.others"))
                                        .suggests((ctx, builder) -> {
                                            Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            final boolean v = BoolArgumentType.getBool(ctx, "value");
                                            final Player target = Bukkit.getPlayerExact(
                                                    StringArgumentType.getString(ctx, "target"));
                                            if (target == null) {
                                                msg(plugin, ctx.getSource().getSender(),
                                                        "<red>Player not found or not online.</red>");
                                                return 0;
                                            }
                                            plugin.getNametagManager().setShowingOwnNametagToSelf(target, v);
                                            msg(plugin, ctx.getSource().getSender(),
                                                    "<green>Set show-own (self) for <yellow><name></yellow> to <yellow><state></yellow></green>",
                                                    Placeholder.unparsed("name", target.getName()),
                                                    Placeholder.unparsed("state", String.valueOf(v)));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("showothers")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    final boolean v = BoolArgumentType.getBool(ctx, "value");
                                    return execShowOthersSelf(plugin, ctx.getSource().getSender(), v);
                                })
                                .then(Commands.argument("target", StringArgumentType.word())
                                        .requires(stack -> stack.getSender().hasPermission("unt.preferences.others"))
                                        .suggests((ctx, builder) -> {
                                            Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> {
                                            final boolean v = BoolArgumentType.getBool(ctx, "value");
                                            final Player target = Bukkit.getPlayerExact(
                                                    StringArgumentType.getString(ctx, "target"));
                                            if (target == null) {
                                                msg(plugin, ctx.getSource().getSender(),
                                                        "<red>Player not found or not online.</red>");
                                                return 0;
                                            }
                                            plugin.getNametagManager().setShowingOwnNametagToOthers(target, v);
                                            msg(plugin, ctx.getSource().getSender(),
                                                    "<green>Set show-own-to-others for <yellow><name></yellow> to <yellow><state></yellow></green>",
                                                    Placeholder.unparsed("name", target.getName()),
                                                    Placeholder.unparsed("state", String.valueOf(v)));
                                            return Command.SINGLE_SUCCESS;
                                        }))));
    }

    private static int execSeeOthersSelf(@NotNull UnlimitedNameTags plugin, @NotNull CommandSender sender,
            boolean seeOthers) {
        if (!(sender instanceof Player p)) {
            msg(plugin, sender, "<red>Players only (or specify a target with permission).</red>");
            return 0;
        }
        return applySeeOthers(plugin, sender, p, seeOthers);
    }

    private static int applySeeOthers(@NotNull UnlimitedNameTags plugin, @NotNull CommandSender feedback,
            @NotNull Player target, boolean seeOthers) {
        if (seeOthers) {
            plugin.getNametagManager().showOtherNametags(target);
        } else {
            plugin.getNametagManager().hideOtherNametags(target);
        }
        msg(plugin, feedback,
                "<green>Set seeing others' nametags for <yellow><name></yellow> to <yellow><state></yellow></green>",
                Placeholder.unparsed("name", target.getName()),
                Placeholder.unparsed("state", String.valueOf(seeOthers)));
        return Command.SINGLE_SUCCESS;
    }

    private static int execShowOwnSelf(@NotNull UnlimitedNameTags plugin, @NotNull CommandSender sender,
            boolean show) {
        if (!(sender instanceof Player p)) {
            msg(plugin, sender, "<red>Players only (or specify a target with permission).</red>");
            return 0;
        }
        plugin.getNametagManager().setShowingOwnNametagToSelf(p, show);
        msg(plugin, sender, "<green>Your show-own (self) is now <yellow><state></yellow></green>",
                Placeholder.unparsed("state", String.valueOf(show)));
        return Command.SINGLE_SUCCESS;
    }

    private static int execShowOthersSelf(@NotNull UnlimitedNameTags plugin, @NotNull CommandSender sender,
            boolean show) {
        if (!(sender instanceof Player p)) {
            msg(plugin, sender, "<red>Players only (or specify a target with permission).</red>");
            return 0;
        }
        plugin.getNametagManager().setShowingOwnNametagToOthers(p, show);
        msg(plugin, sender, "<green>Your nametag visibility to others is now <yellow><state></yellow></green>",
                Placeholder.unparsed("state", String.valueOf(show)));
        return Command.SINGLE_SUCCESS;
    }
}
