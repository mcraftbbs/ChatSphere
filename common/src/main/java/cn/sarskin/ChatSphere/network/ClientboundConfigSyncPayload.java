package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Server → client: current server config values (full snapshot on join, single key on change). */
public record ClientboundConfigSyncPayload(Map<String, String> values) {
    public static final ResourceLocation ID = new ResourceLocation(ModInfo.MODID, "config_sync_s2c");

    public FriendlyByteBuf toBuf() {
        FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        buf.writeInt(values.size());
        for (Map.Entry<String, String> e : values.entrySet()) {
            writeUtf(buf, e.getKey());
            writeUtf(buf, e.getValue());
        }
        return buf;
    }

    public static ClientboundConfigSyncPayload read(FriendlyByteBuf buf) {
        int count = Math.min(Math.max(buf.readInt(), 0), 512);
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < count; i++) {
            values.put(PayloadLimits.readUtf(buf), PayloadLimits.readUtf(buf));
        }
        return new ClientboundConfigSyncPayload(values);
    }

    private static void writeUtf(FriendlyByteBuf buf, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }
}
