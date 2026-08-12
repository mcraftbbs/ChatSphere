package cn.sarskin.ChatSphere.neoforge.client;

import cn.sarskin.ChatSphere.client.screen.ConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public class ModClientSetup {
    public static void init(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
            (IConfigScreenFactory) (container, modListScreen) -> new ConfigScreen(modListScreen));
        modContainer.getEventBus().addListener(ModClientModBusEvents::registerGuiLayers);
    }
}
