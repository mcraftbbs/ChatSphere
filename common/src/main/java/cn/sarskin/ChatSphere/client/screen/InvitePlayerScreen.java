package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatDataStore;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.PlayerSkinCache;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.Ui;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.mixin.ScreenAccessor;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Invite list: header, online player rows with skin head, member badge, invite button. */
public class InvitePlayerScreen extends Screen {
    private static final int PAD = 12;
    private static final int HEADER_H = 36;
    private static final int ROW_H = 36;
    private static final int BTN_W = 52;
    private static final int BTN_H = 20;

    private final Screen parent;
    private final String channelId;
    private ChatDataStore.ChannelConfig config;
    private String localPlayerUuid;
    private int scrollOffset;
    private int scrollMax;
    private int btnAreaX;
    private final List<InviteRow> rows = new ArrayList<>();
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();

    public InvitePlayerScreen(Screen parent, String channelId) {
        super(Component.translatable("screen.chatsphere.invite_player.title"));
        this.parent = parent;
        this.channelId = channelId;
    }

    @Override
    protected void init() {
        refreshData();
        buildRows();
        createRowButtons();
        scrollOffset = 0;
        btnAreaX = width - PAD - BTN_W - 4;
    }

    private void refreshData() {
        config = ChatHistoryManager.getInstance().getChannelConfig(channelId);
        localPlayerUuid = minecraft != null && minecraft.player != null
            ? minecraft.player.getUUID().toString() : null;
    }

    private void buildRows() {
        rows.clear();
        if (minecraft == null || minecraft.getConnection() == null) return;

        Set<String> channelMembers = new HashSet<>(config.members);
        for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
            String uuid = info.getProfile().getId().toString();
            if (uuid.equals(localPlayerUuid)) continue;
            boolean inChannel = channelMembers.contains(uuid);
            boolean invited = config.invitedPlayers.contains(uuid);
            rows.add(new InviteRow(uuid, info.getProfile().getName(), inChannel, invited));
        }
    }

    private void createRowButtons() {
        scrollWidgets.forEach(this::removeWidget);
        scrollWidgets.clear();
        for (InviteRow r : rows) {
            if (r.inChannel) continue;
            r.btnInvite = makeBtn(
                r.invited ? "screen.chatsphere.invite_player.btn_uninvite" : "screen.chatsphere.invite_player.btn_invite",
                b -> toggleInvite(r.uuid),
                r.invited ? StyledButton.Style.CONFIRM : StyledButton.Style.DEFAULT,
                "screen.chatsphere.invite_player.tip_invite");
        }
    }

    private StyledButton makeBtn(String labelKey, Button.OnPress action, StyledButton.Style style, String tipKey) {
        StyledButton btn = StyledButton.styledBuilder(Component.translatable(labelKey), action)
            .bounds(btnAreaX, 0, BTN_W, BTN_H).style(style).build();
        btn.setTooltip(Tooltip.create(Component.translatable(tipKey)));
        addActionWidget(btn);
        return btn;
    }

    private void addActionWidget(AbstractWidget w) {
        scrollWidgets.add(w);
        addRenderableWidget(w);
    }

    private void repositionButtons() {
        int y = HEADER_H - scrollOffset;
        for (InviteRow r : rows) {
            if (r.btnInvite != null) {
                r.btnInvite.setY(y + (ROW_H - BTN_H) / 2);
            }
            y += ROW_H;
        }
    }

    private void toggleInvite(String uuid) {
        if (minecraft != null && minecraft.getConnection() != null && minecraft.player != null) {
            var conn = minecraft.getConnection().getConnection();
            conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new ServerboundChannelActionPayload(
                    ServerboundChannelActionPayload.Action.TOGGLE_INVITE,
                    channelId, minecraft.player.getUUID(),
                    false, uuid, "",
                    List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "", false, "")));
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
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        BackgroundBlur.blurScreen(g, width, height);
        super.renderBackground(g, mx, my, pt);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);

        scrollMax = Math.max(0, rows.size() * ROW_H - (height - HEADER_H - 40));
        scrollOffset = Mth.clamp(scrollOffset, 0, scrollMax);
        repositionButtons();

        int iconX = PAD;
        int iconY = (HEADER_H - 18) / 2;
        Ui.fillRoundedRect(g, iconX, iconY, 18, 18, 5, Theme.iconBtnBg());
        g.drawString(font, "+", iconX + (18 - font.width("+")) / 2, iconY + 5, Theme.accent(), false);

        Component title = Component.translatable("screen.chatsphere.invite_player.title");
        g.drawString(font, title, iconX + 26, (HEADER_H - 8) / 2, Theme.text(), false);

        long invitedCount = rows.stream().filter(r -> r.invited).count();
        Component info = Component.translatable("screen.chatsphere.invite_player.info_count",
            rows.size(), invitedCount);
        g.drawString(font, info, width - PAD - 16 - 8 - font.width(info), (HEADER_H - 8) / 2, Theme.textDim(), false);

        int closeX = width - PAD - 16;
        int closeY = (HEADER_H - 16) / 2;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + 16 && mouseY >= closeY && mouseY < closeY + 16;
        if (closeHover) {
            Ui.fillRoundedRect(g, closeX, closeY, 16, 16, 4, Theme.hoverRow());
        }
        g.drawString(font, "×", closeX + (16 - font.width("×")) / 2, closeY + 4,
            closeHover ? Theme.text() : Theme.textInactive(), false);

        g.fill(PAD, HEADER_H + 2, width - PAD, HEADER_H + 3, Theme.divider());

        if (rows.isEmpty()) {
            Component empty = Component.translatable("screen.chatsphere.invite_player.empty");
            g.drawString(font, empty, width / 2 - font.width(empty) / 2, HEADER_H + 40, Theme.textDim(), false);
        } else {
            int y = HEADER_H - scrollOffset;
            for (InviteRow r : rows) {
                drawRow(g, r, y, mouseX, mouseY);
                y += ROW_H;
            }
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

        // Buttons on top of rows
        for (var renderable : ((ScreenAccessor) this).chatsphere$getRenderables()) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void drawRow(GuiGraphics g, InviteRow r, int y, int mouseX, int mouseY) {
        int rowW = width - PAD * 2;
        boolean hovered = mouseX >= PAD && mouseX < width - PAD && mouseY >= y && mouseY < y + ROW_H;

        if (r.inChannel) {
            Ui.fillRoundedRect(g, PAD, y, rowW, ROW_H, 6, Ui.withOpacity(Theme.textDim(), 0.12f));
        } else if (hovered) {
            Ui.fillRoundedRect(g, PAD, y, rowW, ROW_H, 6, Theme.hoverRow());
        }

        int avX = PAD + 4;
        int avY = y + (ROW_H - 20) / 2;
        drawPlayerHead(g, r.uuid, avX, avY, 20);

        // Status dot beside the avatar (not overlaid on the head)
        int dot = 8;
        int dotX = avX + 20 + 5;
        int dotY = y + (ROW_H - dot) / 2;
        Ui.fillRoundedRect(g, dotX, dotY, dot, dot, dot / 2, 0xFF23A55A);

        int textX = dotX + dot + 6;
        int nameY = y + (ROW_H - 8) / 2;
        g.drawString(font, r.name, textX, nameY, r.inChannel ? Theme.textDim() : Theme.textMain(), false);

        if (r.inChannel) {
            Component badge = Component.translatable("screen.chatsphere.invite_player.status_member");
            g.drawString(font, badge, width - PAD - 4 - font.width(badge), nameY, Theme.textFaint(), false);
        }
    }

    private void drawPlayerHead(GuiGraphics g, String uuidStr, int x, int y, int size) {
        try {
            PlayerFaceRenderer.draw(g, PlayerSkinCache.getSkin(UUID.fromString(uuidStr)), x, y, size);
        } catch (Exception ignored) {}
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            int closeX = width - PAD - 16;
            int closeY = (HEADER_H - 16) / 2;
            if (mx >= closeX && mx < closeX + 16 && my >= closeY && my < closeY + 16) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < PAD || mx > width - PAD || my < HEADER_H) return false;
        if (scrollMax <= 0) return false;
        scrollOffset = Mth.clamp(scrollOffset - (int) (sy * 20), 0, scrollMax);
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
