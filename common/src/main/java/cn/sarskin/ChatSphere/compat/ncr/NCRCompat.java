package cn.sarskin.ChatSphere.compat.ncr;

import cn.sarskin.ChatSphere.platform.LoaderFacade;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

public class NCRCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChatSphere/NCR");
    private static final String NCR_MOD_ID = "nochatreports";
    private static Boolean loaded;

    public static boolean isNCRLoaded() {
        if (loaded == null) {
            loaded = LoaderFacade.isModLoaded(NCR_MOD_ID);
        }
        return loaded;
    }

    public static Component getSafetyStatusComponent() {
        if (!isNCRLoaded())
            return Component.translatable("ncr.chatsphere.not_installed").withStyle(ChatFormatting.GRAY);

        try {
            Class<?> stateClass = Class.forName("com.aizistral.nochatreports.common.core.ServerSafetyState");
            Method getCurrent = stateClass.getMethod("getCurrent");
            Object level = getCurrent.invoke(null);
            String name = level.toString();
            return switch (name) {
                case "SECURE" -> Component.translatable("ncr.chatsphere.safe").withStyle(ChatFormatting.GREEN);
                case "INSECURE" -> Component.translatable("ncr.chatsphere.unsafe").withStyle(ChatFormatting.RED);
                case "SINGLEPLAYER" -> Component.translatable("ncr.chatsphere.singleplayer").withStyle(ChatFormatting.YELLOW);
                default -> Component.translatable("ncr.chatsphere.unknown").withStyle(ChatFormatting.GRAY);
            };
        } catch (Exception e) {
            LOGGER.warn("Failed to query NCR safety status", e);
            return Component.translatable("ncr.chatsphere.unknown").withStyle(ChatFormatting.GRAY);
        }
    }
}
