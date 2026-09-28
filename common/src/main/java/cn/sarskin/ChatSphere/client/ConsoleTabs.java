package cn.sarskin.ChatSphere.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Client side console tabs, kept as JSON in the client config. */
public final class ConsoleTabs {
    private ConsoleTabs() {}

    public record Tab(String name, String pattern, boolean hideFromAll) {
        public boolean matches(String text) {
            if (pattern == null || pattern.isEmpty()) return false;
            Pattern compiled = compiled(pattern);
            return compiled != null && compiled.matcher(text).find();
        }
    }

    // Cached per pattern; a broken pattern caches as null
    private static final Map<String, Pattern> COMPILED = new ConcurrentHashMap<>();

    private static Pattern compiled(String pattern) {
        return COMPILED.computeIfAbsent(pattern, p -> {
            try {
                return Pattern.compile(p);
            } catch (PatternSyntaxException e) {
                return null;
            }
        });
    }

    public static List<Tab> parse(String json) {
        List<Tab> tabs = new ArrayList<>();
        if (json == null || json.isBlank()) return tabs;
        try {
            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return tabs;
            for (JsonElement element : root.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                String name = object.has("name") ? object.get("name").getAsString() : "";
                String pattern = object.has("pattern") ? object.get("pattern").getAsString() : "";
                boolean hide = object.has("hideFromAll") && object.get("hideFromAll").getAsBoolean();
                if (name.isBlank() && pattern.isBlank()) continue;
                tabs.add(new Tab(name.isBlank() ? pattern : name, pattern, hide));
            }
        } catch (RuntimeException e) {
            return new ArrayList<>();
        }
        return tabs;
    }

    public static String toJson(List<Tab> tabs) {
        JsonArray array = new JsonArray();
        for (Tab tab : tabs) {
            JsonObject object = new JsonObject();
            object.addProperty("name", tab.name());
            object.addProperty("pattern", tab.pattern());
            object.addProperty("hideFromAll", tab.hideFromAll());
            array.add(object);
        }
        return array.toString();
    }

    /* Rows for one tab, oldest first; the all tab drops rows another tab claims exclusively. */
    public static List<Integer> build(List<ChatMessageData> messages, List<Tab> tabs, int selectedTab) {
        boolean filtering = !tabs.isEmpty() && selectedTab > 0 && selectedTab <= tabs.size();
        Tab selected = filtering ? tabs.get(selectedTab - 1) : null;

        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            String text = messages.get(i).plainText();
            if (selected != null) {
                if (!selected.matches(text)) continue;
            } else if (!tabs.isEmpty() && hiddenFromAll(tabs, text)) {
                continue;
            }
            rows.add(i);
        }
        return rows;
    }

    private static boolean hiddenFromAll(List<Tab> tabs, String text) {
        for (Tab tab : tabs) {
            if (tab.hideFromAll() && tab.matches(text)) return true;
        }
        return false;
    }
}
