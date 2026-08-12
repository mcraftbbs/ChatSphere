package cn.sarskin.ChatSphere.client.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VoiceRoom {
    public String name;
    public final List<String> members = new ArrayList<>();

    public VoiceRoom() {}

    public VoiceRoom(String name) {
        this.name = name;
    }

    public VoiceRoom(String name, List<String> members) {
        this.name = name;
        this.members.addAll(members);
    }
}