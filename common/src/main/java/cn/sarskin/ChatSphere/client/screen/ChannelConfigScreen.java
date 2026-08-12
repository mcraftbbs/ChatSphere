package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ChatDataStore;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.voice.VoiceIntegration;
import cn.sarskin.ChatSphere.client.voice.VoiceRoom;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.UiToggle;
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

    private int subChannelTabIdx = -1;
    private boolean dragActive;
    private int dragSection;
    private int dragFrom;
    private int dragTo;
    private int dragInsertPos = -1;
    private String setParentId;
    private final List<String> subOrder = new ArrayList<>();
    private EditBox subCreateInput;
    private String renamingId;
    private EditBox renameBox;

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

        boolean isSub = ChatHistoryManager.isSubChannel(channelId);

        List<Opt> general = new ArrayList<>();
        if (!isSub) {
            general.add(new Opt("screen.chatsphere.channel_config.public_label", y -> mkPublicToggle(y)));
            general.add(new Opt("screen.chatsphere.channel_config.show_in_explore", y -> mkExploreToggle(y)));
            general.add(new Opt("screen.chatsphere.channel_config.main_chat_label", y -> mkChatToggle(y)));
        }
        general.add(new Opt("screen.chatsphere.channel_config.display_name", y -> {
            EditBox box = new EditBox(font, inputX, y, btnW, 16,
                Component.translatable("screen.chatsphere.channel_config.display_name_hint"));
            box.setMaxLength(32);
            box.setBordered(true);
            box.setValue(config.displayName);
            box.setResponder(val -> {
                config.displayName = val;
                scheduleChannelUpdate();
            });
            return box;
        }));
        if (!isSub) {
            general.add(new Opt("screen.chatsphere.channel_config.default_sub_label", y -> {
                EditBox box = new EditBox(font, inputX, y, btnW, 16,
                    Component.translatable("screen.chatsphere.channel_config.default_sub_hint"));
                box.setMaxLength(32);
                box.setBordered(true);
                box.setValue(config.defaultSubChannel);
                box.setVisible(!config.mainChatEnabled);
                box.setResponder(val -> {
                    config.defaultSubChannel = val.trim();
                    scheduleChannelUpdate();
                });
                return box;
            }));
        }
        general.add(new Opt("screen.chatsphere.channel_config.description", y -> {
            EditBox box = new EditBox(font, inputX, y, btnW, 16,
                Component.translatable("screen.chatsphere.channel_config.description_hint"));
            box.setMaxLength(64);
            box.setBordered(true);
            box.setValue(config.description);
            box.setResponder(val -> {
                config.description = val;
                scheduleChannelUpdate();
            });
            return box;
        }));
        if (!isSub) {
            general.add(new Opt("screen.chatsphere.channel_config.invite_code", y -> {
                String code = config.inviteCode.isEmpty() ? Component.translatable("screen.chatsphere.channel_config.invite_code_na").getString() : config.inviteCode;
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
        }
        cats.add(new Cat("screen.chatsphere.channel_config.tab_general", general));

        List<Opt> members = new ArrayList<>();
        if (!isSub) {
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
        }

        if (isAdmin() && !ChatHistoryManager.isSubChannel(channelId)) {
            List<Opt> subOpts = new ArrayList<>();
            cats.add(new Cat("screen.chatsphere.channel_config.tab_subchannels", subOpts));
            subChannelTabIdx = cats.size() - 1;
        }

        if (VoiceIntegration.isAnyVoiceModPresent()) {
        List<Opt> voiceOpts = new ArrayList<>();
        voiceOpts.add(new Opt("", y -> {
            Component info = Component.translatable("screen.chatsphere.channel_config.voice_room_count", config.voiceRooms.size());
            return new AbstractWidget(30, y, width - 50, 12, Component.empty()) {
                @Override public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    g.drawString(font, info.getVisualOrderText(), getX(), getY(), Theme.textInactive(), false);
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
                // Do not rebuild while the player is typing in an edit box — rebuilding
                // mid-input clears focus and loses the caret.
                if (getFocused() instanceof EditBox) return;
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

        if (selectedCat == subChannelTabIdx) {
            subOrder.clear();
            subOrder.addAll(history.getDescendantChannels(channelId));
            renamingId = null;
            renameBox = null;
            subCreateInput = new EditBox(font, 30, 0, Math.max(100, btnW - 60), 16,
                Component.translatable("screen.chatsphere.channel_config.subchannel_name_hint"));
            subCreateInput.setMaxLength(32);
            subCreateInput.setBordered(true);
            addRenderableWidget(subCreateInput);
        }

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

    private UiToggle mkPublicToggle(int y) {
        UiToggle toggle = new UiToggle(inputX, y, btnW, 20, config.isPublic,
            v -> {
                config.isPublic = v;
                ChatHistoryManager.getInstance().updateChannelConfig(channelId, config);
            });
        toggle.setTooltip(Tooltip.create(Component.translatable("screen.chatsphere.config.tip_toggle")));
        return toggle;
    }

    private UiToggle mkExploreToggle(int y) {
        UiToggle toggle = new UiToggle(inputX, y, btnW, 20, config.showInExplore,
            v -> {
                config.showInExplore = v;
                ChatHistoryManager.getInstance().updateChannelConfig(channelId, config);
            });
        toggle.setTooltip(Tooltip.create(Component.translatable("screen.chatsphere.config.tip_toggle")));
        return toggle;
    }

    private UiToggle mkChatToggle(int y) {
        UiToggle toggle = new UiToggle(inputX, y, btnW, 20, config.mainChatEnabled,
            v -> {
                config.mainChatEnabled = v;
                ChatHistoryManager.getInstance().updateChannelConfig(channelId, config);
                clearWidgets();
                init();
            });
        toggle.setTooltip(Tooltip.create(Component.translatable("screen.chatsphere.channel_config.tip_main_chat")));
        return toggle;
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

    private long scheduledChannelUpdateAt = -1;

    /** Debounce per-keystroke edits to avoid UPDATE_CONFIG floods. */
    private void scheduleChannelUpdate() {
        scheduledChannelUpdateAt = System.currentTimeMillis() + 400;
    }

    @Override
    public void tick() {
        copyToast.tick();
        if (scheduledChannelUpdateAt > 0 && System.currentTimeMillis() >= scheduledChannelUpdateAt) {
            scheduledChannelUpdateAt = -1;
            ChatHistoryManager.getInstance().updateChannelConfig(channelId, config);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int contentBottom = height - 48;
        for (AbstractWidget w : scrollWidgets) {
            int wy = w.getY();
            w.visible = wy >= CONTENT_Y && wy + ROW_H <= contentBottom;
        }
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, width / 2 - font.width(title) / 2, 14, Theme.text(), false);

        int cx = tabX;
        for (int i = 0; i < cats.size(); i++) {
            Component label = Component.translatable(cats.get(i).key());
            int w = font.width(label) + TAB_PAD * 2 + 4;
            boolean sel = i == selectedCat;
            boolean hover = mouseX >= cx && mouseX <= cx + w && mouseY >= TAB_Y && mouseY <= TAB_Y + 22;
            if (sel)
                g.fill(cx, TAB_Y + 20, cx + w, TAB_Y + 22, Theme.accent());
            else if (hover)
                g.fill(cx, TAB_Y, cx + w, TAB_Y + 22, Theme.divider());
            g.drawString(font, label, cx + TAB_PAD, TAB_Y + 7,
                sel ? Theme.accent() : Theme.text(), false);
            cx += w + 6;
        }

        g.fill(10, CONTENT_Y - 6, width - 10, CONTENT_Y - 5, Theme.divider());

        int y = CONTENT_Y - scrollOffset;
        for (Opt opt : cats.get(selectedCat).opts()) {
            if (y >= CONTENT_Y && y + ROW_H <= contentBottom) {
                if (!opt.key().isEmpty()) {
                    g.drawString(font, Component.translatable(opt.key()), optLabelX, y + 6, Theme.text(), false);
                }
            }
            y += ROW_H;
        }

        if (selectedCat == subChannelTabIdx) {
            renderSubChannelTab(g, mouseX, mouseY);
        }

        copyToast.render(g, 0, width);
    }

    private void renderSubChannelTab(GuiGraphics g, int mouseX, int mouseY) {
        int contentBottom = height - 48;
        int ry = CONTENT_Y - scrollOffset;
        int labelX = 30;
        boolean visible;

        visible = ry >= CONTENT_Y && ry + ROW_H <= contentBottom;
        if (visible) {
            g.drawString(font, Component.translatable("screen.chatsphere.channel_config.subchannels_title"),
                    labelX, ry + 4, Theme.accent(), false);
        }
        ry += ROW_H;

        if (subCreateInput != null) {
            visible = ry >= CONTENT_Y && ry + ROW_H <= contentBottom;
            subCreateInput.visible = visible;
            if (visible) {
                subCreateInput.setY(ry + 1);
                g.drawString(font, Component.translatable("screen.chatsphere.channel_config.subchannel_create"),
                        labelX, ry + 8, Theme.text(), false);
                int boxX = labelX + 110;
                int boxW = Math.max(80, width - boxX - 90);
                subCreateInput.setX(boxX);
                subCreateInput.setWidth(boxW);
                int btnX = boxX + boxW + 6;
                boolean hover = mouseX >= btnX && mouseX <= btnX + 64 && mouseY >= ry && mouseY <= ry + ROW_H;
                g.fill(btnX, ry + 2, btnX + 64, ry + 20, hover ? Theme.accent() : 0xFF2A5A2A);
                g.renderOutline(btnX, ry + 2, 64, 18, 0xFF489A48);
                Component txt = Component.translatable("screen.chatsphere.channel_config.subchannel_add");
                g.drawString(font, txt, btnX + (64 - font.width(txt)) / 2, ry + 7, 0xFFFFFFFF, false);
            }
            ry += ROW_H;
        }

        if (subOrder.isEmpty()) {
            visible = ry >= CONTENT_Y && ry + ROW_H <= contentBottom;
            if (visible) {
                g.drawString(font, Component.translatable("screen.chatsphere.channel_config.subchannel_empty"),
                        labelX + 8, ry + 6, Theme.textDim(), false);
            }
            ry += ROW_H;
        }
        int renameRowY = -1;
        for (int i = 0; i < subOrder.size(); i++) {
            visible = ry >= CONTENT_Y && ry + ROW_H <= contentBottom;
            String rowId = subOrder.get(i);
            if (rowId.equals(renamingId)) {
                renameRowY = visible ? ry : -1;
            }
            if (visible) {
                if (dragActive && dragInsertPos == i) {
                    renderInsertLine(g, ry);
                }
                renderReorderRow(g, ry, mouseX, mouseY, rowId, i, false, ChatHistoryManager.channelDepth(rowId));
            }
            ry += ROW_H;
        }
        if (dragActive && dragInsertPos == subOrder.size()) {
            renderInsertLine(g, ry);
        }

        if (renamingId != null && renameBox != null) {
            if (renameRowY >= 0) {
                renameBox.visible = true;
                renameBox.setY(renameRowY + 1);
                renameBox.setX(30 + 12);
                renameBox.setWidth(Math.max(100, Math.min(btnW, width - 30 - 12 - 60)));
            } else {
                renameBox.visible = false;
            }
        }

        if (setParentId != null) {
            boolean candVisible = ry >= CONTENT_Y && ry + ROW_H <= contentBottom;
            if (candVisible) {
                g.drawString(font, Component.translatable("screen.chatsphere.channel_config.subchannel_parent_pick",
                        parentDisplayName(setParentId)), labelX, ry + 4, Theme.accent(), false);
            }
            ry += ROW_H;
            List<String> candidates = parentCandidates(setParentId);
            for (String cid : candidates) {
                candVisible = ry >= CONTENT_Y && ry + ROW_H <= contentBottom;
                if (candVisible) {
                    boolean hover = mouseY >= ry && mouseY < ry + ROW_H && mouseX >= labelX && mouseX < width - 16;
                    g.fill(labelX, ry + 1, width - 16, ry + ROW_H - 1, hover ? 0x22FFFFFF : 0x00000000);
                    Component cname = cid.isEmpty()
                            ? Component.translatable("screen.chatsphere.channel_config.subchannel_parent_top")
                            : Component.literal(parentDisplayName(cid));
                    g.drawString(font, Component.literal(cid.isEmpty() ? "" : "  ").append(cname),
                            labelX + 8, ry + 6, Theme.text(), false);
                }
                ry += ROW_H;
            }
        }
    }

    private String parentDisplayName(String id) {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        Component name = history.getConversationDisplayName(id);
        return name != null ? name.getString() : id;
    }

    private List<String> parentCandidates(String childId) {
        List<String> candidates = new ArrayList<>();
        candidates.add(""); // top level (main channel)
        String currentParent = ChatHistoryManager.subParentOf(childId);
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        for (String id : subOrder) {
            if (ChatHistoryManager.channelDepth(id) == 1 && !id.equals(childId)
                    && !id.equals(currentParent)) {
                candidates.add(id);
            }
        }
        return candidates;
    }

    private void renderInsertLine(GuiGraphics g, int y) {
        int x0 = 30;
        g.fill(x0, y - 1, width - 16, y + 1, Theme.accent());
        g.fill(x0 - 3, y - 3, x0 + 3, y + 3, Theme.accent());
    }

    private void renderReorderRow(GuiGraphics g, int ry, int mouseX, int mouseY,
                                  String id, int index, boolean isTopSection, int indent) {
        boolean isDragged = dragActive && index == dragFrom;
        int rowX = 30 + indent * 12;
        if (isDragged) {
            g.fill(rowX - 8, ry + 1, width - 16, ry + ROW_H - 1, 0x30FFFFFF);
        }
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        Component name = history.getConversationDisplayName(id);
        String nameStr = name != null ? name.getString() : id;

        boolean renamingThis = id.equals(renamingId);

        if (!renamingThis) {
            int handleW = 12;
            g.drawString(font, Component.literal("\u2630"), rowX, ry + 7,
                    mouseY >= ry && mouseY < ry + ROW_H && mouseX >= rowX && mouseX < rowX + handleW + 8
                            ? Theme.accent() : Theme.textDim(), false);

            g.drawString(font, Component.literal(nameStr), rowX + 22, ry + 6, Theme.text(), false);

            int rightEdge = width - 16;
            int delW = 18;
            int delX = rightEdge - delW;
            int renameW = 22;
            int renameX = delX - 6 - renameW;
            int parentW = 22;
            int parentX = renameX - 6 - parentW;

            boolean delHover = mouseX >= delX && mouseX < delX + delW && mouseY >= ry && mouseY < ry + ROW_H;
            g.fill(delX, ry + 3, delX + delW, ry + 19, delHover ? 0xFF7A2828 : 0xFF5A1E1E);
            g.drawString(font, Component.literal("×"), delX + (delW - font.width("×")) / 2, ry + 7, 0xFFFFFFFF, false);
            if (delHover) {
                g.renderTooltip(font, Component.translatable("screen.chatsphere.channel_config.subchannel_delete_tip"), mouseX, mouseY);
            }

            boolean renameHover = mouseX >= renameX && mouseX < renameX + renameW && mouseY >= ry && mouseY < ry + ROW_H;
            g.fill(renameX, ry + 3, renameX + renameW, ry + 19, renameHover ? 0xFF3A5A8A : 0xFF2A3A5A);
            Component editTxt = Component.literal("✎");
            g.drawString(font, editTxt, renameX + (renameW - font.width(editTxt)) / 2, ry + 7, 0xFFFFFFFF, false);
            if (renameHover) {
                g.renderTooltip(font, Component.translatable("screen.chatsphere.channel_config.subchannel_rename_tip"), mouseX, mouseY);
            }

            boolean parentHover = mouseX >= parentX && mouseX < parentX + parentW && mouseY >= ry && mouseY < ry + ROW_H;
            g.fill(parentX, ry + 3, parentX + parentW, ry + 19, parentHover ? 0xFF3A5A5A : 0xFF2A3A3A);
            Component parentTxt = Component.literal("⇄");
            g.drawString(font, parentTxt, parentX + (parentW - font.width(parentTxt)) / 2, ry + 7, 0xFFFFFFFF, false);
            if (parentHover) {
                g.renderTooltip(font, Component.translatable("screen.chatsphere.channel_config.subchannel_parent_tip"), mouseX, mouseY);
            }
        }
    }

    private int subRowStartY() {
        return CONTENT_Y - scrollOffset + ROW_H + (subCreateInput != null ? ROW_H : 0);
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
            if (selectedCat == subChannelTabIdx) {
                if (handleSubChannelClick(mouseX, mouseY)) return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleSubChannelClick(double mouseX, double mouseY) {
        int ry = CONTENT_Y - scrollOffset + ROW_H;
        if (subCreateInput != null && mouseY >= ry && mouseY < ry + ROW_H) {
            int boxX = 30 + 110;
            int boxW = Math.max(80, width - boxX - 90);
            int btnX = boxX + boxW + 6;
            if (mouseX >= btnX && mouseX <= btnX + 64) {
                String name = subCreateInput.getValue().trim();
                if (!name.isEmpty() && minecraft != null && minecraft.player != null
                        && minecraft.getConnection() != null) {
                    if (!name.contains("/")) {
                        String newId = channelId + "/" + name;
                        var conn = minecraft.getConnection().getConnection();
                        conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                                new ServerboundChannelActionPayload(
                                        ServerboundChannelActionPayload.Action.CREATE,
                                        newId, minecraft.player.getUUID(), false, "", "",
                                        List.<String>of(), List.<String>of(), List.<String>of(),
                                        "", false, "", "", "", false, "")));
                        subCreateInput.setValue("");
                    }
                }
                return true;
            }
            ry += ROW_H;
        } else if (subCreateInput != null) {
            ry += ROW_H;
        }

        for (int i = 0; i < subOrder.size(); i++) {
            if (mouseY >= ry && mouseY < ry + ROW_H) {
                String id = subOrder.get(i);
                if (id.equals(renamingId)) return false;
                int rowX = 30 + ChatHistoryManager.channelDepth(id) * 12;
                if (mouseX >= rowX && mouseX < rowX + 20) {
                    startDrag(1, i);
                    return true;
                }
                int rightEdge = width - 16;
                int delX = rightEdge - 18;
                int renameX = delX - 6 - 22;
                int parentX = renameX - 6 - 22;
                if (mouseX >= delX && mouseX < delX + 18) {
                    if (minecraft != null && minecraft.player != null && minecraft.getConnection() != null) {
                        var conn = minecraft.getConnection().getConnection();
                        conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                                new ServerboundChannelActionPayload(
                                        ServerboundChannelActionPayload.Action.REMOVE_CHANNEL,
                                        id, minecraft.player.getUUID(), false, "", "",
                                        List.<String>of(), List.<String>of(), List.<String>of(),
                                        "", false, "", "", "", false, "")));
                    }
                    return true;
                }
                if (mouseX >= renameX && mouseX < renameX + 22) {
                    beginRename(id);
                    return true;
                }
                if (mouseX >= parentX && mouseX < parentX + 22) {
                    setParentId = id.equals(setParentId) ? null : id;
                    return true;
                }
                return false;
            }
            ry += ROW_H;
        }

        if (setParentId != null) {
            ry += ROW_H; // header row
            List<String> candidates = parentCandidates(setParentId);
            for (String cid : candidates) {
                if (mouseY >= ry && mouseY < ry + ROW_H) {
                    if (minecraft != null && minecraft.player != null && minecraft.getConnection() != null) {
                        if (cid.isEmpty()) {
                            ChatHistoryManager.getInstance().sendMoveSubChannel(setParentId, channelId);
                        } else {
                            ChatHistoryManager.getInstance().sendMoveSubChannel(setParentId, cid);
                        }
                        setParentId = null;
                    }
                    return true;
                }
                ry += ROW_H;
            }
            setParentId = null;
            return true;
        }
        return false;
    }

    private void beginRename(String id) {
        if (renameBox != null) {
            removeWidget(renameBox);
        }
        renamingId = id;
        renameBox = new EditBox(font, 30, 0, Math.max(100, btnW), 16,
                Component.translatable("screen.chatsphere.channel_config.subchannel_name_hint"));
        renameBox.setMaxLength(32);
        renameBox.setBordered(true);
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        Component cur = history.getConversationDisplayName(id);
        if (cur != null) renameBox.setValue(cur.getString());
        addRenderableWidget(renameBox);
        setFocused(renameBox);
    }

    private void confirmRename() {
        if (renamingId != null && renameBox != null) {
            String name = renameBox.getValue().trim();
            if (!name.isEmpty() && !name.contains("/")) {
                ChatHistoryManager.getInstance().sendRenameSubChannel(renamingId, name);
            }
        }
        renamingId = null;
        renameBox = null;
        clearWidgets();
        init();
    }

    private void startDrag(int section, int index) {
        dragActive = true;
        dragSection = section;
        dragFrom = index;
        dragTo = index;
        dragInsertPos = -1;
        if (subCreateInput != null) subCreateInput.setFocused(false);
        if (renameBox != null) renameBox.setFocused(false);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragActive && button == 0 && selectedCat == subChannelTabIdx) {
            int startY = subRowStartY();
            int idx = (int) Math.floor((mouseY - startY) / ROW_H);
            idx = Math.max(0, Math.min(subOrder.size() - 1, idx));
            String srcId = subOrder.get(dragFrom);
            String tgtId = subOrder.get(idx);
            int srcDepth = ChatHistoryManager.channelDepth(srcId);
            int tgtDepth = ChatHistoryManager.channelDepth(tgtId);
            // Same-level insert line: upper half = before the row, lower half = after it.
            if (srcDepth == tgtDepth && !tgtId.equals(srcId)) {
                double inRow = mouseY - startY - idx * ROW_H;
                dragInsertPos = inRow < ROW_H / 2.0 ? idx : idx + 1;
            } else {
                dragInsertPos = -1;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragActive && button == 0) {
            dragActive = false;
            int insertPos = dragInsertPos;
            dragInsertPos = -1;
            if (insertPos >= 0 && insertPos != dragFrom && insertPos != dragFrom + 1
                    && minecraft != null && minecraft.player != null
                    && minecraft.getConnection() != null) {
                String srcId = subOrder.get(dragFrom);
                String srcParent = ChatHistoryManager.subParentOf(srcId);
                String src = subOrder.remove(dragFrom);
                int insert = insertPos > dragFrom ? insertPos - 1 : insertPos;
                subOrder.add(Math.min(insert, subOrder.size()), src);
                List<String> siblings = new ArrayList<>();
                int srcDepth = ChatHistoryManager.channelDepth(srcId);
                for (String id : subOrder) {
                    if (ChatHistoryManager.channelDepth(id) == srcDepth
                            && ChatHistoryManager.subParentOf(id).equals(srcParent)) {
                        siblings.add(id);
                    }
                }
                ChatHistoryManager.getInstance().sendReorderChannels(srcParent, siblings);
            }
            dragFrom = dragTo = -1;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renameBox != null && renameBox.isFocused() && (keyCode == 257 || keyCode == 335)) {
            confirmRename();
            return true;
        }
        if (renameBox != null && renameBox.isFocused() && keyCode == 256) {
            renamingId = null;
            renameBox = null;
            clearWidgets();
            init();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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
        int total;
        if (selectedCat == subChannelTabIdx) {
            total = ROW_H + (subCreateInput != null ? ROW_H : 0);
            total += Math.max(ROW_H, subOrder.size() * ROW_H);
            if (setParentId != null) {
                total += ROW_H + parentCandidates(setParentId).size() * ROW_H;
            }
        } else {
            total = cats.get(selectedCat).opts().size() * ROW_H;
        }
        return Math.max(0, CONTENT_Y + total - (height - 48));
    }

    @Override
    public void onClose() {
        ChatHistoryManager.getInstance().saveNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public void removed() {
        ChatHistoryManager.getInstance().removeChannelConfigChangeListener(configChangeListener);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        BackgroundBlur.blurScreen(g, width, height);
        super.renderBackground(g, mx, my, pt);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
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
            g.drawString(font, room.name + " (" + room.members.size() + ")", leftX, ry + 3, Theme.text(), false);

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
                g.drawString(font, membersStr, leftX + 2, ry + 14, Theme.textDim(), false);
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
