package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.Ui;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/** Confirm popup for deleting/leaving a channel. */
public class ConfirmDeleteChannelScreen extends Screen {
    private static final int POPUP_WIDTH = 240;
    private static final int POPUP_HEIGHT = 124;

    private final Screen parent;
    private final String channelId;
    private final boolean leaveMode;

    public ConfirmDeleteChannelScreen(Screen parent, String channelId) {
        this(parent, channelId, false);
    }

    public ConfirmDeleteChannelScreen(Screen parent, String channelId, boolean leaveMode) {
        super(Component.translatable(leaveMode ? "screen.chatsphere.confirm_leave.title" : "screen.chatsphere.confirm_delete.title"));
        this.parent = parent;
        this.channelId = channelId;
        this.leaveMode = leaveMode;
    }

    @Override
    protected void init() {
        int popupX = (width - POPUP_WIDTH) / 2;
        int popupY = (height - POPUP_HEIGHT) / 2;
        int btnW = 90;

        addRenderableWidget(StyledButton.styledBuilder(
            Component.translatable("screen.chatsphere.confirm_delete.confirm"),
            btn -> {
                if (leaveMode) leaveChannel();
                else deleteChannel();
                if (minecraft != null) minecraft.setScreen(parent);
            }
        ).bounds(popupX + 10, popupY + POPUP_HEIGHT - 30, btnW, 20).style(StyledButton.Style.DANGER).tooltip(
            Component.translatable("screen.chatsphere.confirm_delete.tip_confirm")
        ).build());

        addRenderableWidget(StyledButton.styledBuilder(
            Component.translatable("screen.chatsphere.confirm_delete.cancel"),
            btn -> onClose()
        ).bounds(popupX + POPUP_WIDTH - 10 - btnW, popupY + POPUP_HEIGHT - 30, btnW, 20).style(StyledButton.Style.CANCEL).tooltip(
            Component.translatable("screen.chatsphere.confirm_delete.tip_cancel")
        ).build());
    }

    private void deleteChannel() {
        if (minecraft == null || minecraft.player == null) return;
        UUID playerUuid = minecraft.player.getUUID();
        if (minecraft.getConnection() != null) {
            var conn = minecraft.getConnection().getConnection();
            conn.send(new net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket(ServerboundChannelActionPayload.ID,
                new ServerboundChannelActionPayload(
                    ServerboundChannelActionPayload.Action.REMOVE_CHANNEL,
                    channelId, playerUuid, true, "", "",
                    List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "", false, "").toBuf()));
        }
    }

    private void leaveChannel() {
        if (minecraft == null || minecraft.player == null) return;
        UUID playerUuid = minecraft.player.getUUID();
        if (minecraft.getConnection() != null) {
            var conn = minecraft.getConnection().getConnection();
            conn.send(new net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket(ServerboundChannelActionPayload.ID,
                new ServerboundChannelActionPayload(
                    ServerboundChannelActionPayload.Action.LEAVE_CHANNEL,
                    channelId, playerUuid, true, "", "",
                    List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "", false, "").toBuf()));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);

        int popupX = (width - POPUP_WIDTH) / 2;
        int popupY = (height - POPUP_HEIGHT) / 2;

        int radius = Theme.cardRadius();
        Ui.fillRoundedRect(g, popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, radius, Theme.popupBg3());
        if (Theme.popupBorderVisible()) {
            Ui.renderRoundedOutline(g, popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, radius, Theme.popupOutline2());
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

        Component warn = Component.translatable(leaveMode ? "screen.chatsphere.confirm_leave.warning" : "screen.chatsphere.confirm_delete.warning");
        g.drawString(font, warn, popupX + 10, popupY + 38, 0xFFFF6666, false);

        Component hint = Component.translatable(leaveMode ? "screen.chatsphere.confirm_leave.hint" : "screen.chatsphere.confirm_delete.hint");
        g.drawString(font, hint, popupX + 10, popupY + 54, Theme.textInactive(), false);

        for (var renderable : ((cn.sarskin.ChatSphere.mixin.ScreenAccessor) this).chatsphere$getRenderables()) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int popupX = (width - POPUP_WIDTH) / 2;
            int popupY = (height - POPUP_HEIGHT) / 2;
            int closeX = popupX + POPUP_WIDTH - 8 - 16;
            int closeY = popupY + 7;
            if (mouseX >= closeX && mouseX < closeX + 16 && mouseY >= closeY && mouseY < closeY + 16) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
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

    @Override
    public void renderBackground(GuiGraphics g) {
        BackgroundBlur.blurScreen(g, width, height);
        super.renderBackground(g);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }
}
