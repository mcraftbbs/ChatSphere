package cn.sarskin.ChatSphere.client;

import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.network.ServerboundConfigUpdatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/** Shared client lifecycle hooks used by both the NeoForge and Fabric platforms. */
public final class ClientHooks {
    private static final Logger LOGGER = LoggerFactory.getLogger("ChatSphere-ClientHooks");
    private ClientHooks() {}

    /** Sends server config values that were set before connecting. */
    public static void onClientLogin() {
        Map<String, Boolean> pending = ModServerConfig.flushPendingBooleans();
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        if (conn == null || mc.player == null) return;
        for (Map.Entry<String, Boolean> e : pending.entrySet()) {
            conn.send(new ServerboundCustomPayloadPacket(
                    new ServerboundConfigUpdatePayload(e.getKey(), String.valueOf(e.getValue()))));
        }
        // Pull server-shared custom emoji after joining.
        cn.sarskin.ChatSphere.client.emoji.CustomEmojiRegistry.requestSync();
    }

    public static void onClientDisconnect() {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        try {
            history.saveNow();
        } catch (Throwable t) {
            // never let a save failure escalate into the disconnect/crash path
            LOGGER.warn("Failed to save chat data on disconnect (ignored)", t);
        }
        history.setServerConnected(false);
    }
}
