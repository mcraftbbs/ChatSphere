package cn.sarskin.ChatSphere.neoforge;

import cn.sarskin.ChatSphere.neoforge.client.ModKeyMappings;
import cn.sarskin.ChatSphere.neoforge.network.ModNetworkSetup;
import cn.sarskin.ChatSphere.platform.PlatformPaths;
import cn.sarskin.ChatSphere.neoforge.server.ModServerEvents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ModMain.MODID)
public class ModMain {
    public static final String MODID = "chatsphere";
    public static final String DEFAULT_CHANNEL_ID = "#general";
    public static final Logger LOGGER = LoggerFactory.getLogger(ModMain.class);

    public ModMain(IEventBus modEventBus, ModContainer modContainer) {
        PlatformPaths.setProvider(new PlatformPaths.Provider() {
            @Override
            public java.nio.file.Path gameDir() {
                return FMLPaths.GAMEDIR.get();
            }

            @Override
            public java.nio.file.Path configDir() {
                return FMLPaths.CONFIGDIR.get();
            }
        });
        modEventBus.register(ModNetworkSetup.class);
        NeoForge.EVENT_BUS.register(ModServerEvents.class);
        NeoForge.EVENT_BUS.register(ModCommands.class);
        if (FMLLoader.getDist() == Dist.CLIENT) {
            modEventBus.register(ModKeyMappings.class);
            // Load client-only setup reflectively to avoid RuntimeDistCleaner errors on server
            try {
                Class.forName("cn.sarskin.ChatSphere.neoforge.client.ModClientSetup")
                    .getMethod("init", ModContainer.class)
                    .invoke(null, modContainer);
            } catch (Exception ignored) {}
        }

        // Load PlasmoVoice room addon if PV is installed
        try {
            if (ModList.get().isLoaded("plasmovoice")) {
                Class<?> pvsClass = Class.forName("su.plo.voice.api.server.PlasmoVoiceServer");
                Object loader = pvsClass.getMethod("getAddonsLoader").invoke(null);
                Object addon = Class.forName("cn.sarskin.ChatSphere.server.voice.PlasmoRoomAddon")
                        .getConstructor().newInstance();
                loader.getClass().getMethod("load", Object.class).invoke(loader, addon);
            }
        } catch (Exception ignored) {}
    }
}
