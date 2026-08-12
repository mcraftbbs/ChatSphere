package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModInfo;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;

public record ClientboundPermissionResponsePayload(String scope, boolean allowed) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientboundPermissionResponsePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModInfo.MODID, "perm_response"));

    public static final StreamCodec<ByteBuf, ClientboundPermissionResponsePayload> STREAM_CODEC =
            StreamCodec.of(ClientboundPermissionResponsePayload::write, ClientboundPermissionResponsePayload::read);

    private static void write(ByteBuf buf, ClientboundPermissionResponsePayload p) {
        byte[] bytes = p.scope.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
        buf.writeBoolean(p.allowed);
    }

    private static ClientboundPermissionResponsePayload read(ByteBuf buf) {
        byte[] bytes = PayloadLimits.readBytes(buf, PayloadLimits.MAX_UTF_BYTES);
        String scope = new String(bytes, StandardCharsets.UTF_8);
        boolean allowed = buf.readBoolean();
        return new ClientboundPermissionResponsePayload(scope, allowed);
    }


    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}