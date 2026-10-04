package cn.sarskin.ChatSphere.forge.network;

import cn.sarskin.ChatSphere.ModInfo;
import cn.sarskin.ChatSphere.network.ClientPayloadHandlers;
import cn.sarskin.ChatSphere.network.ServerPayloadHandlers;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * One Forge channel carrying two envelopes; the inner buffer is handed to the original
 * payload codec, so every message keeps its wire format.
 */
public final class ForgeNetwork {
    private static final String PROTOCOL = "1";
    private static SimpleChannel channel;

    private ForgeNetwork() {}

    @SubscribeEvent
    public static void register(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(ForgeNetwork::init);
    }

    private static synchronized void init() {
        if (channel != null) return;
        SimpleChannel c = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(ModInfo.MODID, "main"),
                () -> PROTOCOL,
                NetworkRegistry.ACCEPTVANILLA,
                NetworkRegistry.ACCEPTVANILLA);
        c.registerMessage(0, EnvelopeC2S.class,
                EnvelopeC2S::encode, EnvelopeC2S::decode, EnvelopeC2S::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        c.registerMessage(1, EnvelopeS2C.class,
                EnvelopeS2C::encode, EnvelopeS2C::decode, EnvelopeS2C::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        channel = c;
    }

    public static void sendToServer(ResourceLocation id, FriendlyByteBuf buf) {
        init();
        channel.sendToServer(new EnvelopeC2S(id.toString(), buf.array()));
    }

    public static void sendToPlayer(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buf) {
        init();
        channel.send(PacketDistributor.PLAYER.with(() -> player), new EnvelopeS2C(id.toString(), buf.array()));
    }

    public record EnvelopeC2S(String id, byte[] data) {
        public static void encode(EnvelopeC2S msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.id);
            buf.writeByteArray(msg.data);
        }

        public static EnvelopeC2S decode(FriendlyByteBuf buf) {
            return new EnvelopeC2S(buf.readUtf(), buf.readByteArray());
        }

        public static void handle(EnvelopeC2S msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ServerPlayer sender = context.getSender();
                if (sender == null) return;
                ResourceLocation key = ResourceLocation.tryParse(msg.id);
                if (key == null) return;
                FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(msg.data));
                dispatchServer(sender, key, buf);
            });
            context.setPacketHandled(true);
        }
    }

    public record EnvelopeS2C(String id, byte[] data) {
        public static void encode(EnvelopeS2C msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.id);
            buf.writeByteArray(msg.data);
        }

        public static EnvelopeS2C decode(FriendlyByteBuf buf) {
            return new EnvelopeS2C(buf.readUtf(), buf.readByteArray());
        }

        public static void handle(EnvelopeS2C msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            context.enqueueWork(() -> {
                ResourceLocation key = ResourceLocation.tryParse(msg.id);
                if (key == null) return;
                FriendlyByteBuf buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.wrappedBuffer(msg.data));
                dispatchClient(key, buf);
            });
            context.setPacketHandled(true);
        }
    }

    private static void dispatchServer(ServerPlayer sender, ResourceLocation key, FriendlyByteBuf buf) {
        if (key.equals(cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload.ID)) {
            ServerPayloadHandlers.channelAction(sender, cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload.read(buf));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ServerboundPermissionCheckPayload.ID)) {
            ServerPayloadHandlers.permissionCheck(sender, cn.sarskin.ChatSphere.network.ServerboundPermissionCheckPayload.read(buf));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ServerboundConfigUpdatePayload.ID)) {
            ServerPayloadHandlers.configUpdate(sender, cn.sarskin.ChatSphere.network.ServerboundConfigUpdatePayload.read(buf));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ServerboundVoicePacket.ID)) {
            ServerPayloadHandlers.voicePacket(sender, cn.sarskin.ChatSphere.network.ServerboundVoicePacket.read(buf));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ServerboundVoiceRequestPayload.ID)) {
            ServerPayloadHandlers.voiceRequest(sender, cn.sarskin.ChatSphere.network.ServerboundVoiceRequestPayload.read(buf));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ServerboundCommandMessagePayload.ID)) {
            ServerPayloadHandlers.commandMessage(sender, cn.sarskin.ChatSphere.network.ServerboundCommandMessagePayload.read(buf));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ServerboundCustomEmojiPayload.ID)) {
            ServerPayloadHandlers.customEmoji(sender, cn.sarskin.ChatSphere.network.ServerboundCustomEmojiPayload.read(buf));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ServerboundTypingPayload.ID)) {
            ServerPayloadHandlers.typing(sender, cn.sarskin.ChatSphere.network.ServerboundTypingPayload.read(buf));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ServerboundChatImagePayload.ID)) {
            ServerPayloadHandlers.chatImage(sender, cn.sarskin.ChatSphere.network.ServerboundChatImagePayload.read(buf));
        }
    }

    private static void dispatchClient(ResourceLocation key, FriendlyByteBuf buf) {
        if (key.equals(cn.sarskin.ChatSphere.network.ClientboundChannelSyncPayload.ID)) {
            ClientPayloadHandlers.safe("channelSync", () -> ClientPayloadHandlers.channelSync(cn.sarskin.ChatSphere.network.ClientboundChannelSyncPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload.ID)) {
            ClientPayloadHandlers.safe("messageSync", () -> ClientPayloadHandlers.messageSync(
                    net.minecraft.client.Minecraft.getInstance().player, cn.sarskin.ChatSphere.network.ClientboundMessageSyncPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundChatPayload.ID)) {
            ClientPayloadHandlers.safe("chat", () -> ClientPayloadHandlers.chat(
                    net.minecraft.client.Minecraft.getInstance().player, cn.sarskin.ChatSphere.network.ClientboundChatPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundPermissionResponsePayload.ID)) {
            ClientPayloadHandlers.safe("permissionResponse", () -> ClientPayloadHandlers.permissionResponse(cn.sarskin.ChatSphere.network.ClientboundPermissionResponsePayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundPublicChannelListPayload.ID)) {
            ClientPayloadHandlers.safe("publicChannelList", () -> ClientPayloadHandlers.publicChannelList(cn.sarskin.ChatSphere.network.ClientboundPublicChannelListPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundBridgeInfoPayload.ID)) {
            ClientPayloadHandlers.safe("bridgeInfo", () -> ClientPayloadHandlers.bridgeInfo(cn.sarskin.ChatSphere.network.ClientboundBridgeInfoPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundVoicePacket.ID)) {
            ClientPayloadHandlers.safe("voice", () -> ClientPayloadHandlers.voice(cn.sarskin.ChatSphere.network.ClientboundVoicePacket.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundChannelRenamedPayload.ID)) {
            ClientPayloadHandlers.safe("channelRenamed", () -> ClientPayloadHandlers.channelRenamed(cn.sarskin.ChatSphere.network.ClientboundChannelRenamedPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundConfigSyncPayload.ID)) {
            ClientPayloadHandlers.safe("configSync", () -> ClientPayloadHandlers.configSync(cn.sarskin.ChatSphere.network.ClientboundConfigSyncPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundCustomEmojiPayload.ID)) {
            ClientPayloadHandlers.safe("customEmoji", () -> ClientPayloadHandlers.customEmoji(cn.sarskin.ChatSphere.network.ClientboundCustomEmojiPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundTypingPayload.ID)) {
            ClientPayloadHandlers.safe("typing", () -> ClientPayloadHandlers.typing(cn.sarskin.ChatSphere.network.ClientboundTypingPayload.read(buf)));
        } else if (key.equals(cn.sarskin.ChatSphere.network.ClientboundChatImagePayload.ID)) {
            ClientPayloadHandlers.safe("chatImage", () -> ClientPayloadHandlers.chatImage(cn.sarskin.ChatSphere.network.ClientboundChatImagePayload.read(buf)));
        }
    }
}
