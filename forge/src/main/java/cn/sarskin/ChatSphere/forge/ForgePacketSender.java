package cn.sarskin.ChatSphere.forge;

import cn.sarskin.ChatSphere.platform.PacketSender;
import cn.sarskin.ChatSphere.forge.network.ForgeNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Forge send path: every payload travels inside the chatsphere:main envelope. */
public final class ForgePacketSender implements PacketSender.Provider {
    @Override
    public void toServer(ResourceLocation id, FriendlyByteBuf buf) {
        ForgeNetwork.sendToServer(id, buf);
    }

    @Override
    public void toPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buf) {
        ForgeNetwork.sendToPlayer(player, id, buf);
    }
}
