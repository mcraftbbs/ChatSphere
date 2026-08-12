package cn.sarskin.ChatSphere.server;

import cn.sarskin.ChatSphere.server.ModServerChannels;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Shared /chatsphere command bodies used by both the NeoForge and Fabric platforms. */
public final class CommandHandlers {
    private CommandHandlers() {}

    public static int executeHelp(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.header"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.help"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.list"), false);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.chatsphere.help.info"), false);
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
}
