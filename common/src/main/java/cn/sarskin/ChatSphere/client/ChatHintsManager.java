package cn.sarskin.ChatSphere.client;

import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;

public class ChatHintsManager {
    private static final ChatHintsManager INSTANCE = new ChatHintsManager();
    private static final int HINT_DURATION = 60;

    private final Deque<HintEntry> hintQueue = new ArrayDeque<>();
    private int hintTicks;

    public static ChatHintsManager getInstance() {
        return INSTANCE;
    }

    public void addHint(Component text, boolean isMention) {
        hintQueue.add(new HintEntry(text, isMention));
        if (hintTicks <= 0) {
            hintTicks = HINT_DURATION;
        }
    }

    public Component getCurrentHint() {
        if (hintQueue.isEmpty() || hintTicks <= 0) return null;
        return hintQueue.peek().text;
    }

    public boolean isCurrentHintMention() {
        if (hintQueue.isEmpty()) return false;
        return hintQueue.peek().isMention;
    }

    public int getHintTicks() {
        return hintTicks;
    }

    public void tick() {
        if (hintTicks > 0) {
            hintTicks--;
            if (hintTicks <= 0) {
                hintQueue.poll();
                if (!hintQueue.isEmpty()) {
                    hintTicks = HINT_DURATION;
                }
            }
        }
    }

    public static int fadeAlpha(int ticks, int fadeIn, int hold, int fadeOut) {
        if (ticks > hold + fadeOut) return 0;
        if (ticks > hold) {
            int fadeTicks = ticks - hold;
            return Math.max(0, (int) (255 * (1.0f - (float) fadeTicks / fadeOut)));
        }
        if (ticks > fadeIn) return 255;
        return Math.min(255, (int) (255 * (float) ticks / fadeIn));
    }

    private record HintEntry(Component text, boolean isMention) {}
}
