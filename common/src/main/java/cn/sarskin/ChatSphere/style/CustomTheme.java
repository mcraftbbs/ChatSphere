package cn.sarskin.ChatSphere.style;

import cn.sarskin.ChatSphere.platform.PlatformPaths;
import cn.sarskin.ChatSphere.style.ThemeSpec.AnimSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Active custom theme; parsed strictly by ThemeFileParser (data-only values); failures keep the previous theme. */
public final class CustomTheme {
    public static final CustomTheme INSTANCE = new CustomTheme();
    public static final String EXT = ".ctheme";
    public static final String LEGACY_EXT = ".csstyle";
    public static final String[] PRESETS = { "preset-square", "preset-pixel", "preset-original", "preset-stream" };
    /** Bump to force reinstalling stale bundled presets. */
    private static final String PRESET_VERSION_MARK = "preset-version: 4";

    private static final Logger LOGGER = LoggerFactory.getLogger("ChatSphere-Theme");
    private static final Path DIR = PlatformPaths.configDir().resolve("chatsphere").resolve("themes");

    private ThemeSpec active;
    private String activeFile = "";
    private String error;
    private volatile boolean initialized;
    private int revision;

    private CustomTheme() {}

    private void ensureInitialized() {
        if (initialized) return;
        synchronized (this) {
            if (initialized) return;
            try {
                Files.createDirectories(DIR);
            } catch (IOException e) {
                LOGGER.error("Failed to create theme dir {}", DIR, e);
            }
            installPresets();
            initialized = true;
            try {
                cn.sarskin.ChatSphere.config.ModClientConfig cfg =
                        cn.sarskin.ChatSphere.config.ModClientConfig.CONFIG;
                if (cfg.customThemeActive.get()) {
                    String f = cfg.customThemeFile.get();
                    if (f != null && f.endsWith(LEGACY_EXT)) {
                        f = f.substring(0, f.length() - LEGACY_EXT.length()) + EXT;
                        cfg.customThemeFile.set(f);
                        cfg.CONFIG_SPEC.save();
                    }
                    if (f != null && !f.isEmpty()) {
                        int idx = indexOfPreset(f);
                        if (idx >= 0 && idx != cfg.uiCornerStyle.get()) {
                            // preset must follow the corner cards
                            f = PRESETS[cfg.uiCornerStyle.get()] + EXT;
                            cfg.customThemeFile.set(f);
                            cfg.CONFIG_SPEC.save();
                        }
                        load(f);
                    }
                }
            } catch (IllegalStateException ignored) {
                // config not loaded yet
            }
        }
    }

    private void installPresets() {
        migrateLegacyFiles();
        for (String preset : PRESETS) {
            Path target = DIR.resolve(preset + EXT);
            boolean needsInstall = !Files.exists(target);
            if (!needsInstall) {
                try {
                    String content = new String(Files.readAllBytes(target), StandardCharsets.UTF_8);
                    ThemeFileParser.parse(content);
                    if (!content.contains(PRESET_VERSION_MARK)) {
                        LOGGER.info("Preset theme '{}' is outdated; reinstalling from jar", preset);
                        needsInstall = true;
                    }
                } catch (Exception e) {
                    LOGGER.warn("Preset theme '{}' is invalid ({}); reinstalling from jar",
                            preset, e.getMessage());
                    needsInstall = true;
                }
            }
            if (!needsInstall) continue;
            try (java.io.InputStream in = CustomTheme.class.getResourceAsStream(
                    "/assets/chatsphere/themes/" + preset + EXT)) {
                if (in == null) {
                    LOGGER.warn("Bundled preset theme missing from classpath: {}", preset);
                    continue;
                }
                Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Installed/repaired preset theme {}", target.getFileName());
            } catch (IOException e) {
                LOGGER.error("Failed to install preset theme {}", preset, e);
            }
        }
    }

    /** Rename legacy *.csstyle files to the new *.ctheme extension (best-effort). */
    private void migrateLegacyFiles() {
        try {
            if (!Files.isDirectory(DIR)) return;
            try (var stream = Files.list(DIR)) {
                for (Path p : stream.filter(p -> p.getFileName().toString().endsWith(LEGACY_EXT)).toList()) {
                    String name = p.getFileName().toString();
                    Path target = DIR.resolve(name.substring(0, name.length() - LEGACY_EXT.length()) + EXT);
                    if (Files.exists(target)) {
                        Files.deleteIfExists(p);
                        continue;
                    }
                    Files.move(p, target);
                    LOGGER.info("Migrated legacy theme {} -> {}", name, target.getFileName());
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to migrate legacy theme files", e);
        }
    }

    public void init() {
        ensureInitialized();
    }

    public Path dir() { return DIR; }

    public boolean isActive() { return active != null; }
    public ThemeSpec active() { return active; }
    public String currentFile() { return activeFile; }
    public String currentName() { return active == null ? "" : active.name; }
    public String error() { return error; }

    /** Bumped whenever the active theme changes; Theme's color cache keys off this. */
    public int revision() { return revision; }

    public Integer color(boolean dark, String key) {
        ensureInitialized();
        if (active == null) return null;
        return dark ? active.dark.get(key) : active.light.get(key);
    }

    public Integer style(String key) {
        ensureInitialized();
        return active == null ? null : active.styles.get(key);
    }

    /** Seed the config from styles.popupBorder; the config is the renderer's live value. */
    private void syncConfigFromStyles() {
        if (active == null) return;
        Integer v = active.styles.get("popupBorder");
        if (v == null) return;
        try {
            cn.sarskin.ChatSphere.config.ModClientConfig cfg =
                    cn.sarskin.ChatSphere.config.ModClientConfig.CONFIG;
            if (cfg.popupBorder.get() != (v != 0)) {
                cfg.popupBorder.set(v != 0);
                cfg.CONFIG_SPEC.save();
            }
        } catch (IllegalStateException ignored) {
            // config not loaded yet
        }
    }

    public AnimSpec anim(String key) {
        ensureInitialized();
        if (active == null) return AnimSpec.NONE;
        return active.animations.getOrDefault(key, AnimSpec.NONE);
    }

    public List<String> listFiles() {
        List<String> out = new ArrayList<>();
        try {
            if (Files.isDirectory(DIR)) {
                try (var stream = Files.list(DIR)) {
                    stream.filter(p -> p.getFileName().toString().endsWith(EXT))
                            .map(p -> p.getFileName().toString())
                            .sorted(Comparator.naturalOrder())
                            .forEach(out::add);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to list themes", e);
        }
        return out;
    }

    public String read(String fileName) throws IOException {
        Path p = safeResolve(fileName);
        long size = Files.size(p);
        if (size > ThemeValidator.MAX_FILE_BYTES)
            throw new IOException("file too large (" + size + " bytes, max " + ThemeValidator.MAX_FILE_BYTES + ")");
        return new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
    }

    /** Load a theme from disk; on failure keeps the previous theme and records the error. */
    public boolean load(String fileName) {
        try {
            String content = read(fileName);
            ThemeSpec spec = ThemeFileParser.parse(content);
            spec.name = spec.name.isEmpty() ? stripExt(fileName) : spec.name;
            active = spec;
            activeFile = fileName;
            error = null;
            revision++;
            LOGGER.info("Loaded theme '{}' from {}", spec.name, fileName);
            syncConfigFromStyles();
            return true;
        } catch (Exception e) {
            error = e.getMessage();
            LOGGER.warn("Theme '{}' rejected: {}", fileName, error);
            return false;
        }
    }

    /** Apply a spec in memory (editor preview) without touching disk. */
    public void apply(ThemeSpec spec, String fileName) {
        active = spec;
        activeFile = fileName;
        error = null;
        revision++;
    }

    /** Write a theme file and load it; returns false (with error) if content is invalid. */
    public boolean save(String fileName, String content) {
        try {
            ThemeSpec spec = ThemeFileParser.parse(content);
            write(safeResolve(fileName), content);
            spec.name = spec.name.isEmpty() ? stripExt(fileName) : spec.name;
            active = spec;
            activeFile = fileName;
            error = null;
            revision++;
            syncConfigFromStyles();
            return true;
        } catch (Exception e) {
            error = e.getMessage();
            LOGGER.warn("Theme save rejected: {}", error);
            return false;
        }
    }

    public void delete(String fileName) {
        try {
            Files.deleteIfExists(safeResolve(fileName));
            if (fileName.equals(activeFile)) unload();
            revision++;
        } catch (IOException e) {
            error = e.getMessage();
        }
    }

    public void unload() {
        active = null;
        activeFile = "";
        error = null;
        revision++;
    }

    public void clearError() { error = null; }

    /** True when the file is one of the bundled preset files. */
    public static boolean isPreset(String fileName) {
        return indexOfPreset(fileName) >= 0;
    }

    private static int indexOfPreset(String fileName) {
        if (fileName == null) return -1;
        for (int i = 0; i < PRESETS.length; i++) {
            if (fileName.equals(PRESETS[i] + EXT)) return i;
        }
        return -1;
    }

    /** Mutates the active theme in memory and persists it back; null maps keep their blocks untouched. */
    public boolean syncValues(Map<String, Integer> styles, Map<String, Integer> darkColors, Map<String, Integer> lightColors) {
        ensureInitialized();
        if (active == null || activeFile.isEmpty()) return false;
        boolean changed = false;
        if (styles != null) {
            for (Map.Entry<String, Integer> e : styles.entrySet()) {
                if (!e.getValue().equals(active.styles.get(e.getKey()))) {
                    active.styles.put(e.getKey(), e.getValue());
                    changed = true;
                }
            }
        }
        if (darkColors != null) {
            for (Map.Entry<String, Integer> e : darkColors.entrySet()) {
                if (!e.getValue().equals(active.dark.get(e.getKey()))) {
                    active.dark.put(e.getKey(), e.getValue());
                    changed = true;
                }
            }
        }
        if (lightColors != null) {
            for (Map.Entry<String, Integer> e : lightColors.entrySet()) {
                if (!e.getValue().equals(active.light.get(e.getKey()))) {
                    active.light.put(e.getKey(), e.getValue());
                    changed = true;
                }
            }
        }
        if (!changed) return false;
        revision++;
        try {
            write(safeResolve(activeFile), render(active));
            return true;
        } catch (IOException e) {
            error = e.getMessage();
            LOGGER.warn("Theme sync rejected: {}", error);
            return false;
        }
    }

    private static Path safeResolve(String fileName) throws IOException {
        if (fileName == null || fileName.isEmpty()) throw new IOException("empty file name");
        if (!fileName.endsWith(EXT)) throw new IOException("must end with " + EXT);
        Path p = DIR.resolve(fileName).normalize();
        if (!p.startsWith(DIR.normalize())) throw new IOException("illegal path");
        return p;
    }

    private static void write(Path p, String content) throws IOException {
        Files.createDirectories(p.getParent());
        Files.write(p, content.getBytes(StandardCharsets.UTF_8));
    }

    private static String stripExt(String fileName) {
        return fileName.endsWith(EXT) ? fileName.substring(0, fileName.length() - EXT.length()) : fileName;
    }

    public static String template() {
        return """
                // ChatSphere custom theme (.ctheme)
                // CS1; colors #RGB/#RRGGBB/#AARRGGBB; numbers plain/px/%; duration+easing
                // colorSeed: all unset colors derive from this seed (HCT-style tonal derivation).
                CS1;
                theme "My Theme" version 1;

                dark {
                    // screenBg: #1A1A2E;
                    // text: #FFFFFF;
                }

                light {
                    // screenBg: #F0F0F5;
                    // text: #1A1A1A;
                }

                styles {
                    // colorSeed: #8888FF;
                    // bubbleCornerRadius: 6;
                    // messageLineSpacing: 2px;
                    // sidebarWidth: 100px;
                    // avatarRadius: 4px;
                    // blurIntensity: 100%;
                }

                animations {
                    // messageSlideIn: 120ms ease-out;
                    // bubblePopIn: none;
                    // bubbleFadeIn: 180ms ease-out;
                    // notificationPulse: 400ms ease-in-out;
                }
                """;
    }

    /** Serialize a spec back to .ctheme text (used by the in-game editor). */
    public static String render(ThemeSpec spec) {
        StringBuilder sb = new StringBuilder();
        String name = spec.name == null || spec.name.isEmpty() ? "My Theme" : spec.name;
        sb.append("CS1;\n");
        sb.append("theme \"").append(name.replace("\"", "'")).append("\" version 1;\n\n");

        renderBlock(sb, "dark", spec.dark);
        renderBlock(sb, "light", spec.light);

        if (!spec.styles.isEmpty()) {
            sb.append("styles {\n");
            for (Map.Entry<String, Integer> e : spec.styles.entrySet()) {
                String key = kebab(e.getKey());
                int v = e.getValue();
                if ("uiCornerStyle".equals(e.getKey())) {
                    sb.append("    ").append(key).append(": ").append(ThemeValidator.enumName(e.getKey(), v)).append(";\n");
                    continue;
                }
                if (ThemeValidator.STYLE_PROPS.get(e.getKey()).type() == ThemeValidator.ValType.COLOR) {
                    sb.append("    ").append(key).append(": ").append(colorStr(v)).append(";\n");
                    continue;
                }
                String unit = "messageLineSpacing".equals(e.getKey()) || "sidebarWidth".equals(e.getKey())
                        || "avatarRadius".equals(e.getKey()) ? "px"
                        : "blurIntensity".equals(e.getKey()) ? "%" : "";
                sb.append("    ").append(key).append(": ").append(v).append(unit).append(";\n");
            }
            sb.append("}\n\n");
        }

        if (!spec.animations.isEmpty()) {
            sb.append("animations {\n");
            for (Map.Entry<String, AnimSpec> e : spec.animations.entrySet()) {
                AnimSpec a = e.getValue();
                sb.append("    ").append(kebab(e.getKey())).append(": ")
                        .append(a.enabled() ? a.durationMs + "ms " + a.easing : "none").append(";\n");
            }
            sb.append("}\n");
        }
        return sb.toString();
    }

    private static void renderBlock(StringBuilder sb, String blockName, Map<String, Integer> props) {
        if (props.isEmpty()) return;
        sb.append(blockName).append(" {\n");
        for (Map.Entry<String, Integer> e : props.entrySet()) {
            sb.append("    ").append(kebab(e.getKey())).append(": ").append(colorStr(e.getValue())).append(";\n");
        }
        sb.append("}\n\n");
    }

    private static String colorStr(int argb) {
        if ((argb >>> 24) == 0xFF) return String.format("#%06X", argb & 0xFFFFFF);
        return String.format("#%08X", argb);
    }

    private static String kebab(String camel) {
        StringBuilder sb = new StringBuilder(camel.length() + 4);
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) sb.append('-').append(Character.toLowerCase(c));
            else sb.append(c);
        }
        return sb.toString();
    }
}
