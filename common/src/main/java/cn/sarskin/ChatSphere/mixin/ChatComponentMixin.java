package cn.sarskin.ChatSphere.mixin;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ModVoiceMessagesIntegration;
import cn.sarskin.ChatSphere.client.screen.ModChatScreen;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component message, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ChatHistoryManager history = ChatHistoryManager.getInstance();
        history.addCommandMessage(
                message,
                mc.player.getUUID(),
                Component.literal(""),
                false);
        if (!ModClientConfig.CONFIG.compatVanillaChat.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true, require = 1)
    private void onAddMessageTagged(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        String tagStr = tag != null ? tag.logTag() : "";
        if (tagStr.startsWith("VoiceMessage#")) {
            ModVoiceMessagesIntegration.handleVoiceChatMessage(message, tagStr);
            ci.cancel();
        } else {
            ChatHistoryManager history = ChatHistoryManager.getInstance();
            history.addCommandMessage(message, mc.player.getUUID(), Component.literal(""), false);
            if (!ModClientConfig.CONFIG.compatVanillaChat.get()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onChatRender(GuiGraphics guiGraphics, int mouseX, int mouseY, int partialTick, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof ModChatScreen) {
            ci.cancel();
        }
    }
}
