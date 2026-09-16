package cn.sarskin.ChatSphere.server;

import cn.sarskin.ChatSphere.server.ModServerChannels;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Shared /chatsphere command bodies used by both the NeoForge and Fabric platforms. */
public final class CommandHandlers {
    private CommandHandlers() {}

    private static final int OP_LEVEL = 2;
    private static final int BACKUP_LIST_LIMIT = 10;

    public static int executeHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.header"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.help"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.list"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.info"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.doctor"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.backup"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.backups"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.restore"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.purge"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.reload"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.switch_hint"), false);
        return 1;
    }

    public static int executeList(CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        ModServerChannels msc = ModServerChannels.getInstance(server);
        List<ModServerChannels.ChannelEntry> list = msc.getAllChannels();

        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.list.header", list.size()), false);

        for (ModServerChannels.ChannelEntry e : list) {
            int memberCount = e.members().size();
            String visKey = e.isPublic() ? "command.chatsphere.list.public" : "command.chatsphere.list.private";
            ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.list.entry",
                    e.id(),
                    Component.translatable(visKey),
                    memberCount,
                    e.admins().size()), false);
        }
        return 1;
    }

    public static int executeInfo(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        String channelId = name.startsWith("#") ? name : "#" + name;
        var server = ctx.getSource().getServer();
        ModServerChannels msc = ModServerChannels.getInstance(server);
        ModServerChannels.ChannelEntry entry = msc.getChannel(channelId);

        if (entry == null) {
            ctx.getSource().sendFailure(Component.translatable("command.chatsphere.info.not_found", channelId));
            return 0;
        }

        String displayName = entry.displayName().isEmpty() ? entry.id().substring(1) : entry.displayName();
        String description = entry.description().isEmpty() ?
                Component.translatable("command.chatsphere.info.no_description").getString() :
                entry.description();
        String visKey = entry.isPublic() ? "command.chatsphere.info.public" : "command.chatsphere.info.private";

        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.info.header", entry.id()), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.info.display_name", displayName), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.info.status", Component.translatable(visKey)), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.info.description", description), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.info.owner", entry.owner()), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.info.members", entry.members().size()), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.info.admins", entry.admins().size()), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.info.muted", entry.mutedPlayers().size()), false);
        return 1;
    }

    public static int executeDoctor(CommandContext<CommandSourceStack> ctx) {
        if (!requireOp(ctx)) return 0;
        ModServerChannels msc = ModServerChannels.getInstance(ctx.getSource().getServer());
        int chat = msc.chatMessageCount();
        int console = msc.commandMessageCount();
        int chatCap = cn.sarskin.ChatSphere.config.ModServerConfig.CONFIG.maxChatHistory.get();
        int consoleCap = cn.sarskin.ChatSphere.config.ModServerConfig.CONFIG.maxCommandMessages.get();
        int backups = ModServerChannels.listBackupTimestamps().size();

        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.doctor.header"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.doctor.chat", chat, chatCap), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.doctor.console", console, consoleCap), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.doctor.files",
                formatBytes(msc.dataFileBytes(true)), formatBytes(msc.dataFileBytes(false))), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.doctor.backups", backups), false);
        DiscordBridge bridge = DiscordBridge.getInstance(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.doctor.discord",
                Component.translatable(discordStateKey(bridge)), bridge.mirrorChannel()), false);
        if (!bridge.lastError().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable(
                    "command.chatsphere.doctor.discord_error", bridge.lastError()), false);
        }
        if (chat > chatCap || console > consoleCap) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.doctor.overflow"), false);
        }
        return 1;
    }

    private static String discordStateKey(DiscordBridge bridge) {
        if (!cn.sarskin.ChatSphere.config.ModServerConfig.CONFIG.discordEnabled.get()) {
            return "command.chatsphere.doctor.discord_off";
        }
        if (bridge.webhookConfigured()) return "command.chatsphere.doctor.discord_webhook";
        if (bridge.botConfigured()) return "command.chatsphere.doctor.discord_bot";
        return "command.chatsphere.doctor.discord_unconfigured";
    }

    public static int executeBackup(CommandContext<CommandSourceStack> ctx) {
        if (!requireOp(ctx)) return 0;
        ModServerChannels.getInstance(ctx.getSource().getServer()).backupNow();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.backup.done"), false);
        return 1;
    }

    public static int executeBackups(CommandContext<CommandSourceStack> ctx) {
        if (!requireOp(ctx)) return 0;
        List<String> stamps = ModServerChannels.listBackupTimestamps();
        if (stamps.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.backup.none"), false);
            return 1;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.backup.list_header", stamps.size()), false);
        for (String stamp : stamps.subList(0, Math.min(stamps.size(), BACKUP_LIST_LIMIT))) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.backup.list_entry", stamp), false);
        }
        return 1;
    }

    public static int executeRestore(CommandContext<CommandSourceStack> ctx) {
        if (!requireOp(ctx)) return 0;
        String stamp = StringArgumentType.getString(ctx, "timestamp");
        ModServerChannels msc = ModServerChannels.getInstance(ctx.getSource().getServer());
        String result = msc.restoreMessagesFromBackup(stamp);
        switch (result) {
            case "ok" -> ctx.getSource().sendSuccess(
                    () -> Component.translatable("command.chatsphere.restore.ok", stamp), true);
            case "not_found" -> ctx.getSource().sendFailure(
                    Component.translatable("command.chatsphere.restore.not_found", stamp));
            default -> ctx.getSource().sendFailure(
                    Component.translatable("command.chatsphere.restore.failed"));
        }
        return "ok".equals(result) ? 1 : 0;
    }

    public static int executePurge(CommandContext<CommandSourceStack> ctx) {
        if (!requireOp(ctx)) return 0;
        String name = StringArgumentType.getString(ctx, "channel").trim();
        String channelId = name.startsWith("#") ? name : "#" + name;
        ModServerChannels msc = ModServerChannels.getInstance(ctx.getSource().getServer());
        int removed = msc.purgeChannel(channelId);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.purge.done", channelId, removed), true);
        return 1;
    }

    public static int executeReload(CommandContext<CommandSourceStack> ctx) {
        if (!requireOp(ctx)) return 0;
        ModServerChannels msc = ModServerChannels.getInstance(ctx.getSource().getServer());
        msc.flush();
        msc.reloadMessages();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.reload.done"), true);
        return 1;
    }

    /** The whole {@code /chatsphere} tree; both loaders register this. */
    public static LiteralArgumentBuilder<CommandSourceStack> buildRoot() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("chatsphere")
                .then(Commands.literal("help").executes(CommandHandlers::executeHelp))
                .then(Commands.literal("list").executes(CommandHandlers::executeList))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(CommandHandlers::executeInfo)))
                .then(Commands.literal("doctor").executes(CommandHandlers::executeDoctor))
                .then(Commands.literal("backup").executes(CommandHandlers::executeBackup))
                .then(Commands.literal("backups").executes(CommandHandlers::executeBackups))
                .then(Commands.literal("restore")
                        .then(Commands.argument("timestamp", StringArgumentType.word())
                                .executes(CommandHandlers::executeRestore)))
                .then(Commands.literal("purge")
                        .then(Commands.argument("channel", StringArgumentType.greedyString())
                                .executes(CommandHandlers::executePurge)))
                .then(Commands.literal("reload").executes(CommandHandlers::executeReload))
                .executes(CommandHandlers::executeHelp);
        return root;
    }

    private static boolean requireOp(CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().hasPermission(OP_LEVEL)) return true;
        ctx.getSource().sendFailure(Component.translatable("command.chatsphere.op_only"));
        return false;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024.0));
    }
}
