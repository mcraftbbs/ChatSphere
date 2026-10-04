package cn.sarskin.ChatSphere.fabric.network;

import cn.sarskin.ChatSphere.platform.PacketSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Fabric send path: the packets the common code used to build inline. */
public final class FabricPacketSender implements PacketSender.Provider {
    private FabricPacketSender() {}

    public static void init() {
        PacketSender.setProvider(new FabricPacketSender());
    }

    @Override
    public void toServer(ResourceLocation id, FriendlyByteBuf buf) {
        ClientPacketListener conn = Minecraft.getInstance().getConnection();
        if (conn == null) return;
        conn.send(new ServerboundCustomPayloadPacket(id, buf));
    }

    @Override
    public void toPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buf) {
        player.connection.send(new ClientboundCustomPayloadPacket(id, buf));
    }
}
