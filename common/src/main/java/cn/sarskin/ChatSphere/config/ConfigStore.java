package cn.sarskin.ChatSphere.config;

import cn.sarskin.ChatSphere.platform.PlatformPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Simple JSON config store shared by client & server; replaces NeoForge ModConfigSpec. */
public class ConfigStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigStore.class);
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;
    private final Map<String, Object> values = new HashMap<>();
    private final Map<String, Object> defaults = new HashMap<>();

    public ConfigStore(String fileName) {
        this.file = PlatformPaths.configDir().resolve(fileName);
        load();
    }

    /** Default kept so every key is written on save. */
    public synchronized void registerDefault(String key, Object value) {
        defaults.put(key, value);
    }

    public synchronized boolean getBool(String key, boolean def) {
        Object o = values.get(key);
        return o instanceof Boolean b ? b : def;
    }

    public synchronized int getInt(String key, int def) {
        Object o = values.get(key);
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return def;
    }

    public synchronized String getStr(String key, String def) {
        Object o = values.get(key);
        return o instanceof String s ? s : def;
    }

    public synchronized List<String> getStrList(String key, List<String> def) {
        Object o = values.get(key);
        if (o instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object e : list) {
                if (e instanceof String s) out.add(s);
            }
            return out;
        }
        return def;
    }

    public synchronized void set(String key, Object value) {
        values.put(key, value);
    }

    /** Serializes the current values (defaults merged in) as JSON; used for edit-session snapshots. */
    public synchronized String exportJson() {
        for (Map.Entry<String, Object> e : defaults.entrySet()) {
            values.putIfAbsent(e.getKey(), e.getValue());
        }
        return GSON.toJson(values);
    }

    /** Replaces all values from a previously exported JSON snapshot and persists it. */
    public synchronized void importJson(String json) {
        if (json == null || json.isEmpty()) return;
        try {
            Map<String, Object> loaded = GSON.fromJson(json, MAP_TYPE);
            if (loaded == null) return;
            values.clear();
            values.putAll(loaded);
            save();
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to import config {}: {}", file, e.toString());
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            for (Map.Entry<String, Object> e : defaults.entrySet()) {
                values.putIfAbsent(e.getKey(), e.getValue());
            }
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(values, w);
            }
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.warn("Failed to save config {}: {}", file, e.toString());
        }
    }

    private void load() {
        boolean existed = Files.exists(file);
        if (existed) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Map<String, Object> loaded = GSON.fromJson(r, MAP_TYPE);
                if (loaded != null) values.putAll(loaded);
            } catch (IOException e) {
                LOGGER.warn("Failed to read config {}: {}", file, e.toString());
            } catch (RuntimeException e) {
                LOGGER.error("Config {} is corrupted ({}); backing it up and using defaults", file, e.toString());
                try {
                    Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {
                }
            }
        }
        // integral doubles -> int, keeps files clean
        boolean dirty = false;
        for (Map.Entry<String, Object> e : new ArrayList<>(values.entrySet())) {
            Object v = e.getValue();
            if (v instanceof Double d && d == Math.floor(d) && !d.isInfinite() && !d.isNaN()
                    && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE) {
                values.put(e.getKey(), d.intValue());
                dirty = true;
            }
        }
        if (dirty || !existed) {
            save();
        }
        if (!existed) {
            migrateFromLegacyToml();
        }
    }

    private static final Pattern TOML_KEY_VALUE = Pattern.compile("^\\s*([A-Za-z0-9_]+)\\s*=\\s*(.+?)\\s*$");
    private static final Pattern TOML_BOOL = Pattern.compile("^(true|false)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOML_INT = Pattern.compile("^-?\\d+$");
    private static final Pattern TOML_STRING = Pattern.compile("^\"((?:[^\"\\\\]|\\\\.)*)\"$");
    private static final Pattern TOML_LIST = Pattern.compile("^\\[(.*)\\]$");

    /** One-time migration from NeoForge TOML configs (chatsphere-client.toml / chatsphere-server.toml). */
    private void migrateFromLegacyToml() {
        String legacy = file.getFileName().toString().replace(".json", ".toml");
        Path old = file.getParent().resolve(legacy);
        if (!Files.exists(old)) return;
        try {
            for (String line : Files.readAllLines(old, StandardCharsets.UTF_8)) {
                Matcher m = TOML_KEY_VALUE.matcher(line);
                if (!m.matches()) continue;
                String key = m.group(1);
                String raw = m.group(2);
                Object v = parseTomlValue(raw);
                if (v != null) values.put(key, v);
            }
            Files.move(old, old.resolveSibling(old.getFileName() + ".migrated"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            save();
            LOGGER.info("Migrated legacy config {} -> {}", old.getFileName(), file.getFileName());
        } catch (IOException e) {
            LOGGER.warn("Failed to migrate config {}: {}", old, e.toString());
        }
    }

    private static Object parseTomlValue(String raw) {
        if (TOML_BOOL.matcher(raw).matches()) return Boolean.parseBoolean(raw);
        if (TOML_INT.matcher(raw).matches()) {
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher s = TOML_STRING.matcher(raw);
        if (s.matches()) return s.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        Matcher l = TOML_LIST.matcher(raw);
        if (l.matches()) {
            List<String> out = new ArrayList<>();
            for (String item : l.group(1).split(",")) {
                String t = item.trim();
                if (t.isEmpty()) continue;
                Matcher is = TOML_STRING.matcher(t);
                if (is.matches()) out.add(is.group(1));
                else out.add(t);
            }
            return out;
        }
        return null;
    }
}
