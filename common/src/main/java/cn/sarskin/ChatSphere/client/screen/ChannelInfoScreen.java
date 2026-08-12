package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class ChannelInfoScreen extends Screen {
    private static final int ROW_H = 28;
    private static final int CONTENT_Y = 68;

    private final Screen parent;
    private final String channelId;

    private int optLabelX, inputX, btnW;
    private String displayName, description;
    private int memberCount, adminCount, onlineCount;
    private List<FormattedCharSequence> descLines;

    public ChannelInfoScreen(Screen parent, String channelId) {
        super(Component.translatable("screen.chatsphere.channel_info.title", channelId.substring(1)));
        this.parent = parent;
        this.channelId = channelId;
    }

    @Override
    protected void init() {
        optLabelX = 30;
        btnW = Math.min(160, width - optLabelX - 80);
        inputX = width - btnW - 40;

        var config = ChatHistoryManager.getInstance().getChannelConfig(channelId);
        displayName = config.displayName;
        description = config.description;
        memberCount = config.members.size();
        adminCount = config.admins.size();
        onlineCount = (int) config.members.stream()
            .filter(u -> {
                if (minecraft == null || minecraft.getConnection() == null) return false;
                return minecraft.getConnection().getOnlinePlayers().stream()
                    .anyMatch(p -> p.getProfile().getId().toString().equals(u));
            }).count();
        descLines = description.isEmpty() ? java.util.Collections.emptyList()
            : font.split(Component.literal(description), btnW);

        int y = CONTENT_Y + 4 + ROW_H * 3 + 14;

        if (!descLines.isEmpty()) {
            y += Math.max(0, (descLines.size() - 1) * (font.lineHeight + 2));
        }

        addRenderableWidget(StyledButton.styledBuilder(
            Component.translatable("screen.chatsphere.channel_info.leave_channel"),
            btn -> minecraft.setScreen(new ConfirmDeleteChannelScreen(this, channelId, true))
        ).bounds(inputX, y, btnW, 20).style(StyledButton.Style.DANGER).tooltip(
            Component.translatable("screen.chatsphere.channel_info.tip_leave")
        ).build());

        addRenderableWidget(StyledButton.styledBuilder(
            Component.translatable("screen.chatsphere.channel_info.back"),
            btn -> onClose()
        ).bounds(width / 2 - 50, height - 32, 100, 20).style(StyledButton.Style.CONFIRM).tooltip(
            Component.translatable("screen.chatsphere.channel_info.tip_back")
        ).build());
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        BackgroundBlur.blurScreen(g, width, height);
        super.renderBackground(g);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);

        // Title
        g.drawString(font, title, width / 2 - font.width(title) / 2, 14, Theme.text(), false);

        // Separator
        g.fill(10, CONTENT_Y - 6, width - 10, CONTENT_Y - 5, Theme.divider());

        int y = CONTENT_Y + 4;

        // Display Name row
        Component dnLabel = Component.translatable("screen.chatsphere.channel_config.display_name");
        g.drawString(font, dnLabel, optLabelX, y + 6, Theme.text(), false);
        Component dnVal = displayName.isEmpty()
            ? Component.translatable("screen.chatsphere.channel_info.no_display_name")
            : Component.literal(displayName);
        g.drawString(font, dnVal, inputX, y + 6, Theme.textDim(), false);
        y += ROW_H;

        // Description row
        Component descLabel = Component.translatable("screen.chatsphere.channel_config.description");
        g.drawString(font, descLabel, optLabelX, y + 6, Theme.text(), false);
        if (descLines.isEmpty()) {
            g.drawString(font, Component.translatable("screen.chatsphere.channel_info.no_description"),
                inputX, y + 6, Theme.textDim(), false);
        } else {
            int lineH = font.lineHeight + 2;
            int dy = y + 4;
            for (FormattedCharSequence line : descLines) {
                g.drawString(font, line, inputX, dy, Theme.textDim(), false);
                dy += lineH;
            }
        }
        y += ROW_H;

        // Member count row
        Component memberInfo = Component.translatable("screen.chatsphere.channel_config.member_count", memberCount)
            .copy().append("  ")
            .append(Component.translatable("screen.chatsphere.channel_config.online_member_count", onlineCount));
        g.drawString(font, memberInfo, optLabelX, y + 6, Theme.textInactive(), false);
        y += ROW_H;

        // Admin count row
        Component adminInfo = Component.translatable("screen.chatsphere.channel_config.admin_count", adminCount);
        g.drawString(font, adminInfo, optLabelX, y + 6, Theme.textInactive(), false);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }


    @Override
    public boolean isPauseScreen() { return false; }
}
