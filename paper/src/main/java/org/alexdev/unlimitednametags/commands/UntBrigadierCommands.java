package org.alexdev.unlimitednametags.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
import org.alexdev.unlimitednametags.config.ColorStrings;
import org.alexdev.unlimitednametags.config.Formatter;
import org.alexdev.unlimitednametags.config.GlowOverride;
import org.alexdev.unlimitednametags.config.NametagGlowOverrides;
import org.alexdev.unlimitednametags.config.TextFormatter;
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
                    .then(literalGlow(plugin))
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
        msg(plugin, sender, "<green>/unt debugger</green> <gray>- Toggle nametag debug logging</gray>");
        msg(plugin, sender, "<green>/unt debugger</green> <gray><true|false></gray> <gray>- Set nametag debug logging</gray>");
        msg(plugin, sender, "<green>/unt billboard</green> <gray><type></gray> <gray>- Set default billboard</gray>");
        msg(plugin, sender, "<green>/unt formatter</green> <gray><name></gray> <gray>- Set default text formatter</gray>");
        msg(plugin, sender, "<green>/unt hideOtherNametags</green> <gray>- Hide other players' nametags</gray>");
        msg(plugin, sender, "<green>/unt showOtherNametags</green> <gray>- Show other players' nametags</gray>");
        msg(plugin, sender, "<green>/unt preferences …</green> <gray>- Per-player nametag visibility (see /unt preferences)");
        msg(plugin, sender, "<green>/unt glow …</green> <gray>- Per-player glow overrides (see /unt glow)");
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
                .executes(ctx -> {
                    final boolean debug = !plugin.getNametagManager().isDebug();
                    plugin.getNametagManager().setDebug(debug);
                    msg(plugin, ctx.getSource().getSender(),
                            "<green>UnlimitedNameTags debug mode set to <yellow><state></yellow></green>",
                            Placeholder.unparsed("state", String.valueOf(debug)));
                    return Command.SINGLE_SUCCESS;
                })
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
                                plugin.getConfigManager().getSettings().getBehavior().setDefaultBillboard(c);
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
                                final TextFormatter f = TextFormatter.valueOf(raw.toUpperCase());
                                plugin.getConfigManager().getSettings().getBehavior().setFormat(f);
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

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> literalGlow(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.literal("glow")
                .requires(stack -> stack.getSender().hasPermission("unt.glow"))
                .executes(ctx -> {
                    msg(plugin, ctx.getSource().getSender(),
                            "<yellow>/unt glow fixed</yellow> <gray><player> <group> <color></gray>");
                    msg(plugin, ctx.getSource().getSender(),
                            "<yellow>/unt glow animation</yellow> <gray><id> [player] [group]</gray>");
                    msg(plugin, ctx.getSource().getSender(),
                            "<yellow>/unt glow rate</yellow> <gray><rate> <id> [player] [group]</gray>");
                    msg(plugin, ctx.getSource().getSender(),
                            "<yellow>/unt glow rainbow</yellow> <gray><player> <group> [speed]</gray>");
                    msg(plugin, ctx.getSource().getSender(),
                            "<yellow>/unt glow gradient</yellow> <gray><player> <group> <color...> [interval]</gray>");
                    msg(plugin, ctx.getSource().getSender(),
                            "<yellow>/unt glow clear</yellow> <gray><player> [group]</gray>");
                    msg(plugin, ctx.getSource().getSender(),
                            "<yellow>/unt glow get</yellow> <gray>[player]</gray>");
                    return Command.SINGLE_SUCCESS;
                })
                .then(glowPlayerArg(plugin, "fixed")
                        .then(Commands.argument("group", IntegerArgumentType.integer(0))
                                .then(Commands.argument("color", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            final Player target = glowTarget(ctx, plugin);
                                            if (target == null) {
                                                return 0;
                                            }
                                            final int group = IntegerArgumentType.getInteger(ctx, "group");
                                            final String color = StringArgumentType.getString(ctx, "color").trim();
                                            if (!ColorStrings.isValid(color)) {
                                                msg(plugin, ctx.getSource().getSender(),
                                                        "<red>Invalid color. Use #RRGGBB or R,G,B.</red>");
                                                return 0;
                                            }
                                            plugin.getNametagManager().setDisplayGroupGlow(
                                                    target.getUniqueId(), group, NametagGlowOverrides.fixed(color), true);
                                            msg(plugin, ctx.getSource().getSender(),
                                                    "<green>Set fixed glow on <yellow><name></yellow> group <yellow><group></yellow>.</green>",
                                                    Placeholder.unparsed("name", target.getName()),
                                                    Placeholder.unparsed("group", String.valueOf(group)));
                                            return Command.SINGLE_SUCCESS;
                                        }))))
                .then(Commands.literal("animation")
                        .then(glowAnimationApplyTree(plugin, ctx -> 1.0)))
                .then(Commands.literal("rate")
                        .then(Commands.argument("rate", DoubleArgumentType.doubleArg(0.01))
                                .then(glowAnimationApplyTree(plugin,
                                        ctx -> DoubleArgumentType.getDouble(ctx, "rate")))))
                .then(glowPlayerArg(plugin, "rainbow")
                        .then(Commands.argument("group", IntegerArgumentType.integer(0))
                                .executes(ctx -> execGlowRainbow(plugin, ctx, 1.0))
                                .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.01))
                                        .executes(ctx -> execGlowRainbow(plugin, ctx,
                                                DoubleArgumentType.getDouble(ctx, "speed"))))))
                .then(glowPlayerArg(plugin, "gradient")
                        .then(Commands.argument("group", IntegerArgumentType.integer(0))
                                .then(Commands.argument("colors", StringArgumentType.greedyString())
                                        .executes(ctx -> execGlowGradient(plugin, ctx, 10)))))
                .then(Commands.literal("clear")
                        .then(glowRequiredTargetArg(plugin)
                                .executes(ctx -> execGlowClearAll(plugin, ctx))
                                .then(glowGroupArgForTarget(plugin)
                                        .executes(ctx -> execGlowClearGroup(plugin, ctx)))))
                .then(Commands.literal("get")
                        .executes(ctx -> {
                            if (!(ctx.getSource().getSender() instanceof Player self)) {
                                msg(plugin, ctx.getSource().getSender(),
                                        "<red>Players only (or /unt glow get <player>).</red>");
                                return 0;
                            }
                            sendGlowLines(plugin, ctx.getSource().getSender(), self);
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(glowRequiredTargetArg(plugin)
                                .executes(ctx -> {
                                    final Player target = glowTarget(ctx, plugin);
                                    if (target == null) {
                                        return 0;
                                    }
                                    sendGlowLines(plugin, ctx.getSource().getSender(), target);
                                    return Command.SINGLE_SUCCESS;
                                })));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> glowPlayerArg(
            @NotNull UnlimitedNameTags plugin, @NotNull String sub) {
        return Commands.literal(sub).then(glowRequiredTargetArg(plugin));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> glowAnimationIdArg(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.argument("animationId", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    plugin.getKnownGlowAnimationIds().forEach(builder::suggest);
                    return builder.buildFuture();
                });
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> glowAnimationApplyTree(
            @NotNull UnlimitedNameTags plugin,
            @NotNull java.util.function.ToDoubleFunction<com.mojang.brigadier.context.CommandContext<CommandSourceStack>> rate) {
        return glowAnimationIdArg(plugin)
                .executes(ctx -> execGlowAnimationSelf(plugin, ctx, rate.applyAsDouble(ctx), null))
                .then(glowRequiredTargetArg(plugin)
                        .executes(ctx -> execGlowAnimationTarget(plugin, ctx, rate.applyAsDouble(ctx), null))
                        .then(glowGroupArg(plugin)
                                .executes(ctx -> execGlowAnimationTarget(plugin, ctx, rate.applyAsDouble(ctx),
                                        IntegerArgumentType.getInteger(ctx, "group")))))
                .then(glowGroupArg(plugin)
                        .executes(ctx -> execGlowAnimationSelf(plugin, ctx, rate.applyAsDouble(ctx),
                                IntegerArgumentType.getInteger(ctx, "group"))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> glowRequiredTargetArg(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.argument("target", StringArgumentType.word())
                .suggests((ctx, builder) -> {
                    Bukkit.getOnlinePlayers().forEach(player -> builder.suggest(player.getName()));
                    return builder.buildFuture();
                });
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> glowGroupArgForTarget(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.argument("group", IntegerArgumentType.integer(0))
                .suggests((ctx, builder) -> {
                    final Player target = glowTarget(ctx, plugin);
                    if (target == null) {
                        return builder.buildFuture();
                    }
                    final int count = glowDisplayGroupCount(plugin, target);
                    for (int i = 0; i < count; i++) {
                        builder.suggest(i);
                    }
                    return builder.buildFuture();
                });
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> glowGroupArg(
            @NotNull UnlimitedNameTags plugin) {
        return Commands.argument("group", IntegerArgumentType.integer(0))
                .suggests((ctx, builder) -> {
                    final Player target = resolveGlowAnimationTarget(ctx, plugin, false);
                    if (target == null) {
                        return builder.buildFuture();
                    }
                    final int count = glowDisplayGroupCount(plugin, target);
                    for (int i = 0; i < count; i++) {
                        builder.suggest(i);
                    }
                    return builder.buildFuture();
                });
    }

    private static int glowDisplayGroupCount(
            @NotNull UnlimitedNameTags plugin,
            @NotNull Player target) {
        return plugin.getNametagManager().getEffectiveNametag(target).displayGroups().size();
    }

    @org.jetbrains.annotations.Nullable
    private static Player resolveGlowAnimationTarget(
            @NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            @NotNull UnlimitedNameTags plugin,
            boolean messageOnMissingSelf) {
        try {
            final String name = StringArgumentType.getString(ctx, "target");
            return Bukkit.getPlayerExact(name);
        } catch (IllegalArgumentException ignored) {
            if (ctx.getSource().getSender() instanceof Player player) {
                return player;
            }
            if (messageOnMissingSelf) {
                msg(plugin, ctx.getSource().getSender(),
                        "<red>Players only (or specify a target).</red>");
            }
            return null;
        }
    }

    @org.jetbrains.annotations.Nullable
    private static Player glowTarget(
            @NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            @NotNull UnlimitedNameTags plugin) {
        final CommandSender sender = ctx.getSource().getSender();
        final String name = StringArgumentType.getString(ctx, "target");
        final Player target = Bukkit.getPlayerExact(name);
        if (target == null) {
            msg(plugin, sender, "<red>Player not found or not online.</red>");
            return null;
        }
        if (!sender.equals(target) && !sender.hasPermission("unt.glow.others")) {
            msg(plugin, sender, "<red>You may not modify other players' glow.</red>");
            return null;
        }
        return target;
    }

    @org.jetbrains.annotations.Nullable
    private static Player glowSelf(
            @NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            @NotNull UnlimitedNameTags plugin) {
        final CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        msg(plugin, sender, "<red>Players only (or specify a target).</red>");
        return null;
    }

    private static int execGlowAnimationSelf(
            @NotNull UnlimitedNameTags plugin,
            @NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            double rate,
            @org.jetbrains.annotations.Nullable Integer group) {
        final Player target = glowSelf(ctx, plugin);
        if (target == null) {
            return 0;
        }
        return execGlowAnimation(plugin, ctx, rate, target, group);
    }

    private static int execGlowAnimationTarget(
            @NotNull UnlimitedNameTags plugin,
            @NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            double rate,
            @org.jetbrains.annotations.Nullable Integer group) {
        final Player target = glowTarget(ctx, plugin);
        if (target == null) {
            return 0;
        }
        return execGlowAnimation(plugin, ctx, rate, target, group);
    }

    private static int execGlowAnimation(
            @NotNull UnlimitedNameTags plugin,
            @NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            double rate,
            @NotNull Player target,
            @org.jetbrains.annotations.Nullable Integer group) {
        final String id = StringArgumentType.getString(ctx, "animationId");
        if (!plugin.isKnownGlowAnimationId(id)) {
            msg(plugin, ctx.getSource().getSender(),
                    "<red>Unknown glow animation: <gray><id></gray></red>",
                    Placeholder.unparsed("id", id));
            return 0;
        }
        final GlowOverride glow = NametagGlowOverrides.reference(id, rate);
        final int groupCount = glowDisplayGroupCount(plugin, target);
        if (groupCount == 0) {
            msg(plugin, ctx.getSource().getSender(),
                    "<red><name> has no display groups.</red>",
                    Placeholder.unparsed("name", target.getName()));
            return 0;
        }
        if (group != null) {
            if (group < 0 || group >= groupCount) {
                msg(plugin, ctx.getSource().getSender(),
                        "<red>Invalid group <yellow><group></yellow> for <yellow><name></yellow> (0-<max>).</red>",
                        Placeholder.unparsed("group", String.valueOf(group)),
                        Placeholder.unparsed("name", target.getName()),
                        Placeholder.unparsed("max", String.valueOf(groupCount - 1)));
                return 0;
            }
            plugin.getNametagManager().setDisplayGroupGlow(target.getUniqueId(), group, glow, true);
        } else {
            for (int i = 0; i < groupCount; i++) {
                plugin.getNametagManager().setDisplayGroupGlow(target.getUniqueId(), i, glow, true, false);
            }
            plugin.getNametagManager().applyPersistentGlowOverrides(target);
            plugin.getNametagManager().refresh(target, false);
        }
        final boolean allGroups = group == null;
        if (rate == 1.0) {
            if (allGroups) {
                msg(plugin, ctx.getSource().getSender(),
                        "<green>Set glow animation <yellow><id></yellow> on <yellow><name></yellow>.</green>",
                        Placeholder.unparsed("id", id),
                        Placeholder.unparsed("name", target.getName()));
            } else {
                msg(plugin, ctx.getSource().getSender(),
                        "<green>Set glow animation <yellow><id></yellow> on <yellow><name></yellow> group <yellow><group></yellow>.</green>",
                        Placeholder.unparsed("id", id),
                        Placeholder.unparsed("name", target.getName()),
                        Placeholder.unparsed("group", String.valueOf(group)));
            }
        } else if (allGroups) {
            msg(plugin, ctx.getSource().getSender(),
                    "<green>Set glow animation <yellow><id></yellow> (rate <yellow><rate></yellow>) on <yellow><name></yellow>.</green>",
                    Placeholder.unparsed("id", id),
                    Placeholder.unparsed("rate", String.valueOf(rate)),
                    Placeholder.unparsed("name", target.getName()));
        } else {
            msg(plugin, ctx.getSource().getSender(),
                    "<green>Set glow animation <yellow><id></yellow> (rate <yellow><rate></yellow>) on <yellow><name></yellow> group <yellow><group></yellow>.</green>",
                    Placeholder.unparsed("id", id),
                    Placeholder.unparsed("rate", String.valueOf(rate)),
                    Placeholder.unparsed("name", target.getName()),
                    Placeholder.unparsed("group", String.valueOf(group)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int execGlowRainbow(
            @NotNull UnlimitedNameTags plugin,
            @NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            double speed) {
        final Player target = glowTarget(ctx, plugin);
        if (target == null) {
            return 0;
        }
        final int group = IntegerArgumentType.getInteger(ctx, "group");
        plugin.getNametagManager().setDisplayGroupGlow(
                target.getUniqueId(), group, NametagGlowOverrides.rainbow(speed), true);
        msg(plugin, ctx.getSource().getSender(),
                "<green>Set rainbow glow on <yellow><name></yellow> group <yellow><group></yellow>.</green>",
                Placeholder.unparsed("name", target.getName()),
                Placeholder.unparsed("group", String.valueOf(group)));
        return Command.SINGLE_SUCCESS;
    }

    private static int execGlowGradient(
            @NotNull UnlimitedNameTags plugin,
            @NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx,
            int defaultInterval) {
        final Player target = glowTarget(ctx, plugin);
        if (target == null) {
            return 0;
        }
        final int group = IntegerArgumentType.getInteger(ctx, "group");
        final String raw = StringArgumentType.getString(ctx, "colors").trim();
        final String[] tokens = raw.split("\\s+");
        int interval = defaultInterval;
        final List<String> colors = new java.util.ArrayList<>();
        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].equalsIgnoreCase("interval")) {
                if (i + 1 >= tokens.length) {
                    msg(plugin, ctx.getSource().getSender(), "<red>Missing value after interval.</red>");
                    return 0;
                }
                try {
                    interval = Integer.parseInt(tokens[i + 1]);
                } catch (NumberFormatException ignored) {
                    msg(plugin, ctx.getSource().getSender(), "<red>Invalid gradient interval.</red>");
                    return 0;
                }
                break;
            }
            if (!ColorStrings.isValid(tokens[i])) {
                msg(plugin, ctx.getSource().getSender(),
                        "<red>Invalid gradient color: <gray><color></gray></red>",
                        Placeholder.unparsed("color", tokens[i]));
                return 0;
            }
            colors.add(tokens[i]);
        }
        if (colors.size() < 2) {
            msg(plugin, ctx.getSource().getSender(), "<red>Gradient requires at least two colors.</red>");
            return 0;
        }
        final GlowOverride.GradientGlowOverride gradient = NametagGlowOverrides.gradient(colors, interval);
        plugin.getNametagManager().setDisplayGroupGlow(target.getUniqueId(), group, gradient, true);
        msg(plugin, ctx.getSource().getSender(),
                "<green>Set gradient glow on <yellow><name></yellow> group <yellow><group></yellow>.</green>",
                Placeholder.unparsed("name", target.getName()),
                Placeholder.unparsed("group", String.valueOf(group)));
        return Command.SINGLE_SUCCESS;
    }

    private static int execGlowClearAll(
            @NotNull UnlimitedNameTags plugin,
            @NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        final Player target = glowTarget(ctx, plugin);
        if (target == null) {
            return 0;
        }
        final int groups = plugin.getNametagManager().getEffectiveNametag(target).displayGroups().size();
        for (int i = 0; i < groups; i++) {
            plugin.getNametagManager().clearDisplayGroupGlow(target.getUniqueId(), i, true, false);
        }
        plugin.getNametagManager().applyPersistentGlowOverrides(target);
        plugin.getNametagManager().refresh(target, false);
        msg(plugin, ctx.getSource().getSender(),
                "<green>Cleared all glow overrides for <yellow><name></yellow>.</green>",
                Placeholder.unparsed("name", target.getName()));
        return Command.SINGLE_SUCCESS;
    }

    private static int execGlowClearGroup(
            @NotNull UnlimitedNameTags plugin,
            @NotNull com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        final Player target = glowTarget(ctx, plugin);
        if (target == null) {
            return 0;
        }
        final int group = IntegerArgumentType.getInteger(ctx, "group");
        final int groupCount = glowDisplayGroupCount(plugin, target);
        if (group < 0 || group >= groupCount) {
            msg(plugin, ctx.getSource().getSender(),
                    "<red>Invalid group <yellow><group></yellow> for <yellow><name></yellow> (0-<max>).</red>",
                    Placeholder.unparsed("group", String.valueOf(group)),
                    Placeholder.unparsed("name", target.getName()),
                    Placeholder.unparsed("max", String.valueOf(groupCount - 1)));
            return 0;
        }
        plugin.getNametagManager().clearDisplayGroupGlow(target.getUniqueId(), group, true);
        msg(plugin, ctx.getSource().getSender(),
                "<green>Cleared glow on <yellow><name></yellow> group <yellow><group></yellow>.</green>",
                Placeholder.unparsed("name", target.getName()),
                Placeholder.unparsed("group", String.valueOf(group)));
        return Command.SINGLE_SUCCESS;
    }

    private static void sendGlowLines(
            @NotNull UnlimitedNameTags plugin,
            @NotNull CommandSender to,
            @NotNull Player subject) {
        msg(plugin, to, "<gray>Glow overrides for</gray> <yellow><name></yellow><gray>:</gray>",
                Placeholder.unparsed("name", subject.getName()));
        final int groups = plugin.getNametagManager().getEffectiveNametag(subject).displayGroups().size();
        for (int i = 0; i < groups; i++) {
            final String value = plugin.getNametagManager()
                    .getDisplayGroupGlowOverride(subject.getUniqueId(), i)
                    .map(g -> g.getClass().getSimpleName())
                    .orElse("none");
            msg(plugin, to, "  <yellow><group></yellow><gray>:</gray> <white><value></white>",
                    Placeholder.unparsed("group", String.valueOf(i)),
                    Placeholder.unparsed("value", value));
        }
    }
}
