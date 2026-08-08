package cn.sarskin.ChatSphere.style;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * White-list of theme properties: key -> value type/range.
 * Anything not listed here rejects the whole file (see ThemeFileParser).
 */
public final class ThemeValidator {
    private ThemeValidator() {}

    public enum ValType { COLOR, NUMBER, PERCENT, DURATION, ANIM, ENUM }

    public record PropDef(ValType type, int min, int max, List<String> enums) {
        static PropDef color() { return new PropDef(ValType.COLOR, 0, 0, null); }
        static PropDef num(int min, int max) { return new PropDef(ValType.NUMBER, min, max, null); }
        static PropDef percent(int min, int max) { return new PropDef(ValType.PERCENT, min, max, null); }
        static PropDef duration(int min, int max) { return new PropDef(ValType.DURATION, min, max, null); }
        static PropDef enumOf(String... values) { return new PropDef(ValType.ENUM, 0, 0, List.of(values)); }
    }

    /** Enum value -> integer mapping (also used to serialize back to file). */
    public static int enumIndex(String key, String value) {
        if ("uiCornerStyle".equals(key)) {
            return switch (value) {
                case "square" -> 0;
                case "pixel" -> 1;
                case "stream" -> 3;
                default -> 2;
            };
        }
        return 0;
    }

    public static String enumName(String key, int index) {
        if ("uiCornerStyle".equals(key)) {
            return switch (index) {
                case 0 -> "square";
                case 1 -> "pixel";
                case 3 -> "stream";
                default -> "rounded";
            };
        }
        return "";
    }

    /** Color keys available inside the dark { } and light { } blocks (camelCase id -> def). */
    public static final Map<String, PropDef> COLOR_PROPS = new LinkedHashMap<>();
    /** Numeric keys available inside the styles { } block. */
    public static final Map<String, PropDef> STYLE_PROPS = new LinkedHashMap<>();
    /** Animation keys available inside the animations { } block (duration ms + easing). */
    public static final Map<String, PropDef> ANIM_PROPS = new LinkedHashMap<>();

    public static final Set<String> ALLOWED_BLOCKS = Set.of("dark", "light", "styles", "animations");
    public static final Set<String> ALLOWED_EASINGS = Set.of("none", "linear", "ease-in", "ease-out", "ease-in-out");

    public static final int MAX_FILE_BYTES = 128 * 1024;
    public static final int MAX_PROPS = 200;
    public static final int MAX_BLOCKS = 8;

    static {
        for (String key : List.of(
                "screenBg", "sidebarBg", "panelBg", "panelBg2", "popupBg", "popupBg2", "popupBg3",
                "searchBg", "inputBg", "notifGradTop", "notifGradBot", "replyBarBg", "toolbarBg", "previewSwatchBg",
                "popupOutline", "popupOutline2", "emojiOutline", "itemPickerOutline", "divider", "accentLine",
                "sectionLine", "scrollThumb", "scrollTrack",
                "accent", "hoverRow", "activeRow", "menuHover", "iconBtnBg", "iconBtnHover", "searchCloseBg",
                "emojiCellBg", "emojiTabBg", "emojiTabHover", "emojiTabSel", "slotBg", "slotHover",
                "text", "textMain", "textDim", "textFaint", "textInactive", "floatingText", "floatingTextDim",
                "inputText", "searchPlaceholder",
                "railBg", "msgHover", "inputPillBg", "inputPillBorder",
                "bubbleOwn", "bubbleOther", "bubbleInfoLine", "cmdBubbleOwn", "cmdBubbleOther",
                "bubbleGradientTop", "bubbleGradientBottom",
                "toggleOn", "toggleOff", "toggleKnob")) {
            COLOR_PROPS.put(key, PropDef.color());
        }

        STYLE_PROPS.put("bubbleCornerRadius", PropDef.num(0, 24));
        STYLE_PROPS.put("messageLineSpacing", PropDef.num(0, 24));
        STYLE_PROPS.put("sidebarWidth", PropDef.num(60, 240));
        STYLE_PROPS.put("avatarRadius", PropDef.num(0, 20));
        STYLE_PROPS.put("blurIntensity", PropDef.percent(0, 100));
        STYLE_PROPS.put("uiCornerStyle", PropDef.enumOf("square", "pixel", "rounded", "stream"));
        STYLE_PROPS.put("colorSeed", PropDef.color());

        ANIM_PROPS.put("messageSlideIn", new PropDef(ValType.ANIM, 0, 2000, null));
        ANIM_PROPS.put("bubblePopIn", new PropDef(ValType.ANIM, 0, 2000, null));
        ANIM_PROPS.put("bubbleFadeIn", new PropDef(ValType.ANIM, 0, 2000, null));
        ANIM_PROPS.put("notificationPulse", new PropDef(ValType.ANIM, 0, 2000, null));
    }

    /** Kebab-case (theme file) -> camelCase (internal). Unknown chars rejected by parser regex. */
    public static String normalize(String propName) {
        StringBuilder sb = new StringBuilder(propName.length());
        boolean up = false;
        for (int i = 0; i < propName.length(); i++) {
            char c = propName.charAt(i);
            if (c == '-') { up = true; continue; }
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return sb.toString();
    }

    public static boolean isColorKey(String camel) { return COLOR_PROPS.containsKey(camel); }
    public static boolean isStyleKey(String camel) { return STYLE_PROPS.containsKey(camel); }
    public static boolean isAnimKey(String camel) { return ANIM_PROPS.containsKey(camel); }

    /** Strict hex parsing; caller guarantees the regex already matched. */
    public static int parseColorHex(String hex) {
        String h = hex.substring(1);
        if (h.length() == 3) {
            int r = hexVal(h.charAt(0)), g = hexVal(h.charAt(1)), b = hexVal(h.charAt(2));
            return 0xFF000000 | (r * 0x11 << 16) | (g * 0x11 << 8) | (b * 0x11);
        }
        if (h.length() == 6) {
            return 0xFF000000 | Integer.parseInt(h, 16);
        }
        return (int) Long.parseLong(h, 16); // 8-digit AARRGGBB
    }

    private static int hexVal(char c) {
        return c <= '9' ? c - '0' : (Character.toLowerCase(c) - 'a' + 10);
    }
}
