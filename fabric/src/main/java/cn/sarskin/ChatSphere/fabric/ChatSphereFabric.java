package cn.sarskin.ChatSphere.fabric;

import cn.sarskin.ChatSphere.fabric.network.FabricServerNetwork;
import cn.sarskin.ChatSphere.fabric.server.ChatSphereServerEvents;
import cn.sarskin.ChatSphere.platform.LoaderFacade;
import cn.sarskin.ChatSphere.platform.PlatformPaths;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChatSphereFabric implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(ChatSphereFabric.class);
    public static final String MOD_ID = "chatsphere";

    @Override
    public void onInitialize() {
        LOGGER.info("ChatSphere (fabric) initializing");
        PlatformPaths.setProvider(new PlatformPaths.Provider() {
            @Override
            public java.nio.file.Path gameDir() {
                return FabricLoader.getInstance().getGameDir();
            }

            @Override
            public java.nio.file.Path configDir() {
                return FabricLoader.getInstance().getConfigDir();
            }
        });
        LoaderFacade.setProvider(FabricLoader.getInstance()::isModLoaded);

        ChatSphereServerEvents.init();
        ChatSphereCommands.init();
        FabricServerNetwork.init();
        ChatSphereFabricServerExtra.initPlasmoAddon();
    }
}
