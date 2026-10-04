package cn.sarskin.ChatSphere.forge;

import cn.sarskin.ChatSphere.forge.client.ModKeyMappings;
import cn.sarskin.ChatSphere.forge.network.ForgeNetwork;
import cn.sarskin.ChatSphere.forge.server.ModCommands;
import cn.sarskin.ChatSphere.forge.server.ModServerEvents;
import cn.sarskin.ChatSphere.platform.LoaderFacade;
import cn.sarskin.ChatSphere.platform.PacketSender;
import cn.sarskin.ChatSphere.platform.PlatformPaths;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ModMain.MODID)
public class ModMain {
    public static final String MODID = "chatsphere";
    public static final Logger LOGGER = LoggerFactory.getLogger(ModMain.class);

    public ModMain() {
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
        LoaderFacade.setProvider(ModList.get()::isLoaded);
        PacketSender.setProvider(new ForgePacketSender());

        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.register(ForgeNetwork.class);
        ModServerEvents.init();
        ModCommands.init();

        // Client-only classes are loaded reflectively so dedicated servers never touch them.
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> {
                    modBus.register(ModKeyMappings.class);
                    try {
                        Class.forName("cn.sarskin.ChatSphere.forge.client.ModClientSetup")
                                .getMethod("init", IEventBus.class)
                                .invoke(null, modBus);
                    } catch (Exception ignored) {
                    }
                });

        try {
            if (ModList.get().isLoaded("plasmovoice")) {
                Class<?> pvsClass = Class.forName("su.plo.voice.api.server.PlasmoVoiceServer");
                Object loader = pvsClass.getMethod("getAddonsLoader").invoke(null);
                Object addon = Class.forName("cn.sarskin.ChatSphere.server.voice.PlasmoRoomAddon")
                        .getConstructor().newInstance();
                loader.getClass().getMethod("load", Object.class).invoke(loader, addon);
            }
        } catch (Exception ignored) {
        }
    }
}
