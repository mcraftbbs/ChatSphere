package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatDataStore;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.PlayerSkinCache;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.Ui;
import cn.sarskin.ChatSphere.client.widget.CopyToast;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.mixin.ScreenAccessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Channel detail popout card. */
public class ChannelInfoScreen extends Screen {
    private static final int PAD = 12;
    private static final int HEADER_H = 36;
    private static final int FOOTER_H = 44;
    private static final int GROUP_H = 18;
    private static final int MEMBER_H = 34;
    private static final int CARD_W = 380;

    private final Screen parent;
    private final String channelId;

    private ChatDataStore.ChannelConfig config;
    private String localPlayerUuid;
    private boolean isSub;
    private boolean canEdit;
    private boolean descExpanded;

    private int cardX, cardY, cardW, cardH;
    private int contentTop, contentBottom;
    private int scrollOffset;
    private int scrollMax;

    private final List<MemberRow> memberRows = new ArrayList<>();
    private final List<Object> displayRows = new ArrayList<>();
    private List<FormattedCharSequence> descLines;
    private final CopyToast copyToast = new CopyToast();

    private int descBottom;
    private boolean hasOwner, inviteShown;
    private int onlineMemberCount;
    private long lastOnlineVersion = -1;

    public ChannelInfoScreen(Screen parent, String channelId) {
        super(Component.translatable("screen.chatsphere.channel_info.title", channelId.substring(1)));
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
        MemberRow(String uuid, String name, Group group) {
            this.uuid = uuid;
            this.name = name;
            this.group = group;
        }
    }

    private record SectionHeader(Component label) {}

    private void buildRows() {
        memberRows.clear();
        for (String uuid : config.members) {
            memberRows.add(new MemberRow(uuid, resolvePlayerName(uuid), groupOf(uuid)));
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
        return row instanceof SectionHeader ? GROUP_H : MEMBER_H;
    }

    private int rowsHeight() {
        int h = 0;
        for (Object row : displayRows) h += rowHeight(row);
        return h;
    }

    @Override
    protected void init() {
        config = ChatHistoryManager.getInstance().getChannelConfig(channelId);
        localPlayerUuid = minecraft != null && minecraft.player != null
            ? minecraft.player.getUUID().toString() : null;
        isSub = ChatHistoryManager.isSubChannel(channelId);
        canEdit = minecraft != null && minecraft.player != null
            && (ChatHistoryManager.getInstance().isOwner(channelId, minecraft.player.getUUID())
                || ChatHistoryManager.getInstance().isAdmin(channelId, minecraft.player.getUUID()));

        hasOwner = !config.owner.isEmpty();
        inviteShown = !isSub && !config.inviteCode.isEmpty();

        cardW = Math.min(CARD_W, width - 40);
        int innerW = cardW - PAD * 2;
        descLines = config.description.isEmpty() ? List.of()
            : font.split(Component.literal(config.description), innerW);

        buildRows();
        buildDisplayRows();

        int innerH = contentHeight();
        cardH = Mth.clamp(HEADER_H + PAD + innerH + PAD + FOOTER_H, Math.min(300, height - 56), height - 56);
        cardX = (width - cardW) / 2;
        cardY = (height - cardH) / 2;
        contentTop = cardY + HEADER_H;
        contentBottom = cardY + cardH - FOOTER_H;
        scrollOffset = 0;

        int footerY = cardY + cardH - 40;

        addRenderableWidget(StyledButton.styledBuilder(
            Component.translatable("screen.chatsphere.channel_info.back"),
            btn -> onClose()
        ).bounds(cardX + PAD, footerY, 90, 20).style(StyledButton.Style.CONFIRM).tooltip(
            Component.translatable("screen.chatsphere.channel_info.tip_back")
        ).build());

        if (canEdit) {
            addRenderableWidget(StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.channel_info.edit_channel"),
                btn -> minecraft.setScreen(new ChannelConfigScreen(this, channelId))
            ).bounds(cardX + PAD + 100, footerY, 90, 20).style(StyledButton.Style.DEFAULT).tooltip(
                Component.translatable("screen.chatsphere.channel_info.tip_edit_channel")
            ).build());
        }

        addRenderableWidget(StyledButton.styledBuilder(
            Component.translatable("screen.chatsphere.channel_info.leave_channel"),
            btn -> minecraft.setScreen(new ConfirmDeleteChannelScreen(this, channelId, true))
        ).bounds(cardX + cardW - PAD - 90, footerY, 90, 20).style(StyledButton.Style.DANGER).tooltip(
            Component.translatable("screen.chatsphere.channel_info.tip_leave")
        ).build());
    }

    /** Vertical extent of all scrollable content (About + Members sections). */
    private int contentHeight() {
        int h = 4 + font.lineHeight + 4 + descHeight();
        if (hasOwner) h += 8 + 18;
        if (!isSub) h += 6 + font.lineHeight;
        if (inviteShown) h += 8 + 18;
        h += 14;
        h += 4 + font.lineHeight + 4;
        h += displayRows.isEmpty() ? font.lineHeight : rowsHeight();
        return h;
    }

    private boolean descCollapsible() {
        return descLines.size() > 3;
    }

    private List<FormattedCharSequence> descVisibleLines() {
        if (!descCollapsible()) return descLines;
        return descExpanded ? descLines : descLines.subList(0, 3);
    }

    private int descHeight() {
        int lines = descVisibleLines().size();
        int h = Math.max(font.lineHeight, lines * (font.lineHeight + 2));
        if (descCollapsible()) h += font.lineHeight + 2; // expand/collapse toggle row
        return h;
    }

    @Override
    public void tick() {
        copyToast.tick();
        ChatDataStore.ChannelConfig latest = ChatHistoryManager.getInstance().getChannelConfig(channelId);
        if (latest != config) {
            config = latest;
            clearWidgets();
            init();
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

        scrollMax = Math.max(0, contentHeight() - (contentBottom - contentTop));
        scrollOffset = Mth.clamp(scrollOffset, 0, scrollMax);

        int radius = Theme.cardRadius();
        Ui.fillRoundedRect(g, cardX, cardY, cardW, cardH, radius, Theme.popupBg());
        if (Theme.popupBorderVisible()) {
            Ui.renderRoundedOutline(g, cardX, cardY, cardW, cardH, radius, Theme.popupOutline());
        }

        drawHeader(g, mouseX, mouseY);

        // Content clipped to the card
        Ui.withRoundedClip(g, cardX, contentTop, cardW, contentBottom - contentTop, radius, () -> {
            drawContent(g, mouseX, mouseY);
        });

        if (scrollMax > 0) {
            int trackH = contentBottom - contentTop;
            int thumbH = Math.max(12, trackH * trackH / (trackH + scrollMax));
            int thumbY = contentTop + (trackH - thumbH) * scrollOffset / scrollMax;
            g.fill(cardX + cardW - 5, contentTop, cardX + cardW - 2, contentBottom, Theme.scrollTrack());
            g.fill(cardX + cardW - 5, thumbY, cardX + cardW - 2, thumbY + thumbH, Theme.scrollThumb());
        }

        // Footer widgets on top of the card
        for (var renderable : ((ScreenAccessor) this).chatsphere$getRenderables()) {
            renderable.render(g, mouseX, mouseY, partialTick);
        }

        copyToast.render(g, 0, width);
    }

    private void drawHeader(GuiGraphics g, int mouseX, int mouseY) {
        int iconX = cardX + PAD;
        int iconY = cardY + (HEADER_H - 18) / 2;
        Ui.fillRoundedRect(g, iconX, iconY, 18, 18, 5, Theme.iconBtnBg());
        g.drawString(font, "#", iconX + (18 - font.width("#")) / 2, iconY + 5, Theme.accent(), false);

        String headerName = !config.displayName.isEmpty() ? config.displayName : channelId.substring(1);
        g.drawString(font, Component.literal(headerName), iconX + 26, cardY + (HEADER_H - 8) / 2, Theme.text(), false);

        int closeX = cardX + cardW - PAD - 16;
        int closeY = cardY + (HEADER_H - 16) / 2;
        boolean closeHover = mouseX >= closeX && mouseX < closeX + 16 && mouseY >= closeY && mouseY < closeY + 16;
        if (closeHover) {
            Ui.fillRoundedRect(g, closeX, closeY, 16, 16, 4, Theme.hoverRow());
        }
        g.drawString(font, "×", closeX + (16 - font.width("×")) / 2, closeY + 4,
            closeHover ? Theme.text() : Theme.textInactive(), false);
    }

    private void drawContent(GuiGraphics g, int mouseX, int mouseY) {
        int x = cardX + PAD;
        int y = contentTop - scrollOffset + 4;

        g.drawString(font, Component.translatable("screen.chatsphere.channel_info.section_about"),
            x, y, Theme.textDim(), false);
        y += font.lineHeight + 4;

        List<FormattedCharSequence> vis = descVisibleLines();
        if (vis.isEmpty()) {
            g.drawString(font, Component.translatable("screen.chatsphere.channel_info.no_description"),
                x, y, Theme.textDim(), false);
            y += font.lineHeight;
        } else {
            for (FormattedCharSequence line : vis) {
                g.drawString(font, line, x, y, Theme.textMain(), false);
                y += font.lineHeight + 2;
            }
            if (descCollapsible()) {
                Component toggle = Component.translatable(descExpanded
                    ? "screen.chatsphere.channel_info.collapse"
                    : "screen.chatsphere.channel_info.expand");
                g.drawString(font, toggle, x, y, Theme.accent(), false);
                y += font.lineHeight + 2;
            }
        }
        descBottom = y;

        if (hasOwner) {
            y += 8;
            Component creatorLabel = Component.translatable("screen.chatsphere.channel_info.created_by");
            g.drawString(font, creatorLabel, x, y + 3, Theme.textDim(), false);
            String ownerName = resolvePlayerName(config.owner);
            int avX = x + font.width(creatorLabel) + 10;
            drawPlayerHead(g, config.owner, avX, y, 14);
            g.drawString(font, ownerName, avX + 20, y + 3, 0xFFAA88FF, false);
            int badgeX = avX + 20 + font.width(ownerName) + 6;
            g.drawString(font, Component.translatable("screen.chatsphere.channel_member.role_owner"),
                badgeX, y + 3, Theme.textFaint(), false);
            y += 18;
        }

        if (!isSub) {
            y += 6;
            boolean pub = config.isPublic;
            Component status = Component.translatable(pub
                ? "screen.chatsphere.channel_info.public_channel"
                : "screen.chatsphere.channel_info.private_channel");
            g.drawString(font, status, x, y, pub ? Theme.toggleOn() : Theme.textDim(), false);
            y += font.lineHeight;
        }

        if (inviteShown) {
            y += 8;
            g.drawString(font, Component.translatable("screen.chatsphere.channel_info.invite_code"),
                x, y + 2, Theme.textDim(), false);
            g.drawString(font, config.inviteCode, x + 52, y + 2, Theme.text(), false);
            int copyX = cardX + cardW - PAD - 40;
            boolean copyHover = mouseX >= copyX && mouseX < copyX + 40 && mouseY >= y && mouseY < y + 18;
            Ui.fillRoundedRect(g, copyX, y, 40, 16, 4, copyHover ? Theme.iconBtnHover() : Theme.iconBtnBg());
            Component copyText = Component.translatable("screen.chatsphere.channel_info.copy_code");
            g.drawString(font, copyText, copyX + (40 - font.width(copyText)) / 2, y + 4, Theme.text(), false);
            y += 18;
        }

        y += 14;
        g.drawString(font, Component.translatable("screen.chatsphere.channel_info.section_members"),
            x, y, Theme.textDim(), false);
        Component countLine = Component.translatable("screen.chatsphere.channel_info.member_count_line",
            config.members.size(), onlineMemberCount);
        g.drawString(font, countLine, x + font.width(Component.translatable("screen.chatsphere.channel_info.section_members")) + 8,
            y, Theme.textFaint(), false);
        y += font.lineHeight + 4;

        if (displayRows.isEmpty()) {
            g.drawString(font, Component.translatable("screen.chatsphere.channel_info.no_members"),
                x + 4, y, Theme.textDim(), false);
            return;
        }

        for (Object row : displayRows) {
            if (row instanceof SectionHeader sh) {
                g.drawString(font, sh.label(), x, y + 3, Theme.textDim(), false);
            } else if (row instanceof MemberRow mr) {
                drawMemberRow(g, mr, x, y, mouseX, mouseY);
            }
            y += rowHeight(row);
        }
    }

    private void drawMemberRow(GuiGraphics g, MemberRow r, int x, int y, int mouseX, int mouseY) {
        int rowW = cardW - PAD * 2;
        boolean hovered = mouseX >= x && mouseX < x + rowW && mouseY >= y && mouseY < y + MEMBER_H;
        if (hovered) {
            Ui.fillRoundedRect(g, x, y, rowW, MEMBER_H, 6, Theme.hoverRow());
        }

        int avX = x + 4;
        int avY = y + (MEMBER_H - 20) / 2;
        drawPlayerHead(g, r.uuid, avX, avY, 20);

        // Status dot beside the avatar
        boolean online = isOnline(r.uuid);
        int dot = 8;
        int dotX = avX + 20 + 5;
        int dotY = y + (MEMBER_H - dot) / 2;
        Ui.fillRoundedRect(g, dotX, dotY, dot, dot, dot / 2,
            online ? 0xFF23A55A : 0xFF666666);

        int nameColor = switch (r.group) {
            case OWNER -> 0xFFAA88FF;
            case ADMIN -> 0xFFFFFF88;
            case MEMBER -> Theme.textMain();
        };
        int nameX = dotX + dot + 6;
        int nameY = y + (MEMBER_H - 8) / 2;
        g.drawString(font, r.name, nameX, nameY, nameColor, false);

        Component badge = switch (r.group) {
            case OWNER -> Component.translatable("screen.chatsphere.channel_member.role_owner");
            case ADMIN -> Component.translatable("screen.chatsphere.channel_member.role_admin");
            case MEMBER -> null;
        };
        if (badge != null) {
            int badgeX = nameX + font.width(r.name) + 8;
            if (badgeX + font.width(badge) < cardX + cardW - PAD - 4) {
                g.drawString(font, badge, badgeX, nameY, Theme.textFaint(), false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int closeX = cardX + cardW - PAD - 16;
        int closeY = cardY + (HEADER_H - 16) / 2;
        if (button == 0 && mx >= closeX && mx < closeX + 16 && my >= closeY && my < closeY + 16) {
            onClose();
            return true;
        }

        // Toggle row sits right above descBottom
        if (button == 0 && descCollapsible() && my >= descBottom - (font.lineHeight + 2)
                && my < descBottom && mx >= cardX + PAD && mx < cardX + cardW - PAD) {
            descExpanded = !descExpanded;
            return true;
        }

        if (button == 0 && inviteShown) {
            int inviteY = descBottom + (hasOwner ? 8 + 18 + 6 + font.lineHeight : 6 + font.lineHeight) + 8;
            int copyX = cardX + cardW - PAD - 40;
            if (mx >= copyX && mx < copyX + 40 && my >= inviteY && my < inviteY + 18) {
                copyInviteCode();
                return true;
            }
        }

        if (button == 1) {
            MemberRow r = memberRowAt(mx, my);
            if (r != null && !r.uuid.equals(localPlayerUuid)) {
                startWhisper(r);
                return true;
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    private MemberRow memberRowAt(double mx, double my) {
        if (mx < cardX || mx >= cardX + cardW || my < contentTop || my > contentBottom) return null;
        double y = contentTop - scrollOffset + 4 + font.lineHeight + 4 + descHeight()
            + (hasOwner ? 8 + 18 : 0) + (isSub ? 0 : 6 + font.lineHeight)
            + (inviteShown ? 8 + 18 : 0) + 14 + font.lineHeight + 4;
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
        if (mx < cardX || mx > cardX + cardW || my < contentTop || my > contentBottom) return false;
        if (scrollMax <= 0) return false;
        scrollOffset = Mth.clamp(scrollOffset - (int) (sy * 20), 0, scrollMax);
        return true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void copyInviteCode() {
        if (minecraft != null) {
            minecraft.keyboardHandler.setClipboard(config.inviteCode);
            copyToast.show();
        }
    }

    private void startWhisper(MemberRow r) {
        if (minecraft == null || minecraft.player == null) return;
        UUID local = minecraft.player.getUUID();
        UUID target = UUID.fromString(r.uuid);
        String convId = local.compareTo(target) < 0 ? local + ":" + target : target + ":" + local;
        ChatHistoryManager.getInstance().addPrivateConversation(convId, Component.literal(r.name));
        minecraft.setScreen(new ModChatScreen("/msg " + r.name + " "));
    }

    private void drawPlayerHead(GuiGraphics g, String uuidStr, int x, int y, int size) {
        try {
            PlayerFaceRenderer.draw(g, PlayerSkinCache.getSkin(UUID.fromString(uuidStr)), x, y, size);
        } catch (Exception ignored) {}
    }

    private String resolvePlayerName(String uuid) {
        if (minecraft != null && minecraft.getConnection() != null) {
            for (var info : minecraft.getConnection().getOnlinePlayers()) {
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
