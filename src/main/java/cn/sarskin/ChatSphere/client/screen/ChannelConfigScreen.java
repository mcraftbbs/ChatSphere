package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatDataStore;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.voice.VoiceIntegration;
import cn.sarskin.ChatSphere.client.voice.VoiceRoom;
import cn.sarskin.ChatSphere.client.widget.CopyToast;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class ChannelConfigScreen extends Screen {
    private static final int ROW_H = 28;
    private static final int TAB_Y = 38;
    private static final int CONTENT_Y = 68;
    private static final int TAB_PAD = 6;

    private final Screen parent;
    private final String channelId;
    private ChatDataStore.ChannelConfig config;

    private int tabX, optLabelX, inputX, btnW;
    private int selectedCat;
    private int scrollOffset;
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();
    private final CopyToast copyToast = new CopyToast();
    private StyledButton inviteCodeBtn;
    private StyledButton regenBtn;
    private Runnable configChangeListener;

    private interface WidgetFactory { AbstractWidget create(int y); }
    private record Opt(String key, WidgetFactory factory) {}
    private record Cat(String key, List<Opt> opts) {}

    private List<Cat> cats;

    public ChannelConfigScreen(Screen parent, String channelId) {
        super(Component.translatable("screen.chatsphere.channel_config.title", channelId.substring(1)));
        this.parent = parent;
        this.channelId = channelId;
    }

    public ChannelConfigScreen(Screen parent, String channelId, int restoreTab) {
        this(parent, channelId);
        this.selectedCat = restoreTab;
    }

    private void buildCats() {
        cats = new ArrayList<>();

        List<Opt> general = new ArrayList<>();
        general.add(new Opt("screen.chatsphere.channel_config.public_label", y -> mkPublicToggle(y)));
        general.add(new Opt("screen.chatsphere.channel_config.show_in_explore", y -> mkExploreToggle(y)));
        general.add(new Opt("screen.chatsphere.channel_config.display_name", y -> {
            EditBox box = new EditBox(font, inputX, y, btnW, 16,
                Component.translatable("screen.chatsphere.channel_config.display_name_hint"));
            box.setMaxLength(32);
            box.setBordered(true);
            box.setValue(config.displayName);
            box.setResponder(val -> {
                config.displayName = val;
                ChatHistoryManager.getInstance().updateChannelConfig(channelId, config);
            });
            return box;
        }));
        general.add(new Opt("screen.chatsphere.channel_config.description", y -> {
            EditBox box = new EditBox(font, inputX, y, btnW, 16,
                Component.translatable("screen.chatsphere.channel_config.description_hint"));
            box.setMaxLength(64);
            box.setBordered(true);
            box.setValue(config.description);
            box.setResponder(val -> {
                config.description = val;
                ChatHistoryManager.getInstance().updateChannelConfig(channelId, config);
            });
            return box;
        }));
        general.add(new Opt("screen.chatsphere.channel_config.invite_code", y -> {
            String code = config.inviteCode.isEmpty() ? "N/A" : config.inviteCode;
            inviteCodeBtn = StyledButton.styledBuilder(
                Component.literal(code),
                btn -> {
                    if (!config.inviteCode.isEmpty()) {
                        minecraft.keyboardHandler.setClipboard(config.inviteCode);
                        copyToast.show();
                    }
                }
            ).bounds(inputX, y, btnW - 90, 20).tooltip(
                Component.translatable("screen.chatsphere.channel_config.tip_invite_code")
            ).build();
            return inviteCodeBtn;
        }));
        general.add(new Opt("", y -> {
            int regenBtnW = 80;
            regenBtn = StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.channel_config.regenerate_code"),
                btn -> {
                    config.inviteCode = generateCode();
                    if (inviteCodeBtn != null)
                        inviteCodeBtn.setMessage(Component.literal(config.inviteCode));
                    ChatHistoryManager.getInstance().updateChannelConfig(channelId, config);
                }
            ).bounds(inputX + btnW - regenBtnW, y, regenBtnW, 20).tooltip(
                Component.translatable("screen.chatsphere.channel_config.tip_regen_code")
            ).build();
            return regenBtn;
        }));
        cats.add(new Cat("screen.chatsphere.channel_config.tab_general", general));

        List<Opt> members = new ArrayList<>();
        members.add(new Opt("", y -> {
            int memberCount = config.members.size();
            long onlineCount = config.members.stream()
                .filter(u -> {
                    if (minecraft == null || minecraft.getConnection() == null) return false;
                    return minecraft.getConnection().getOnlinePlayers().stream()
                        .anyMatch(p -> p.getProfile().getId().toString().equals(u));
                }).count();
            Component info = Component.translatable("screen.chatsphere.channel_config.member_count", memberCount)
                .copy().append("  ")
                .append(Component.translatable("screen.chatsphere.channel_config.online_member_count", onlineCount));
            return StyledButton.styledBuilder(info, btn -> {})
                .bounds(optLabelX, y, width - optLabelX - 20, 20).build();
        }));
        members.add(new Opt("", y -> {
            int adminCount = config.admins.size();
            Component info = Component.translatable("screen.chatsphere.channel_config.admin_count", adminCount);
            return StyledButton.styledBuilder(info, btn -> {})
                .bounds(optLabelX, y, width - optLabelX - 20, 20).build();
        }));
        members.add(new Opt("", y -> {
        int mutedCount = config.mutedPlayers.size();
        Component info = Component.translatable("screen.chatsphere.channel_config.muted_count", mutedCount);
        return StyledButton.styledBuilder(info, btn -> {})
            .bounds(optLabelX, y, width - optLabelX - 20, 20).build();
        }));
        members.add(new Opt("screen.chatsphere.channel_config.manage_members", y ->
            StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.channel_config.manage_members"),
                btn -> minecraft.setScreen(new ChannelMemberScreen(this, channelId))
            ).bounds(inputX, y, btnW, 20).tooltip(
                Component.translatable("screen.chatsphere.channel_config.hint")
            ).build()
        ));
        members.add(new Opt("screen.chatsphere.channel_config.invite_players", y ->
            StyledButton.styledBuilder(
                Component.translatable("screen.chatsphere.channel_config.invite_players"),
                btn -> minecraft.setScreen(new InvitePlayerScreen(this, channelId))
            ).bounds(inputX, y, btnW, 20).tooltip(
                Component.translatable("screen.chatsphere.channel_config.hint")
            ).build()
        ));
        cats.add(new Cat("screen.chatsphere.channel_config.tab_members", members));

        if (VoiceIntegration.isAnyVoiceModPresent()) {
        List<Opt> voiceOpts = new ArrayList<>();
        voiceOpts.add(new Opt("", y -> {
            Component info = Component.translatable("screen.chatsphere.channel_config.voice_room_count", config.voiceRooms.size());
            return new AbstractWidget(30, y, width - 50, 12, Component.empty()) {
                @Override public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    g.drawString(font, info.getVisualOrderText(), getX(), getY(), 0xFFAAAAAA, false);
                }
                @Override protected void updateWidgetNarration(NarrationElementOutput n) {}
                @Override public boolean mouseClicked(double mx, double my, int btn) { return false; }
            };
        }));
        String myUuid = minecraft != null && minecraft.player != null ? minecraft.player.getUUID().toString() : null;
        for (VoiceRoom vr : config.voiceRooms) {
            boolean admin = myUuid != null && (ChatHistoryManager.getInstance().isOwner(channelId, UUID.fromString(myUuid))
                || ChatHistoryManager.getInstance().isAdmin(channelId, UUID.fromString(myUuid)));
            voiceOpts.add(new Opt("", y -> new VoiceRoomWidget(y, width, channelId, vr, myUuid, admin)));
        }
        if (isAdmin()) {
            voiceOpts.add(new Opt("", y -> {
                EditBox input = new EditBox(font, inputX, y + 4, btnW - 55, 16,
                    Component.translatable("screen.chatsphere.channel_config.voice_room_name_hint"));
                input.setMaxLength(32);
                input.setBordered(true);
                StyledButton btn = StyledButton.styledBuilder(
                    Component.translatable("screen.chatsphere.channel_config.voice_create"),
                    unused -> {
                        String name = input.getValue().trim();
                        if (name.isEmpty()) return;
                        config.voiceRooms.add(new VoiceRoom(name, new ArrayList<>()));
                        ChatHistoryManager.getInstance().sendVoiceRoomAction(
                            ServerboundChannelActionPayload.Action.CREATE_VOICE_ROOM, channelId, name);
                        input.setValue("");
                        clearWidgets(); init();
                    }
                ).bounds(inputX + btnW - 55, y + 2, 55, 18).style(StyledButton.Style.CONFIRM)
                .tooltip(Component.translatable("screen.chatsphere.channel_config.tip_create_voice")).build();
                addRenderableWidget(input);
                addRenderableWidget(btn);
                AbstractWidget wrapper = new AbstractWidget(0, y, 0, 0, Component.empty()) {
                    @Override
                    public void setY(int yy) {
                        super.setY(yy);
                        input.setY(yy + 4);
                        btn.setY(yy + 2);
                    }
                    @Override
                    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {}
                    @Override
                    public boolean mouseClicked(double mx, double my, int button) { return false; }
                    @Override
                    protected void updateWidgetNarration(NarrationElementOutput n) {}
                };
                return wrapper;
            }));
        }
        cats.add(new Cat("screen.chatsphere.channel_config.tab_voice", voiceOpts));
        }

        List<Opt> danger = new ArrayList<>();
        if (isOwner()) {
            danger.add(new Opt("screen.chatsphere.channel_config.delete_channel", y ->
                StyledButton.styledBuilder(
                    Component.translatable("screen.chatsphere.channel_config.delete_channel"),
                    btn -> minecraft.setScreen(new ConfirmDeleteChannelScreen(this, channelId))
                ).bounds(inputX, y, btnW, 20).style(StyledButton.Style.DANGER).tooltip(
                    Component.translatable("screen.chatsphere.confirm_delete.tip_confirm")
                ).build()
            ));
        }
        cats.add(new Cat("screen.chatsphere.channel_config.tab_delete", danger));
    }

    @Override
    protected void init() {
        try { minecraft.gameRenderer.loadEffect(ResourceLocation.fromNamespaceAndPath("minecraft", "shaders/post/blur.json")); } catch (Exception ignored) {}
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        config = history.getChannelConfig(channelId);

        UUID playerUuid = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        boolean canEdit = history.isOwner(channelId, playerUuid) || history.isAdmin(channelId, playerUuid);
        if (!canEdit) {
            if (minecraft != null) minecraft.setScreen(parent);
            return;
        }

        buildCats();

        if (configChangeListener != null) {
            ChatHistoryManager.getInstance().removeChannelConfigChangeListener(configChangeListener);
        }
        configChangeListener = () -> {
            ChatDataStore.ChannelConfig latest = ChatHistoryManager.getInstance().getChannelConfig(channelId);
            if (latest != config) {
                config = latest;
                clearWidgets();
                init();
            }
        };
        ChatHistoryManager.getInstance().addChannelConfigChangeListener(configChangeListener);

        int totalTabW = 0;
        for (Cat c : cats) totalTabW += font.width(Component.translatable(c.key())) + TAB_PAD * 2 + 4;
        tabX = (width - totalTabW) / 2;

        optLabelX = 30;
        btnW = Math.min(160, width - optLabelX - 80);
        inputX = width - btnW - 40;

        scrollWidgets.clear();
        scrollOffset = Mth.clamp(scrollOffset, 0, calcMaxScroll());

        int y = CONTENT_Y - scrollOffset;
        for (Opt opt : cats.get(selectedCat).opts()) {
            scrollWidgets.add(addRenderableWidget(opt.factory().create(y)));
            y += ROW_H;
        }

        addRenderableWidget(StyledButton.styledBuilder(
            CommonComponents.GUI_DONE,
            btn -> onClose()
        ).bounds(width / 2 - 50, height - 32, 100, 20).style(StyledButton.Style.CONFIRM).tooltip(
            Component.translatable("screen.chatsphere.channel_config.tip_done")
        ).build());
    }

    private boolean isOwner() {
        UUID playerUuid = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        return playerUuid != null && ChatHistoryManager.getInstance().isOwner(channelId, playerUuid);
    }

    private boolean isAdmin() {
        UUID playerUuid = minecraft != null && minecraft.player != null ? minecraft.player.getUUID() : null;
        return playerUuid != null && (ChatHistoryManager.getInstance().isOwner(channelId, playerUuid)
            || ChatHistoryManager.getInstance().isAdmin(channelId, playerUuid));
    }

    private StyledButton mkPublicToggle(int y) {
        return StyledButton.styledBuilder(
            buildPublicLabel(),
            btn -> {
                config.isPublic = !config.isPublic;
                btn.setMessage(buildPublicLabel());
                ((StyledButton) btn).setStyle(config.isPublic ? StyledButton.Style.TOGGLE_ON : StyledButton.Style.TOGGLE_OFF);
                ChatHistoryManager.getInstance().updateChannelConfig(channelId, config);
            }
        ).bounds(inputX, y, btnW, 20).style(
            config.isPublic ? StyledButton.Style.TOGGLE_ON : StyledButton.Style.TOGGLE_OFF
        ).tooltip(
            Component.translatable("screen.chatsphere.config.tip_toggle")
        ).build();
    }

    private Component buildPublicLabel() {
        if (config.isPublic) {
            return Component.translatable("screen.chatsphere.channel_config.enabled");
        }
        return Component.translatable("screen.chatsphere.channel_config.disabled");
    }

    private StyledButton mkExploreToggle(int y) {
        return StyledButton.styledBuilder(
            buildExploreLabel(),
            btn -> {
                config.showInExplore = !config.showInExplore;
                btn.setMessage(buildExploreLabel());
                ((StyledButton) btn).setStyle(config.showInExplore ? StyledButton.Style.TOGGLE_ON : StyledButton.Style.TOGGLE_OFF);
                ChatHistoryManager.getInstance().updateChannelConfig(channelId, config);
            }
        ).bounds(inputX, y, btnW, 20).style(
            config.showInExplore ? StyledButton.Style.TOGGLE_ON : StyledButton.Style.TOGGLE_OFF
        ).tooltip(
            Component.translatable("screen.chatsphere.config.tip_toggle")
        ).build();
    }

    private Component buildExploreLabel() {
        return Component.translatable(config.showInExplore
            ? "screen.chatsphere.channel_config.enabled"
            : "screen.chatsphere.channel_config.disabled");
    }

    private void switchCategory(int idx) {
        if (idx == selectedCat) return;
        selectedCat = idx;
        scrollOffset = 0;
        setFocused(null);
        clearWidgets();
        init();
    }

    private static String generateCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        Random rng = new Random();
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
        return sb.toString();
    }

    @Override
    public void tick() {
        copyToast.tick();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, width / 2 - font.width(title) / 2, 14, 0xFFFFFF, false);

        int cx = tabX;
        for (int i = 0; i < cats.size(); i++) {
            Component label = Component.translatable(cats.get(i).key());
            int w = font.width(label) + TAB_PAD * 2 + 4;
            boolean sel = i == selectedCat;
            boolean hover = mouseX >= cx && mouseX <= cx + w && mouseY >= TAB_Y && mouseY <= TAB_Y + 22;
            if (sel)
                g.fill(cx, TAB_Y + 20, cx + w, TAB_Y + 22, 0xFF8888FF);
            else if (hover)
                g.fill(cx, TAB_Y, cx + w, TAB_Y + 22, 0x225A4A7E);
            g.drawString(font, label, cx + TAB_PAD, TAB_Y + 7,
                sel ? 0xFF8888FF : 0xFFFFFFFF, false);
            cx += w + 6;
        }

        g.fill(10, CONTENT_Y - 6, width - 10, CONTENT_Y - 5, 0x225A4A7E);

        int y = CONTENT_Y - scrollOffset;
        for (Opt opt : cats.get(selectedCat).opts()) {
            if (y > -ROW_H && y < height) {
                if (!opt.key().isEmpty()) {
                    g.drawString(font, Component.translatable(opt.key()), optLabelX, y + 6, 0xFFFFFFFF, false);
                }
            }
            y += ROW_H;
        }

        copyToast.render(g, 0, width);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int cx = tabX;
            for (int i = 0; i < cats.size(); i++) {
                int w = font.width(Component.translatable(cats.get(i).key())) + TAB_PAD * 2 + 4;
                if (mouseX >= cx && mouseX <= cx + w && mouseY >= TAB_Y && mouseY <= TAB_Y + 22) {
                    switchCategory(i);
                    return true;
                }
                cx += w + 6;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = calcMaxScroll();
        if (maxScroll <= 0) return false;
        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int y = CONTENT_Y - scrollOffset;
        List<Opt> opts = cats.get(selectedCat).opts();
        for (int i = 0; i < opts.size() && i < scrollWidgets.size(); i++) {
            scrollWidgets.get(i).setY(y);
            y += ROW_H;
        }
        return true;
    }

    private int calcMaxScroll() {
        int total = cats.get(selectedCat).opts().size() * ROW_H;
        return Math.max(0, CONTENT_Y + total - (height - 42));
    }

    @Override
    public void onClose() {
        ChatHistoryManager.getInstance().saveNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        try { minecraft.gameRenderer.loadEffect(null); } catch (Exception ignored) {}
        ChatHistoryManager.getInstance().removeChannelConfigChangeListener(configChangeListener);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private class VoiceRoomWidget extends AbstractWidget {
        private final String channelId;
        private final String roomName;
        private final String myUuid;
        private final boolean isAdmin;

        VoiceRoomWidget(int y, int w, String channelId, VoiceRoom room, String myUuid, boolean isAdmin) {
            super(0, y, w, ROW_H, Component.empty());
            this.channelId = channelId;
            this.roomName = room.name;
            this.myUuid = myUuid;
            this.isAdmin = isAdmin;
        }

        private VoiceRoom currentRoom() {
            for (VoiceRoom vr : config.voiceRooms) {
                if (vr.name.equals(roomName)) return vr;
            }
            return null;
        }

        @Override
        public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
            VoiceRoom room = currentRoom();
            if (room == null) return;
            int ry = getY();
            boolean isMember = myUuid != null && room.members.contains(myUuid);

            int leftX = 30;
            int rightEdge = width - 20;
            int delW = 22;
            int delX = rightEdge - delW;
            int btnW = 72;
            int btnX = delX - 4 - btnW;

            // Room name + count
            g.drawString(font, room.name + " (" + room.members.size() + ")", leftX, ry + 3, 0xFFFFFFFF, false);

            // Member names (second line)
            if (!room.members.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (String uuid : room.members) {
                    String name = resolvePlayerName(uuid);
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(name);
                }
                String membersStr = sb.toString();
                int maxW = rightEdge - leftX - 10;
                if (font.width(membersStr) > maxW) {
                    while (font.width(membersStr + "…") > maxW && membersStr.length() > 0) {
                        membersStr = membersStr.substring(0, membersStr.length() - 1);
                    }
                    membersStr += "…";
                }
                g.drawString(font, membersStr, leftX + 2, ry + 14, 0xFF888888, false);
            }

            // Join/Leave button
            g.fill(btnX, ry + 1, btnX + btnW, ry + 19, isMember ? 0xFF5A2828 : 0xFF285A28);
            g.renderOutline(btnX, ry + 1, btnW, 18, isMember ? 0xFF9A4848 : 0xFF489A48);
            Component btnText = Component.translatable(isMember
                ? "screen.chatsphere.channel_config.voice_leave"
                : "screen.chatsphere.channel_config.voice_join");
            g.drawString(font, btnText, btnX + (btnW - font.width(btnText)) / 2, ry + 6, 0xFFFFFFFF, false);

            // Tooltip for join/leave button
            if (mx >= btnX && mx < btnX + btnW && my >= ry + 1 && my < ry + 19) {
                g.renderTooltip(font, Component.translatable(isMember
                    ? "screen.chatsphere.channel_config.tip_leave_voice"
                    : "screen.chatsphere.channel_config.tip_join_voice"), mx, my);
            }

            // Delete button (admin only)
            if (isAdmin) {
                g.fill(delX, ry + 1, delX + delW, ry + 19, 0xFF5A1E1E);
                g.renderOutline(delX, ry + 1, delW, 18, 0xFF9A4E4E);
                g.drawString(font, Component.literal("×"), delX + (delW - font.width("×")) / 2, ry + 6, 0xFFFFFFFF, false);

                // Tooltip for delete button
                if (mx >= delX && mx < delX + delW && my >= ry + 1 && my < ry + 19) {
                    g.renderTooltip(font, Component.translatable("screen.chatsphere.channel_config.tip_kick_member"), mx, my);
                }
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (btn != 0) return false;
            VoiceRoom room = currentRoom();
            if (room == null) return false;
            int ry = getY();
            boolean isMember = myUuid != null && room.members.contains(myUuid);
            int rightEdge = width - 20;
            int delW = 22;
            int delX = rightEdge - delW;
            int btnW = 72;
            int btnX = delX - 4 - btnW;

            if (mx >= btnX && mx < btnX + btnW && my >= ry + 1 && my < ry + 19) {
                ServerboundChannelActionPayload.Action act = isMember ? ServerboundChannelActionPayload.Action.LEAVE_VOICE_ROOM : ServerboundChannelActionPayload.Action.JOIN_VOICE_ROOM;
                if (myUuid != null) {
                    if (isMember) {
                        room.members.remove(myUuid);
                    } else {
                        for (VoiceRoom vr : config.voiceRooms) vr.members.remove(myUuid);
                        room.members.add(myUuid);
                    }
                }
                ChatHistoryManager.getInstance().sendVoiceRoomAction(act, channelId, roomName);
                clearWidgets(); init();
                return true;
            }

            if (isAdmin && mx >= delX && mx < delX + delW && my >= ry + 1 && my < ry + 19) {
                config.voiceRooms.removeIf(vr -> vr.name.equals(roomName));
                ChatHistoryManager.getInstance().sendVoiceRoomAction(
                    ServerboundChannelActionPayload.Action.DELETE_VOICE_ROOM, channelId, roomName);
                clearWidgets(); init();
                return true;
            }

            return false;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput n) {}
    }

    private String resolvePlayerName(String uuid) {
        String name = config.playerNames.get(uuid);
        if (name != null) return name;
        name = ChatHistoryManager.getInstance().getPlayerName(uuid);
        if (name != null) return name;
        return uuid.substring(0, 8) + "…";
    }
}
