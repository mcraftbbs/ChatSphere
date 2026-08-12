package cn.sarskin.ChatSphere.fabric;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Server-side extras that need reflection (PlasmoVoice addon). */
public final class ChatSphereFabricServerExtra {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatSphereFabricServerExtra.class);

    private ChatSphereFabricServerExtra() {}

    public static void initPlasmoAddon() {
        try {
            if (cn.sarskin.ChatSphere.platform.LoaderFacade.isModLoaded("plasmovoice")) {
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
