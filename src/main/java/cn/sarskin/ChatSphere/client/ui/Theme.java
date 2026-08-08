package cn.sarskin.ChatSphere.client.ui;

import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.style.CustomTheme;

import java.util.HashMap;
import java.util.Map;

public final class Theme {
    private Theme() {}

    public static boolean isDark() {
        return ModClientConfig.CONFIG.themeDark.get();
    }

    public static int cornerStyle() {
        Integer v = CustomTheme.INSTANCE.style("uiCornerStyle");
        if (v != null) return v;
        return ModClientConfig.CONFIG.uiCornerStyle.get();
    }

    public static boolean originalStyle() {
        return cornerStyle() == 2;
    }

    /** Stream-style layout & message rendering (4th corner style). */
    public static boolean stream() {
        return cornerStyle() == 3;
    }

    // ---------- Seed-based tonal derivation ----------

    /**
     * Seed for derived colors: explicit styles.colorSeed wins, else an explicit accent;
     * none = no derivation (built-in defaults apply).
     */
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
        // lines & outlines
        DERIVE.put("popupOutline", new DeriveSpec(80, 75, 0.08f, 0x66, 0x66));
        DERIVE.put("popupOutline2", new DeriveSpec(80, 75, 0.08f, 0x88, 0x88));
        DERIVE.put("emojiOutline", new DeriveSpec(80, 75, 0.08f, 0x55, 0x55));
        DERIVE.put("itemPickerOutline", new DeriveSpec(80, 75, 0.08f, 0x55, 0x55));
        DERIVE.put("divider", new DeriveSpec(45, 75, 0.10f, 0x22, 0x33));
        DERIVE.put("accentLine", new DeriveSpec(55, 75, 0.15f, 0x44, 0x44));
        DERIVE.put("sectionLine", new DeriveSpec(80, 70, 0.10f, 0x44, 0x33));
        DERIVE.put("scrollThumb", new DeriveSpec(60, 70, 0.18f, 0x70, 0x70));
        DERIVE.put("scrollTrack", new DeriveSpec(25, 90, 0.06f, 0x30, 0x30));
        // accent & selection
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
        // text
        DERIVE.put("text", new DeriveSpec(92, 12, 0.03f, 0xFF, 0xFF));
        DERIVE.put("textMain", new DeriveSpec(80, 25, 0.04f, 0xFF, 0xFF));
        DERIVE.put("textDim", new DeriveSpec(65, 45, 0.05f, 0xFF, 0xFF));
        DERIVE.put("textFaint", new DeriveSpec(55, 55, 0.05f, 0xFF, 0xFF));
        DERIVE.put("textInactive", new DeriveSpec(75, 40, 0.05f, 0xFF, 0xFF));
        DERIVE.put("floatingText", new DeriveSpec(92, 12, 0.03f, 0xFF, 0xFF));
        DERIVE.put("floatingTextDim", new DeriveSpec(92, 12, 0.03f, 0x99, 0x99));
        // bubbles & toggle
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

    // ---------- Surfaces ----------

    public static int screenBg() {
        Integer v = CustomTheme.INSTANCE.color(isDark(), "screenBg");
        if (v != null) return v;
        Integer dr = derived(isDark(), "screenBg");
        int base = dr != null ? (dr & 0xFFFFFF) : (isDark() ? 0x1A1A2E : 0xF0F0F5);
        if (ModClientConfig.CONFIG.backgroundBlur.get()) {
            float bi = blurIntensity();
            int baseA = isDark() ? 0xCC : 0xBB;
            int a = Math.max(0, Math.min(255, (int) (baseA * bi)));
            return (a << 24) | base;
        }
        return 0xE6000000 | base;
    }

    public static int screenBgSolid() {
        return ov(isDark(), "screenBg", 0xE61A1A2E, 0xE6F0F0F5);
    }

    public static int sidebarBg() {
        return ov(isDark(), "sidebarBg", 0xDD1A1A2E, 0xDDE8E8F2);
    }

    public static int panelBg() {
        return ov(isDark(), "panelBg", 0xDD2A2A4E, 0xDDEAAf4);
    }

    public static int panelBg2() {
        return ov(isDark(), "panelBg2", 0xDD1E1E3E, 0xDDEAAf4);
    }

    public static int popupBg() {
        return ov(isDark(), "popupBg", 0xDD222244, 0xDDEDEDF6);
    }

    public static int popupBg2() {
        return ov(isDark(), "popupBg2", 0xCC222244, 0xCCE8E8F2);
    }

    public static int popupBg3() {
        return ov(isDark(), "popupBg3", 0xCC1A1A2E, 0xCCECECF4);
    }

    public static int searchBg() {
        return ov(isDark(), "searchBg", 0xBB1A1A2E, 0xCCFFFFFF);
    }

    public static int inputBg() {
        return ov(isDark(), "inputBg", 0x44111122, 0x33000000);
    }

    public static int notifGradTop() {
        return ov(isDark(), "notifGradTop", 0xCC1A1A3E, 0xCCE0E0EA);
    }

    public static int notifGradBot() {
        return ov(isDark(), "notifGradBot", 0xCC2A2A4E, 0xCCEAEAF4);
    }

    public static int replyBarBg() {
        return ov(isDark(), "replyBarBg", 0xCC333355, 0xCCE4E4EE);
    }

    public static int toolbarBg() {
        return ov(isDark(), "toolbarBg", 0x88000000, 0x88000000);
    }

    /** Stream-style: left icon rail background (#1E1F22 dark / #E3E5E8 light). */
    public static int railBg() {
        return ov(isDark(), "railBg", 0xFF1E1F22, 0xFFE3E5E8);
    }

    /** Stream-style message row hover background (#2E3035 dark). */
    public static int msgHover() {
        return ov(isDark(), "msgHover", 0xFF2E3035, 0xFFF4F4F7);
    }

    /** Stream-style input pill background (#383A40 dark / white light). */
    public static int inputPillBg() {
        return ov(isDark(), "inputPillBg", 0xFF383A40, 0xFFFFFFFF);
    }

    /** Stream-style input pill border (#404249 dark). */
    public static int inputPillBorder() {
        return ov(isDark(), "inputPillBorder", 0xFF404249, 0xFFB0B4BB);
    }

    public static int previewSwatchBg() {
        return ov(isDark(), "previewSwatchBg", 0xFF3A3A4A, 0xFFDDDDE8);
    }

    // ---------- Borders & lines ----------

    /**
     * Whether popup/screen outline borders are drawn. Controlled by the popupBorder
     * config option and forced off for square/pixel corner styles.
     */
    public static boolean popupBorderVisible() {
        return cn.sarskin.ChatSphere.config.ModClientConfig.CONFIG.popupBorder.get()
                && cornerStyle() == 2;
    }

    public static int popupOutline() {
        return ov(isDark(), "popupOutline", 0x66FFFFFF, 0x66000000);
    }

    public static int popupOutline2() {
        return ov(isDark(), "popupOutline2", 0x88FFFFFF, 0x88000000);
    }

    public static int emojiOutline() {
        return ov(isDark(), "emojiOutline", 0x55FFFFFF, 0x55000000);
    }

    public static int itemPickerOutline() {
        return ov(isDark(), "itemPickerOutline", 0x55FFFFFF, 0x55000000);
    }

    public static int divider() {
        return ov(isDark(), "divider", 0x225A4A7E, 0x338888CC);
    }

    public static int accentLine() {
        return ov(isDark(), "accentLine", 0x445A4A7E, 0x448888CC);
    }

    public static int sectionLine() {
        return ov(isDark(), "sectionLine", 0x44FFFFFF, 0x339999BB);
    }

    public static int scrollThumb() {
        return ov(isDark(), "scrollThumb", 0x706666AA, 0x70AAAAEE);
    }

    public static int scrollTrack() {
        return ov(isDark(), "scrollTrack", 0x30333333, 0x30888888);
    }

    // ---------- Accent & selection ----------

    public static int accent() {
        return ov(isDark(), "accent", 0xFF8888FF, 0xFF6666DD);
    }

    public static int hoverRow() {
        return ov(isDark(), "hoverRow", 0x22333388, 0x33AAAAFF);
    }

    public static int activeRow() {
        return ov(isDark(), "activeRow", 0x66333388, 0x66AAAAFF);
    }

    public static int menuHover() {
        return ov(isDark(), "menuHover", 0x44448888, 0x448888FF);
    }

    public static int iconBtnBg() {
        return ov(isDark(), "iconBtnBg", 0x44000000, 0x22FFFFFF);
    }

    public static int iconBtnHover() {
        return ov(isDark(), "iconBtnHover", 0x66333388, 0x66AAAAFF);
    }

    public static int searchCloseBg() {
        return ov(isDark(), "searchCloseBg", 0x22333333, 0x44AAAAAA);
    }

    public static int emojiCellBg() {
        return ov(isDark(), "emojiCellBg", 0x44446688, 0x448888CC);
    }

    public static int emojiTabBg() {
        return ov(isDark(), "emojiTabBg", 0x22000000, 0x22FFFFFF);
    }

    public static int emojiTabHover() {
        return ov(isDark(), "emojiTabHover", 0x44333366, 0x44AAAAFF);
    }

    public static int emojiTabSel() {
        return ov(isDark(), "emojiTabSel", 0x664466AA, 0x66AAAAFF);
    }

    public static int slotBg() {
        return ov(isDark(), "slotBg", 0x44000000, 0x22FFFFFF);
    }

    public static int slotHover() {
        return ov(isDark(), "slotHover", 0x66444488, 0x66AAAAFF);
    }

    // ---------- Text ----------

    public static int text() {
        return ov(isDark(), "text", 0xFFFFFFFF, 0xFF1A1A1A);
    }

    /** Stable per-name hue color (Stream-style username coloring). */
    public static int nameColor(String name) {
        int h = Math.floorMod(name.hashCode(), 360);
        return isDark() ? hslToRgb(h, 0.55f, 0.72f) : hslToRgb(h, 0.50f, 0.32f);
    }

    public static int textMain() {
        return ov(isDark(), "textMain", 0xFFCCCCCC, 0xFF333333);
    }

    public static int textDim() {
        return ov(isDark(), "textDim", 0xFF888888, 0xFF666666);
    }

    public static int textFaint() {
        return ov(isDark(), "textFaint", 0xFF555555, 0xFF888888);
    }

    public static int textInactive() {
        return ov(isDark(), "textInactive", 0xFFAAAAAA, 0xFF666666);
    }

    // Floating/overlay text (chat header, time separators, search hints, command messages)
    public static int floatingText() {
        return ov(isDark(), "floatingText", 0xFFFFFFFF, 0xFF1A1A1A);
    }

    public static int floatingTextDim() {
        return ov(isDark(), "floatingTextDim", 0x99FFFFFF, 0x991A1A1A);
    }

    public static int inputText() {
        return ov(isDark(), "inputText", 0xFFE0E0E0, 0xFFE0E0E0);
    }

    public static int searchPlaceholder() {
        return ov(isDark(), "searchPlaceholder", 0xFF666666, 0xFF888888);
    }

    // ---------- Bubbles ----------

    public static int bubbleOwnFallback() {
        Integer v = CustomTheme.INSTANCE.color(isDark(), "bubbleOwn");
        if (v != null) return v;
        Integer dr = derived(isDark(), "bubbleOwn");
        if (dr != null) return 0xFF000000 | (dr & 0xFFFFFF);
        return ModClientConfig.parseHexColor(ModClientConfig.CONFIG.bubbleColorOwn.get(),
                isDark() ? 0xFF1D3B5C : 0xFFD9E8FF);
    }

    public static int bubbleOtherFallback() {
        Integer v = CustomTheme.INSTANCE.color(isDark(), "bubbleOther");
        if (v != null) return v;
        Integer dr = derived(isDark(), "bubbleOther");
        if (dr != null) return 0xFF000000 | (dr & 0xFFFFFF);
        return ModClientConfig.parseHexColor(ModClientConfig.CONFIG.bubbleColorOther.get(),
                isDark() ? 0xFF26262E : 0xFFFFFFFF);
    }

    public static int cmdBubbleOwnBg() {
        Integer v = CustomTheme.INSTANCE.color(isDark(), "cmdBubbleOwn");
        if (v != null) return v;
        return isDark() ? 0xFF2D2D2D : 0xFF2D2D2D;
    }

    public static int cmdBubbleOtherBg() {
        Integer v = CustomTheme.INSTANCE.color(isDark(), "cmdBubbleOther");
        if (v != null) return v;
        return isDark() ? 0xFF1E1E2E : 0xFF1E1E2E;
    }

    public static int bubbleInfoLine() {
        return ov(isDark(), "bubbleInfoLine", 0xFF555555, 0xFF888888);
    }

    public static boolean bubbleGradientEnabled() {
        return CustomTheme.INSTANCE.color(isDark(), "bubbleGradientTop") != null
                && CustomTheme.INSTANCE.color(isDark(), "bubbleGradientBottom") != null;
    }

    public static int bubbleGradientTop() {
        Integer v = CustomTheme.INSTANCE.color(isDark(), "bubbleGradientTop");
        return v != null ? v : bubbleOwnFallback();
    }

    public static int bubbleGradientBottom() {
        Integer v = CustomTheme.INSTANCE.color(isDark(), "bubbleGradientBottom");
        return v != null ? v : bubbleOwnFallback();
    }

    // ---------- Toggle ----------

    public static int toggleOn() {
        return ov(isDark(), "toggleOn", 0xFF2E7D4F, 0xFF3BA55D);
    }

    public static int toggleOff() {
        return ov(isDark(), "toggleOff", 0xFF3A3A4A, 0xFFB8B8C4);
    }

    public static int toggleKnob() {
        return ov(isDark(), "toggleKnob", 0xFFF0F0F0, 0xFFFFFFFF);
    }

    // ---------- Style numbers (custom theme) ----------

    /** Bubble corner radius; theme-file only (styles.bubble-corner-radius), 4px default. */
    public static int bubbleCornerRadius() {
        Integer v = CustomTheme.INSTANCE.style("bubbleCornerRadius");
        return v != null ? v : 4;
    }

    public static int messageLineSpacing() {
        Integer v = CustomTheme.INSTANCE.style("messageLineSpacing");
        return v != null ? v : 0;
    }

    public static int sidebarWidth() {
        Integer v = CustomTheme.INSTANCE.style("sidebarWidth");
        return v != null ? v : 100;
    }

    public static int avatarRadius() {
        Integer v = CustomTheme.INSTANCE.style("avatarRadius");
        return v != null ? v : 0;
    }

    public static float blurIntensity() {
        Integer v = CustomTheme.INSTANCE.style("blurIntensity");
        return v != null ? Math.max(0, Math.min(100, v)) / 100f : 1f;
    }

    public static int bubbleTextOn(int bgColor) {
        int r = (bgColor >> 16) & 0xFF;
        int g = (bgColor >> 8) & 0xFF;
        int b = bgColor & 0xFF;
        int lum = (r * 299 + g * 587 + b * 114) / 1000;
        return lum >= 150 ? 0xFF1A1A1A : 0xFFF0F0F0;
    }
}
