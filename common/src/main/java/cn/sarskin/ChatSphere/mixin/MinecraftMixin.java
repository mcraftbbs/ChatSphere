package cn.sarskin.ChatSphere.mixin;

import cn.sarskin.ChatSphere.client.screen.ModChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.InBedChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void chatsphere$replaceInBedChatScreen(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof InBedChatScreen) {
            mc.setScreen(new ModChatScreen(""));
        }
    }
}
