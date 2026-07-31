package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.UiToggle;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.network.ServerboundConfigUpdatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;

public class ServerConfigScreen extends Screen {
    private final Screen lastScreen;

    private static final int ROW_H = 28;
    private static final int TAB_Y = 38;
    private static final int CONTENT_Y = 68;
    private static final int TAB_PAD = 6;

    private int tabX, optLabelX, inputX, btnW;
    private int selectedCat;
    private int scrollOffset;
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();

    private interface WidgetFactory {
        AbstractWidget create(int y);
    }
    private record Opt(String key, WidgetFactory factory) {}
    private record Cat(String key, List<Opt> opts) {}

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
        chat.add(new Opt("config.chatsphere.backup_interval",
            y -> mkIntBox(y, "backupIntervalMinutes", safeGetStr(ModServerConfig.CONFIG.backupIntervalMinutes, "0"), 0, 1440, 4)));
        chat.add(new Opt("config.chatsphere.backup_keep",
            y -> mkIntBox(y, "backupKeepMax", safeGetStr(ModServerConfig.CONFIG.backupKeepMax, "1"), 1, 100, 3)));
        cats.add(new Cat("config.chatsphere.behavior", chat));

        List<Opt> sync = new ArrayList<>();
        sync.add(new Opt("config.chatsphere.sync_default_channel", y -> mkBool(y, "syncDefaultChannel", ModServerConfig.CONFIG.syncDefaultChannel)));
        sync.add(new Opt("config.chatsphere.channel_history", y -> mkBool(y, "channelHistoryEnabled", ModServerConfig.CONFIG.channelHistoryEnabled)));
        cats.add(new Cat("config.chatsphere.sync", sync));

        List<Opt> explore = new ArrayList<>();
        explore.add(new Opt("config.chatsphere.explore_enabled", y -> mkBool(y, "exploreEnabled", ModServerConfig.CONFIG.exploreEnabled)));
        explore.add(new Opt("config.chatsphere.explore_min_members",
            y -> mkIntBox(y, "exploreMinMembers", safeGetStr(ModServerConfig.CONFIG.exploreMinMembers, "0"), 0, 100, 3)));
        cats.add(new Cat("config.chatsphere.explore", explore));

        List<Opt> banned = new ArrayList<>();
        banned.add(new Opt("config.chatsphere.banned_words", y -> {
            int boxW = Math.min(btnW * 3, Math.max(btnW, width - inputX - 10));
            EditBox box = new EditBox(font, inputX, y, boxW, 60, Component.literal(""));
            box.setValue(safeGetStr(ModServerConfig.CONFIG.bannedWords, ""));
            box.setMaxLength(10000);
            box.setResponder(val -> sendConfigUpdate("bannedWords", val));
            return box;
        }));
        cats.add(new Cat("config.chatsphere.banned_words_cat", banned));

        List<Opt> voice = new ArrayList<>();
        voice.add(new Opt("config.chatsphere.voice_offline_enabled", y -> mkBool(y, "voiceOfflineStorage", ModServerConfig.CONFIG.voiceOfflineStorage)));
        voice.add(new Opt("config.chatsphere.voice_offline_max_age",
            y -> mkIntBox(y, "voiceOfflineMaxAgeHours", safeGetStr(ModServerConfig.CONFIG.voiceOfflineMaxAgeHours, "24"), 1, 168, 3)));
        voice.add(new Opt("config.chatsphere.voice_offline_max_per_player",
            y -> mkIntBox(y, "voiceOfflineMaxPerPlayer", safeGetStr(ModServerConfig.CONFIG.voiceOfflineMaxPerPlayer, "10"), 1, 50, 2)));
        cats.add(new Cat("config.chatsphere.voice_offline", voice));
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
        for (Opt opt : cats.get(selectedCat).opts()) {
            scrollWidgets.add(addRenderableWidget(opt.factory().create(y)));
            y += ROW_H;
        }

        addRenderableWidget(Button.builder(
            CommonComponents.GUI_BACK,
            btn -> onClose()
        ).bounds(width / 2 - 100, height - 32, 200, 20)
            .tooltip(Tooltip.create(Component.translatable("screen.chatsphere.server_config.tip_back"))).build());
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
        if (mc == null || mc.getConnection() == null) return;
        mc.getConnection().getConnection().send(
            new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new ServerboundConfigUpdatePayload(key, value)));
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

    private AbstractWidget mkBool(int y, String fieldName, ModConfigSpec.BooleanValue cfg) {
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

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        g.fill(0, 0, this.width, this.height, Theme.screenBg());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int contentBottom = height - 48;
        for (AbstractWidget w : scrollWidgets) {
            int wy = w.getY();
            w.visible = wy >= CONTENT_Y && wy + w.getHeight() <= contentBottom;
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
        for (Opt opt : cats.get(selectedCat).opts()) {
            if (y >= CONTENT_Y && y + ROW_H <= contentBottom) {
                g.drawString(font, Component.translatable(opt.key()), optLabelX, y + 6, Theme.text(), false);
            }
            y += ROW_H;
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
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(lastScreen);
    }

    private int calcMaxScroll() {
        int total = cats.get(selectedCat).opts().size() * ROW_H;
        return Math.max(0, CONTENT_Y + total - (height - 48));
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

    @Override
    public boolean isPauseScreen() { return false; }
}
