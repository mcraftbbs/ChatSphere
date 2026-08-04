package cn.sarskin.ChatSphere.client.ui;

import cn.sarskin.ChatSphere.style.ThemeSpec.AnimSpec;

/** Easing/progress helpers for theme animations. -1 means "not animating". */
public final class ThemeAnim {
    private ThemeAnim() {}

    /** 0..1 progress -> eased value; unknown easings stay linear. */
    public static float ease(String easing, float t) {
        return switch (easing) {
            case "ease-in" -> t * t;
            case "ease-out" -> 1f - (1f - t) * (1f - t);
            case "ease-in-out" -> t < 0.5f ? 2f * t * t : 1f - (2f - 2f * t) * (2f - 2f * t) / 2f;
            default -> t;
        };
    }

    /** Eased 0..1 progress of an animation started at spawnMs; -1 if disabled, not started or done. */
    public static float progress(long spawnMs, AnimSpec spec) {
        if (spawnMs <= 0 || !spec.enabled()) return -1;
        long age = System.currentTimeMillis() - spawnMs;
        if (age >= spec.durationMs) return -1;
        return ease(spec.easing, Math.max(0f, (float) age / spec.durationMs));
    }

    /** Looping 0..1 pulse, period = durationMs; -1 when disabled. */
    public static float pulseFactor(AnimSpec spec) {
        if (!spec.enabled()) return -1;
        long period = Math.max(1, spec.durationMs);
        float t = (System.currentTimeMillis() % period) / (float) period;
        return (float) (0.5 + 0.5 * Math.sin(2 * Math.PI * t));
    }
}
