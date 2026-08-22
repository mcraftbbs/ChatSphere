package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.Ui;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Mute-duration picker; sends TOGGLE_MUTE with target "uuid:untilMillis" (timed) or bare "uuid" (permanent). */
public class MuteDurationScreen extends Screen {
    private static final int POPUP_WIDTH = 240;
    private static final int POPUP_HEIGHT = 232;
    private static final int ROW_H = 30;
    private static final int HEADER_H = 30;

    private final Screen parent;
    private final String channelId;
    private final String targetUuid;

    public MuteDurationScreen(Screen parent, String channelId, String targetUuid) {
        super(Component.translatable("screen.chatsphere.mute_duration.title"));
        this.parent = parent;
        this.channelId = channelId;
        this.targetUuid = targetUuid;
    }

    @Override
    protected void init() {
        int popupX = (width - POPUP_WIDTH) / 2;
        int popupY = (height - POPUP_HEIGHT) / 2;
        int y = popupY + HEADER_H + 8;

        addDuration(y, "screen.chatsphere.mute_duration.30m", 30L * 60 * 1000); y += ROW_H;
        addDuration(y, "screen.chatsphere.mute_duration.1h", 60L * 60 * 1000); y += ROW_H;
        addDuration(y, "screen.chatsphere.mute_duration.6h", 6L * 60 * 60 * 1000); y += ROW_H;
        addDuration(y, "screen.chatsphere.mute_duration.24h", 24L * 60 * 60 * 1000); y += ROW_H;
        addDuration(y, "screen.chatsphere.mute_duration.permanent", 0L); y += ROW_H;

        addRenderableWidget(StyledButton.styledBuilder(
            Component.translatable("screen.chatsphere.mute_duration.cancel"),
            btn -> onClose()
        ).bounds(popupX + 10, popupY + POPUP_HEIGHT - 28, POPUP_WIDTH - 20, 20).style(StyledButton.Style.CANCEL).build());
    }

    private void addDuration(int y, String key, long millis) {
        int popupX = (width - POPUP_WIDTH) / 2;
        int btnX = popupX + 14;
        addRenderableWidget(StyledButton.styledBuilder(
            Component.translatable(key),
            btn -> {
                // 0 = permanent (bare uuid), else uuid:untilMillis
                String spec = millis <= 0 ? targetUuid
                        : targetUuid + ":" + (System.currentTimeMillis() + millis);
                sendMute(spec);
            }
        ).bounds(btnX, y, POPUP_WIDTH - 28, 22).style(StyledButton.Style.DEFAULT).build());
    }

    private void sendMute(String spec) {
        if (minecraft != null && minecraft.player != null && minecraft.getConnection() != null) {
            minecraft.getConnection().getConnection().send(
                new net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket(ServerboundChannelActionPayload.ID,
                    new ServerboundChannelActionPayload(
                        ServerboundChannelActionPayload.Action.TOGGLE_MUTE,
                        channelId, minecraft.player.getUUID(), false, spec, "",
                        List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "", false, "").toBuf()));
        }
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        BackgroundBlur.blurScreen(g, width, height);
        super.renderBackground(g);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        int popupX = (width - POPUP_WIDTH) / 2;
        int popupY = (height - POPUP_HEIGHT) / 2;
        int radius = Theme.cardRadius();

        Ui.fillRoundedRect(g, popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, radius, Theme.popupBg());
        if (Theme.popupBorderVisible()) {
            Ui.renderRoundedOutline(g, popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, radius, Theme.popupOutline());
        }

        int iconX = popupX + 8;
        int iconY = popupY + 6;
        Ui.fillRoundedRect(g, iconX, iconY, 18, 18, 5, 0x335A1E1E);
        g.drawString(font, "!", iconX + (18 - font.width("!")) / 2, iconY + 5, 0xFFFF6666, false);
        g.drawString(font, title, iconX + 26, popupY + 10, Theme.text(), false);

        int closeX = popupX + POPUP_WIDTH - 8 - 16;
        int closeY = popupY + 7;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + 16 && mouseY >= closeY && mouseY < closeY + 16;
        if (closeHover) {
            Ui.fillRoundedRect(g, closeX, closeY, 16, 16, 4, Theme.hoverRow());
        }
        g.drawString(font, "×", closeX + (16 - font.width("×")) / 2, closeY + 4,
            closeHover ? Theme.text() : Theme.textInactive(), false);

        for (var renderable : ((cn.sarskin.ChatSphere.mixin.ScreenAccessor) this).chatsphere$getRenderables()) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int popupX = (width - POPUP_WIDTH) / 2;
            int popupY = (height - POPUP_HEIGHT) / 2;
            int closeX = popupX + POPUP_WIDTH - 8 - 16;
            int closeY = popupY + 7;
            if (mx >= closeX && mx < closeX + 16 && my >= closeY && my < closeY + 16) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { onClose(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
