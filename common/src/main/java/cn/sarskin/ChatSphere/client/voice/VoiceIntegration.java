package cn.sarskin.ChatSphere.client.voice;

import cn.sarskin.ChatSphere.platform.LoaderFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class VoiceIntegration {
    private static final Logger LOGGER = LoggerFactory.getLogger("VoiceIntegration");
    private static volatile boolean checked;
    private static volatile boolean svcAvailable;
    private static volatile boolean plasmoAvailable;
    private static Object svcApi;
    private static final Object SVC_LOCK = new Object();

    private static Class<?> svcApiClass;
    private static Method svcGroupBuilder;
    private static Class<?> svcGroupBuilderClass;
    private static Method svcSetId;
    private static Method svcSetName;
    private static Class<?> svcGroupTypeClass;
    private static Object svcIsolatedType;
    private static Method svcSetType;
    private static Method svcSetPassword;
    private static Method svcBuild;
    private static Class<?> svcConnectionClass;
    private static Method svcGetConnection;
    private static Method svcSetGroup;
    private static Method svcGetGroup;
    private static Method svcGroupGetName;
    private static Class<?> svcGroupClass;
    private static volatile boolean svcReflectionCached;

    public static void initSvc() {
        if (!checked) detect();
    }

    public static boolean isAnyVoiceModPresent() {
        if (!checked) detect();
        return svcAvailable || plasmoAvailable;
    }

    public static boolean isSvcPresent() {
        if (!checked) detect();
        return svcAvailable;
    }

    public static boolean isPlasmoPresent() {
        if (!checked) detect();
        return plasmoAvailable;
    }

    private static void detect() {
        checked = true;
        try {
            svcAvailable = LoaderFacade.isModLoaded("voicechat");
        } catch (Exception e) {
            svcAvailable = false;
        }
        try {
            plasmoAvailable = LoaderFacade.isModLoaded("plasmovoice");
        } catch (Exception e) {
            plasmoAvailable = false;
        }
    }

    public static void joinVoiceRoom(String channelId, String roomName, UUID playerUuid) {
        if (isSvcPresent()) joinSvcGroup(channelId, roomName, playerUuid);
        if (isPlasmoPresent()) joinPlasmoBroadcast(channelId, roomName, playerUuid);
    }

    public static void leaveVoiceRoom(String channelId, String roomName, UUID playerUuid) {
        if (isSvcPresent()) leaveSvcGroup(channelId, roomName, playerUuid);
        if (isPlasmoPresent()) leavePlasmoBroadcast(channelId, roomName, playerUuid);
    }

    private static void setSvcApi(Object api) {
        synchronized (SVC_LOCK) {
            svcApi = api;
        }
    }

    public static Object getSvcApi() {
        synchronized (SVC_LOCK) {
            return svcApi;
        }
    }

    public static void onServerStarted(Object api) {
        if (isSvcPresent()) {
            setSvcApi(api);
            cacheSvcReflection();
        }
    }

    private static Method findMethod(Class<?> clz, String name, Class<?>... paramTypes) {
        try { return clz.getMethod(name, paramTypes); }
        catch (NoSuchMethodException e) { return null; }
    }

    private static void cacheSvcReflection() {
        try {
            svcApiClass = Class.forName("de.maxhenkel.voicechat.api.VoicechatServerApi");
            svcGroupBuilder = svcApiClass.getMethod("groupBuilder");
            svcGroupBuilderClass = Class.forName("de.maxhenkel.voicechat.api.Group$Builder");
            svcSetId = svcGroupBuilderClass.getMethod("setId", UUID.class);
            svcSetName = svcGroupBuilderClass.getMethod("setName", String.class);
            svcGroupTypeClass = Class.forName("de.maxhenkel.voicechat.api.Group$Type");
            Field isolatedField = svcGroupTypeClass.getField("ISOLATED");
            svcIsolatedType = isolatedField.get(null);
            svcSetType = svcGroupBuilderClass.getMethod("setType", svcGroupTypeClass);
            svcSetPassword = svcGroupBuilderClass.getMethod("setPassword", String.class);
            svcBuild = svcGroupBuilderClass.getMethod("build");
            svcConnectionClass = Class.forName("de.maxhenkel.voicechat.api.VoicechatConnection");
            svcGetConnection = findMethod(svcApiClass, "getConnectionOf", UUID.class);
            if (svcGetConnection == null) svcGetConnection = findMethod(svcApiClass, "fromUuid", UUID.class);
            svcGroupClass = Class.forName("de.maxhenkel.voicechat.api.Group");
            svcSetGroup = svcConnectionClass.getMethod("setGroup", svcGroupClass);
            svcGetGroup = findMethod(svcConnectionClass, "getGroup");
            svcGroupGetName = findMethod(svcGroupClass, "getName");
            svcReflectionCached = true;
        } catch (Throwable e) {
            svcReflectionCached = false;
            LOGGER.warn("SVC reflection caching failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void joinSvcGroup(String channelId, String roomName, UUID playerUuid) {
        try {
            Object api = getSvcApi();
            if (api == null) {
                LOGGER.warn("SVC join skipped: server API not captured yet (VoicechatServerStartedEvent never fired?)");
                return;
            }
            if (!svcReflectionCached) {
                LOGGER.warn("SVC join skipped: reflection cache is empty, see the caching warning above");
                return;
            }
            String groupName = expectedGroupName(channelId, roomName);
            UUID groupId = UUID.nameUUIDFromBytes(groupName.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Object builder = svcGroupBuilder.invoke(api);
            svcSetId.invoke(builder, groupId);
            svcSetName.invoke(builder, groupName);
            svcSetType.invoke(builder, svcIsolatedType);
            svcSetPassword.invoke(builder, "");
            Object group = svcBuild.invoke(builder);
            Object connection = svcGetConnection.invoke(api, playerUuid);
            if (connection == null) {
                // A group only reaches SVC's list once a connection adopts it, so a null connection means the room never appears
                LOGGER.warn("SVC join skipped: no voice connection for {} (player not voice-connected yet), group={}",
                        playerUuid, groupName);
                return;
            }
            svcSetGroup.invoke(connection, group);
            LOGGER.info("SVC joined group={} for {}", groupName, playerUuid);
        } catch (Throwable e) {
            LOGGER.warn("SVC join failed for group chatsphere_{}_{}: {}", channelId, roomName, e.toString());
        }
    }

    /** True when SVC already has a connection object for this player, i.e. voice is handshaked. */
    public static boolean isVoiceConnected(UUID playerUuid) {
        if (!isSvcPresent() || !svcReflectionCached || playerUuid == null) return false;
        try {
            Object api = getSvcApi();
            if (api == null) return false;
            return svcGetConnection.invoke(api, playerUuid) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    /** Groups are rebuilt per server start, so a member list can survive while the connection sits in no group. */
    public static boolean isInSvcGroup(String channelId, String roomName, UUID playerUuid) {
        if (!isSvcPresent()) return true;
        if (!svcReflectionCached || playerUuid == null) return true;
        try {
            Object api = getSvcApi();
            if (api == null) return true;
            Object connection = svcGetConnection.invoke(api, playerUuid);
            if (connection == null) return false;
            Object group = svcGetGroup.invoke(connection);
            if (group == null) return false;
            Object name = svcGroupGetName.invoke(group);
            return expectedGroupName(channelId, roomName).equals(name);
        } catch (Throwable e) {
            // A probe that cannot read the state must not kick the player out of the room
            return true;
        }
    }

    private static String expectedGroupName(String channelId, String roomName) {
        return "chatsphere_" + channelId.replace(":", "_") + "_" + roomName;
    }

    @SuppressWarnings("unchecked")
    private static void leaveSvcGroup(String channelId, String roomName, UUID playerUuid) {
        try {
            Object api = getSvcApi();
            if (api == null || !svcReflectionCached) return;
            Object connection = svcGetConnection.invoke(api, playerUuid);
            if (connection != null) {
                svcSetGroup.invoke(connection, new Object[] { null });
            }
        } catch (Throwable e) {
            LOGGER.warn("SVC leave failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void joinPlasmoBroadcast(String channelId, String roomName, UUID playerUuid) {
        try {
            Class.forName("cn.sarskin.ChatSphere.server.voice.PlasmoRoomAddon")
                    .getMethod("joinRoom", String.class, String.class, UUID.class)
                    .invoke(null, channelId, roomName, playerUuid);
        } catch (Exception e) {
            LOGGER.warn("PV joinRoom failed: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private static void leavePlasmoBroadcast(String channelId, String roomName, UUID playerUuid) {
        try {
            Class.forName("cn.sarskin.ChatSphere.server.voice.PlasmoRoomAddon")
                    .getMethod("leaveRoom", String.class, String.class, UUID.class)
                    .invoke(null, channelId, roomName, playerUuid);
        } catch (Exception e) {
            LOGGER.warn("PV leaveRoom failed: {}", e.getMessage());
        }
    }
}