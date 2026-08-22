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
import java.util.List;
import java.util.UUID;

/** Member list (member management). */
public class ChannelMemberScreen extends Screen {
    private static final int PAD = 12;
    private static final int HEADER_H = 36;
    private static final int GROUP_H = 18;
    private static final int ROW_H = 32;
    private static final int BTN_H = 16;
    private static final int BTN_W = 36;
    private static final int BTN_GAP = 2;

    private final Screen parent;
    private final String channelId;
    private ChatDataStore.ChannelConfig config;
    private String localPlayerUuid;
    private int scrollOffset;
    private int scrollMax;
    private int btnAreaX;
    private int onlineMemberCount;
    private long lastOnlineVersion = -1;

    private final List<MemberRow> memberRows = new ArrayList<>();
    private final List<Object> displayRows = new ArrayList<>();
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();

    public ChannelMemberScreen(Screen parent, String channelId) {
        super(Component.translatable("screen.chatsphere.channel_member.title"));
        this.parent = parent;
        this.channelId = channelId;
    }

    private enum Group { OWNER, ADMIN, MEMBER }

    private Group groupOf(String uuid) {
        if (uuid == null || uuid.isEmpty()) return Group.MEMBER;
        if (uuid.equals(config.owner)) return Group.OWNER;
        if (config.admins.contains(uuid)) return Group.ADMIN;
        return Group.MEMBER;
    }

    private boolean isOnline(String uuid) {
        return ChatHistoryManager.getInstance().isPlayerOnline(uuid);
    }

    private static class MemberRow {
        final String uuid;
        final String name;
        final Group group;
        final boolean isSelf;
        AbstractWidget btnMute, btnKick, btnAdmin;
        MemberRow(String uuid, String name, Group group, boolean isSelf) {
            this.uuid = uuid;
            this.name = name;
            this.group = group;
            this.isSelf = isSelf;
        }
    }

    private record SectionHeader(Component label) {}

    private void buildRows() {
        memberRows.clear();
        for (String uuid : config.members) {
            boolean isSelf = uuid.equals(localPlayerUuid);
            memberRows.add(new MemberRow(uuid, resolvePlayerName(uuid), groupOf(uuid), isSelf));
        }
    }

    /** Online group first, then offline. */
    private void buildDisplayRows() {
        displayRows.clear();
        List<MemberRow> online = new ArrayList<>();
        List<MemberRow> offline = new ArrayList<>();
        for (MemberRow r : memberRows) {
            (isOnline(r.uuid) ? online : offline).add(r);
        }
        onlineMemberCount = online.size();
        if (!online.isEmpty()) {
            displayRows.add(new SectionHeader(Component.translatable(
                "screen.chatsphere.channel_info.group_online", online.size())));
            displayRows.addAll(online);
        }
        if (!offline.isEmpty()) {
            displayRows.add(new SectionHeader(Component.translatable(
                "screen.chatsphere.channel_info.group_offline", offline.size())));
            displayRows.addAll(offline);
        }
    }

    private static int rowHeight(Object row) {
        return row instanceof SectionHeader ? GROUP_H : ROW_H;
    }

    private int contentHeight() {
        int h = 0;
        for (Object row : displayRows) h += rowHeight(row);
        return h;
    }

    @Override
    protected void init() {
        config = ChatHistoryManager.getInstance().getChannelConfig(channelId);
        localPlayerUuid = minecraft != null && minecraft.player != null
            ? minecraft.player.getUUID().toString() : null;
        btnAreaX = width - 3 * (BTN_W + BTN_GAP) + BTN_GAP - PAD - 4;

        buildRows();
        buildDisplayRows();
        createRowButtons();
        scrollOffset = 0;
    }

    private void createRowButtons() {
        scrollWidgets.forEach(this::removeWidget);
        scrollWidgets.clear();

        Group viewer = groupOf(localPlayerUuid);
        for (MemberRow r : memberRows) {
            if (r.group == Group.OWNER || r.isSelf) continue;

            int fx = btnAreaX;
            if (viewer == Group.OWNER) {
                boolean muted = isPlayerMuted(r.uuid);
                r.btnMute = makeBtn(
                    muted ? "screen.chatsphere.channel_member.btn_unmute" : "screen.chatsphere.channel_member.btn_mute",
                    b -> muteAction(r), fx,
                    muted ? StyledButton.Style.DANGER : StyledButton.Style.DEFAULT,
                    "screen.chatsphere.channel_member.tip_mute");
                fx += BTN_W + BTN_GAP;

                r.btnKick = makeBtn(
                    "screen.chatsphere.channel_member.btn_kick",
                    b -> kickMember(r.uuid), fx,
                    StyledButton.Style.DANGER,
                    "screen.chatsphere.channel_member.tip_kick");
                fx += BTN_W + BTN_GAP;

                boolean adm = config.admins.contains(r.uuid);
                r.btnAdmin = makeBtn(
                    adm ? "screen.chatsphere.channel_member.btn_demote" : "screen.chatsphere.channel_member.btn_admin",
                    b -> toggleAdmin(r.uuid), fx,
                    adm ? StyledButton.Style.TOGGLE_ON : StyledButton.Style.DEFAULT,
                    "screen.chatsphere.channel_member.tip_admin");

            } else if (viewer == Group.ADMIN && r.group == Group.MEMBER) {
                boolean muted = isPlayerMuted(r.uuid);
                r.btnMute = makeBtn(
                    muted ? "screen.chatsphere.channel_member.btn_unmute" : "screen.chatsphere.channel_member.btn_mute",
                    b -> muteAction(r), fx,
                    muted ? StyledButton.Style.DANGER : StyledButton.Style.DEFAULT,
                    "screen.chatsphere.channel_member.tip_mute");
                fx += BTN_W + BTN_GAP;

                r.btnKick = makeBtn(
                    "screen.chatsphere.channel_member.btn_kick",
                    b -> kickMember(r.uuid), fx,
                    StyledButton.Style.DANGER,
                    "screen.chatsphere.channel_member.tip_kick");
            }
            // viewer == Group.MEMBER → no buttons
        }
    }

    private StyledButton makeBtn(String labelKey, Button.OnPress action, int fx,
                                  StyledButton.Style style, String tipKey) {
        StyledButton btn = StyledButton.styledBuilder(Component.translatable(labelKey), action)
            .bounds(fx, 0, BTN_W, BTN_H).style(style).build();
        btn.setTooltip(Tooltip.create(Component.translatable(tipKey)));
        addActionWidget(btn);
        return btn;
    }

    private void addActionWidget(AbstractWidget w) {
        scrollWidgets.add(w);
        addRenderableWidget(w);
    }

    /** Follows every display row's current Y (scroll + online regrouping). */
    private void repositionRowButtons() {
        int y = HEADER_H - scrollOffset;
        for (Object row : displayRows) {
            if (row instanceof MemberRow mr) {
                int btnY = y + (ROW_H - BTN_H) / 2;
                if (mr.btnMute != null) mr.btnMute.setY(btnY);
                if (mr.btnKick != null) mr.btnKick.setY(btnY);
                if (mr.btnAdmin != null) mr.btnAdmin.setY(btnY);
            }
            y += rowHeight(row);
        }
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void toggleMute(String uuid) { sendAction(ServerboundChannelActionPayload.Action.TOGGLE_MUTE, uuid); }

    /** Mute entries are "uuid" (permanent) or "uuid:untilMillis" (timed). */
    private boolean isPlayerMuted(String uuid) {
        long now = System.currentTimeMillis();
        for (String e : config.mutedPlayers) {
            String uuidPart = e;
            long until = Long.MAX_VALUE;
            int colon = e.indexOf(':');
            if (colon > 0) {
                uuidPart = e.substring(0, colon);
                try {
                    until = Long.parseLong(e.substring(colon + 1));
                } catch (NumberFormatException ignored) {
                    until = 0;
                }
            }
            if (uuidPart.equals(uuid) && until > now) return true;
        }
        return false;
    }

    /** Already muted → unmute; otherwise open the mute-duration picker. */
    private void muteAction(MemberRow r) {
        if (isPlayerMuted(r.uuid)) {
            toggleMute(r.uuid);
        } else if (minecraft != null) {
            minecraft.setScreen(new MuteDurationScreen(this, channelId, r.uuid));
        }
    }
    private void toggleAdmin(String uuid) { sendAction(ServerboundChannelActionPayload.Action.TOGGLE_ADMIN, uuid); }
    private void kickMember(String uuid) { sendAction(ServerboundChannelActionPayload.Action.KICK_MEMBER, uuid); }

    private void sendAction(ServerboundChannelActionPayload.Action actionType, String targetUuid) {
        if (minecraft != null && minecraft.getConnection() != null && minecraft.player != null) {
            minecraft.getConnection().getConnection().send(
                new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                    new ServerboundChannelActionPayload(actionType, channelId, minecraft.player.getUUID(),
                        false, targetUuid, "", List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "", false, "")));
        }
        rebuild();
    }

    private void startWhisper(String targetUuid, String targetName) {
        if (minecraft == null || minecraft.player == null) return;
        UUID local = minecraft.player.getUUID();
        UUID target = UUID.fromString(targetUuid);
        String convId = local.compareTo(target) < 0 ? local + ":" + target : target + ":" + local;
        ChatHistoryManager.getInstance().addPrivateConversation(convId, Component.literal(targetName));
        minecraft.setScreen(new ModChatScreen("/msg " + targetName + " "));
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

        // Rebuild grouping only when cached online state changed
        long onlineVer = ChatHistoryManager.getInstance().getOnlineVersion();
        if (onlineVer != lastOnlineVersion) {
            lastOnlineVersion = onlineVer;
            buildDisplayRows();
        }
        scrollMax = Math.max(0, contentHeight() - (height - HEADER_H - 8));
        scrollOffset = Mth.clamp(scrollOffset, 0, scrollMax);
        repositionRowButtons();

        drawHeader(g, mouseX, mouseY);

        g.fill(PAD, HEADER_H + 2, width - PAD, HEADER_H + 3, Theme.divider());

        int y = HEADER_H - scrollOffset;
        for (Object row : displayRows) {
            if (row instanceof SectionHeader sh) {
                g.drawString(font, sh.label(), PAD + 2, y + 3, Theme.textDim(), false);
            } else if (row instanceof MemberRow mr) {
                drawMemberRow(g, mr, y, mouseX, mouseY);
            }
            y += rowHeight(row);
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

    private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
        int iconX = PAD;
        int iconY = (HEADER_H - 18) / 2;
        Ui.fillRoundedRect(g, iconX, iconY, 18, 18, 5, Theme.iconBtnBg());
        g.drawString(font, "#", iconX + (18 - font.width("#")) / 2, iconY + 5, Theme.accent(), false);

        Component title = Component.translatable("screen.chatsphere.channel_member.title");
        g.drawString(font, title, iconX + 26, (HEADER_H - 8) / 2, Theme.text(), false);

        Component info = Component.translatable("screen.chatsphere.channel_member.info_count",
            config.members.size(), onlineMemberCount, config.admins.size(), config.mutedPlayers.size());
        g.drawString(font, info, width - PAD - 16 - 8 - font.width(info), (HEADER_H - 8) / 2, Theme.textDim(), false);

        int closeX = width - PAD - 16;
        int closeY = (HEADER_H - 16) / 2;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + 16 && mouseY >= closeY && mouseY < closeY + 16;
        if (closeHover) {
            Ui.fillRoundedRect(g, closeX, closeY, 16, 16, 4, Theme.hoverRow());
        }
        g.drawString(font, "×", closeX + (16 - font.width("×")) / 2, closeY + 4,
            closeHover ? Theme.text() : Theme.textInactive(), false);
    }

    private void drawMemberRow(GuiGraphics g, MemberRow r, int y, int mouseX, int mouseY) {
        int rowW = width - PAD * 2;
        boolean hovered = mouseX >= PAD && mouseX < width - PAD && mouseY >= y && mouseY < y + ROW_H;
        if (hovered) {
            Ui.fillRoundedRect(g, PAD, y, rowW, ROW_H, 6, Theme.hoverRow());
        }

        int avX = PAD + 4;
        int avY = y + (ROW_H - 20) / 2;
        drawPlayerHead(g, r.uuid, avX, avY, 20);

        // Status dot beside the avatar
        boolean online = isOnline(r.uuid);
        int dot = 8;
        int dotX = avX + 20 + 5;
        int dotY = y + (ROW_H - dot) / 2;
        Ui.fillRoundedRect(g, dotX, dotY, dot, dot, dot / 2,
            online ? 0xFF23A55A : 0xFF666666);

        int nameColor = switch (r.group) {
            case OWNER -> 0xFFAA88FF;
            case ADMIN -> 0xFFFFFF88;
            case MEMBER -> Theme.textMain();
        };
        int nameX = dotX + dot + 6;
        int nameY = y + (ROW_H - 8) / 2;
        g.drawString(font, r.name, nameX, nameY, nameColor, false);

        Component badge = r.isSelf
            ? Component.translatable("screen.chatsphere.channel_member.role_self")
            : switch (r.group) {
                case OWNER -> Component.translatable("screen.chatsphere.channel_member.role_owner");
                case ADMIN -> Component.translatable("screen.chatsphere.channel_member.role_admin");
                case MEMBER -> null;
            };
        if (badge != null) {
            int badgeX = nameX + font.width(r.name) + 8;
            if (badgeX + font.width(badge) < btnAreaX - 4) {
                g.drawString(font, badge, badgeX, nameY, Theme.textFaint(), false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int closeX = width - PAD - 16;
        int closeY = (HEADER_H - 16) / 2;
        if (button == 0 && mx >= closeX && mx < closeX + 16 && my >= closeY && my < closeY + 16) {
            onClose();
            return true;
        }

        if (button == 1) {
            MemberRow r = memberRowAt(mx, my);
            if (r != null && !r.isSelf) {
                startWhisper(r.uuid, r.name);
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    private MemberRow memberRowAt(double mx, double my) {
        if (mx < PAD || mx > width - PAD || my < HEADER_H || my > height) return null;
        double y = HEADER_H - scrollOffset;
        for (Object row : displayRows) {
            if (my >= y && my < y + rowHeight(row)) {
                return row instanceof MemberRow mr ? mr : null;
            }
            y += rowHeight(row);
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < PAD || mx > width - PAD || my < HEADER_H) return false;
        if (scrollMax <= 0) return false;
        scrollOffset = Mth.clamp(scrollOffset - (int) (sy * 20), 0, scrollMax);
        return true;
    }

    @Override
    public void onClose() {
        ChatHistoryManager.getInstance().saveNow();
        if (minecraft != null) minecraft.setScreen(new ChannelConfigScreen(parent, channelId, 1));
    }

    @Override
    public boolean isPauseScreen() { return false; }

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
        String cached = ChatHistoryManager.getInstance().getPlayerName(uuid);
        if (cached != null) return cached;
        return uuid.length() >= 8 ? uuid.substring(0, 8) : uuid;
    }
}
