package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class JoinChannelScreen extends Screen {
    private static final int POPUP_WIDTH = 200;
    private static final int POPUP_HEIGHT = 80;

    private final Screen parent;
    private EditBox codeInput;
    private StyledButton confirmBtn;
    private StyledButton cancelBtn;

    public JoinChannelScreen(Screen parent) {
        super(Component.translatable("screen.chatsphere.join_channel.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int popupX = (this.width - POPUP_WIDTH) / 2;
        int popupY = (this.height - POPUP_HEIGHT) / 2;

        this.codeInput = new EditBox(this.font, popupX + 10, popupY + 20, POPUP_WIDTH - 20, 16,
                Component.translatable("screen.chatsphere.join_channel.input_label"));
        this.codeInput.setMaxLength(16);
        this.codeInput.setBordered(true);
        this.codeInput.setHint(Component.translatable("screen.chatsphere.join_channel.input_hint"));
        this.addWidget(this.codeInput);
        this.setInitialFocus(this.codeInput);

        this.confirmBtn = this.addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.join_channel.confirm"),
                btn -> confirm()
        ).bounds(popupX + 10, popupY + 50, 80, 20).style(StyledButton.Style.CONFIRM).tooltip(
                Component.translatable("screen.chatsphere.join_channel.tip_confirm")
        ).build());

        this.cancelBtn = this.addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.join_channel.cancel"),
                btn -> cancel()
        ).bounds(popupX + POPUP_WIDTH - 10 - 80, popupY + 50, 80, 20).style(StyledButton.Style.CANCEL).tooltip(
                Component.translatable("screen.chatsphere.join_channel.tip_cancel")
        ).build());
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        super.renderBackground(g);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics);

        int popupX = (this.width - POPUP_WIDTH) / 2;
        int popupY = (this.height - POPUP_HEIGHT) / 2;

        guiGraphics.fill(popupX, popupY, popupX + POPUP_WIDTH, popupY + POPUP_HEIGHT, Theme.popupBg2());
        if (Theme.popupBorderVisible()) {
            guiGraphics.renderOutline(popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, Theme.popupOutline());
        }

        String title = this.title.getString();
        guiGraphics.drawString(this.font, title,
                popupX + (POPUP_WIDTH - this.font.width(title)) / 2,
                popupY + 5, Theme.text(), false);

        this.codeInput.render(guiGraphics, mouseX, mouseY, partialTick);

        for (Renderable renderable : ((cn.sarskin.ChatSphere.mixin.ScreenAccessor) this).chatsphere$getRenderables()) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.codeInput.mouseClicked(mouseX, mouseY, button);
        if (this.confirmBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.cancelBtn.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            cancel();
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm() {
        String code = this.codeInput.getValue().trim().toUpperCase();
        if (!code.isEmpty()) {
            ChatHistoryManager history = ChatHistoryManager.getInstance();
            if (this.minecraft != null && this.minecraft.player != null
                    && this.minecraft.getConnection() != null && history.isServerConnected()) {
                var conn = this.minecraft.getConnection().getConnection();
                conn.send(new net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket(ServerboundChannelActionPayload.ID, new ServerboundChannelActionPayload(
                                ServerboundChannelActionPayload.Action.JOIN_BY_CODE,
                                "", this.minecraft.player.getUUID(),
                                true, "", "", List.<String>of(), List.<String>of(), List.<String>of(), code, true, "", "", "", false, "").toBuf()));;
            }
        }
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void cancel() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
