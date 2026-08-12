package cn.sarskin.ChatSphere.fabric;

import cn.sarskin.ChatSphere.server.CommandHandlers;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;

public final class ChatSphereCommands {
    private ChatSphereCommands() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("chatsphere")
                        .then(Commands.literal("help")
                                .executes(CommandHandlers::executeHelp))
                        .then(Commands.literal("list")
                                .executes(CommandHandlers::executeList))
                        .then(Commands.literal("info")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(CommandHandlers::executeInfo)))
                        .executes(CommandHandlers::executeHelp)));
    }
}
