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
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class ChannelMemberScreen extends Screen {
    private static final int ROW_H = 32;
    private static final int CONTENT_Y = 68;

    private final Screen parent;
    private final String channelId;
    private ChatDataStore.ChannelConfig config;
    private String localPlayerUuid;
    private int scrollOffset;
    private final List<PlayerRow> rows = new ArrayList<>();
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();

    public ChannelMemberScreen(Screen parent, String channelId) {
        super(Component.translatable("screen.chatsphere.channel_member.title"));
        this.parent = parent;
        this.channelId = channelId;
    }

    @Override
    protected void init() {
        config = ChatHistoryManager.getInstance().getChannelConfig(channelId);
        localPlayerUuid = minecraft != null && minecraft.player != null
            ? minecraft.player.getUUID().toString() : null;
        buildRows();
        repositionWidgets();
    }

    // ---- permission group helpers ----

    private enum Group { OWNER, ADMIN, MEMBER }

    private Group getGroup(String uuid) {
        if (uuid == null || uuid.isEmpty()) return Group.MEMBER;
        if (uuid.equals(config.owner)) return Group.OWNER;
        if (config.admins.contains(uuid)) return Group.ADMIN;
        return Group.MEMBER;
    }

    // ---- row building ----

    private void buildRows() {
        rows.clear();
        scrollWidgets.forEach(this::removeWidget);
        scrollWidgets.clear();

        for (String uuid : config.members) {
            String name = resolvePlayerName(uuid);
            boolean isOwner = uuid.equals(config.owner);
            boolean isSelf = uuid.equals(localPlayerUuid);
            rows.add(new PlayerRow(uuid, name, isOwner, isSelf, getGroup(uuid)));
        }
    }

    // ---- button logic ----

    private void repositionWidgets() {
        int btnH = 16;
        int btnW = 36;
        int gap = 2;
        int btnAreaX = width - 3 * (btnW + gap) + gap - 12;

        Group viewer = getGroup(localPlayerUuid);

        for (int i = 0; i < rows.size(); i++) {
            PlayerRow r = rows.get(i);
            int y = CONTENT_Y + ROW_H + i * ROW_H - scrollOffset;
            clearRowButtons(r);

            if (r.isOwner || r.isSelf) continue;

            int fx = btnAreaX;

            if (viewer == Group.OWNER) {
                boolean muted = config.mutedPlayers.contains(r.uuid);
                r.btnMute = makeBtn(
                    muted ? "screen.chatsphere.channel_member.btn_unmute" : "screen.chatsphere.channel_member.btn_mute",
                    b -> toggleMute(r.uuid), fx, y, btnH,
                    muted ? StyledButton.Style.DANGER : StyledButton.Style.DEFAULT,
                    "screen.chatsphere.channel_member.tip_mute");
                fx += btnW + gap;

                r.btnKick = makeBtn(
                    "screen.chatsphere.channel_member.btn_kick",
                    b -> kickMember(r.uuid), fx, y, btnH,
                    StyledButton.Style.DANGER,
                    "screen.chatsphere.channel_member.tip_kick");
                fx += btnW + gap;

                boolean adm = config.admins.contains(r.uuid);
                r.btnAdmin = makeBtn(
                    adm ? "screen.chatsphere.channel_member.btn_demote" : "screen.chatsphere.channel_member.btn_admin",
                    b -> toggleAdmin(r.uuid), fx, y, btnH,
                    adm ? StyledButton.Style.TOGGLE_ON : StyledButton.Style.DEFAULT,
                    "screen.chatsphere.channel_member.tip_admin");

            } else if (viewer == Group.ADMIN && r.group == Group.MEMBER) {
                boolean muted = config.mutedPlayers.contains(r.uuid);
                r.btnMute = makeBtn(
                    muted ? "screen.chatsphere.channel_member.btn_unmute" : "screen.chatsphere.channel_member.btn_mute",
                    b -> toggleMute(r.uuid), fx, y, btnH,
                    muted ? StyledButton.Style.DANGER : StyledButton.Style.DEFAULT,
                    "screen.chatsphere.channel_member.tip_mute");
                fx += btnW + gap;

                r.btnKick = makeBtn(
                    "screen.chatsphere.channel_member.btn_kick",
                    b -> kickMember(r.uuid), fx, y, btnH,
                    StyledButton.Style.DANGER,
                    "screen.chatsphere.channel_member.tip_kick");
            }
            // viewer == Group.MEMBER → no buttons
        }
    }

    // ---- widget helpers ----

    private StyledButton makeBtn(String labelKey, Button.OnPress action, int fx, int y, int btnH,
                                  StyledButton.Style style, String tipKey) {
        StyledButton btn = StyledButton.styledBuilder(Component.translatable(labelKey), action)
            .bounds(fx, y + (ROW_H - btnH) / 2, 36, btnH).style(style).build();
        btn.setTooltip(Tooltip.create(Component.translatable(tipKey)));
        addActionWidget(btn);
        return btn;
    }

    private void clearRowButtons(PlayerRow r) {
        if (r.btnMute != null) { removeWidget(r.btnMute); r.btnMute = null; }
        if (r.btnKick != null) { removeWidget(r.btnKick); r.btnKick = null; }
        if (r.btnAdmin != null) { removeWidget(r.btnAdmin); r.btnAdmin = null; }
    }

    private AbstractWidget addActionWidget(AbstractWidget w) {
        scrollWidgets.add(w);
        return addRenderableWidget(w);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    // ---- toggle actions ----

    private void toggleMute(String uuid) { sendAction(ServerboundChannelActionPayload.Action.TOGGLE_MUTE, uuid); }
    private void toggleAdmin(String uuid) { sendAction(ServerboundChannelActionPayload.Action.TOGGLE_ADMIN, uuid); }
    private void kickMember(String uuid) { sendAction(ServerboundChannelActionPayload.Action.KICK_MEMBER, uuid); }

    private void sendAction(ServerboundChannelActionPayload.Action actionType, String targetUuid) {
        if (minecraft != null && minecraft.getConnection() != null && minecraft.player != null) {
            minecraft.getConnection().getConnection().send(
                new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                    new ServerboundChannelActionPayload(actionType, channelId, minecraft.player.getUUID(),
                        false, targetUuid, "", List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "")));
        }
        rebuild();
    }

    // ---- private whisper ----

    private void startWhisper(String targetUuid, String targetName) {
        if (minecraft == null || minecraft.player == null) return;
        UUID local = minecraft.player.getUUID();
        UUID target = UUID.fromString(targetUuid);
        String convId = local.compareTo(target) < 0 ? local + ":" + target : target + ":" + local;
        ChatHistoryManager.getInstance().addPrivateConversation(convId, Component.literal(targetName));
        minecraft.setScreen(new ModChatScreen("/msg " + targetName + " "));
    }

    // ---- tick (real-time update) ----

    @Override
    public void tick() {
        ChatDataStore.ChannelConfig latest = ChatHistoryManager.getInstance().getChannelConfig(channelId);
        if (latest != config) {
            config = latest;
            rebuild();
        }
    }

    // ---- render ----

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, width / 2 - font.width(title) / 2, 14, Theme.text(), false);
        g.fill(10, CONTENT_Y - 6, width - 10, CONTENT_Y - 5, Theme.divider());

        int online = 0;
        if (minecraft != null && minecraft.getConnection() != null) {
            for (String uuid : config.members) {
                if (minecraft.getConnection().getPlayerInfo(UUID.fromString(uuid)) != null) online++;
            }
        }
        g.drawString(font, Component.translatable("screen.chatsphere.channel_member.info_count",
            config.members.size(), online, config.admins.size(), config.mutedPlayers.size()),
            30, CONTENT_Y + 4, Theme.textDim(), false);

        int y = CONTENT_Y + ROW_H;
        for (int i = 0; i < rows.size(); i++) {
            PlayerRow r = rows.get(i);
            int ry = y + i * ROW_H - scrollOffset;
            if (ry < CONTENT_Y - ROW_H || ry > height) continue;

            boolean hovered = mouseX >= 10 && mouseX <= width - 10 && mouseY >= ry && mouseY < ry + ROW_H;
            int bg = r.isOwner ? 0x226644AA : (r.isSelf ? 0x22446688 : (hovered ? Theme.hoverRow() : 0));
            if (bg != 0) g.fill(10, ry, width - 10, ry + ROW_H, bg);

            drawPlayerHead(g, r.uuid, 14, ry + 6, 10);

            boolean isOnline = minecraft != null && minecraft.getConnection() != null
                    && minecraft.getConnection().getPlayerInfo(java.util.UUID.fromString(r.uuid)) != null;
            int dotColor = isOnline ? 0xFF44FF44 : 0xFF666666;
            g.fill(28, ry + 11, 34, ry + 17, dotColor);

            int nameColor = r.group == Group.OWNER ? 0xFFAA88FF : (r.isSelf ? 0xFFFFFF88 : Theme.textMain());
            g.drawString(font, r.name, 38, ry + 3, nameColor, false);
            String uuidText = r.uuid.substring(0, Math.min(8, r.uuid.length()));
            g.drawString(font, uuidText, 38, ry + 13, Theme.textFaint(), false);

            // group label next to uuid
            Component groupLabel = getGroupLabel(r);
            if (groupLabel != null) {
                int labelX = 38 + font.width(uuidText) + 6;
                g.drawString(font, groupLabel, labelX, ry + 13, r.group == Group.OWNER ? 0xFFAA88FF : 0xFFFFFF88, false);
            }
        }
    }

    private Component getGroupLabel(PlayerRow r) {
        if (r.group == Group.OWNER) return Component.translatable("screen.chatsphere.channel_member.role_owner");
        if (r.isSelf) return Component.translatable("screen.chatsphere.channel_member.role_self");
        if (r.group == Group.ADMIN) return Component.translatable("screen.chatsphere.channel_member.role_admin");
        return null;
    }

    // ---- draw helpers ----

    private void drawPlayerHead(GuiGraphics g, String uuidStr, int x, int y, int size) {
        try {
            PlayerFaceRenderer.draw(g, PlayerSkinCache.getSkin(UUID.fromString(uuidStr)), x, y, size);
        } catch (Exception ignored) {}
    }

    private String resolvePlayerName(String uuid) {
        if (minecraft != null && minecraft.getConnection() != null) {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                if (info.getProfile().getId().toString().equals(uuid)) {
                    config.playerNames.put(uuid, info.getProfile().getName());
                    return info.getProfile().getName();
                }
            }
        }
        if (config.playerNames.containsKey(uuid)) return config.playerNames.get(uuid);
        if (ChatHistoryManager.getInstance().getPlayerName(uuid) != null)
            return ChatHistoryManager.getInstance().getPlayerName(uuid);
        return uuid.length() >= 8 ? uuid.substring(0, 8) : uuid;
    }

    // ---- input ----

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < 10 || mx > width - 10) return false;
        scrollOffset -= (int) (sy * 20);
        scrollOffset = Math.max(0, Math.min(scrollOffset, Math.max(0, rows.size() * ROW_H - (height - CONTENT_Y - 40))));
        repositionWidgets();
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 1) {
            int y = CONTENT_Y + ROW_H;
            for (int i = 0; i < rows.size(); i++) {
                PlayerRow r = rows.get(i);
                int ry = y + i * ROW_H - scrollOffset;
                if (mx >= 10 && mx <= width - 10 && my >= ry && my < ry + ROW_H && !r.isOwner && !r.isSelf) {
                    startWhisper(r.uuid, r.name);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int k, int s, int m) {
        if (k == 256) { onClose(); return true; }
        return super.keyPressed(k, s, m);
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

    // ---- inner types ----

    private static class PlayerRow {
        final String uuid;
        final String name;
        final boolean isOwner;
        final boolean isSelf;
        final Group group;
        AbstractWidget btnMute, btnKick, btnAdmin;

        PlayerRow(String uuid, String name, boolean isOwner, boolean isSelf, Group group) {
            this.uuid = uuid;
            this.name = name;
            this.isOwner = isOwner;
            this.isSelf = isSelf;
            this.group = group;
        }
    }
}
