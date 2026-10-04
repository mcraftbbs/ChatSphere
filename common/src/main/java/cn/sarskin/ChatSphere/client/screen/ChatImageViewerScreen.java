package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.image.ChatImageCache;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Full screen image view; click or ESC goes back to the chat. */
public class ChatImageViewerScreen extends Screen {
    private static final int MARGIN = 24;
    private final Screen parent;
    private final String imageId;

    public ChatImageViewerScreen(Screen parent, String imageId) {
        super(Component.translatable("chatsphere.image.label"));
        this.parent = parent;
        this.imageId = imageId;
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        BackgroundBlur.blurScreen(g, width, height);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        ResourceLocation texture = ChatImageCache.texture(imageId);
        int[] size = texture != null ? ChatImageCache.textureSize(imageId) : null;
        int maxW = Math.max(40, this.width - MARGIN * 2);
        int maxH = Math.max(40, this.height - MARGIN * 2 - 16);
        if (size == null || size[0] <= 0 || size[1] <= 0) {
            int boxW = Math.min(maxW, 200);
            int boxH = Math.min(maxH, 120);
            int x = (this.width - boxW) / 2;
            int y = (this.height - boxH) / 2;
            Ui.fillRoundedRect(g, x, y, boxW, boxH, 6, Theme.popupBg());
            if (Theme.popupBorderVisible()) {
                Ui.renderRoundedOutline(g, x, y, boxW, boxH, 6, Theme.popupOutline());
            }
            g.drawString(font, title, x + (boxW - font.width(title)) / 2, y + boxH / 2 - 4, Theme.textDim(), false);
            return;
        }
        float scale = Math.min((float) maxW / size[0], (float) maxH / size[1]);
        int drawW = Math.max(1, Math.round(size[0] * scale));
        int drawH = Math.max(1, Math.round(size[1] * scale));
        int x = (this.width - drawW) / 2;
        int y = (this.height - drawH) / 2;
        g.blit(texture, x, y, drawW, drawH, 0f, 0f, size[0], size[1], size[0], size[1]);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        onClose();
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
