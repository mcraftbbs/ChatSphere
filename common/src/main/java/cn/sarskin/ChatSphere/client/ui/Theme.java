package cn.sarskin.ChatSphere.client.ui;

import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.style.CustomTheme;

import java.util.HashMap;
import java.util.Map;

public final class Theme {
    private Theme() {}

    private static ThemeSnapshot cached;
    private static boolean frameActive;

    /** Frame start: the next snapshot() rebuilds once so all Theme.* calls read the same values. */
    public static void beginFrame() {
        frameActive = true;
        cached = null;
    }

    /** Immutable snapshot of every theme value for the current frame. */
    public static ThemeSnapshot snapshot() {
        if (cached == null || cached.customThemeRevision != CustomTheme.INSTANCE.revision()) {
            cached = new ThemeSnapshot();
        }
        return cached;
    }

    /** Force the next snapshot() to rebuild (e.g. after config hot-save outside a frame). */
    public static void invalidateSnapshot() {
        cached = null;
    }

    public static boolean isDark() {
        return snapshot().dark;
    }

    public static int cornerStyle() {
        return snapshot().cornerStyle;
    }

    /** Corner radius for cards/panels: square=0, pixel=8, original=3, stream=10. */
    public static int cardRadius() {
        return switch (cornerStyle()) {
            case 0 -> 0;
            case 1 -> 8;
            case 2 -> 3;
            default -> 10;
        };
    }

    /** Corner radius for small elements: square=0, pixel=3, original=2, stream=6. */
    public static int buttonRadius() {
        return switch (cornerStyle()) {
            case 0 -> 0;
            case 1 -> 3;
            case 2 -> 2;
            default -> 6;
        };
    }

    public static boolean originalStyle() {
        return snapshot().cornerStyle == 2;
    }

    /** Stream-style layout & message rendering (4th corner style). */
    public static boolean stream() {
        return snapshot().cornerStyle == 3;
    }

    /** Seed: styles.colorSeed wins, else explicit accent; none disables derivation. */
    private static Integer seedColor() {
        Integer v = CustomTheme.INSTANCE.style("colorSeed");
        if (v != null) return v;
        Integer d = CustomTheme.INSTANCE.color(true, "accent");
        if (d != null) return d;
        return CustomTheme.INSTANCE.color(false, "accent");
    }

    private record DeriveSpec(float darkTone, float lightTone, float chromaScale, int darkAlpha, int lightAlpha) {}

    private static final Map<String, DeriveSpec> DERIVE = new HashMap<>();
    static {
        // surfaces: dark tones 8-22 / light 88-97, near-neutral chroma
        DERIVE.put("screenBg", new DeriveSpec(8, 97, 0.06f, 0xE6, 0xE6));
        DERIVE.put("sidebarBg", new DeriveSpec(10, 95, 0.07f, 0xDD, 0xDD));
        DERIVE.put("panelBg", new DeriveSpec(16, 93, 0.10f, 0xDD, 0xDD));
        DERIVE.put("panelBg2", new DeriveSpec(12, 94, 0.08f, 0xDD, 0xDD));
        DERIVE.put("popupBg", new DeriveSpec(14, 95, 0.09f, 0xDD, 0xDD));
        DERIVE.put("popupBg2", new DeriveSpec(14, 95, 0.09f, 0xCC, 0xCC));
        DERIVE.put("popupBg3", new DeriveSpec(10, 94, 0.07f, 0xCC, 0xCC));
        DERIVE.put("searchBg", new DeriveSpec(10, 96, 0.07f, 0xBB, 0xCC));
        DERIVE.put("notifGradTop", new DeriveSpec(12, 94, 0.10f, 0xCC, 0xCC));
        DERIVE.put("notifGradBot", new DeriveSpec(18, 92, 0.12f, 0xCC, 0xCC));
        DERIVE.put("replyBarBg", new DeriveSpec(20, 90, 0.12f, 0xCC, 0xCC));
        DERIVE.put("previewSwatchBg", new DeriveSpec(22, 88, 0.12f, 0xFF, 0xFF));
        DERIVE.put("popupOutline", new DeriveSpec(80, 75, 0.08f, 0x66, 0x66));
        DERIVE.put("popupOutline2", new DeriveSpec(80, 75, 0.08f, 0x88, 0x88));
        DERIVE.put("emojiOutline", new DeriveSpec(80, 75, 0.08f, 0x55, 0x55));
        DERIVE.put("itemPickerOutline", new DeriveSpec(80, 75, 0.08f, 0x55, 0x55));
        DERIVE.put("divider", new DeriveSpec(45, 75, 0.10f, 0x22, 0x33));
        DERIVE.put("accentLine", new DeriveSpec(55, 75, 0.15f, 0x44, 0x44));
        DERIVE.put("sectionLine", new DeriveSpec(80, 70, 0.10f, 0x44, 0x33));
        DERIVE.put("scrollThumb", new DeriveSpec(60, 70, 0.18f, 0x70, 0x70));
        DERIVE.put("scrollTrack", new DeriveSpec(25, 90, 0.06f, 0x30, 0x30));
        DERIVE.put("accent", new DeriveSpec(50, 50, 1.0f, 0xFF, 0xFF));
        DERIVE.put("hoverRow", new DeriveSpec(60, 85, 0.35f, 0x22, 0x33));
        DERIVE.put("activeRow", new DeriveSpec(60, 85, 0.35f, 0x66, 0x66));
        DERIVE.put("menuHover", new DeriveSpec(70, 80, 0.40f, 0x44, 0x44));
        DERIVE.put("iconBtnBg", new DeriveSpec(20, 90, 0.08f, 0x44, 0x22));
        DERIVE.put("iconBtnHover", new DeriveSpec(60, 85, 0.35f, 0x66, 0x66));
        DERIVE.put("searchCloseBg", new DeriveSpec(45, 75, 0.15f, 0x22, 0x44));
        DERIVE.put("emojiCellBg", new DeriveSpec(40, 80, 0.25f, 0x44, 0x44));
        DERIVE.put("emojiTabBg", new DeriveSpec(20, 90, 0.08f, 0x22, 0x22));
        DERIVE.put("emojiTabHover", new DeriveSpec(55, 85, 0.30f, 0x44, 0x44));
        DERIVE.put("emojiTabSel", new DeriveSpec(65, 80, 0.40f, 0x66, 0x66));
        DERIVE.put("slotBg", new DeriveSpec(20, 90, 0.08f, 0x44, 0x22));
        DERIVE.put("slotHover", new DeriveSpec(60, 85, 0.35f, 0x66, 0x66));
        DERIVE.put("text", new DeriveSpec(92, 12, 0.03f, 0xFF, 0xFF));
        DERIVE.put("textMain", new DeriveSpec(80, 25, 0.04f, 0xFF, 0xFF));
        DERIVE.put("textDim", new DeriveSpec(65, 45, 0.05f, 0xFF, 0xFF));
        DERIVE.put("textFaint", new DeriveSpec(55, 55, 0.05f, 0xFF, 0xFF));
        DERIVE.put("textInactive", new DeriveSpec(75, 40, 0.05f, 0xFF, 0xFF));
        DERIVE.put("floatingText", new DeriveSpec(92, 12, 0.03f, 0xFF, 0xFF));
        DERIVE.put("floatingTextDim", new DeriveSpec(92, 12, 0.03f, 0x99, 0x99));
        DERIVE.put("bubbleOwn", new DeriveSpec(42, 92, 0.45f, 0xFF, 0xFF));
        DERIVE.put("bubbleOther", new DeriveSpec(20, 94, 0.10f, 0xFF, 0xFF));
        DERIVE.put("bubbleInfoLine", new DeriveSpec(55, 50, 0.05f, 0xFF, 0xFF));
        DERIVE.put("toggleOn", new DeriveSpec(45, 60, 0.40f, 0xFF, 0xFF));
        DERIVE.put("toggleOff", new DeriveSpec(30, 82, 0.08f, 0xFF, 0xFF));
        DERIVE.put("toggleKnob", new DeriveSpec(96, 96, 0.02f, 0xFF, 0xFF));
    }

    /** Derived color for a key, or null when no seed / not derivable; cached per theme revision. */
    private static final Map<String, Integer> DERIVE_IDS = new HashMap<>();
    private static final Map<Long, Integer> DERIVED = new HashMap<>();
    private static int cacheRev = -1;
    private static Integer seedCache;
    private static boolean seedComputed;

    private static int deriveId(String key) {
        Integer id = DERIVE_IDS.get(key);
        if (id == null) {
            id = DERIVE_IDS.size();
            DERIVE_IDS.put(key, id);
        }
        return id;
    }

    private static Integer derived(boolean dark, String key) {
        int rev = CustomTheme.INSTANCE.revision();
        if (rev != cacheRev) {
            cacheRev = rev;
            seedComputed = false;
            DERIVED.clear();
        }
        if (!seedComputed) {
            seedCache = seedColor();
            seedComputed = true;
        }
        if (seedCache == null) return null;
        DeriveSpec d = DERIVE.get(key);
        if (d == null) return null;
        long ck = ((long) deriveId(key) << 1) | (dark ? 1 : 0);
        Integer v = DERIVED.get(ck);
        if (v != null) return v;
        int tone = dark ? (int) d.darkTone : (int) d.lightTone;
        int alpha = dark ? d.darkAlpha : d.lightAlpha;
        v = (alpha << 24) | (deriveRgb(seedCache, d.chromaScale, tone, key) & 0xFFFFFF);
        DERIVED.put(ck, v);
        return v;
    }

    private static int deriveRgb(int seed, float chromaScale, int tone, String key) {
        int rgb = seed & 0xFFFFFF;
        if ("accent".equals(key)) return rgb;
        float[] hsl = rgbToHsl(rgb);
        float chroma = Math.max(0f, Math.min(0.6f, hsl[1] * chromaScale));
        return hslToRgb(hsl[0], chroma, tone / 100f);
    }

    private static float[] rgbToHsl(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float l = (max + min) / 2f;
        float h, s;
        if (max == min) {
            h = 0;
            s = 0;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) h = 60 * (((g - b) / d) % 6);
            else if (max == g) h = 60 * ((b - r) / d + 2);
            else h = 60 * ((r - g) / d + 4);
        }
        if (h < 0) h += 360;
        return new float[]{ h, s, l };
    }

    private static int hslToRgb(float h, float s, float l) {
        h = ((h % 360) + 360) % 360;
        float c = (1 - Math.abs(2 * l - 1)) * s;
        float x = c * (1 - Math.abs((h / 60f) % 2 - 1));
        float m = l - c / 2f;
        float r = 0, g = 0, b = 0;
        if (h < 60) { r = c; g = x; }
        else if (h < 120) { r = x; g = c; }
        else if (h < 180) { g = c; b = x; }
        else if (h < 240) { g = x; b = c; }
        else if (h < 300) { r = x; b = c; }
        else { r = c; b = x; }
        return 0xFF000000
                | ((int) Math.round((r + m) * 255) << 16)
                | ((int) Math.round((g + m) * 255) << 8)
                | (int) Math.round((b + m) * 255);
    }

    private static int ov(boolean dark, String key, int defDark, int defLight) {
        Integer v = CustomTheme.INSTANCE.color(dark, key);
        if (v != null) return v;
        Integer d = derived(dark, key);
        if (d != null) return d;
        return dark ? defDark : defLight;
    }

    private static boolean rawDark() {
        return ModClientConfig.CONFIG.themeDark.get();
    }

    private static int rawCornerStyle() {
        Integer v = CustomTheme.INSTANCE.style("uiCornerStyle");
        if (v != null) return v;
        return ModClientConfig.CONFIG.uiCornerStyle.get();
    }

    private static float rawBlurIntensity() {
        Integer v = CustomTheme.INSTANCE.style("blurIntensity");
        return v != null ? Math.max(0, Math.min(100, v)) / 100f : 1f;
    }

    private static boolean rawPopupBorderVisible(boolean dark, int cornerStyle) {
        // popupBorder applies to all corner styles; theme styles only seed the config default
        return cn.sarskin.ChatSphere.config.ModClientConfig.CONFIG.popupBorder.get();
    }

    private static int rawScreenBg(boolean dark, float blurIntensity) {
        Integer v = CustomTheme.INSTANCE.color(dark, "screenBg");
        if (v != null) return v;
        Integer dr = derived(dark, "screenBg");
        int base = dr != null ? (dr & 0xFFFFFF) : (dark ? 0x1A1A2E : 0xF0F0F5);
        if (ModClientConfig.CONFIG.backgroundBlur.get()) {
            int baseA = dark ? 0xCC : 0xBB;
            int a = Math.max(0, Math.min(255, (int) (baseA * blurIntensity)));
            return (a << 24) | base;
        }
        return 0xE6000000 | base;
    }

    private static int rawBubbleOwn(boolean dark) {
        Integer v = CustomTheme.INSTANCE.color(dark, "bubbleOwn");
        if (v != null) return v;
        Integer dr = derived(dark, "bubbleOwn");
        if (dr != null) return 0xFF000000 | (dr & 0xFFFFFF);
        return ModClientConfig.parseHexColor(ModClientConfig.CONFIG.bubbleColorOwn.get(),
                dark ? 0xFF1D3B5C : 0xFFD9E8FF);
    }

    private static int rawBubbleOther(boolean dark) {
        Integer v = CustomTheme.INSTANCE.color(dark, "bubbleOther");
        if (v != null) return v;
        Integer dr = derived(dark, "bubbleOther");
        if (dr != null) return 0xFF000000 | (dr & 0xFFFFFF);
        return ModClientConfig.parseHexColor(ModClientConfig.CONFIG.bubbleColorOther.get(),
                dark ? 0xFF26262E : 0xFFFFFFFF);
    }

    /** Immutable per-frame capture of every theme value. */
    public static final class ThemeSnapshot {
        final int customThemeRevision = CustomTheme.INSTANCE.revision();
        public final boolean dark;
        public final int cornerStyle;
        public final boolean popupBorderVisible;
        public final boolean bubbleGradientEnabled;
        public final int bubbleCornerRadius;
        public final int messageLineSpacing;
        public final int sidebarWidth;
        public final int avatarRadius;
        public final float blurIntensity;

        public final int screenBg;
        public final int screenBgSolid;
        public final int sidebarBg;
        public final int panelBg;
        public final int panelBg2;
        public final int popupBg;
        public final int popupBg2;
        public final int popupBg3;
        public final int searchBg;
        public final int inputBg;
        public final int notifGradTop;
        public final int notifGradBot;
        public final int replyBarBg;
        public final int toolbarBg;
        public final int railBg;
        public final int railSep;
        public final int railIconBg;
        public final int railIconGlyph;
        public final int railIconGlyphDim;
        public final int msgHover;
        public final int inputPillBg;
        public final int inputPillBorder;
        public final int previewSwatchBg;

        public final int popupOutline;
        public final int popupOutline2;
        public final int emojiOutline;
        public final int itemPickerOutline;
        public final int divider;
        public final int accentLine;
        public final int sectionLine;
        public final int scrollThumb;
        public final int scrollTrack;

        public final int accent;
        public final int hoverRow;
        public final int activeRow;
        public final int menuHover;
        public final int iconBtnBg;
        public final int iconBtnHover;
        public final int searchCloseBg;
        public final int emojiCellBg;
        public final int emojiTabBg;
        public final int emojiTabHover;
        public final int emojiTabSel;
        public final int slotBg;
        public final int slotHover;

        public final int text;
        public final int textMain;
        public final int textDim;
        public final int textFaint;
        public final int textInactive;
        public final int floatingText;
        public final int floatingTextDim;
        public final int inputText;
        public final int searchPlaceholder;

        public final int bubbleOwnFallback;
        public final int bubbleOtherFallback;
        public final int cmdBubbleOwnBg;
        public final int cmdBubbleOtherBg;
        public final int bubbleInfoLine;
        public final int bubbleGradientTop;
        public final int bubbleGradientBottom;

        public final int toggleOn;
        public final int toggleOff;
        public final int toggleKnob;

        private ThemeSnapshot() {
            this.dark = rawDark();
            this.cornerStyle = rawCornerStyle();
            this.popupBorderVisible = rawPopupBorderVisible(dark, cornerStyle);
            this.bubbleGradientEnabled = CustomTheme.INSTANCE.color(dark, "bubbleGradientTop") != null
                    && CustomTheme.INSTANCE.color(dark, "bubbleGradientBottom") != null;
            this.bubbleCornerRadius = styleOr("bubbleCornerRadius", 4);
            this.messageLineSpacing = styleOr("messageLineSpacing", 0);
            this.sidebarWidth = styleOr("sidebarWidth", 100);
            this.avatarRadius = styleOr("avatarRadius", 0);
            this.blurIntensity = rawBlurIntensity();

            this.screenBg = rawScreenBg(dark, blurIntensity);
            this.screenBgSolid = ov(dark, "screenBg", 0xE61A1A2E, 0xE6F0F0F5);
            this.sidebarBg = ov(dark, "sidebarBg", 0xDD1A1A2E, 0xDDE8E8F2);
            this.panelBg = ov(dark, "panelBg", 0xDD2A2A4E, 0xDDEAAAF4);
            this.panelBg2 = ov(dark, "panelBg2", 0xDD1E1E3E, 0xDDEAAAF4);
            this.popupBg = ov(dark, "popupBg", 0xDD222244, 0xDDEDEDF6);
            this.popupBg2 = ov(dark, "popupBg2", 0xCC222244, 0xCCE8E8F2);
            this.popupBg3 = ov(dark, "popupBg3", 0xCC1A1A2E, 0xCCECECF4);
            this.searchBg = ov(dark, "searchBg", 0xBB1A1A2E, 0xCCFFFFFF);
            this.inputBg = ov(dark, "inputBg", 0x44111122, 0x33000000);
            this.notifGradTop = ov(dark, "notifGradTop", 0xCC1A1A3E, 0xCCE0E0EA);
            this.notifGradBot = ov(dark, "notifGradBot", 0xCC2A2A4E, 0xCCEAEAF4);
            this.replyBarBg = ov(dark, "replyBarBg", 0xCC333355, 0xCCE4E4EE);
            this.toolbarBg = ov(dark, "toolbarBg", 0x88000000, 0x88000000);
            this.railBg = ov(dark, "railBg", 0xFF1E1F22, 0xFFE3E5E8);
            this.railSep = ov(dark, "railSep", 0xFF202225, 0xFFC4C7CE);
            this.railIconBg = ov(dark, "railIconBg", 0xFF313338, 0xFFD6D8DD);
            this.railIconGlyph = ov(dark, "railIconGlyph", 0xFFB5BAC1, 0xFF4F545C);
            this.railIconGlyphDim = ov(dark, "railIconGlyphDim", 0xFF6A6F78, 0xFF2F3338);
            this.msgHover = ov(dark, "msgHover", 0xFF2E3035, 0xFFF4F4F7);
            this.inputPillBg = ov(dark, "inputPillBg", 0xFF383A40, 0xFFFFFFFF);
            this.inputPillBorder = ov(dark, "inputPillBorder", 0xFF404249, 0xFFB0B4BB);
            this.previewSwatchBg = ov(dark, "previewSwatchBg", 0xFF3A3A4A, 0xFFDDDDE8);

            this.popupOutline = ov(dark, "popupOutline", 0x66FFFFFF, 0x66000000);
            this.popupOutline2 = ov(dark, "popupOutline2", 0x88FFFFFF, 0x88000000);
            this.emojiOutline = ov(dark, "emojiOutline", 0x55FFFFFF, 0x55000000);
            this.itemPickerOutline = ov(dark, "itemPickerOutline", 0x55FFFFFF, 0x55000000);
            this.divider = ov(dark, "divider", 0x225A4A7E, 0x338888CC);
            this.accentLine = ov(dark, "accentLine", 0x445A4A7E, 0x448888CC);
            this.sectionLine = ov(dark, "sectionLine", 0x44FFFFFF, 0x339999BB);
            this.scrollThumb = ov(dark, "scrollThumb", 0x706666AA, 0x70AAAAEE);
            this.scrollTrack = ov(dark, "scrollTrack", 0x30333333, 0x30888888);

            this.accent = ov(dark, "accent", 0xFF8888FF, 0xFF6666DD);
            this.hoverRow = ov(dark, "hoverRow", 0x22333388, 0x33AAAAFF);
            this.activeRow = ov(dark, "activeRow", 0x66333388, 0x66AAAAFF);
            this.menuHover = ov(dark, "menuHover", 0x44448888, 0x448888FF);
            this.iconBtnBg = ov(dark, "iconBtnBg", 0x44000000, 0x22FFFFFF);
            this.iconBtnHover = ov(dark, "iconBtnHover", 0x66333388, 0x66AAAAFF);
            this.searchCloseBg = ov(dark, "searchCloseBg", 0x22333333, 0x44AAAAAA);
            this.emojiCellBg = ov(dark, "emojiCellBg", 0x44446688, 0x448888CC);
            this.emojiTabBg = ov(dark, "emojiTabBg", 0x22000000, 0x22FFFFFF);
            this.emojiTabHover = ov(dark, "emojiTabHover", 0x44333366, 0x44AAAAFF);
            this.emojiTabSel = ov(dark, "emojiTabSel", 0x664466AA, 0x66AAAAFF);
            this.slotBg = ov(dark, "slotBg", 0x44000000, 0x22FFFFFF);
            this.slotHover = ov(dark, "slotHover", 0x66444488, 0x66AAAAFF);

            this.text = ov(dark, "text", 0xFFFFFFFF, 0xFF1A1A1A);
            this.textMain = ov(dark, "textMain", 0xFFCCCCCC, 0xFF333333);
            this.textDim = ov(dark, "textDim", 0xFF888888, 0xFF666666);
            this.textFaint = ov(dark, "textFaint", 0xFF555555, 0xFF888888);
            this.textInactive = ov(dark, "textInactive", 0xFFAAAAAA, 0xFF666666);
            this.floatingText = ov(dark, "floatingText", 0xFFFFFFFF, 0xFF1A1A1A);
            this.floatingTextDim = ov(dark, "floatingTextDim", 0x99FFFFFF, 0x991A1A1A);
            this.inputText = ov(dark, "inputText", 0xFFE0E0E0, 0xFFE0E0E0);
            this.searchPlaceholder = ov(dark, "searchPlaceholder", 0xFF666666, 0xFF888888);

            this.bubbleOwnFallback = rawBubbleOwn(dark);
            this.bubbleOtherFallback = rawBubbleOther(dark);
            Integer cmdOwn = CustomTheme.INSTANCE.color(dark, "cmdBubbleOwn");
            this.cmdBubbleOwnBg = cmdOwn != null ? cmdOwn : (dark ? 0xFF2D2D2D : 0xFF2D2D2D);
            Integer cmdOther = CustomTheme.INSTANCE.color(dark, "cmdBubbleOther");
            this.cmdBubbleOtherBg = cmdOther != null ? cmdOther : (dark ? 0xFF1E1E2E : 0xFF1E1E2E);
            this.bubbleInfoLine = ov(dark, "bubbleInfoLine", 0xFF555555, 0xFF888888);
            this.bubbleGradientTop = CustomTheme.INSTANCE.color(dark, "bubbleGradientTop") != null
                    ? CustomTheme.INSTANCE.color(dark, "bubbleGradientTop") : bubbleOwnFallback;
            this.bubbleGradientBottom = CustomTheme.INSTANCE.color(dark, "bubbleGradientBottom") != null
                    ? CustomTheme.INSTANCE.color(dark, "bubbleGradientBottom") : bubbleOwnFallback;

            this.toggleOn = ov(dark, "toggleOn", 0xFF2E7D4F, 0xFF3BA55D);
            this.toggleOff = ov(dark, "toggleOff", 0xFF3A3A4A, 0xFFB8B8C4);
            this.toggleKnob = ov(dark, "toggleKnob", 0xFFF0F0F0, 0xFFFFFFFF);
        }

        private int styleOr(String key, int def) {
            Integer v = CustomTheme.INSTANCE.style(key);
            return v != null ? v : def;
        }

        /** Stable per-name hue color (Stream-style username coloring). */
        public int nameColor(String name) {
            int h = Math.floorMod(name.hashCode(), 360);
            return dark ? hslToRgb(h, 0.55f, 0.72f) : hslToRgb(h, 0.50f, 0.32f);
        }
    }

    public static int screenBg() {
        return snapshot().screenBg;
    }

    public static int screenBgSolid() {
        return snapshot().screenBgSolid;
    }

    public static int sidebarBg() {
        return snapshot().sidebarBg;
    }

    public static int panelBg() {
        return snapshot().panelBg;
    }

    public static int panelBg2() {
        return snapshot().panelBg2;
    }

    public static int popupBg() {
        return snapshot().popupBg;
    }

    public static int popupBg2() {
        return snapshot().popupBg2;
    }

    public static int popupBg3() {
        return snapshot().popupBg3;
    }

    public static int searchBg() {
        return snapshot().searchBg;
    }

    public static int inputBg() {
        return snapshot().inputBg;
    }

    public static int notifGradTop() {
        return snapshot().notifGradTop;
    }

    public static int notifGradBot() {
        return snapshot().notifGradBot;
    }

    public static int replyBarBg() {
        return snapshot().replyBarBg;
    }

    public static int toolbarBg() {
        return snapshot().toolbarBg;
    }

    /** Stream-style: left icon rail background (#1E1F22 dark / #E3E5E8 light). */
    public static int railBg() {
        return snapshot().railBg;
    }

    /** Stream-style: 1px rail/sidebar separator (#202225 dark / #C4C7CE light). */
    public static int railSep() {
        return snapshot().railSep;
    }

    /** Stream-style: rail icon pill background (#313338 dark / #D6D8DD light). */
    public static int railIconBg() {
        return snapshot().railIconBg;
    }

    /** Stream-style: rail icon glyph color (#B5BAC1 dark / #4F545C light). */
    public static int railIconGlyph() {
        return snapshot().railIconGlyph;
    }

    /** Stream-style: dim rail icon glyph color (#6A6F78 dark / #2F3338 light). */
    public static int railIconGlyphDim() {
        return snapshot().railIconGlyphDim;
    }

    /** Stream-style message row hover background (#2E3035 dark). */
    public static int msgHover() {
        return snapshot().msgHover;
    }

    /** Stream-style input pill background (#383A40 dark / white light). */
    public static int inputPillBg() {
        return snapshot().inputPillBg;
    }

    /** Stream-style input pill border (#404249 dark). */
    public static int inputPillBorder() {
        return snapshot().inputPillBorder;
    }

    public static int previewSwatchBg() {
        return snapshot().previewSwatchBg;
    }

    /** Popup/screen outline borders; driven by the popupBorder config option. */
    public static boolean popupBorderVisible() {
        return snapshot().popupBorderVisible;
    }

    public static int popupOutline() {
        return snapshot().popupOutline;
    }

    public static int popupOutline2() {
        return snapshot().popupOutline2;
    }

    public static int emojiOutline() {
        return snapshot().emojiOutline;
    }

    public static int itemPickerOutline() {
        return snapshot().itemPickerOutline;
    }

    public static int divider() {
        return snapshot().divider;
    }

    public static int accentLine() {
        return snapshot().accentLine;
    }

    public static int sectionLine() {
        return snapshot().sectionLine;
    }

    public static int scrollThumb() {
        return snapshot().scrollThumb;
    }

    public static int scrollTrack() {
        return snapshot().scrollTrack;
    }

    public static int accent() {
        return snapshot().accent;
    }

    public static int hoverRow() {
        return snapshot().hoverRow;
    }

    public static int activeRow() {
        return snapshot().activeRow;
    }

    public static int menuHover() {
        return snapshot().menuHover;
    }

    public static int iconBtnBg() {
        return snapshot().iconBtnBg;
    }

    public static int iconBtnHover() {
        return snapshot().iconBtnHover;
    }

    public static int searchCloseBg() {
        return snapshot().searchCloseBg;
    }

    public static int emojiCellBg() {
        return snapshot().emojiCellBg;
    }

    public static int emojiTabBg() {
        return snapshot().emojiTabBg;
    }

    public static int emojiTabHover() {
        return snapshot().emojiTabHover;
    }

    public static int emojiTabSel() {
        return snapshot().emojiTabSel;
    }

    public static int slotBg() {
        return snapshot().slotBg;
    }

    public static int slotHover() {
        return snapshot().slotHover;
    }

    public static int text() {
        return snapshot().text;
    }

    /** Stable per-name hue color (Stream-style username coloring). */
    public static int nameColor(String name) {
        return snapshot().nameColor(name);
    }

    public static int textMain() {
        return snapshot().textMain;
    }

    public static int textDim() {
        return snapshot().textDim;
    }

    public static int textFaint() {
        return snapshot().textFaint;
    }

    public static int textInactive() {
        return snapshot().textInactive;
    }

    public static int floatingText() {
        return snapshot().floatingText;
    }

    public static int floatingTextDim() {
        return snapshot().floatingTextDim;
    }

    public static int inputText() {
        return snapshot().inputText;
    }

    public static int searchPlaceholder() {
        return snapshot().searchPlaceholder;
    }

    public static int bubbleOwnFallback() {
        return snapshot().bubbleOwnFallback;
    }

    public static int bubbleOtherFallback() {
        return snapshot().bubbleOtherFallback;
    }

    public static int cmdBubbleOwnBg() {
        return snapshot().cmdBubbleOwnBg;
    }

    public static int cmdBubbleOtherBg() {
        return snapshot().cmdBubbleOtherBg;
    }

    public static int bubbleInfoLine() {
        return snapshot().bubbleInfoLine;
    }

    public static boolean bubbleGradientEnabled() {
        return snapshot().bubbleGradientEnabled;
    }

    public static int bubbleGradientTop() {
        return snapshot().bubbleGradientTop;
    }

    public static int bubbleGradientBottom() {
        return snapshot().bubbleGradientBottom;
    }

    public static int toggleOn() {
        return snapshot().toggleOn;
    }

    public static int toggleOff() {
        return snapshot().toggleOff;
    }

    public static int toggleKnob() {
        return snapshot().toggleKnob;
    }

    /** Bubble corner radius; theme-file only (styles.bubble-corner-radius), 4px default. */
    public static int bubbleCornerRadius() {
        return snapshot().bubbleCornerRadius;
    }

    public static int messageLineSpacing() {
        return snapshot().messageLineSpacing;
    }

    public static int sidebarWidth() {
        return snapshot().sidebarWidth;
    }

    public static int avatarRadius() {
        return snapshot().avatarRadius;
    }

    public static float blurIntensity() {
        return snapshot().blurIntensity;
    }

    public static int bubbleTextOn(int bgColor) {
        int r = (bgColor >> 16) & 0xFF;
        int g = (bgColor >> 8) & 0xFF;
        int b = bgColor & 0xFF;
        int lum = (r * 299 + g * 587 + b * 114) / 1000;
        return lum >= 150 ? 0xFF1A1A1A : 0xFFF0F0F0;
    }
}