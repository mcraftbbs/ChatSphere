package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.Ui;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.style.CustomTheme;
import cn.sarskin.ChatSphere.style.ThemeFileParser;
import cn.sarskin.ChatSphere.style.ThemeSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.sarskin.ChatSphere.config.ModClientConfig.CONFIG_SPEC;

/** User themes (non-preset), rendered as cards in the theme's own colors. */
public class ThemeGalleryScreen extends Screen {
    private final Screen lastScreen;

    private static final int CONTENT_Y = 68;
    private static final int CARD_H = 96;
    private static final int ROW_GAP = 20;

    private List<String> themes = new ArrayList<>();
    private int scroll;
    private String errorPrefix = "";
    private String lastFailedFile;
    private final Map<String, ThemeSpec> parsedCache = new HashMap<>();
    private final Set<String> failedCache = new HashSet<>();

    public ThemeGalleryScreen(Screen lastScreen) {
        super(Component.translatable("screen.chatsphere.theme_gallery.title"));
        this.lastScreen = lastScreen;
    }

    public void refresh() {
        themes = new ArrayList<>();
        parsedCache.clear();
        failedCache.clear();
        for (String f : CustomTheme.INSTANCE.listFiles()) {
            boolean preset = false;
            for (String p : CustomTheme.PRESETS) {
                if (f.equals(p + CustomTheme.EXT)) { preset = true; break; }
            }
            if (!preset) themes.add(f);
        }
        for (String f : themes) {
            try {
                parsedCache.put(f, ThemeFileParser.parse(CustomTheme.INSTANCE.read(f)));
            } catch (Exception e) {
                failedCache.add(f);
            }
        }
        scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    private int[] grid() {
        int gap = 24;
        int cardW = Math.max(90, Math.min(150, (width - 32 - gap * 2) / 3));
        int totalW = cardW * 3 + gap * 2;
        return new int[] { cardW, gap, (width - totalW) / 2 };
    }

    private int maxScroll() {
        int rows = (themes.size() + 2) / 3;
        int gridH = themes.isEmpty() ? 30 : rows * CARD_H + (rows - 1) * ROW_GAP;
        int content = 28 + gridH;
        return Math.max(0, CONTENT_Y + content - (height - 48));
    }

    @Override
    protected void init() {
        errorPrefix = Component.translatable("screen.chatsphere.theme_gallery.load_error").getString();
        refresh();
        addRenderableWidget(Button.builder(
                Component.translatable("screen.chatsphere.theme_gallery.close"), b -> onClose())
                .bounds(width / 2 - 60, height - 30, 120, 18).build());
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        BackgroundBlur.blurScreen(g, width, height);
        super.renderBackground(g);
        g.fill(0, 0, width, height, Theme.screenBg());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Theme.beginFrame();
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        g.drawString(font, title, width / 2 - font.width(title) / 2, 14, Theme.text(), false);
        drawErrorBanner(g);
        g.fill(10, CONTENT_Y - 6, width - 10, CONTENT_Y - 5, Theme.divider());

        int[] l = grid();
        int cardW = l[0], gap = l[1], startX = l[2];

        int y2 = CONTENT_Y + 2;
        Component sec = Component.translatable("config.chatsphere.custom_themes_section");
        g.drawString(font, sec, 14, y2 + 3, Theme.textDim(), false);
        boolean active = ModClientConfig.CONFIG.customThemeActive.get();
        boolean failed = CustomTheme.INSTANCE.error() != null;
        int toggleBtnW = 88;
        int btnY = y2 - 2;
        int togX = width - 10 - toggleBtnW;
        int togBg = !active ? Theme.slotBg() : (failed ? 0xFFFF4444 : Theme.accent());
        Ui.fillRoundedRect(g, togX, btnY, toggleBtnW, 18, 3, togBg);
        Component togText = Component.translatable(
                active ? "screen.chatsphere.config.enabled" : "screen.chatsphere.config.disabled");
        g.drawString(font, togText, togX + (toggleBtnW - font.width(togText)) / 2, btnY + 5,
                active ? 0xFFFFFFFF : Theme.textDim(), false);

        int y3 = CONTENT_Y + 28 - scroll;
        if (themes.isEmpty()) {
            Component empty = Component.translatable("config.chatsphere.custom_themes_empty");
            g.drawString(font, empty, 14, y3 + 8, Theme.textFaint(), false);
            return;
        }
        for (int i = 0; i < themes.size(); i++) {
            int r = i / 3, c = i % 3;
            int cx = startX + c * (cardW + gap);
            int cy = y3 + r * (CARD_H + ROW_GAP);
            if (cy + CARD_H < CONTENT_Y || cy > height - 48) continue;
            drawCustomCard(g, mouseX, mouseY, cx, cy, cardW, CARD_H, themes.get(i));
        }
    }

    private void drawCustomCard(GuiGraphics g, int mouseX, int mouseY, int cx, int cy, int cardW, int cardH, String file) {
        boolean sel = CustomTheme.INSTANCE.isActive() && file.equals(CustomTheme.INSTANCE.currentFile());
        boolean hover = mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + cardH;
        Ui.fillRoundedRect(g, cx, cy, cardW, cardH, 6, Theme.panelBg());
        Ui.renderRoundedOutline(g, cx, cy, cardW, cardH, 6, sel ? Theme.accent() : (hover ? Theme.popupOutline() : Theme.popupOutline2()));
        if (sel) {
            Ui.fillRoundedRect(g, cx, cy, cardW, 20, 6, 0x336666DD);
        }
        ThemeSpec spec = parsedCache.get(file);
        if (spec != null) {
            drawThemePreview(g, cx + 10, cy + 24, cardW - 20, 52, spec);
        } else if (failedCache.contains(file)) {
            Component bad = Component.literal(errorPrefix);
            g.drawString(font, bad, cx + 10, cy + 24, 0xFFFF8888, false);
        }
        String name = file.substring(0, file.length() - CustomTheme.EXT.length());
        Component nameC = Component.literal(name);
        g.drawString(font, nameC, cx + (cardW - font.width(nameC)) / 2, cy + cardH - 18,
                sel ? Theme.accent() : Theme.text(), false);
    }

    private void drawErrorBanner(GuiGraphics g) {
        String err = CustomTheme.INSTANCE.error();
        if (err == null || err.isEmpty()) return;
        int py = 32;
        g.fill(10, py - 3, width - 10, py + 12, 0x33FF4444);
        String text = lastFailedFile == null ? errorPrefix + ": " + err
                : lastFailedFile + ": " + errorPrefix + ": " + err;
        text = font.plainSubstrByWidth(text, width - 24);
        g.drawString(font, text, 14, py, 0xFFFF8888, false);
    }

    /** Mini chat preview drawn in the theme's own colors (temporarily applied). */
    private void drawThemePreview(GuiGraphics g, int x, int y, int w, int h, ThemeSpec spec) {
        ThemeSpec prev = CustomTheme.INSTANCE.active();
        String prevFile = CustomTheme.INSTANCE.currentFile();
        CustomTheme.INSTANCE.apply(spec, "");
        try {
            int px = x, py = y;
            g.fill(px, py, px + w, py + h, Theme.screenBgSolid());
            int sw2 = Math.max(4, (int) (w * 0.22));
            g.fill(px, py, px + sw2, py + h, Theme.sidebarBg());
            g.fill(px + sw2 + 2, py + 2, px + sw2 + 2 + Math.max(6, (int) (w * 0.30)), py + 3, Theme.accent());
            Ui.fillRoundedRectStyle(g, Theme.cornerStyle(), px + (int) (w * 0.30), py + 8,
                    (int) (w * 0.52), 10, 3, Theme.bubbleOwnFallback());
            Ui.fillRoundedRectStyle(g, Theme.cornerStyle(), px + (int) (w * 0.30), py + 24,
                    (int) (w * 0.40), 10, 3, Theme.bubbleOtherFallback());
            g.fill(px + (int) (w * 0.30), py + 40, px + (int) (w * 0.56), py + 41, Theme.textDim());
            g.fill(px + (int) (w * 0.30), py + 45, px + (int) (w * 0.48), py + 46, Theme.textFaint());
        } finally {
            CustomTheme.INSTANCE.apply(prev, prevFile);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int[] l = grid();
            int cardW = l[0], gap = l[1], startX = l[2];

            int y2 = CONTENT_Y + 2;
            int toggleBtnW = 88;
            int btnY = y2 - 2;
            int togX = width - 10 - toggleBtnW;
            if (mouseY >= btnY && mouseY <= btnY + 18) {
                if (mouseX >= togX && mouseX <= togX + toggleBtnW) {
                    boolean next = !ModClientConfig.CONFIG.customThemeActive.get();
                    ModClientConfig.CONFIG.customThemeActive.set(next);
                    CONFIG_SPEC.save();
                    if (next) {
                        String f = ModClientConfig.CONFIG.customThemeFile.get();
                        if (f != null && !f.isEmpty() && !CustomTheme.INSTANCE.load(f)) {
                            lastFailedFile = f;
                        }
                    } else {
                        CustomTheme.INSTANCE.unload();
                    }
                    return true;
                }
            }

            int y3 = CONTENT_Y + 28 - scroll;
            for (int i = 0; i < themes.size(); i++) {
                int r = i / 3, c = i % 3;
                int cx = startX + c * (cardW + gap);
                int cy = y3 + r * (CARD_H + ROW_GAP);
                if (mouseX >= cx && mouseX <= cx + cardW && mouseY >= cy && mouseY <= cy + CARD_H) {
                    String file = themes.get(i);
                    if (CustomTheme.INSTANCE.load(file)) {
                        ModClientConfig.CONFIG.customThemeFile.set(file);
                        ModClientConfig.CONFIG.customThemeActive.set(true);
                        CONFIG_SPEC.save();
                    } else {
                        lastFailedFile = file;
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int max = maxScroll();
        if (max <= 0) return false;
        scroll = Mth.clamp(scroll - (int) (scrollY * 20), 0, max);
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (Minecraft.getInstance().screen == this) Minecraft.getInstance().setScreen(lastScreen);
    }
}
