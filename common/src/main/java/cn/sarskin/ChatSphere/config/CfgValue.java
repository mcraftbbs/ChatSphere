package cn.sarskin.ChatSphere.config;

import java.util.List;

/** Typed handles over a {@link ConfigStore}; API mirrors the old ModConfigSpec values (.get()/.set()). */
public final class CfgValue {
    private CfgValue() {}

    public static final class Bool {
        private final ConfigStore store;
        private final String key;
        private final boolean def;

        public Bool(ConfigStore store, String key, boolean def) {
            this.store = store;
            this.key = key;
            this.def = def;
            store.registerDefault(key, def);
        }

        public boolean get() {
            return store.getBool(key, def);
        }

        public void set(boolean v) {
            store.set(key, v);
        }
    }

    public static final class Int {
        private final ConfigStore store;
        private final String key;
        private final int def;

        public Int(ConfigStore store, String key, int def) {
            this.store = store;
            this.key = key;
            this.def = def;
            store.registerDefault(key, def);
        }

        public int get() {
            return store.getInt(key, def);
        }

        public void set(int v) {
            store.set(key, v);
        }
    }

    public static final class Str {
        private final ConfigStore store;
        private final String key;
        private final String def;

        public Str(ConfigStore store, String key, String def) {
            this.store = store;
            this.key = key;
            this.def = def;
            store.registerDefault(key, def);
        }

        public String get() {
            return store.getStr(key, def);
        }

        public void set(String v) {
            store.set(key, v);
        }
    }

    public static final class StrList {
        private final ConfigStore store;
        private final String key;
        private final List<String> def;

        public StrList(ConfigStore store, String key, List<String> def) {
            this.store = store;
            this.key = key;
            this.def = def;
            store.registerDefault(key, def);
        }

        public List<String> get() {
            return store.getStrList(key, def);
        }

        public void set(List<String> v) {
            store.set(key, v);
        }
    }
}
