package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public class ConfirmDeleteChannelScreen extends Screen {
    private static final int POPUP_WIDTH = 240;
    private static final int POPUP_HEIGHT = 120;

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
            conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new ServerboundChannelActionPayload(
                    ServerboundChannelActionPayload.Action.REMOVE_CHANNEL,
                    channelId, playerUuid, true, "", "",
                    List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "", false, "")));
        }
    }

    private void leaveChannel() {
        if (minecraft == null || minecraft.player == null) return;
        UUID playerUuid = minecraft.player.getUUID();
        if (minecraft.getConnection() != null) {
            var conn = minecraft.getConnection().getConnection();
            conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new ServerboundChannelActionPayload(
                    ServerboundChannelActionPayload.Action.LEAVE_CHANNEL,
                    channelId, playerUuid, true, "", "",
                    List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "", false, "")));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);

        int popupX = (width - POPUP_WIDTH) / 2;
        int popupY = (height - POPUP_HEIGHT) / 2;

        g.fill(popupX, popupY, popupX + POPUP_WIDTH, popupY + POPUP_HEIGHT, Theme.popupBg3());
        if (Theme.popupBorderVisible()) {
            g.renderOutline(popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, Theme.popupOutline2());
        }

        String title = this.title.getString();
        g.drawString(font, title, popupX + (POPUP_WIDTH - font.width(title)) / 2, popupY + 8, Theme.text(), false);

        Component warn = Component.translatable(leaveMode ? "screen.chatsphere.confirm_leave.warning" : "screen.chatsphere.confirm_delete.warning");
        g.drawString(font, warn, popupX + 10, popupY + 34, 0xFFFF6666, false);

        Component hint = Component.translatable(leaveMode ? "screen.chatsphere.confirm_leave.hint" : "screen.chatsphere.confirm_delete.hint");
        g.drawString(font, hint, popupX + 10, popupY + 50, Theme.textInactive(), false);

        for (var renderable : this.renderables) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        BackgroundBlur.blurScreen(g, width, height);
        super.renderBackground(g, mx, my, pt);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }
}
