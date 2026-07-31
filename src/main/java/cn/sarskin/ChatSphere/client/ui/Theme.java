package cn.sarskin.ChatSphere.client.ui;

import cn.sarskin.ChatSphere.config.ModClientConfig;

public final class Theme {
    private Theme() {}

    public static boolean isDark() {
        return ModClientConfig.CONFIG.themeDark.get();
    }

    public static int cornerStyle() {
        return ModClientConfig.CONFIG.uiCornerStyle.get();
    }

    public static boolean originalStyle() {
        return cornerStyle() == 2;
    }

    // ---------- Surfaces ----------

    public static int screenBg() {
        if (isDark()) return ModClientConfig.CONFIG.backgroundBlur.get() ? 0xCC1A1A2E : 0xE61A1A2E;
        return ModClientConfig.CONFIG.backgroundBlur.get() ? 0xBBF0F0F5 : 0xE6F0F0F5;
    }

    public static int sidebarBg() {
        return isDark() ? 0xDD1A1A2E : 0xDDE8E8F2;
    }

    public static int panelBg() {
        return isDark() ? 0xDD2A2A4E : 0xDDEAEAf4;
    }

    public static int panelBg2() {
        return isDark() ? 0xDD1E1E3E : 0xDDEAEAf4;
    }

    public static int popupBg() {
        return isDark() ? 0xDD222244 : 0xDDEDEDF6;
    }

    public static int popupBg2() {
        return isDark() ? 0xCC222244 : 0xCCE8E8F2;
    }

    public static int popupBg3() {
        return isDark() ? 0xCC1A1A2E : 0xCCECECF4;
    }

    public static int searchBg() {
        return isDark() ? 0xBB1A1A2E : 0xCCFFFFFF;
    }

    public static int inputBg() {
        return isDark() ? 0x44111122 : 0x33000000;
    }

    public static int notifGradTop() {
        return isDark() ? 0xCC1A1A3E : 0xCCE0E0EA;
    }

    public static int notifGradBot() {
        return isDark() ? 0xCC2A2A4E : 0xCCEAEAF4;
    }

    public static int replyBarBg() {
        return isDark() ? 0xCC333355 : 0xCCE4E4EE;
    }

    public static int toolbarBg() {
        return isDark() ? 0x88000000 : 0x88FFFFFF;
    }

    public static int previewSwatchBg() {
        return isDark() ? 0xFF3A3A4A : 0xFFDDDDE8;
    }

    // ---------- Borders & lines ----------

    public static int popupOutline() {
        return isDark() ? 0x66FFFFFF : 0x66000000;
    }

    public static int popupOutline2() {
        return isDark() ? 0x88FFFFFF : 0x88000000;
    }

    public static int emojiOutline() {
        return isDark() ? 0x55FFFFFF : 0x55000000;
    }

    public static int itemPickerOutline() {
        return isDark() ? 0x55FFFFFF : 0x55000000;
    }

    public static int divider() {
        return isDark() ? 0x225A4A7E : 0x338888CC;
    }

    public static int accentLine() {
        return isDark() ? 0x445A4A7E : 0x448888CC;
    }

    public static int sectionLine() {
        return isDark() ? 0x44FFFFFF : 0x339999BB;
    }

    public static int scrollThumb() {
        return isDark() ? 0x706666AA : 0x70AAAAEE;
    }

    public static int scrollTrack() {
        return isDark() ? 0x30333333 : 0x30888888;
    }

    // ---------- Accent & selection ----------

    public static int accent() {
        return isDark() ? 0xFF8888FF : 0xFF6666DD;
    }

    public static int hoverRow() {
        return isDark() ? 0x22333388 : 0x33AAAAFF;
    }

    public static int activeRow() {
        return isDark() ? 0x66333388 : 0x66AAAAFF;
    }

    public static int menuHover() {
        return isDark() ? 0x44448888 : 0x448888FF;
    }

    public static int iconBtnBg() {
        return isDark() ? 0x44000000 : 0x22FFFFFF;
    }

    public static int iconBtnHover() {
        return isDark() ? 0x66333388 : 0x66AAAAFF;
    }

    public static int searchCloseBg() {
        return isDark() ? 0x22333333 : 0x44AAAAAA;
    }

    public static int emojiCellBg() {
        return isDark() ? 0x44446688 : 0x448888CC;
    }

    public static int emojiTabBg() {
        return isDark() ? 0x22000000 : 0x22FFFFFF;
    }

    public static int emojiTabHover() {
        return isDark() ? 0x44333366 : 0x44AAAAFF;
    }

    public static int emojiTabSel() {
        return isDark() ? 0x664466AA : 0x66AAAAFF;
    }

    public static int slotBg() {
        return isDark() ? 0x44000000 : 0x22FFFFFF;
    }

    public static int slotHover() {
        return isDark() ? 0x66444488 : 0x66AAAAFF;
    }

    // ---------- Text ----------

    public static int text() {
        return isDark() ? 0xFFFFFFFF : 0xFF1A1A1A;
    }

    public static int textMain() {
        return isDark() ? 0xFFCCCCCC : 0xFF333333;
    }

    public static int textDim() {
        return isDark() ? 0xFF888888 : 0xFF666666;
    }

    public static int textFaint() {
        return isDark() ? 0xFF555555 : 0xFF888888;
    }

    public static int textInactive() {
        return isDark() ? 0xFFAAAAAA : 0xFF666666;
    }

    public static int searchPlaceholder() {
        return isDark() ? 0xFF666666 : 0xFF888888;
    }

    // ---------- Bubbles ----------

    public static int bubbleOwnFallback() {
        return isDark() ? 0xFF1D3B5C : 0xFFD9E8FF;
    }

    public static int bubbleOtherFallback() {
        return isDark() ? 0xFF26262E : 0xFFFFFFFF;
    }

    public static int cmdBubbleOwnBg() {
        return isDark() ? 0xFF2D2D2D : 0xFFE4E4E8;
    }

    public static int cmdBubbleOtherBg() {
        return isDark() ? 0xFF1E1E2E : 0xFFDADAE2;
    }

    public static int bubbleInfoLine() {
        return isDark() ? 0xFF555555 : 0xFF888888;
    }

    // ---------- Toggle ----------

    public static int toggleOn() {
        return isDark() ? 0xFF2E7D4F : 0xFF3BA55D;
    }

    public static int toggleOff() {
        return isDark() ? 0xFF3A3A4A : 0xFFB8B8C4;
    }

    public static int toggleKnob() {
        return isDark() ? 0xFFF0F0F0 : 0xFFFFFFFF;
    }

    public static int bubbleTextOn(int bgColor) {
        int r = (bgColor >> 16) & 0xFF;
        int g = (bgColor >> 8) & 0xFF;
        int b = bgColor & 0xFF;
        int lum = (r * 299 + g * 587 + b * 114) / 1000;
        return lum >= 150 ? 0xFF1A1A1A : 0xFFF0F0F0;
    }
}
