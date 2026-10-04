package cn.sarskin.ChatSphere.forge.client;

import cn.sarskin.ChatSphere.client.screen.ConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;

public final class ModClientSetup {
    private ModClientSetup() {}

    public static void init(IEventBus modBus) {
        modBus.register(ModClientModBusEvents.class);
        modBus.register(ModKeyMappings.class);
        ModClientEvents.init();
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, lastScreen) -> new ConfigScreen(lastScreen)));
    }
}
