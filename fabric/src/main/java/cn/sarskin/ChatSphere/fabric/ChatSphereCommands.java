package cn.sarskin.ChatSphere.fabric;

import cn.sarskin.ChatSphere.server.CommandHandlers;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

public final class ChatSphereCommands {
    private ChatSphereCommands() {}

    public static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandHandlers.buildRoot()));
    }
}
