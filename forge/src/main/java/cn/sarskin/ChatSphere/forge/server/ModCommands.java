package cn.sarskin.ChatSphere.forge.server;

import cn.sarskin.ChatSphere.server.CommandHandlers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Registered on the game bus so commands also work in single player and on LAN. */
public final class ModCommands {
    private ModCommands() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.register(ModCommands.class);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(CommandHandlers.buildRoot());
    }
}
