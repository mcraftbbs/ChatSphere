package cn.sarskin.ChatSphere.style;

import java.util.LinkedHashMap;
import java.util.Map;

/** Parsed .ctheme file. Values are validated/clamped by ThemeValidator; data only, nothing executes. */
public final class ThemeSpec {
    public String name = "";

    /** dark-mode color overrides: key (camelCase) -> ARGB int */
    public final Map<String, Integer> dark = new LinkedHashMap<>();
    /** light-mode color overrides: key (camelCase) -> ARGB int */
    public final Map<String, Integer> light = new LinkedHashMap<>();
    /** numeric style overrides: key -> clamped int */
    public final Map<String, Integer> styles = new LinkedHashMap<>();
    /** animation specs: duration ms + easing for messageSlideIn / bubblePopIn / notificationPulse */
    public final Map<String, AnimSpec> animations = new LinkedHashMap<>();

    public static final class AnimSpec {
        public static final AnimSpec NONE = new AnimSpec(0, "none");

        public final int durationMs;
        public final String easing;

        public AnimSpec(int durationMs, String easing) {
            this.durationMs = durationMs;
            this.easing = easing;
        }

        public boolean enabled() {
            return durationMs > 0 && !"none".equals(easing);
        }
    }
}
