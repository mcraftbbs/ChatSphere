package cn.sarskin.ChatSphere.server.voice;

import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.capture.ServerActivation;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerBroadcastSource;
import su.plo.voice.api.server.player.VoicePlayer;
import su.plo.voice.proto.packets.udp.clientbound.SourceAudioPacket;
import su.plo.voice.proto.packets.udp.serverbound.PlayerAudioPacket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Addon(id = "chatsphere_rooms", name = "ChatSphere Voice Rooms", version = "1.0.0", authors = {"xwwsdd"})
public class PlasmoRoomAddon implements AddonInitializer {
    private static PlasmoRoomAddon instance;

    @InjectPlasmoVoice
    private PlasmoVoiceServer voiceServer;

    private ServerActivation activation;
    private ServerSourceLine sourceLine;
    private final Map<String, RoomInfo> rooms = new ConcurrentHashMap<>();
    private final Map<UUID, ServerBroadcastSource> playerSources = new ConcurrentHashMap<>();

    @Override
    public void onAddonInitialize() {
        instance = this;

        activation = voiceServer.getActivationManager().createBuilder(
                this,
                "chatsphere_room",
                "pv.activation.chatsphere_room",
                "plasmovoice:textures/icons/microphone_priority.png",
                "pv.activation.chatsphere_room",
                50
        ).build();

        sourceLine = voiceServer.getSourceLineManager().createBuilder(
                this,
                "chatsphere_room",
                "pv.source_line.chatsphere_room",
                "plasmovoice:textures/icons/speaker_priority.png",
                50
        ).build();

        activation.onPlayerActivation(this::onRoomActivation);
        activation.onPlayerActivationEnd(this::onRoomActivationEnd);
    }

    @Override
    public void onAddonShutdown() {
        rooms.clear();
        playerSources.clear();
        instance = null;
    }

    private ServerActivation.Result onRoomActivation(VoicePlayer player, PlayerAudioPacket packet) {
        ServerBroadcastSource source = playerSources.get(player.getInstance().getUuid());
        if (source == null) return ServerActivation.Result.IGNORED;

        SourceAudioPacket out = new SourceAudioPacket(
                packet.getSequenceNumber(),
                (byte) source.getState(),
                packet.getData(),
                source.getId(),
                (short) 0
        );
        source.sendAudioPacket(out, null);
        return ServerActivation.Result.HANDLED;
    }

    private ServerActivation.Result onRoomActivationEnd(VoicePlayer player, su.plo.voice.proto.packets.tcp.serverbound.PlayerAudioEndPacket packet) {
        ServerBroadcastSource source = playerSources.get(player.getInstance().getUuid());
        if (source == null) return ServerActivation.Result.IGNORED;

        source.sendPacket(new su.plo.voice.proto.packets.tcp.clientbound.SourceAudioEndPacket(
                source.getId(), packet.getSequenceNumber()
        ));
        return ServerActivation.Result.HANDLED;
    }

    public static void joinRoom(String channelId, String roomName, UUID playerUuid) {
        PlasmoRoomAddon a = instance;
        if (a == null) return;

        String key = channelId + ":" + roomName;
        RoomInfo room = a.rooms.computeIfAbsent(key, k -> new RoomInfo());

        var voicePlayer = a.voiceServer.getPlayerManager().getPlayerById(playerUuid);
        if (voicePlayer.isEmpty()) return;

        room.members.add(playerUuid);

        List<VoicePlayer> others = new ArrayList<>();
        for (UUID uid : room.members) {
            if (!uid.equals(playerUuid)) {
                a.voiceServer.getPlayerManager().getPlayerById(uid).ifPresent(others::add);
            }
        }

        ServerBroadcastSource source = a.sourceLine.createBroadcastSource(false);
        source.setSender(voicePlayer.get());
        source.addFilter(p -> !p.getInstance().getUuid().equals(playerUuid));
        Set<VoicePlayer> targetSet = new HashSet<>(others);
        source.setPlayers(targetSet);
        a.playerSources.put(playerUuid, source);

        for (VoicePlayer other : others) {
            ServerBroadcastSource otherSource = a.playerSources.get(other.getInstance().getUuid());
            if (otherSource != null) {
                Set<VoicePlayer> updated = new HashSet<>(otherSource.getPlayers());
                voicePlayer.ifPresent(updated::add);
                otherSource.setPlayers(updated);
            }
        }
    }

    public static void leaveRoom(String channelId, String roomName, UUID playerUuid) {
        PlasmoRoomAddon a = instance;
        if (a == null) return;

        String key = channelId + ":" + roomName;
        RoomInfo room = a.rooms.get(key);
        if (room == null) return;

        room.members.remove(playerUuid);

        ServerBroadcastSource source = a.playerSources.remove(playerUuid);
        if (source != null) {
            a.sourceLine.removeSource(source);
        }

        for (UUID uid : room.members) {
            ServerBroadcastSource otherSource = a.playerSources.get(uid);
            if (otherSource != null) {
                Set<VoicePlayer> updated = new HashSet<>(otherSource.getPlayers());
                updated.removeIf(p -> p.getInstance().getUuid().equals(playerUuid));
                otherSource.setPlayers(updated);
            }
        }

        if (room.members.isEmpty()) {
            a.rooms.remove(key);
        }
    }

    private static class RoomInfo {
        final Set<UUID> members = ConcurrentHashMap.newKeySet();
    }
}
