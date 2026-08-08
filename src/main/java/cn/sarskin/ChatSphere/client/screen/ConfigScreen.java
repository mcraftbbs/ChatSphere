package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.PlayerSkinCache;
import cn.sarskin.ChatSphere.compat.ncr.NCRCompat;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.Ui;
import cn.sarskin.ChatSphere.client.ui.UiToggle;
import cn.sarskin.ChatSphere.network.ServerboundConfigUpdatePayload;
import cn.sarskin.ChatSphere.network.ServerboundPermissionCheckPayload;
import cn.sarskin.ChatSphere.style.CustomTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static cn.sarskin.ChatSphere.config.ModClientConfig.CONFIG_SPEC;

public class ConfigScreen extends Screen {
    private final Screen lastScreen;
    private String pendingOpMsg;

    private static final int ROW_H = 28;
    private static final int GROUP_H = 22;
    private static final int TAB_Y = 38;
    private static final int CONTENT_Y = 68;
    private static final int TAB_PAD = 6;

    private int tabX, optLabelX, inputX, btnW, tabW;
    private int selectedCat;
    private int scrollOffset;
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();

    private interface WidgetFactory {
        AbstractWidget create(int y);
    }

    private record Opt(String key, WidgetFactory factory, java.util.function.Supplier<String> previewColor) {
        Opt(String key, WidgetFactory factory) { this(key, factory, null); }
    }

    private static class Group {
        final String key;
        final List<Opt> opts;
        boolean collapsed;
        Group(String key, List<Opt> opts) {
            this.key = key;
            this.opts = opts;
        }
    }

    private record Cat(String key, List<Group> groups) {
        static Cat plain(String key, List<Opt> opts) { return new Cat(key, List.of(new Group(null, opts))); }
    }

    private List<Cat> cats;

    public ConfigScreen() {
        super(Component.translatable("screen.chatsphere.config.title"));
        this.lastScreen = null;
    }

    public ConfigScreen(Screen lastScreen) {
        super(Component.translatable("screen.chatsphere.config.title"));
        this.lastScreen = lastScreen;
    }

    private void buildCats() {
        if (cats != null) return;
        cats = new ArrayList<>();

        List<Opt> ui = new ArrayList<>();
        ui.add(new Opt("config.chatsphere.show_timestamp", y -> mkBool(y, ModClientConfig.CONFIG.showTimestamp)));
        ui.add(new Opt("config.chatsphere.show_sender_name", y -> mkBool(y, ModClientConfig.CONFIG.showSenderName)));
        ui.add(new Opt("config.chatsphere.show_avatar", y -> mkBool(y, ModClientConfig.CONFIG.showAvatar)));
        ui.add(new Opt("config.chatsphere.theme", y -> mkBool(y, ModClientConfig.CONFIG.themeDark)));
        ui.add(new Opt("config.chatsphere.background_blur", y -> mkBool(y, ModClientConfig.CONFIG.backgroundBlur)));
        ui.add(new Opt("config.chatsphere.popup_border", y -> mkBool(y, ModClientConfig.CONFIG.popupBorder)));
        ui.add(new Opt("config.chatsphere.strong_hint", y -> mkServerBool(y, "showStrongHint", ModServerConfig.CONFIG.showStrongHint)));
        cats.add(Cat.plain("config.chatsphere.ui", ui));

        cats.add(new Cat("config.chatsphere.corner_style_cat", List.of()));

        List<Opt> behavior = new ArrayList<>();
        behavior.add(new Opt("config.chatsphere.anti_spam", y -> mkServerBool(y, "antiSpam", ModServerConfig.CONFIG.antiSpam)));
        behavior.add(new Opt("config.chatsphere.preserve_input", y -> mkBool(y, ModClientConfig.CONFIG.preserveInput)));
        behavior.add(new Opt("config.chatsphere.max_chat_history",
            y -> mkIntBox(y, safeGetStr(ModServerConfig.CONFIG.maxChatHistory, "50"), 50, 1000, 4, v -> sendConfigUpdate("maxChatHistory", String.valueOf(v))), null));
        behavior.add(new Opt("config.chatsphere.max_command_messages",
            y -> mkIntBox(y, safeGetStr(ModServerConfig.CONFIG.maxCommandMessages, "500"), 50, 2000, 4, v -> sendConfigUpdate("maxCommandMessages", String.valueOf(v))), null));
        behavior.add(new Opt("config.chatsphere.scroll_history_limit",
            y -> mkIntBox(y, String.valueOf(ModClientConfig.CONFIG.scrollHistoryLimit.get()), 50, 1000, 3, v -> { ModClientConfig.CONFIG.scrollHistoryLimit.set(v); CONFIG_SPEC.save(); }), null));
        behavior.add(new Opt("config.chatsphere.command_history_limit",
            y -> mkIntBox(y, String.valueOf(ModClientConfig.CONFIG.commandHistoryLimit.get()), 10, 500, 3, v -> { ModClientConfig.CONFIG.commandHistoryLimit.set(v); CONFIG_SPEC.save(); }), null));
        cats.add(Cat.plain("config.chatsphere.behavior", behavior));

        List<Opt> sound = new ArrayList<>();
        sound.add(new Opt("config.chatsphere.notification_sound", y -> mkBool(y, ModClientConfig.CONFIG.notificationSound)));
        sound.add(new Opt("config.chatsphere.notification_badge", y -> mkBool(y, ModClientConfig.CONFIG.notificationBadge)));
        sound.add(new Opt("config.chatsphere.sound_mention", y -> mkBool(y, ModClientConfig.CONFIG.soundMention)));
        sound.add(new Opt("config.chatsphere.sound_whisper", y -> mkBool(y, ModClientConfig.CONFIG.soundWhisper)));
        sound.add(new Opt("config.chatsphere.sound_system", y -> mkBool(y, ModClientConfig.CONFIG.soundSystem)));
        sound.add(new Opt("config.chatsphere.sound_public", y -> mkBool(y, ModClientConfig.CONFIG.soundPublic)));
        cats.add(Cat.plain("config.chatsphere.sound_settings", sound));

        List<Opt> bubble = new ArrayList<>();
        bubble.add(new Opt("config.chatsphere.bubble_color_own",
            y -> mkHexBox(y, String.format("#%06X", Theme.bubbleOwnFallback() & 0xFFFFFF), s -> {
                ModClientConfig.CONFIG.bubbleColorOwn.set(s); CONFIG_SPEC.save();
                int argb = ModClientConfig.parseHexColor(s, 0xFF8888FF);
                CustomTheme.INSTANCE.syncValues(null, Map.of("bubbleOwn", argb), Map.of("bubbleOwn", argb));
            }),
            ModClientConfig.CONFIG.bubbleColorOwn::get));
        bubble.add(new Opt("config.chatsphere.bubble_color_other",
            y -> mkHexBox(y, String.format("#%06X", Theme.bubbleOtherFallback() & 0xFFFFFF), s -> {
                ModClientConfig.CONFIG.bubbleColorOther.set(s); CONFIG_SPEC.save();
                int argb = ModClientConfig.parseHexColor(s, 0xFF8888FF);
                CustomTheme.INSTANCE.syncValues(null, Map.of("bubbleOther", argb), Map.of("bubbleOther", argb));
            }),
            ModClientConfig.CONFIG.bubbleColorOther::get));
        cats.add(Cat.plain("config.chatsphere.bubble", bubble));

        List<Opt> skin = new ArrayList<>();
        skin.add(new Opt("config.chatsphere.custom_skin_api_url",
            y -> mkStrBox(y, ModClientConfig.CONFIG.customSkinApiUrl.get(), s -> { ModClientConfig.CONFIG.customSkinApiUrl.set(s); CONFIG_SPEC.save(); }), null));
        skin.add(new Opt("config.chatsphere.avatar_cache_enabled", y -> mkBool(y, ModClientConfig.CONFIG.avatarCacheEnabled)));
        skin.add(new Opt("config.chatsphere.refresh_skin_cache", y ->
            Button.builder(Component.translatable("config.chatsphere.refresh_skin_cache"), btn -> {
                    btn.active = false;
                    PlayerSkinCache.refreshCache();
                })
                .bounds(inputX, y, btnW, 20)
                .tooltip(Tooltip.create(Component.translatable("config.chatsphere.refresh_skin_cache.tip")))
                .build(), null));
        cats.add(Cat.plain("config.chatsphere.skin", skin));

        List<Group> adv = new ArrayList<>();
        adv.add(new Group("config.chatsphere.channels", List.of(
            new Opt("config.chatsphere.enable_channels", y -> mkServerBool(y, "enableChannels", ModServerConfig.CONFIG.enableChannels)))));
        adv.add(new Group("config.chatsphere.network", List.of(
            new Opt("config.chatsphere.allow_vanilla_connection", y -> mkBool(y, ModClientConfig.CONFIG.allowVanillaConnection)))));
        adv.add(new Group("config.chatsphere.voice_cache", List.of(
            new Opt("config.chatsphere.voice_cache_enabled", y -> mkBool(y, ModClientConfig.CONFIG.voiceCacheEnabled)),
            new Opt("config.chatsphere.voice_cache_max_age",
                y -> mkIntBox(y, String.valueOf(ModClientConfig.CONFIG.voiceCacheMaxAgeHours.get()), 1, 168, 3, v -> { ModClientConfig.CONFIG.voiceCacheMaxAgeHours.set(v); CONFIG_SPEC.save(); }), null),
            new Opt("config.chatsphere.voice_cache_max_mb",
                y -> mkIntBox(y, String.valueOf(ModClientConfig.CONFIG.voiceCacheMaxMB.get()), 16, 8192, 5, v -> { ModClientConfig.CONFIG.voiceCacheMaxMB.set(v); CONFIG_SPEC.save(); }), null))));
        if (NCRCompat.isNCRLoaded()) {
            adv.add(new Group("config.chatsphere.ncr", List.of(
                new Opt("config.chatsphere.ncr_compat", y -> mkBool(y, ModClientConfig.CONFIG.ncrCompat)),
                new Opt("config.chatsphere.ncr_safety", y -> {
                    Component label = NCRCompat.getSafetyStatusComponent();
                    return Button.builder(label, btn -> {})
                            .bounds(inputX, y, btnW, 20).build();
                }),
                new Opt("config.chatsphere.ncr_prevents_reports", y -> mkServerBool(y, "preventsChatReports", ModServerConfig.CONFIG.preventsChatReports)))));
        }
        cats.add(new Cat("config.chatsphere.advanced", adv));

        List<Opt> custTheme = new ArrayList<>();
        custTheme.add(new Opt("config.chatsphere.custom_theme_active", y -> mkThemeToggle(y),
            () -> cn.sarskin.ChatSphere.style.CustomTheme.INSTANCE.isActive()
                    && cn.sarskin.ChatSphere.style.CustomTheme.INSTANCE.error() == null
                    ? "#44FF44" : "#FF4444"));
        custTheme.add(new Opt("config.chatsphere.custom_theme_gallery", y -> Button.builder(
                Component.translatable("config.chatsphere.custom_theme_gallery"),
                btn -> {
                    if (minecraft != null) minecraft.setScreen(new ThemeGalleryScreen(this));
                })
                .bounds(inputX, y, btnW, 20).build(), null));
        cats.add(Cat.plain("config.chatsphere.custom_theme", custTheme));
    }

    private AbstractWidget mkThemeToggle(int y) {
        var cfg = ModClientConfig.CONFIG.customThemeActive;
        if (Theme.originalStyle()) {
            return Button.builder(
                    Component.translatable(cfg.get() ? "screen.chatsphere.config.enabled" : "screen.chatsphere.config.disabled"),
                    btn -> {
                        applyThemeToggle(!cfg.get());
                        btn.setMessage(Component.translatable(
                                cfg.get() ? "screen.chatsphere.config.enabled" : "screen.chatsphere.config.disabled"));
                    })
                    .bounds(inputX, y, btnW, 20)
                    .build();
        }
        return new UiToggle(inputX, y, btnW, 20, cfg.get(), this::applyThemeToggle);
    }

    private void applyThemeToggle(boolean next) {
        ModClientConfig.CONFIG.customThemeActive.set(next);
        CONFIG_SPEC.save();
        if (next) {
            String f = ModClientConfig.CONFIG.customThemeFile.get();
            if (f != null && !f.isEmpty()) cn.sarskin.ChatSphere.style.CustomTheme.INSTANCE.load(f);
        } else {
            cn.sarskin.ChatSphere.style.CustomTheme.INSTANCE.unload();
        }
    }

    private boolean isCornerStyleCat(int idx) {
        return idx >= 0 && idx < cats.size() && cats.get(idx).key().equals("config.chatsphere.corner_style_cat");
    }

    private int[] cornerCardLayout() {
        int gap = 24;
        int margin = 16;
        int availW = width - margin * 2;
        int cardW = Math.max(90, Math.min(150, (availW - gap * 3) / 4));
        int avail = (height - 48) - CONTENT_Y;
        int cardH = Math.max(80, Math.min(152, avail - 8));
        int totalW = cardW * 4 + gap * 3;
        int startX = (width - totalW) / 2;
        int cardY = CONTENT_Y + Math.max(6, (avail - cardH) / 2);
        return new int[] { cardW, cardH, gap, startX, cardY };
    }

    private void drawCornerCards(GuiGraphics g, int mouseX, int mouseY) {
        int style = Theme.cornerStyle();
        int[] l = cornerCardLayout();
        int cardW = l[0], cardH = l[1], gap = l[2], startX = l[3], cardY = l[4];
        for (int i = 0; i < 4; i++) {
            int cx = startX + i * (cardW + gap);
            boolean sel = i == style;
            boolean hover = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cardY && mouseY <= cardY + cardH;
            Ui.fillRoundedRect(g, cx, cardY, cardW, cardH, 6, Theme.panelBg());
            g.renderOutline(cx, cardY, cardW, cardH, sel ? Theme.accent() : (hover ? Theme.popupOutline() : Theme.popupOutline2()));
            if (sel) {
                Ui.fillRoundedRect(g, cx, cardY, cardW, 20, 6, 0x336666DD);
            }
            int prevY = cardY + 24;
            int prevH = Math.max(20, cardH - 70);
            drawCornerPreview(g, cx + 10, prevY, cardW - 20, prevH, i);
            Component name = Component.translatable("config.chatsphere.corner_style." + i);
            int nameY = prevY + prevH + 5;
            g.drawString(font, name, cx + (cardW - font.width(name)) / 2, nameY, Theme.text(), false);
            int btnW = Math.min(64, cardW - 12), btnH = 14;
            int bx = cx + (cardW - btnW) / 2;
            int by = cardY + cardH - 22;
            Ui.fillRoundedRect(g, bx, by, btnW, btnH, 3, sel ? Theme.accent() : Theme.slotBg());
            Component pick = Component.translatable("config.chatsphere.corner_pick");
            g.drawString(font, pick, bx + (btnW - font.width(pick)) / 2, by + 3,
                    sel ? 0xFFFFFFFF : Theme.textDim(), false);
        }
    }

    private void drawCornerPreview(GuiGraphics g, int x, int y, int w, int h, int style) {
        g.fill(x, y, x + w, y + h, 0xFFF4F4FA);
        int pw = Math.min(102, w - 16);
        int ph = Math.min(58, h - 16);
        double s = pw / 102.0;
        int px = x + (w - pw) / 2;
        int py = y + Math.max(2, (h - ph) / 2);
        if (style == 3) {
            drawStreamPreview(g, px, py, pw, ph, s);
            return;
        }
        g.fill(x + (int) (8 * s), y + (int) (2 * s), x + w - (int) (8 * s), y + (int) (3 * s), 0x668888CC);
        g.fill(x + (int) (8 * s), y + h - (int) (3 * s), x + w - (int) (8 * s), y + h - (int) (2 * s), 0x668888CC);
        Ui.fillRoundedRectStyle(g, style, px, py, pw, ph, (int) (8 * s), 0xEB2A2A4E);
        g.fill(px + (int) (8 * s), py + (int) (8 * s), px + (int) (32 * s), py + (int) (14 * s), 0xFFDDDDEE);
        g.fill(px + (int) (8 * s), py + (int) (20 * s), px + (int) (78 * s), py + (int) (26 * s), 0xFFDDDDEE);
        g.fill(px + (int) (8 * s), py + (int) (32 * s), px + (int) (58 * s), py + (int) (38 * s), 0xFFDDDDEE);
        Ui.fillRoundedRectStyle(g, style, px + pw - (int) (42 * s), py + ph - (int) (15 * s),
                (int) (34 * s), (int) (10 * s), (int) (4 * s), 0xFF8888FF);
        g.fill(px + pw - (int) (34 * s), py + ph - (int) (11 * s),
                px + pw - (int) (22 * s), py + ph - (int) (10 * s), 0xFFFFFFFF);
    }

    /** Stream-style preview: left icon rail + flat message rows (avatar, name, timestamp, text). */
    private void drawStreamPreview(GuiGraphics g, int x, int y, int w, int h, double s) {
        int railW = (int) (13 * s);
        g.fill(x, y, x + railW, y + h, 0xFF1E1F22);
        for (int i = 0; i < 3; i++) {
            int iy = y + (int) (7 * s) + i * (int) (10 * s);
            int is = (int) (6 * s);
            g.fill(x + (railW - is) / 2, iy, x + (railW + is) / 2, iy + is, i == 0 ? 0xFFB5BAC1 : 0xFF6A6F78);
        }
        int cx = x + railW + (int) (6 * s);
        int right = x + w - (int) (2 * s);
        int avatarS = (int) (7 * s);
        int nameH = (int) (4 * s);
        int textH = (int) (4 * s);
        int nameW = (int) (30 * s);
        int textW = (int) (46 * s);
        int[] rows = { (int) (3 * s), (int) (12 * s), (int) (21 * s), (int) (30 * s) };
        for (int r = 0; r < 4; r++) {
            int ry = y + rows[r];
            int bx = cx;
            boolean withAvatar = r % 2 == 0;
            if (withAvatar) {
                g.fill(bx, ry, bx + avatarS, ry + avatarS, 0xFFAAAAFF);
                bx += avatarS + (int) (4 * s);
            }
            if (withAvatar) {
                g.fill(bx, ry, bx + nameW, ry + nameH, 0xFFDDDDEE);
                g.fill(right - (int) (10 * s), ry, right, ry + (int) (3 * s), 0xFFB8BCC8);
            }
            g.fill(bx, ry + (int) (5 * s), bx + textW, ry + (int) (5 * s) + textH, 0xFFDDDDEE);
        }
    }

    @Override
    protected void init() {
        buildCats();

        int totalTabW = 0;
        for (Cat c : cats) totalTabW += font.width(Component.translatable(c.key())) + TAB_PAD * 2 + 4;
        tabX = (width - totalTabW) / 2;
        tabW = totalTabW / cats.size();

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
                y += ROW_H;
            }
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> onClose())
            .bounds(width / 2 - 100, height - 32, 200, 20)
            .tooltip(Tooltip.create(Component.translatable("screen.chatsphere.config.tip_done"))).build());

        addRenderableWidget(Button.builder(
            Component.translatable("screen.chatsphere.config.server_config"),
            btn -> tryOpenServerConfig()
        ).bounds(width / 2 + 106, height - 32, 100, 20)
            .tooltip(Tooltip.create(Component.translatable("screen.chatsphere.config.tip_server_config"))).build());
    }

    private void switchCategory(int idx) {
        if (idx == selectedCat) return;
        selectedCat = idx;
        scrollOffset = 0;
        setFocused(null);
        clearWidgets();
        init();
    }

    private EditBox mkHexBox(int y, String initial, Consumer<String> onChange) {
        EditBox box = new EditBox(font, inputX, y, btnW, 20, Component.literal(""));
        box.setValue(initial);
        box.setMaxLength(7);
        box.setResponder(s -> {
            if (!s.matches("#?[0-9a-fA-F]{0,6}")) return;
            if (s.length() == 6 && !s.startsWith("#")) {
                box.setValue("#" + s);
                onChange.accept("#" + s);
            } else if (s.length() == 7) {
                onChange.accept(s);
            }
        });
        return box;
    }

    private EditBox mkIntBox(int y, String initial, int min, int max, int maxLen, Consumer<Integer> onChange) {
        EditBox box = new EditBox(font, inputX, y, btnW, 20, Component.literal(""));
        box.setValue(initial);
        box.setMaxLength(maxLen);
        box.setResponder(s -> {
            if (!s.matches("\\d*")) return;
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) onChange.accept(v);
            } catch (NumberFormatException ignored) {}
        });
        return box;
    }

    private EditBox mkStrBox(int y, String initial, Consumer<String> onChange) {
        EditBox box = new EditBox(font, inputX, y, btnW, 20, Component.literal(""));
        box.setMaxLength(512);
        box.setValue(initial);
        box.setResponder(onChange::accept);
        return box;
    }

    private void drawColorPreview(GuiGraphics g, int y, String hex) {
        int color = ModClientConfig.parseHexColor(hex, Theme.textDim());
        int px = inputX - 22;
        g.fill(px, y + 4, px + 12, y + 16, Theme.previewSwatchBg());
        g.fill(px + 1, y + 5, px + 11, y + 15, color);
    }

    private AbstractWidget mkBool(int y, ModConfigSpec.BooleanValue cfg) {
        if (Theme.originalStyle()) {
            return Button.builder(
                    Component.translatable(cfg.get() ? "screen.chatsphere.config.enabled" : "screen.chatsphere.config.disabled"),
                    btn -> {
                        cfg.set(!cfg.get());
                        CONFIG_SPEC.save();
                        btn.setMessage(Component.translatable(
                                cfg.get() ? "screen.chatsphere.config.enabled" : "screen.chatsphere.config.disabled"));
                    })
                    .bounds(inputX, y, btnW, 20)
                    .build();
        }
        return new UiToggle(inputX, y, btnW, 20, cfg.get(), v -> {
            cfg.set(v);
            CONFIG_SPEC.save();
        });
    }

    private void sendConfigUpdate(String key, String value) {
        Minecraft mc = minecraft;
        if (mc == null || mc.getConnection() == null) return;
        mc.getConnection().getConnection().send(
            new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new ServerboundConfigUpdatePayload(key, value)));
    }

    private AbstractWidget mkServerBool(int y, String fieldName, ModConfigSpec.BooleanValue cfg) {
        if (Theme.originalStyle()) {
            return Button.builder(
                    Component.translatable(safeGetBool(cfg) ? "screen.chatsphere.config.enabled" : "screen.chatsphere.config.disabled"),
                    btn -> {
                        boolean next = !safeGetBool(cfg);
                        ModServerConfig.safeSetBool(cfg, fieldName, next);
                        sendConfigUpdate(fieldName, String.valueOf(next));
                        btn.setMessage(Component.translatable(
                                next ? "screen.chatsphere.config.enabled" : "screen.chatsphere.config.disabled"));
                    })
                    .bounds(inputX, y, btnW, 20)
                    .build();
        }
        return new UiToggle(inputX, y, btnW, 20, safeGetBool(cfg), v -> {
            ModServerConfig.safeSetBool(cfg, fieldName, v);
            sendConfigUpdate(fieldName, String.valueOf(v));
        });
    }

    private static boolean safeGetBool(ModConfigSpec.BooleanValue cfg) {
        try {
            return cfg.get();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static String safeGetStr(ModConfigSpec.ConfigValue<?> cfg, String def) {
        try {
            Object val = cfg.get();
            return val != null ? val.toString() : def;
        } catch (IllegalStateException e) {
            return def;
        }
    }

    private void tryOpenServerConfig() {
        Minecraft mc = minecraft;
        if (mc == null) return;
        if (mc.isSingleplayer()) {
            mc.setScreen(new ServerConfigScreen(this));
            return;
        }
        if (mc.getConnection() != null) {
            mc.getConnection().getConnection().send(
                new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                    new ServerboundPermissionCheckPayload("SERVER_CONFIG")));
            pendingOpMsg = "chatsphere.server_config.pending_op";
        }
    }

    public void onPermissionResponse(String scope, boolean allowed) {
        pendingOpMsg = null;
        if (minecraft == null) return;
        if (!"SERVER_CONFIG".equals(scope)) return;
        if (allowed) {
            minecraft.setScreen(new ServerConfigScreen(this));
        } else {
            minecraft.player.displayClientMessage(
                Component.translatable("chatsphere.server_config.no_op"), false);
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

        if (pendingOpMsg != null) {
            Component msg = Component.translatable(pendingOpMsg);
            g.drawString(font, msg, width / 2 - font.width(msg) / 2, height / 2, Theme.textDim(), false);
        }

        if (isCornerStyleCat(selectedCat)) {
            drawCornerCards(g, mouseX, mouseY);
            return;
        }
        int y = CONTENT_Y - scrollOffset;
        for (Group grp : cats.get(selectedCat).groups()) {
            if (grp.key != null) {
                if (y >= CONTENT_Y && y + GROUP_H <= contentBottom) {
                    g.fill(10, y, width - 10, y + 1, Theme.sectionLine());
                    String arrow = grp.collapsed ? "\u25B8" : "\u25BE";
                    g.drawString(font, arrow, optLabelX - 14, y + 6, Theme.textDim(), false);
                    g.drawString(font, Component.translatable(grp.key), optLabelX, y + 6, Theme.textDim(), false);
                }
                y += GROUP_H;
            }
            if (grp.collapsed) continue;
            for (Opt opt : grp.opts) {
                if (y >= CONTENT_Y && y + ROW_H <= contentBottom) {
                    g.drawString(font, Component.translatable(opt.key()), optLabelX, y + 6, Theme.text(), false);
                    if (opt.previewColor() != null)
                        drawColorPreview(g, y, opt.previewColor().get());
                }
                y += ROW_H;
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
            if (isCornerStyleCat(selectedCat)) {
                int style = Theme.cornerStyle();
                int[] l = cornerCardLayout();
                int cardW = l[0], cardH = l[1], gap = l[2], startX = l[3], cardY = l[4];
                for (int i = 0; i < 4; i++) {
                    int cx2 = startX + i * (cardW + gap);
                    if (mouseX >= cx2 && mouseX <= cx2 + cardW && mouseY >= cardY && mouseY <= cardY + cardH) {
                        ModClientConfig.CONFIG.uiCornerStyle.set(i);
                        CONFIG_SPEC.save();
                        if (CustomTheme.INSTANCE.isActive()) {
                            // Keep the active theme (preset or custom): only switch the corner
                            // style so any other live adjustments (radius, blur, colors) persist.
                            CustomTheme.INSTANCE.syncValues(Map.of("uiCornerStyle", i), null, null);
                        } else {
                            String preset = CustomTheme.PRESETS[i] + CustomTheme.EXT;
                            if (CustomTheme.INSTANCE.load(preset)) {
                                ModClientConfig.CONFIG.customThemeFile.set(preset);
                                ModClientConfig.CONFIG.customThemeActive.set(true);
                                CONFIG_SPEC.save();
                            }
                        }
                        return true;
                    }
                }
            }
            int y = CONTENT_Y - scrollOffset;
            for (Group grp : cats.get(selectedCat).groups()) {
                if (grp.key != null) {
                    if (mouseY >= y && mouseY < y + GROUP_H && mouseX >= 10 && mouseX < width - 10) {
                        grp.collapsed = !grp.collapsed;
                        scrollOffset = Math.min(scrollOffset, calcMaxScroll());
                        clearWidgets();
                        init();
                        return true;
                    }
                    y += GROUP_H;
                }
                if (!grp.collapsed) y += grp.opts.size() * ROW_H;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        CONFIG_SPEC.save();
        if (minecraft != null) minecraft.setScreen(lastScreen);
    }

    private int calcMaxScroll() {
        int total = 0;
        for (Group grp : cats.get(selectedCat).groups()) {
            if (grp.key != null) total += GROUP_H;
            if (!grp.collapsed) total += grp.opts.size() * ROW_H;
        }
        return Math.max(0, CONTENT_Y + total - (height - 48));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = calcMaxScroll();
        if (maxScroll <= 0) return false;
        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int y = CONTENT_Y - scrollOffset;
        int wi = 0;
        for (Group grp : cats.get(selectedCat).groups()) {
            if (grp.key != null) y += GROUP_H;
            if (grp.collapsed) continue;
            for (int i = 0; i < grp.opts.size() && wi < scrollWidgets.size(); i++) {
                scrollWidgets.get(wi).setY(y);
                wi++;
                y += ROW_H;
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
