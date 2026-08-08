package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.UiToggle;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public class CreateChannelScreen extends Screen {
    private static final int POPUP_WIDTH = 280;
    private static final int POPUP_HEIGHT = 250;

    private final Screen parent;
    private EditBox nameInput;
    private EditBox descInput;
    private StyledButton confirmBtn;
    private StyledButton cancelBtn;
    private UiToggle publicToggle;
    private UiToggle chatToggle;
    private EditBox defaultSubInput;
    private boolean isPublic = true;
    private boolean mainChatEnabled = true;

    public CreateChannelScreen(Screen parent) {
        super(Component.translatable("screen.chatsphere.create_channel.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int popupX = (this.width - POPUP_WIDTH) / 2;
        int popupY = (this.height - POPUP_HEIGHT) / 2;
        int fieldW = POPUP_WIDTH - 20;
        int fieldX = popupX + 10;

        this.nameInput = new EditBox(this.font, fieldX, popupY + 22, fieldW, 16,
                Component.translatable("screen.chatsphere.create_channel.input_label"));
        this.nameInput.setMaxLength(32);
        this.nameInput.setBordered(true);
        this.nameInput.setHint(Component.translatable("screen.chatsphere.create_channel.input_hint"));
        this.addWidget(this.nameInput);
        this.setInitialFocus(this.nameInput);

        this.descInput = new EditBox(this.font, fieldX, popupY + 48, fieldW, 16,
                Component.translatable("screen.chatsphere.channel_config.description"));
        this.descInput.setMaxLength(64);
        this.descInput.setBordered(true);
        this.descInput.setHint(Component.translatable("screen.chatsphere.channel_config.description_hint"));
        this.addWidget(this.descInput);

        int toggleBtnW = 60;
        this.publicToggle = this.addRenderableWidget(new UiToggle(
                popupX + POPUP_WIDTH - 10 - toggleBtnW, popupY + 76, toggleBtnW, 18, isPublic,
                v -> isPublic = v));
        this.publicToggle.setTooltip(Tooltip.create(
                Component.translatable("screen.chatsphere.create_channel.tip_toggle_public")));

        this.chatToggle = this.addRenderableWidget(new UiToggle(
                popupX + POPUP_WIDTH - 10 - toggleBtnW, popupY + 106, toggleBtnW, 18, mainChatEnabled,
                v -> {
                    mainChatEnabled = v;
                    if (this.defaultSubInput != null) {
                        this.defaultSubInput.setVisible(!mainChatEnabled);
                    }
                    if (!mainChatEnabled && this.defaultSubInput != null) {
                        this.setFocused(this.defaultSubInput);
                    }
                }));
        this.chatToggle.setTooltip(Tooltip.create(
                Component.translatable("screen.chatsphere.create_channel.tip_toggle_chat")));

        this.defaultSubInput = new EditBox(this.font, fieldX, popupY + 134, fieldW - 110, 16,
                Component.translatable("screen.chatsphere.channel_config.default_sub_hint"));
        this.defaultSubInput.setMaxLength(32);
        this.defaultSubInput.setBordered(true);
        this.defaultSubInput.setVisible(!mainChatEnabled);
        this.addWidget(this.defaultSubInput);

        int btnW = 90;
        this.confirmBtn = this.addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.create_channel.confirm"),
                btn -> confirm()
        ).bounds(popupX + 10, popupY + POPUP_HEIGHT - 30, btnW, 20).style(StyledButton.Style.CONFIRM).tooltip(
                Component.translatable("screen.chatsphere.create_channel.tip_confirm")
        ).build());

        this.cancelBtn = this.addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.create_channel.cancel"),
                btn -> cancel()
        ).bounds(popupX + POPUP_WIDTH - 10 - btnW, popupY + POPUP_HEIGHT - 30, btnW, 20).style(StyledButton.Style.CANCEL).tooltip(
                Component.translatable("screen.chatsphere.create_channel.tip_cancel")
        ).build());
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int popupX = (this.width - POPUP_WIDTH) / 2;
        int popupY = (this.height - POPUP_HEIGHT) / 2;

        guiGraphics.fill(popupX, popupY, popupX + POPUP_WIDTH, popupY + POPUP_HEIGHT, Theme.popupBg3());
        if (Theme.popupBorderVisible()) {
            guiGraphics.renderOutline(popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, Theme.popupOutline2());
        }

        String title = this.title.getString();
        guiGraphics.drawString(this.font, title,
                popupX + (POPUP_WIDTH - this.font.width(title)) / 2,
                popupY + 5, Theme.text(), false);

        Component nameLabel = Component.translatable("screen.chatsphere.create_channel.input_label");
        guiGraphics.drawString(this.font, nameLabel, popupX + 10, popupY + 22 - 10, Theme.textInactive(), false);

        Component descLabel = Component.translatable("screen.chatsphere.channel_config.description");
        guiGraphics.drawString(this.font, descLabel, popupX + 10, popupY + 48 - 10, Theme.textInactive(), false);

        Component publicLabel = Component.translatable("screen.chatsphere.channel_config.public_label");
        guiGraphics.drawString(this.font, publicLabel, popupX + 10, popupY + 78, Theme.textInactive(), false);

        Component chatLabel = Component.translatable("screen.chatsphere.channel_config.main_chat_label");
        guiGraphics.drawString(this.font, chatLabel, popupX + 10, popupY + 108, Theme.textInactive(), false);

        if (!mainChatEnabled) {
            Component subLabel = Component.translatable("screen.chatsphere.channel_config.default_sub_label");
            guiGraphics.drawString(this.font, subLabel, popupX + 10, popupY + 134 - 10, Theme.textInactive(), false);
        }

        this.nameInput.render(guiGraphics, mouseX, mouseY, partialTick);
        this.descInput.render(guiGraphics, mouseX, mouseY, partialTick);
        this.defaultSubInput.render(guiGraphics, mouseX, mouseY, partialTick);

        for (Renderable renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.nameInput.mouseClicked(mouseX, mouseY, button);
        this.descInput.mouseClicked(mouseX, mouseY, button);
        this.defaultSubInput.mouseClicked(mouseX, mouseY, button);
        if (this.confirmBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.cancelBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.publicToggle.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.chatToggle.mouseClicked(mouseX, mouseY, button)) return true;
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
        String name = this.nameInput.getValue().trim();
        if (name.contains("/")) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
            return;
        }
        if (!name.isEmpty()) {
            ChatHistoryManager history = ChatHistoryManager.getInstance();
            UUID ownerUuid = this.minecraft != null && this.minecraft.player != null
                    ? this.minecraft.player.getUUID() : null;
            String channelId = name.startsWith("#") ? name : "#" + name;
            String description = this.descInput.getValue().trim();
            if (ownerUuid != null && history.isServerConnected() && this.minecraft != null
                    && this.minecraft.getConnection() != null) {
                var conn = this.minecraft.getConnection().getConnection();
                String defaultSub = mainChatEnabled ? "" : this.defaultSubInput.getValue().trim();
                conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                        new ServerboundChannelActionPayload(
                                ServerboundChannelActionPayload.Action.CREATE,
                                channelId, ownerUuid, isPublic, description, "",
                                List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "",
                                mainChatEnabled, defaultSub)));
            } else {
                history.addChannel(name, ownerUuid);
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
