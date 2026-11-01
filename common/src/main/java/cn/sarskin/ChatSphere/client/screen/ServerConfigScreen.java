package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.platform.PacketSender;

import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.UiToggle;
import cn.sarskin.ChatSphere.client.widget.StyledButton;
import cn.sarskin.ChatSphere.config.CfgValue;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.network.ServerboundConfigUpdatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class ServerConfigScreen extends Screen {
    private final Screen lastScreen;

    private static final int ROW_H = 28;
    private static final int TAB_Y = 38;
    private static final int CONTENT_Y = 68;
    private static final int TAB_PAD = 6;
    private static final int GROUP_H = 22;
    /** The banned-word box is multi-line, so it needs real estate and not just one row. */
    private static final int BANNED_H = 60;
    /** Same reason as BANNED_H: the allow-list is a multi-line regex field. */
    private static final int DOMAIN_H = 40;

    private int tabX, optLabelX, inputX, btnW;
    private int selectedCat;
    private int scrollOffset;
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();

    private interface WidgetFactory {
        AbstractWidget create(int y);
    }
    /** {@code h} lets a taller widget reserve its own space; the default is one row. */
    private record Opt(String key, WidgetFactory factory, int h) {
        Opt(String key, WidgetFactory factory) {
            this(key, factory, ROW_H);
        }
    }

    /** A titled block inside a category; matches the client config screen's grouping. */
    private static class Group {
        final String key;
        final List<Opt> opts;
        boolean collapsed;
        /** Last drawn y of the title bar, so a click can be matched to it. */
        int headerY = Integer.MIN_VALUE;
        Group(String key, List<Opt> opts) {
            this.key = key;
            this.opts = opts;
        }
    }

    private record Cat(String key, List<Group> groups) {
        static Cat plain(String key, List<Opt> opts) { return new Cat(key, List.of(new Group(null, opts))); }
    }

    private List<Cat> cats;

    public ServerConfigScreen(Screen lastScreen) {
        super(Component.translatable("screen.chatsphere.server_config.title"));
        this.lastScreen = lastScreen;
    }

    private void buildCats() {
        if (cats != null) return;
        cats = new ArrayList<>();

        List<Opt> chat = new ArrayList<>();
        chat.add(new Opt("config.chatsphere.anti_spam", y -> mkBool(y, "antiSpam", ModServerConfig.CONFIG.antiSpam)));
        chat.add(new Opt("config.chatsphere.strong_hint", y -> mkBool(y, "showStrongHint", ModServerConfig.CONFIG.showStrongHint)));
        chat.add(new Opt("config.chatsphere.enable_channels", y -> mkBool(y, "enableChannels", ModServerConfig.CONFIG.enableChannels)));
        chat.add(new Opt("config.chatsphere.max_chat_history",
            y -> mkIntBox(y, "maxChatHistory", safeGetStr(ModServerConfig.CONFIG.maxChatHistory, "50"), 50, 1000, 4)));
        chat.add(new Opt("config.chatsphere.max_command_messages",
            y -> mkIntBox(y, "maxCommandMessages", safeGetStr(ModServerConfig.CONFIG.maxCommandMessages, "500"), 50, 2000, 4)));

        List<Opt> sync = new ArrayList<>();
        sync.add(new Opt("config.chatsphere.sync_default_channel", y -> mkBool(y, "syncDefaultChannel", ModServerConfig.CONFIG.syncDefaultChannel)));
        sync.add(new Opt("config.chatsphere.channel_history", y -> mkBool(y, "channelHistoryEnabled", ModServerConfig.CONFIG.channelHistoryEnabled)));
        sync.add(new Opt("config.chatsphere.invite_history", y -> mkBool(y, "inviteHistoryEnabled", ModServerConfig.CONFIG.inviteHistoryEnabled)));

        List<Opt> explore = new ArrayList<>();
        explore.add(new Opt("config.chatsphere.explore_enabled", y -> mkBool(y, "exploreEnabled", ModServerConfig.CONFIG.exploreEnabled)));
        explore.add(new Opt("config.chatsphere.explore_min_members",
            y -> mkIntBox(y, "exploreMinMembers", safeGetStr(ModServerConfig.CONFIG.exploreMinMembers, "0"), 0, 100, 3)));

        List<Opt> banned = new ArrayList<>();
        banned.add(new Opt("config.chatsphere.banned_words", y -> {
            int boxW = Math.min(btnW * 3, Math.max(btnW, width - inputX - 10));
            EditBox box = new EditBox(font, inputX, y, boxW, BANNED_H, Component.literal(""));
            box.setValue(safeGetStr(ModServerConfig.CONFIG.bannedWords, ""));
            box.setMaxLength(10000);
            box.setResponder(val -> scheduleConfigSend("bannedWords", val));
            return box;
        }, BANNED_H + 8));

        List<Opt> voice = new ArrayList<>();
        voice.add(new Opt("config.chatsphere.voice_offline_enabled", y -> mkBool(y, "voiceOfflineStorage", ModServerConfig.CONFIG.voiceOfflineStorage)));
        voice.add(new Opt("config.chatsphere.voice_offline_max_age",
            y -> mkIntBox(y, "voiceOfflineMaxAgeHours", safeGetStr(ModServerConfig.CONFIG.voiceOfflineMaxAgeHours, "24"), 1, 168, 3)));
        voice.add(new Opt("config.chatsphere.voice_offline_max_per_player",
            y -> mkIntBox(y, "voiceOfflineMaxPerPlayer", safeGetStr(ModServerConfig.CONFIG.voiceOfflineMaxPerPlayer, "10"), 1, 50, 2)));
        voice.add(new Opt("config.chatsphere.voice_storage_max",
            y -> mkIntBox(y, "voiceStorageMax", safeGetStr(ModServerConfig.CONFIG.voiceStorageMax, "512"), 16, 10000, 5)));

        List<Opt> emoji = new ArrayList<>();
        emoji.add(new Opt("config.chatsphere.emoji_max_total",
            y -> mkIntBox(y, "emojiMaxTotal", safeGetStr(ModServerConfig.CONFIG.emojiMaxTotal, "100"), 1, 10000, 5)));

        List<Opt> backups = new ArrayList<>();
        backups.add(new Opt("config.chatsphere.backup_interval",
            y -> mkIntBox(y, "backupIntervalMinutes", safeGetStr(ModServerConfig.CONFIG.backupIntervalMinutes, "0"), 0, 1440, 4)));
        backups.add(new Opt("config.chatsphere.backup_keep",
            y -> mkIntBox(y, "backupKeepMax", safeGetStr(ModServerConfig.CONFIG.backupKeepMax, "1"), 1, 100, 3)));

        // Formerly separate tabs; they now share one page split into titled groups.
        List<Group> general = new ArrayList<>();
        general.add(new Group("config.chatsphere.group_chat", chat));
        general.add(new Group("config.chatsphere.group_sync", sync));
        general.add(new Group("config.chatsphere.group_explore", explore));
        general.add(new Group("config.chatsphere.group_voice", voice));
        general.add(new Group("config.chatsphere.group_emoji", emoji));
        general.add(new Group("config.chatsphere.group_backup", backups));
        // Words and the Discord bridge are both "what leaves this server", so they stay grouped
        general.add(new Group("config.chatsphere.group_safety", banned));
        cats.add(new Cat("config.chatsphere.general_cat", general));

        List<Opt> links = new ArrayList<>();
        links.add(new Opt("config.chatsphere.link_preview_enabled",
            y -> mkBool(y, "linkPreviewEnabled", ModServerConfig.CONFIG.linkPreviewEnabled)));
        links.add(new Opt("config.chatsphere.link_preview_domains", y -> {
            int boxW = Math.min(btnW * 3, Math.max(btnW, width - inputX - 10));
            EditBox box = new EditBox(font, inputX, y, boxW, DOMAIN_H, Component.literal(""));
            box.setValue(safeGetStr(ModServerConfig.CONFIG.linkPreviewAllowedDomains, ""));
            box.setMaxLength(2000);
            box.setResponder(val -> scheduleConfigSend("linkPreviewAllowedDomains", val));
            return box;
        }, DOMAIN_H + 8));

        List<Opt> images = new ArrayList<>();
        images.add(new Opt("config.chatsphere.chat_image_enabled",
            y -> mkBool(y, "chatImageEnabled", ModServerConfig.CONFIG.chatImageEnabled)));
        images.add(new Opt("config.chatsphere.chat_image_op",
            y -> mkBool(y, "chatImageUploadRequiresOp", ModServerConfig.CONFIG.chatImageUploadRequiresOp)));
        images.add(new Opt("config.chatsphere.chat_image_max_kb",
            y -> mkIntBox(y, "chatImageMaxKb", safeGetStr(ModServerConfig.CONFIG.chatImageMaxKb, "512"), 16, 4096, 4)));
        images.add(new Opt("config.chatsphere.chat_image_max_dim",
            y -> mkIntBox(y, "chatImageMaxDim", safeGetStr(ModServerConfig.CONFIG.chatImageMaxDim, "1280"), 64, 4096, 4)));
        images.add(new Opt("config.chatsphere.chat_image_max_pixels",
            y -> mkIntBox(y, "chatImageMaxPixels", safeGetStr(ModServerConfig.CONFIG.chatImageMaxPixels, "1500000"), 10000, 16000000, 8)));
        images.add(new Opt("config.chatsphere.chat_image_server_ttl",
            y -> mkIntBox(y, "chatImageServerTtlMinutes", safeGetStr(ModServerConfig.CONFIG.chatImageServerTtlMinutes, "1440"), 1, 1000000, 7)));
        images.add(new Opt("config.chatsphere.chat_image_client_ttl",
            y -> mkIntBox(y, "chatImageClientTtlMinutes", safeGetStr(ModServerConfig.CONFIG.chatImageClientTtlMinutes, "2880"), 1, 1000000, 7)));
        images.add(new Opt("config.chatsphere.chat_image_cooldown",
            y -> mkIntBox(y, "chatImageUploadCooldownSeconds", safeGetStr(ModServerConfig.CONFIG.chatImageUploadCooldownSeconds, "10"), 0, 600, 3)));
        images.add(new Opt("config.chatsphere.chat_image_max_per_player",
            y -> mkIntBox(y, "chatImageMaxPerPlayer", safeGetStr(ModServerConfig.CONFIG.chatImageMaxPerPlayer, "50"), 1, 1000000, 7)));

        // Link previews and chat images are the same question: what the chat may pull off the net
        List<Group> media = new ArrayList<>();
        media.add(new Group("config.chatsphere.group_links", links));
        media.add(new Group("config.chatsphere.group_images", images));
        cats.add(new Cat("config.chatsphere.media_cat", media));

        List<Opt> discord = new ArrayList<>();
        discord.add(new Opt("config.chatsphere.discord_enabled",
            y -> mkBool(y, "discordEnabled", ModServerConfig.CONFIG.discordEnabled)));
        discord.add(new Opt("config.chatsphere.discord_webhook",
            y -> mkStrBox(y, "discordWebhookUrl", safeGetStr(ModServerConfig.CONFIG.discordWebhookUrl, ""), 300)));
        discord.add(new Opt("config.chatsphere.discord_token",
            y -> mkStrBox(y, "discordBotToken", safeGetStr(ModServerConfig.CONFIG.discordBotToken, ""), 300)));
        discord.add(new Opt("config.chatsphere.discord_channel_id",
            y -> mkStrBox(y, "discordChannelId", safeGetStr(ModServerConfig.CONFIG.discordChannelId, ""), 32)));
        discord.add(new Opt("config.chatsphere.discord_mirror",
            y -> mkStrBox(y, "discordMirrorChannel", safeGetStr(ModServerConfig.CONFIG.discordMirrorChannel, "#general"), 64)));
        discord.add(new Opt("config.chatsphere.discord_inbound",
            y -> mkBool(y, "discordRelayInbound", ModServerConfig.CONFIG.discordRelayInbound)));
        discord.add(new Opt("config.chatsphere.discord_poll_seconds",
            y -> mkIntBox(y, "discordPollSeconds", safeGetStr(ModServerConfig.CONFIG.discordPollSeconds, "5"), 2, 600, 3)));
        cats.add(Cat.plain("config.chatsphere.discord", discord));
    }

    @Override
    protected void init() {
        buildCats();

        int totalTabW = 0;
        for (Cat c : cats) totalTabW += font.width(Component.translatable(c.key())) + TAB_PAD * 2 + 4;
        tabX = (width - totalTabW) / 2;

        optLabelX = 30;
        btnW = Math.min(80, width - optLabelX - 80);
        inputX = width - btnW - 40;

        scrollWidgets.clear();
        scrollOffset = Mth.clamp(scrollOffset, 0, calcMaxScroll());

        int y = CONTENT_Y - scrollOffset;
        for (Group grp : cats.get(selectedCat).groups()) {
            if (grp.key != null) y += GROUP_H;
            if (grp.collapsed) continue;
            for (Opt opt : grp.opts) {
                scrollWidgets.add(addRenderableWidget(opt.factory().create(y)));
                y += opt.h();
            }
        }

        addRenderableWidget(StyledButton.styledBuilder(
            CommonComponents.GUI_BACK,
            btn -> onClose()
        ).bounds(width / 2 - 100, height - 32, 200, 20)
            .style(StyledButton.Style.CANCEL)
            .tooltip(Component.translatable("screen.chatsphere.server_config.tip_back")).build());
    }

    private void switchCategory(int idx) {
        if (idx == selectedCat) return;
        selectedCat = idx;
        scrollOffset = 0;
        setFocused(null);
        clearWidgets();
        init();
    }

    private void sendConfigUpdate(String key, String value) {
        Minecraft mc = minecraft;
        if (mc == null || mc.getConnection() == null) {
            ModServerConfig.queuePendingUpdate(key, value);
            return;
        }
        PacketSender.toServer(ServerboundConfigUpdatePayload.ID,
                new ServerboundConfigUpdatePayload(key, value).toBuf());
    }

    private EditBox mkIntBox(int y, String fieldName, String initial, int min, int max, int maxLen) {
        EditBox box = new EditBox(font, inputX, y, btnW, 20, Component.literal(""));
        box.setValue(initial);
        box.setMaxLength(maxLen);
        box.setResponder(s -> {
            if (!s.matches("\\d*")) return;
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) sendConfigUpdate(fieldName, String.valueOf(v));
            } catch (NumberFormatException ignored) {}
        });
        return box;
    }

    /** Single-line field; edits go through the debounced sender. */
    private EditBox mkStrBox(int y, String fieldName, String initial, int maxLen) {
        int boxW = Math.min(btnW * 3, Math.max(btnW, width - inputX - 10));
        EditBox box = new EditBox(font, inputX, y, boxW, 20, Component.literal(""));
        box.setValue(initial);
        box.setMaxLength(maxLen);
        boolean secret = ModServerConfig.isSecret(fieldName);
        box.setResponder(val -> {
            // Secrets are write-only: an empty box must not wipe a stored credential
            if (secret && val.isEmpty()) return;
            scheduleConfigSend(fieldName, val);
        });
        if (secret) {
            box.setTooltip(Tooltip.create(Component.translatable("config.chatsphere.discord.secret_hint")));
        }
        return box;
    }

    private AbstractWidget mkBool(int y, String fieldName, CfgValue.Bool cfg) {
        if (Theme.originalStyle()) {
            return originalBoolButton(y, fieldName, cfg);
        }
        return new UiToggle(inputX, y, btnW, 20, safeGetBool(cfg), v -> {
            ModServerConfig.safeSetBool(cfg, fieldName, v);
            sendConfigUpdate(fieldName, String.valueOf(v));
        });
    }

    /** Original corner style shows booleans as a labelled button; still a StyledButton so the role colour carries the state. */
    private AbstractWidget originalBoolButton(int y, String fieldName, CfgValue.Bool cfg) {
        boolean on = safeGetBool(cfg);
        return StyledButton.styledBuilder(boolText(on), btn -> {
            boolean next = !safeGetBool(cfg);
            ModServerConfig.safeSetBool(cfg, fieldName, next);
            sendConfigUpdate(fieldName, String.valueOf(next));
            btn.setMessage(boolText(next));
            ((StyledButton) btn).setStyle(next ? StyledButton.Style.TOGGLE_ON : StyledButton.Style.TOGGLE_OFF);
        }).bounds(inputX, y, btnW, 20)
                .style(on ? StyledButton.Style.TOGGLE_ON : StyledButton.Style.TOGGLE_OFF)
                .build();
    }

    private static Component boolText(boolean on) {
        return Component.translatable(on ? "screen.chatsphere.config.enabled" : "screen.chatsphere.config.disabled");
    }

    private static boolean safeGetBool(CfgValue.Bool cfg) {
        try {
            return cfg.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static String safeGetStr(CfgValue.Str cfg, String def) {
        try {
            return cfg.get() != null ? cfg.get() : def;
        } catch (RuntimeException e) {
            return def;
        }
    }

    private static String safeGetStr(CfgValue.Int cfg, String def) {
        try {
            return String.valueOf(cfg.get());
        } catch (RuntimeException e) {
            return def;
        }
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
            int w = font.width(label) + TAB_PAD * 2;
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
        for (Group grp : cats.get(selectedCat).groups()) {
            if (grp.key != null) {
                grp.headerY = y;
                if (y >= CONTENT_Y && y + GROUP_H <= contentBottom) {
                    g.fill(10, y, width - 10, y + 1, Theme.sectionLine());
                    String arrow = grp.collapsed ? "\u25B8" : "\u25BE";
                    g.drawString(font, arrow, optLabelX - 14, y + 6, Theme.textDim(), false);
                    g.drawString(font, Component.translatable(grp.key), optLabelX,
                            y + (GROUP_H - 8) / 2, Theme.textDim(), false);
                }
                y += GROUP_H;
            }
            if (grp.collapsed) continue;
            for (Opt opt : grp.opts) {
                if (y >= CONTENT_Y && y + opt.h() <= contentBottom && !opt.key().isEmpty()) {
                    g.drawString(font, Component.translatable(opt.key()), optLabelX,
                            y + (opt.h() - 8) / 2, Theme.text(), false);
                }
                y += opt.h();
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int cx = tabX;
            for (int i = 0; i < cats.size(); i++) {
                int w = font.width(Component.translatable(cats.get(i).key())) + TAB_PAD * 2;
                if (mouseX >= cx && mouseX <= cx + w && mouseY >= TAB_Y && mouseY <= TAB_Y + 22) {
                    switchCategory(i);
                    return true;
                }
                cx += w + 6;
            }
            // Group titles toggle on the whole header strip, so the tiny arrow is not the only target
            for (Group grp : cats.get(selectedCat).groups()) {
                if (grp.key == null || grp.headerY == Integer.MIN_VALUE) continue;
                if (mouseY >= grp.headerY && mouseY < grp.headerY + GROUP_H) {
                    grp.collapsed = !grp.collapsed;
                    setFocused(null);
                    clearWidgets();
                    init();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private long scheduledSendAt = -1;
    private String pendingKey;
    private String pendingValue;

    /** Debounce per-keystroke updates to avoid packet floods. */
    private void scheduleConfigSend(String key, String value) {
        pendingKey = key;
        pendingValue = value;
        scheduledSendAt = System.currentTimeMillis() + 400;
    }

    @Override
    public void tick() {
        super.tick();
        if (scheduledSendAt > 0 && System.currentTimeMillis() >= scheduledSendAt) {
            scheduledSendAt = -1;
            if (pendingKey != null) {
                sendConfigUpdate(pendingKey, pendingValue);
                pendingKey = null;
            }
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(lastScreen);
    }

    private int calcMaxScroll() {
        int total = 0;
        for (Group grp : cats.get(selectedCat).groups()) {
            if (grp.key != null) total += GROUP_H;
            if (!grp.collapsed) {
                for (Opt opt : grp.opts) total += opt.h();
            }
        }
        return Math.max(0, CONTENT_Y + total - (height - 48));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int maxScroll = calcMaxScroll();
        if (maxScroll <= 0) return false;
        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int y = CONTENT_Y - scrollOffset;
        int i = 0;
        for (Group grp : cats.get(selectedCat).groups()) {
            if (grp.key != null) y += GROUP_H;
            if (grp.collapsed) continue;
            for (int n = 0; n < grp.opts.size() && i < scrollWidgets.size(); n++, i++) {
                scrollWidgets.get(i).setY(y);
                y += grp.opts.get(n).h();
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

