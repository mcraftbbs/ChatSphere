package cn.sarskin.ChatSphere.style;

import cn.sarskin.ChatSphere.style.ThemeSpec.AnimSpec;
import net.neoforged.fml.loading.FMLPaths;
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

/**
 * Central manager for the active custom theme.
 *
 * Security model:
 *  - themes are read ONLY from config/chatsphere/themes/*.ctheme (path-normalized, local)
 *  - files are parsed by ThemeFileParser with a strict white-list; any failure rejects
 *    the whole file and keeps the previous theme (or built-in defaults)
 *  - values are data-only (ints); nothing is executed
 */
public final class CustomTheme {
    public static final CustomTheme INSTANCE = new CustomTheme();
    public static final String EXT = ".ctheme";
    public static final String LEGACY_EXT = ".csstyle";
    public static final String[] PRESETS = { "preset-square", "preset-pixel", "preset-original", "preset-stream" };

    private static final Logger LOGGER = LoggerFactory.getLogger("ChatSphere-Theme");
    private static final Path DIR = FMLPaths.CONFIGDIR.get().resolve("chatsphere").resolve("themes");

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
                    if (f != null && !f.isEmpty()) load(f);
                }
            } catch (IllegalStateException ignored) {
                // config not loaded yet
            }
        }
    }

    /** Copy bundled preset themes (mod jar resources) into the themes dir on first run. */
    private void installPresets() {
        migrateLegacyFiles();
        for (String preset : PRESETS) {
            Path target = DIR.resolve(preset + EXT);
            if (Files.exists(target)) continue;
            try (java.io.InputStream in = CustomTheme.class.getResourceAsStream(
                    "/assets/chatsphere/themes/" + preset + EXT)) {
                if (in == null) {
                    LOGGER.warn("Bundled preset theme missing from classpath: {}", preset);
                    continue;
                }
                Files.copy(in, target);
                LOGGER.info("Installed preset theme {}", target.getFileName());
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

    public AnimSpec anim(String key) {
        ensureInitialized();
        if (active == null) return AnimSpec.NONE;
        return active.animations.getOrDefault(key, AnimSpec.NONE);
    }

    // ---------- File operations (path-safe, local only) ----------

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
        for (String p : PRESETS) {
            if (fileName != null && fileName.equals(p + EXT)) return true;
        }
        return false;
    }

    /**
     * Config-screen linkage: mutate the active theme's style/color values in memory and
     * persist them back to its file. No-op when no theme file is active. Pass null maps
     * for blocks that should not be touched.
     */
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

    // ---------- Serialization ----------

    public static String template() {
        return """
                // ChatSphere custom theme (.ctheme)
                // CS1 marks the format; colors: #RGB | #RRGGBB | #AARRGGBB   Numbers: plain or px/%  Duration: 120ms
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
