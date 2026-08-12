package cn.sarskin.ChatSphere.neoforge;

import cn.sarskin.ChatSphere.server.CommandHandlers;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registered manually on the game event bus from {@link ModMain} so that the
 * commands are available on both dedicated servers and client-integrated servers
 * (an @EventBusSubscriber would default to Dist.DEDICATED_SERVER and drop the
 * single-player / LAN case).
 */
public class ModCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("chatsphere")
                .then(Commands.literal("help")
                        .executes(CommandHandlers::executeHelp))
                .then(Commands.literal("list")
                        .executes(CommandHandlers::executeList))
                .then(Commands.literal("info")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(CommandHandlers::executeInfo)))
                .executes(CommandHandlers::executeHelp)
        );
    }
}
