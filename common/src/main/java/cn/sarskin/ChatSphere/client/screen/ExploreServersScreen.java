package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.network.ClientboundPublicChannelListPayload;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public class ExploreServersScreen extends Screen {
    private static final int ROW_H = 42;
    private static final int CONTENT_Y = 68;

    private final Screen parent;
    private final List<ChannelRow> rows = new ArrayList<>();
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();
    private int scrollOffset;
    private boolean requestInFlight;
    private long lastRequestMs;

    private static final long REQUEST_RETRY_MS = 3000;

    public ExploreServersScreen(Screen parent) {
        super(Component.translatable("screen.chatsphere.explore.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        scrollWidgets.forEach(this::removeWidget);
        scrollWidgets.clear();

        requestInFlight = false;
        lastRequestMs = 0;
        sendRequest();

        List<ClientboundPublicChannelListPayload.PublicChannelEntry> data =
                ChatHistoryManager.getInstance().getPublicChannels();
        if (data != null) {
            for (var entry : data) {
                rows.add(new ChannelRow(entry));
            }
        }

        int btnY = Math.max(CONTENT_Y + 10, height - 32);
        addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.explore.back"),
                btn -> onClose()
        ).bounds(width / 2 - 50, btnY, 100, 20).style(StyledButton.Style.CONFIRM).tooltip(
                Component.translatable("screen.chatsphere.explore.tip_back")
        ).build());

        repositionWidgets();
    }

    private void sendRequest() {
        if (minecraft == null || minecraft.getConnection() == null) return;
        minecraft.getConnection().getConnection().send(
                new net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket(ServerboundChannelActionPayload.ID, new ServerboundChannelActionPayload(
                                ServerboundChannelActionPayload.Action.LIST_PUBLIC,
                                "", minecraft.player != null ? minecraft.player.getUUID() : null,
                                true, "", "", List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "", false, "").toBuf()));;
    }

    private void repositionWidgets() {
        scrollWidgets.forEach(this::removeWidget);
        scrollWidgets.clear();

        int btnH = 18;
        int btnW = 50;
        int btnAreaX = width - btnW - 20;

        for (int i = 0; i < rows.size(); i++) {
            ChannelRow r = rows.get(i);
            int y = CONTENT_Y + ROW_H + i * ROW_H - scrollOffset;

            StyledButton joinBtn = StyledButton.styledBuilder(
                    Component.translatable("screen.chatsphere.explore.join"),
                    b -> doJoin(r.entry.channelId())
            ).bounds(btnAreaX, y + (ROW_H - btnH) / 2, btnW, btnH)
                    .style(StyledButton.Style.CONFIRM)
                    .tooltip(Component.translatable("screen.chatsphere.explore.tip_join")).build();
            addActionWidget(joinBtn);
            r.joinBtn = joinBtn;
        }
    }

    private AbstractWidget addActionWidget(AbstractWidget w) {
        scrollWidgets.add(w);
        return addRenderableWidget(w);
    }

    private void doJoin(String channelId) {
        if (minecraft == null || minecraft.getConnection() == null || minecraft.player == null) return;
        minecraft.getConnection().getConnection().send(
                new net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket(ServerboundChannelActionPayload.ID, new ServerboundChannelActionPayload(
                                ServerboundChannelActionPayload.Action.JOIN_MEMBER,
                                channelId, minecraft.player.getUUID(),
                                true, "", "", List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "", false, "").toBuf()));;
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

        g.drawString(font, title, width / 2 - font.width(title) / 2, 14, Theme.text(), false);
        g.fill(10, CONTENT_Y - 6, width - 10, CONTENT_Y - 5, Theme.divider());

        ChatHistoryManager history = ChatHistoryManager.getInstance();
        List<ClientboundPublicChannelListPayload.PublicChannelEntry> data =
                history.getPublicChannels();

        boolean dirty = history.isPublicChannelsDirty();
        if (dirty) {
            long now = System.currentTimeMillis();
            if (!requestInFlight || now - lastRequestMs > REQUEST_RETRY_MS) {
                requestInFlight = true;
                lastRequestMs = now;
                sendRequest();
            }
        } else {
            requestInFlight = false;
        }

        if (data == null || dirty) {
            Component loading = Component.translatable("screen.chatsphere.explore.loading");
            g.drawString(font, loading, width / 2 - font.width(loading) / 2,
                    height / 2 - 10, Theme.textDim(), false);
            return;
        }

        if (data.isEmpty()) {
            Component empty = Component.translatable("screen.chatsphere.explore.empty");
            g.drawString(font, empty, width / 2 - font.width(empty) / 2,
                    height / 2 - 10, Theme.textDim(), false);
            return;
        }

        // Refresh rows if data changed (e.g., initial load)
        if (rows.isEmpty()) {
            refresh();
        }

        Component info = Component.translatable("screen.chatsphere.explore.count", data.size());
        g.drawString(font, info, 30, CONTENT_Y + 4, Theme.textDim(), false);

        int y = CONTENT_Y + ROW_H;
        for (int i = 0; i < rows.size(); i++) {
            ChannelRow r = rows.get(i);
            int ry = y + i * ROW_H - scrollOffset;
            if (ry < CONTENT_Y - ROW_H || ry > height) continue;

            boolean hover = mouseY >= ry && mouseY < ry + ROW_H && mouseX >= 10 && mouseX <= width - 10;
            if (hover) g.fill(10, ry, width - 10, ry + ROW_H, Theme.hoverRow());

            var entry = r.entry;

            // Channel name
            g.drawString(font, Component.literal(entry.displayName()),
                    14, ry + 3, Theme.text(), false);

            // Description (truncated to ~50 chars)
            String desc = entry.description() != null && !entry.description().isEmpty()
                    ? entry.description() : "";
            if (desc.length() > 50) desc = desc.substring(0, 47) + "...";
            g.drawString(font, Component.literal(desc),
                    14, ry + 14, Theme.textDim(), false);

            // Member / online count
            Component countInfo = Component.translatable(
                    "screen.chatsphere.explore.member_count", entry.memberCount(), entry.onlineCount());
            g.drawString(font, countInfo, 14, ry + 25, Theme.textInactive(), false);

            // Join button is positioned by repositionWidgets
        }
    }

    private void refresh() {
        rows.clear();
        scrollWidgets.forEach(this::removeWidget);
        scrollWidgets.clear();
        var data = ChatHistoryManager.getInstance().getPublicChannels();
        if (data != null) {
            for (var entry : data) {
                rows.add(new ChannelRow(entry));
            }
        }
        repositionWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX < 10 || mouseX > width - 10) return false;
        scrollOffset -= (int) (scrollY * 20);
        int maxScroll = Math.max(0, rows.size() * ROW_H - (height - CONTENT_Y - ROW_H - 30));
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        repositionWidgets();
        return true;
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

    private static class ChannelRow {
        final ClientboundPublicChannelListPayload.PublicChannelEntry entry;
        AbstractWidget joinBtn;

        ChannelRow(ClientboundPublicChannelListPayload.PublicChannelEntry entry) {
            this.entry = entry;
        }
    }
}
