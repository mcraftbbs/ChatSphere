package cn.sarskin.ChatSphere.platform;

import java.nio.file.Path;

/** Loader-agnostic paths; each platform installs a provider at init. */
public final class PlatformPaths {
    public interface Provider {
        Path gameDir();

        Path configDir();
    }

    private static volatile Provider provider = new Provider() {
        @Override
        public Path gameDir() {
            return Path.of(System.getProperty("user.dir"));
        }

        @Override
        public Path configDir() {
            return Path.of(System.getProperty("user.dir"));
        }
    };

    private PlatformPaths() {}

    public static void setProvider(Provider p) {
        if (p != null) provider = p;
    }

    public static Path gameDir() {
        return provider.gameDir();
    }

    public static Path configDir() {
        return provider.configDir();
    }
}
