package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.Ui;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.mixin.ScreenAccessor;
import cn.sarskin.ChatSphere.network.ClientboundPublicChannelListPayload;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/** Server explorer: rows with icon, name, description, member count and join button. */
public class ExploreServersScreen extends Screen {
    private static final int PAD = 12;
    private static final int HEADER_H = 36;
    private static final int ROW_H = 42;
    private static final int BTN_W = 58;

    private final Screen parent;
    private final List<ChannelRow> rows = new ArrayList<>();
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();
    private int scrollOffset;
    private int scrollMax;
    private int btnAreaX;
    private long lastVersion = -1;
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

        btnAreaX = width - PAD - BTN_W - 4;
        createRowButtons();
        lastVersion = ChatHistoryManager.getInstance().getPublicChannelsVersion();

        addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.explore.back"),
                btn -> onClose()
        ).bounds(width - 10 - 8 - 100, height - 32, 100, 20).style(StyledButton.Style.CONFIRM).tooltip(
                Component.translatable("screen.chatsphere.explore.tip_back")
        ).build());
    }

    private void sendRequest() {
        if (minecraft == null || minecraft.getConnection() == null) return;
        minecraft.getConnection().getConnection().send(
                new net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket(ServerboundChannelActionPayload.ID,
                        new ServerboundChannelActionPayload(
                                ServerboundChannelActionPayload.Action.LIST_PUBLIC,
                                "", minecraft.player != null ? minecraft.player.getUUID() : null,
                                true, "", "", List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "", false, "").toBuf()));
    }

    private void createRowButtons() {
        scrollWidgets.forEach(this::removeWidget);
        scrollWidgets.clear();
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        for (ChannelRow r : rows) {
            boolean joined = history.getChannels().contains(r.entry.channelId());
            StyledButton joinBtn = StyledButton.styledBuilder(
                    Component.translatable(joined ? "screen.chatsphere.explore.joined" : "screen.chatsphere.explore.join"),
                    b -> doJoin(r.entry.channelId())
            ).bounds(btnAreaX, 0, BTN_W, 20)
                    .style(joined ? StyledButton.Style.TOGGLE_ON : StyledButton.Style.CONFIRM)
                    .tooltip(Component.translatable(joined
                            ? "screen.chatsphere.explore.tip_joined"
                            : "screen.chatsphere.explore.tip_join")).build();
            joinBtn.active = !joined;
            addActionWidget(joinBtn);
            r.joinBtn = joinBtn;
            r.joined = joined;
        }
    }

    private void addActionWidget(AbstractWidget w) {
        scrollWidgets.add(w);
        addRenderableWidget(w);
    }

    private void repositionButtons() {
        int y = HEADER_H + 8 - scrollOffset;
        for (ChannelRow r : rows) {
            if (r.joinBtn != null) {
                r.joinBtn.setY(y + (ROW_H - 20) / 2);
            }
            y += ROW_H;
        }
    }

    private void doJoin(String channelId) {
        if (minecraft == null || minecraft.getConnection() == null || minecraft.player == null) return;
        minecraft.getConnection().getConnection().send(
                new net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket(ServerboundChannelActionPayload.ID,
                        new ServerboundChannelActionPayload(
                                ServerboundChannelActionPayload.Action.JOIN_MEMBER,
                                channelId, minecraft.player.getUUID(),
                                true, "", "", List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "", false, "").toBuf()));
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

        ChatHistoryManager history = ChatHistoryManager.getInstance();
        List<ClientboundPublicChannelListPayload.PublicChannelEntry> data = history.getPublicChannels();

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

        // Rebuild rows only on version bump
        if (data != null) {
            long v = history.getPublicChannelsVersion();
            if (v != lastVersion) {
                lastVersion = v;
                refresh();
            }
        }

        int iconX = PAD;
        int iconY = (HEADER_H - 18) / 2;
        Ui.fillRoundedRect(g, iconX, iconY, 18, 18, 5, Theme.iconBtnBg());
        g.drawString(font, "★", iconX + (18 - font.width("★")) / 2, iconY + 5, Theme.accent(), false);

        Component title = Component.translatable("screen.chatsphere.explore.title");
        g.drawString(font, title, iconX + 26, (HEADER_H - 8) / 2, Theme.text(), false);

        if (data != null) {
            Component count = Component.translatable("screen.chatsphere.explore.count", data.size());
            g.drawString(font, count, width - PAD - 16 - 8 - font.width(count), (HEADER_H - 8) / 2, Theme.textDim(), false);
        }

        int closeX = width - PAD - 16;
        int closeY = (HEADER_H - 16) / 2;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + 16 && mouseY >= closeY && mouseY < closeY + 16;
        if (closeHover) {
            Ui.fillRoundedRect(g, closeX, closeY, 16, 16, 4, Theme.hoverRow());
        }
        g.drawString(font, "×", closeX + (16 - font.width("×")) / 2, closeY + 4,
            closeHover ? Theme.text() : Theme.textInactive(), false);

        g.fill(PAD, HEADER_H + 2, width - PAD, HEADER_H + 3, Theme.divider());

        if (data == null || dirty) {
            Component loading = Component.translatable("screen.chatsphere.explore.loading");
            g.drawString(font, loading, width / 2 - font.width(loading) / 2,
                    height / 2 - 10, Theme.textDim(), false);
        } else if (data.isEmpty()) {
            Component empty = Component.translatable("screen.chatsphere.explore.empty");
            g.drawString(font, empty, width / 2 - font.width(empty) / 2,
                    height / 2 - 10, Theme.textDim(), false);
        } else {
            scrollMax = Math.max(0, rows.size() * ROW_H - (height - HEADER_H - 8 - 40));
            scrollOffset = Mth.clamp(scrollOffset, 0, scrollMax);
            repositionButtons();

            int y = HEADER_H + 8 - scrollOffset;
            for (ChannelRow r : rows) {
                drawRow(g, r.entry, y, mouseX, mouseY);
                y += ROW_H;
            }

            if (scrollMax > 0) {
                int trackTop = HEADER_H + 8;
                int trackBot = height - 8;
                int trackH = trackBot - trackTop;
                int thumbH = Math.max(12, trackH * trackH / (trackH + scrollMax));
                int thumbY = trackTop + (trackH - thumbH) * scrollOffset / scrollMax;
                g.fill(width - 5, trackTop, width - 2, trackBot, Theme.scrollTrack());
                g.fill(width - 5, thumbY, width - 2, thumbY + thumbH, Theme.scrollThumb());
            }
        }

        for (var renderable : ((ScreenAccessor) this).chatsphere$getRenderables()) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void drawRow(GuiGraphics g, ClientboundPublicChannelListPayload.PublicChannelEntry entry,
                         int y, int mouseX, int mouseY) {
        int rowW = width - PAD * 2;
        boolean hovered = mouseX >= PAD && mouseX < width - PAD && mouseY >= y && mouseY < y + ROW_H;
        if (hovered) {
            Ui.fillRoundedRect(g, PAD, y, rowW, ROW_H, 6, Theme.hoverRow());
        }

        int iconX = PAD + 4;
        int iconY = y + (ROW_H - 28) / 2;
        Ui.fillRoundedRect(g, iconX, iconY, 28, 28, 7, Theme.iconBtnBg());
        Ui.renderRoundedOutline(g, iconX, iconY, 28, 28, 7, Theme.divider());
        g.drawString(font, "#", iconX + (28 - font.width("#")) / 2, iconY + 10, Theme.accent(), false);

        int textX = iconX + 28 + 12;
        int textW = btnAreaX - textX - 8;

        g.drawString(font, Component.literal(entry.displayName()), textX, y + 3, Theme.text(), false);

        String desc = entry.description() != null && !entry.description().isEmpty() ? entry.description() : "";
        int maxChars = Math.max(20, textW / 6);
        if (desc.length() > maxChars) desc = desc.substring(0, maxChars - 3) + "...";
        g.drawString(font, Component.literal(desc), textX, y + 14, Theme.textDim(), false);

        Component countInfo = Component.translatable(
                "screen.chatsphere.explore.member_count", entry.memberCount(), entry.onlineCount());
        int dot = 6;
        int dotX = textX + 6;
        // Dot centered on the count text
        int dotY = y + 28 - dot / 2;
        Ui.fillRoundedRect(g, dotX - dot / 2, dotY, dot, dot, dot / 2, 0xFF23A55A);
        g.drawString(font, countInfo, dotX + dot, y + 24, Theme.textInactive(), false);
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
        createRowButtons();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int closeX = width - PAD - 16;
            int closeY = (HEADER_H - 16) / 2;
            if (mouseX >= closeX && mouseX < closeX + 16 && mouseY >= closeY && mouseY < closeY + 16) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (mouseX < PAD || mouseX > width - PAD || mouseY < HEADER_H) return false;
        if (scrollMax <= 0) return false;
        scrollOffset = Mth.clamp(scrollOffset - (int) (scrollY * 20), 0, scrollMax);
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
        boolean joined;

        ChannelRow(ClientboundPublicChannelListPayload.PublicChannelEntry entry) {
            this.entry = entry;
        }
    }
}
