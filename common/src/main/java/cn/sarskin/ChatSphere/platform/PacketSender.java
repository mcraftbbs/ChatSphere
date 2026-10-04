package cn.sarskin.ChatSphere.platform;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.function.Predicate;

/** Loader-agnostic payload sending; each platform installs a provider at init. */
public final class PacketSender {
    public interface Provider {
        void toServer(ResourceLocation id, FriendlyByteBuf buf);

        void toPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buf);
    }

    private static volatile Provider provider = new Provider() {
        @Override
        public void toServer(ResourceLocation id, FriendlyByteBuf buf) {
        }

        @Override
        public void toPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buf) {
        }
    };

    private PacketSender() {}

    public static void setProvider(Provider p) {
        if (p != null) provider = p;
    }

    public static void toServer(ResourceLocation id, FriendlyByteBuf buf) {
        provider.toServer(id, buf);
    }

    public static void toPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buf) {
        if (player == null) return;
        provider.toPlayer(player, id, buf);
    }

    public static void toAll(Collection<ServerPlayer> players, ResourceLocation id, FriendlyByteBuf buf) {
        toAll(players, id, buf, null);
    }

    public static void toAll(Collection<ServerPlayer> players, ResourceLocation id, FriendlyByteBuf buf, Predicate<ServerPlayer> filter) {
        if (players == null) return;
        for (ServerPlayer player : players) {
            if (filter == null || filter.test(player)) toPlayer(player, id, buf);
        }
    }
}
