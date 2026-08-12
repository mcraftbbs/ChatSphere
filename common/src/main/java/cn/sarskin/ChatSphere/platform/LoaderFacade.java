package cn.sarskin.ChatSphere.platform;

/** Loader-agnostic mod detection; each platform installs a provider at init. */
public final class LoaderFacade {
    public interface Provider {
        boolean isModLoaded(String modId);
    }

    private static volatile Provider provider = id -> false;

    private LoaderFacade() {}

    public static void setProvider(Provider p) {
        if (p != null) provider = p;
    }

    public static boolean isModLoaded(String modId) {
        return provider.isModLoaded(modId);
    }
}
