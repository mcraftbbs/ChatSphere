package cn.sarskin.ChatSphere.client;

import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.network.ServerboundConfigUpdatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;

import java.util.Map;

/** Shared client lifecycle hooks used by both the NeoForge and Fabric platforms. */
public final class ClientHooks {
    private ClientHooks() {}

    /** Sends server config values that were set before connecting. */
    public static void onClientLogin() {
        Map<String, Boolean> pending = ModServerConfig.flushPendingBooleans();
        if (pending.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        if (conn == null || mc.player == null) return;
        for (Map.Entry<String, Boolean> e : pending.entrySet()) {
            conn.send(new ServerboundCustomPayloadPacket(ServerboundConfigUpdatePayload.ID,
                    new ServerboundConfigUpdatePayload(e.getKey(), String.valueOf(e.getValue())).toBuf()));
        }
    }

    public static void onClientDisconnect() {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        history.saveNow();
        history.setServerConnected(false);
    }
}
