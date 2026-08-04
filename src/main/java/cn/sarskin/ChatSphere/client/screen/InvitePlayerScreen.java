package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatDataStore;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.PlayerSkinCache;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class InvitePlayerScreen extends Screen {
    private static final int ROW_H = 28;
    private static final int CONTENT_Y = 68;

    private final Screen parent;
    private final String channelId;
    private ChatDataStore.ChannelConfig config;
    private String localPlayerUuid;
    private int scrollOffset;
    private final List<InviteRow> rows = new ArrayList<>();
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();
    private EditBox searchBox;

    public InvitePlayerScreen(Screen parent, String channelId) {
        super(Component.translatable("screen.chatsphere.invite_player.title"));
        this.parent = parent;
        this.channelId = channelId;
    }

    @Override
    protected void init() {
        refreshData();
        buildRows();
        repositionWidgets();
    }

    private void refreshData() {
        config = ChatHistoryManager.getInstance().getChannelConfig(channelId);
        localPlayerUuid = minecraft != null && minecraft.player != null
            ? minecraft.player.getUUID().toString() : null;
    }

    private void buildRows() {
        rows.clear();
        scrollWidgets.forEach(this::removeWidget);
        scrollWidgets.clear();

        if (minecraft == null || minecraft.getConnection() == null) return;

        Set<String> channelMembers = new HashSet<>(config.members);
        String selfStr = localPlayerUuid;

        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            String uuid = info.getProfile().getId().toString();
            if (uuid.equals(selfStr)) continue;
            boolean inChannel = channelMembers.contains(uuid);
            boolean invited = config.invitedPlayers.contains(uuid);
            rows.add(new InviteRow(uuid, info.getProfile().getName(), inChannel, invited));
        }
    }

    private void repositionWidgets() {
        int btnH = 16;
        int btnW = 36;
        int btnAreaX = width - btnW - 20;

        for (int i = 0; i < rows.size(); i++) {
            InviteRow r = rows.get(i);
            int y = CONTENT_Y + ROW_H + i * ROW_H - scrollOffset;

            if (r.btnInvite != null) { removeWidget(r.btnInvite); r.btnInvite = null; }

            if (!r.inChannel) {
                StyledButton btn = StyledButton.styledBuilder(
                    Component.translatable(r.invited
                        ? "screen.chatsphere.invite_player.btn_uninvite"
                        : "screen.chatsphere.invite_player.btn_invite"),
                    b -> toggleInvite(r.uuid)
                ).bounds(btnAreaX, y + (ROW_H - btnH) / 2, btnW, btnH)
                    .style(r.invited ? StyledButton.Style.CONFIRM : StyledButton.Style.DEFAULT).build();
                btn.setTooltip(Tooltip.create(Component.translatable("screen.chatsphere.invite_player.tip_invite")));
                addActionWidget(btn);
                r.btnInvite = btn;
            }
        }
    }

    private AbstractWidget addActionWidget(AbstractWidget w) {
        scrollWidgets.add(w);
        return addRenderableWidget(w);
    }

    private void toggleInvite(String uuid) {
        if (minecraft != null && minecraft.getConnection() != null && minecraft.player != null) {
            var conn = minecraft.getConnection().getConnection();
            conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new ServerboundChannelActionPayload(
                    ServerboundChannelActionPayload.Action.TOGGLE_INVITE,
                    channelId, minecraft.player.getUUID(),
                    false, uuid, "",
                    List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "")));
        }
        rebuild();
    }

    private void rebuild() {
        refreshData();
        clearWidgets();
        init();
    }

    @Override
    public void tick() {
        ChatDataStore.ChannelConfig latest = ChatHistoryManager.getInstance().getChannelConfig(channelId);
        if (latest != config) {
            config = latest;
            rebuild();
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.drawString(font, title, width / 2 - font.width(title) / 2, 14, Theme.text(), false);
        g.fill(10, CONTENT_Y - 6, width - 10, CONTENT_Y - 5, Theme.divider());

        int totalOnline = rows.size();
        long invitedCount = rows.stream().filter(r -> r.invited).count();
        Component info = Component.translatable("screen.chatsphere.invite_player.info_count",
            totalOnline, invitedCount);
        g.drawString(font, info, 30, CONTENT_Y + 4, Theme.textDim(), false);

        int y = CONTENT_Y + ROW_H;
        for (int i = 0; i < rows.size(); i++) {
            InviteRow r = rows.get(i);
            int ry = y + i * ROW_H - scrollOffset;
            if (ry < CONTENT_Y - ROW_H || ry > height) continue;

            int bg = r.inChannel
                ? 0x22222266
                : (mouseY >= ry && mouseY < ry + ROW_H && mouseX >= 10 && mouseX <= width - 10
                    ? Theme.hoverRow() : 0x00000000);
            if (bg != 0) g.fill(10, ry, width - 10, ry + ROW_H, bg);

            drawPlayerHead(g, r.uuid, 14, ry + 5, 10);

            int textColor = r.inChannel ? Theme.textDim() : Theme.textMain();
            g.drawString(font, r.name, 28, ry + 4, textColor, false);

            if (r.inChannel) {
                g.drawString(font, Component.translatable("screen.chatsphere.invite_player.status_member"),
                    width - 72, ry + 4, Theme.textDim(), false);
            }
        }
    }

    private void drawPlayerHead(GuiGraphics g, String uuidStr, int x, int y, int size) {
        try {
            PlayerFaceRenderer.draw(g, PlayerSkinCache.getSkin(UUID.fromString(uuidStr)), x, y, size);
        } catch (Exception ignored) {}
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
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
        ChatHistoryManager.getInstance().saveNow();
        if (minecraft != null) minecraft.setScreen(new ChannelConfigScreen(parent, channelId, 1));
    }

    @Override
    public boolean isPauseScreen() { return false; }


    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        BackgroundBlur.blurScreen(g, width, height);
        super.renderBackground(g, mx, my, pt);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }

    private static class InviteRow {
        final String uuid;
        final String name;
        final boolean inChannel;
        boolean invited;
        AbstractWidget btnInvite;

        InviteRow(String uuid, String name, boolean inChannel, boolean invited) {
            this.uuid = uuid;
            this.name = name;
            this.inChannel = inChannel;
            this.invited = invited;
        }
    }
}
