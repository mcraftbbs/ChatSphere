package cn.sarskin.ChatSphere.network;

import cn.sarskin.ChatSphere.ModMain;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.nio.charset.StandardCharsets;

public record ServerboundPermissionCheckPayload(String scope) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ServerboundPermissionCheckPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ModMain.MODID, "perm_check"));

    public static final StreamCodec<ByteBuf, ServerboundPermissionCheckPayload> STREAM_CODEC =
            StreamCodec.of(ServerboundPermissionCheckPayload::write, ServerboundPermissionCheckPayload::read);

    private static void write(ByteBuf buf, ServerboundPermissionCheckPayload p) {
        byte[] bytes = p.scope.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static ServerboundPermissionCheckPayload read(ByteBuf buf) {
        byte[] bytes = PayloadLimits.readBytes(buf, PayloadLimits.MAX_UTF_BYTES);
        return new ServerboundPermissionCheckPayload(new String(bytes, StandardCharsets.UTF_8));
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.player();
            boolean allowed = false;
            if (player instanceof ServerPlayer sp) {
                allowed = sp.hasPermissions(2);
                sp.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(
                        new ClientboundPermissionResponsePayload(scope, allowed)));
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
