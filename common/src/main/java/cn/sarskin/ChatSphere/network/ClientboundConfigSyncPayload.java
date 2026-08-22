package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Server → client: current server config values (full snapshot on join, single key on change). */
public record ClientboundConfigSyncPayload(Map<String, String> values) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundConfigSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "config_sync_s2c"));

    public static final StreamCodec<ByteBuf, ClientboundConfigSyncPayload> STREAM_CODEC =
            StreamCodec.of(ClientboundConfigSyncPayload::write, ClientboundConfigSyncPayload::read);

    private static void write(ByteBuf buf, ClientboundConfigSyncPayload p) {
        buf.writeInt(p.values.size());
        for (Map.Entry<String, String> e : p.values.entrySet()) {
            writeUtf(buf, e.getKey());
            writeUtf(buf, e.getValue());
        }
    }

    private static ClientboundConfigSyncPayload read(ByteBuf buf) {
        int count = Math.min(Math.max(buf.readInt(), 0), 512);
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < count; i++) {
            String key = PayloadLimits.readUtf(buf);
            String value = PayloadLimits.readUtf(buf);
            values.put(key, value);
        }
        return new ClientboundConfigSyncPayload(values);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void writeUtf(ByteBuf buf, String s) {
        if (s == null) s = "";
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }
}
