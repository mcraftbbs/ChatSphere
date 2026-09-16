package cn.sarskin.ChatSphere.client.hud;

import cn.sarskin.ChatSphere.ModInfo;
import cn.sarskin.ChatSphere.client.ChatHintsManager;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ChatMessageData;
import cn.sarskin.ChatSphere.client.PlayerSkinCache;
import cn.sarskin.ChatSphere.client.emoji.CustomEmoji;
import cn.sarskin.ChatSphere.client.emoji.CustomEmojiRegistry;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.util.ItemSerialization;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class ChatHudOverlay {
    public static final ResourceLocation HUD_ID = new ResourceLocation(ModInfo.MODID, "chat_hud");
    public static final ChatHudOverlay INSTANCE = new ChatHudOverlay();

    private static final int MAX_VISIBLE_MESSAGES = 5;
    private static final int BUBBLE_PADDING = 6;
    private static final int BUBBLE_MARGIN = 4;
    private static final int AVATAR_SIZE = 10;
    private static final long MESSAGE_DISPLAY_TIME = 8000;
    private static final int ICON_SIZE = 16;
    private static final int ICON_PADDING = 4;
    private static final ResourceLocation CHAT_ICON = new ResourceLocation(
            ModInfo.MODID, "textures/gui/chat_icon.png");

    private long lastMessageTime;
    private boolean flashing;
    private long flashStartTime;
    private long badgeFlashStartTime;
    private boolean badgeFlashing;

    private ChatHudOverlay() {}

    public void render(GuiGraphics guiGraphics, float partialTick) {
        if (!ModClientConfig.CONFIG.hudEnabled.get()) return;
        Theme.beginFrame();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        if (mc.screen instanceof cn.sarskin.ChatSphere.client.screen.ModChatScreen) return;

        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        int maxBubbleWidth = Math.min(220, Math.max(90, screenWidth * 2 / 5));

        ChatHistoryManager history = ChatHistoryManager.getInstance();
        List<ChatMessageData> recentMessages = history.getRecentMessages(MAX_VISIBLE_MESSAGES);

        long now = System.currentTimeMillis();

        renderStrongHint(guiGraphics, mc, screenWidth, screenHeight);

        int chatStartY = screenHeight - ICON_PADDING - ICON_SIZE - 10;
        int bubbleX = 4;

        int shown = 0;
        for (int i = recentMessages.size() - 1; i >= 0 && shown < MAX_VISIBLE_MESSAGES; i--) {
            ChatMessageData msg = recentMessages.get(i);
            if (now - msg.timestamp() > MESSAGE_DISPLAY_TIME) continue;

            boolean hasItem = msg.itemNbt() != null && !msg.itemNbt().isEmpty();
            ItemStack itemStack = hasItem ? msg.parsedItem() : ItemStack.EMPTY;
            boolean itemRendered = hasItem && !itemStack.isEmpty();

            Component bubbleText = buildBubbleText(mc, msg, itemRendered, maxBubbleWidth);
            int textWidth = mc.font.width(bubbleText);
            int iconExtra = itemRendered ? 18 : 0;
            int bubbleWidth = textWidth + BUBBLE_PADDING * 2 + (ModClientConfig.CONFIG.showAvatar.get() ? AVATAR_SIZE + 4 : 0) + iconExtra;
            int bubbleHeight = (itemRendered ? 20 : 12) + BUBBLE_PADDING * 2;

            int bubbleY = chatStartY - (shown + 1) * (bubbleHeight + BUBBLE_MARGIN);
            if (bubbleY < 0) break;

            drawBubble(guiGraphics, mc, msg, bubbleText, itemRendered, itemStack, bubbleX, bubbleY, bubbleWidth, bubbleHeight);
            shown++;
        }

        drawIcon(guiGraphics, mc, screenWidth, screenHeight, history, now);
    }

    private void renderStrongHint(GuiGraphics g, Minecraft mc, int screenWidth, int screenHeight) {
        if (!ModServerConfig.CONFIG.showStrongHint.get()) return;
        ChatHintsManager hints = ChatHintsManager.getInstance();
        Component hintText = hints.getCurrentHint();
        if (hintText == null) return;

        int ticks = hints.getHintTicks();
        int alpha = ChatHintsManager.fadeAlpha(ticks, 10, 40, 10);
        if (alpha <= 0) return;

        int hintW = mc.font.width(hintText);
        int hintX = (screenWidth - hintW) / 2;
        int hintY = screenHeight - 22 - 30 - mc.font.lineHeight;
        int bgAlpha = alpha / 2;
        g.fill(hintX - 6, hintY - 3, hintX + hintW + 6, hintY + mc.font.lineHeight + 3, (bgAlpha << 24) | 0x000000);
        g.drawString(mc.font, hintText, hintX, hintY, (alpha << 24) | 0xFFFFFF55, false);
    }

    private Component buildBubbleText(Minecraft mc, ChatMessageData msg, boolean itemRendered, int maxWidth) {
        MutableComponent text = Component.literal("");
        boolean showName = ModClientConfig.CONFIG.showSenderName.get();
        boolean showTime = ModClientConfig.CONFIG.showTimestamp.get();
        int bodyBudget = bodyBudget(mc, msg, maxWidth, itemRendered);

        if (msg.conversationType() == ChatMessageData.ConversationType.COMMAND) {
            text.append(Component.literal(msg.isInput() ? "> " : "→ ").withStyle(ChatFormatting.GRAY));
            text.append(clipComponent(mc, msg.senderName(), msg.isInput() ? ChatFormatting.GREEN : ChatFormatting.WHITE, bodyBudget));
            if (showTime) {
                text.append("  ").append(Component.literal(ChatHistoryManager.formatTimestampSmart(msg.timestamp())).withStyle(ChatFormatting.GRAY));
            }
            return text;
        }

        if (showName) {
            text.append(msg.senderName().copy().withStyle(ChatFormatting.AQUA));
            text.append(" ");
        }
        if (!itemRendered) {
            String raw = msg.renderedContent().getString();
            if (raw.startsWith("VoiceMessage#")) {
                text.append(Component.translatable("chatsphere.voice.received").withStyle(ChatFormatting.LIGHT_PURPLE));
            } else {
                text.append(clipContent(mc, msg.renderedContent(), bodyBudget));
            }
        }
        if (showTime) {
            text.append("  ").append(Component.literal(ChatHistoryManager.formatTimestampSmart(msg.timestamp())).withStyle(ChatFormatting.GRAY));
        }
        if (msg.duplicateCount() > 1) {
            text.append(Component.literal(" x" + msg.duplicateCount()).withStyle(ChatFormatting.GOLD));
        }
        return text;
    }

    /** Width left for the body after name, timestamp, avatar and padding. */
    private int bodyBudget(Minecraft mc, ChatMessageData msg, int maxWidth, boolean itemRendered) {
        int used = BUBBLE_PADDING * 2 + (itemRendered ? 18 : 0);
        if (ModClientConfig.CONFIG.showAvatar.get()) used += AVATAR_SIZE + 4;
        if (msg.conversationType() == ChatMessageData.ConversationType.COMMAND) {
            used += mc.font.width(msg.isInput() ? "> " : "→ ");
            if (ModClientConfig.CONFIG.showTimestamp.get()) {
                used += mc.font.width("  " + ChatHistoryManager.formatTimestampSmart(msg.timestamp()));
            }
        } else {
            if (ModClientConfig.CONFIG.showSenderName.get()) used += mc.font.width(msg.senderName()) + mc.font.width(" ");
            if (ModClientConfig.CONFIG.showTimestamp.get()) {
                used += mc.font.width("  " + ChatHistoryManager.formatTimestampSmart(msg.timestamp()));
            }
            if (msg.duplicateCount() > 1) used += mc.font.width(" x" + msg.duplicateCount());
        }
        return Math.max(20, maxWidth - used);
    }

    private Component clipContent(Minecraft mc, Component content, int budget) {
        // HUD is text only, so custom emoji become a label instead of the raw shortcode
        String raw = CustomEmojiRegistry.mapTokens(content.getString(), ChatHudOverlay::emojiLabel);
        String clipped = clip(mc, raw, budget);
        return clipped.equals(raw) && raw.equals(content.getString()) ? content : Component.literal(clipped);
    }

    private static String emojiLabel(CustomEmoji emoji) {
        return Component.translatable(emoji.animated()
                ? "chatsphere.hud.emoji.animated" : "chatsphere.hud.emoji.static").getString();
    }

    private Component clipComponent(Minecraft mc, Component content, ChatFormatting style, int budget) {
        String raw = content.getString();
        String clipped = clip(mc, raw, budget);
        return Component.literal(clipped).withStyle(style);
    }

    /** Character cap first, then shrink until the text fits the pixel budget. */
    private String clip(Minecraft mc, String raw, int budget) {
        int cap = Math.max(8, ModClientConfig.CONFIG.hudMaxChars.get());
        String out = raw.length() > cap ? raw.substring(0, cap) : raw;
        boolean clipped = out.length() < raw.length();
        while (out.length() > 8 && mc.font.width(clipped ? out + "…" : out) > budget) {
            out = out.substring(0, out.length() - 1);
            clipped = true;
        }
        return clipped ? out + "…" : out;
    }

    private void drawBubble(GuiGraphics guiGraphics, Minecraft mc, ChatMessageData msg,
                            Component bubbleText, boolean itemRendered, ItemStack itemStack,
                            int x, int y, int width, int height) {
        int color;
        if (msg.isOwn()) {
            color = ModClientConfig.parseHexColor(ModClientConfig.CONFIG.bubbleColorOwn.get(), Theme.bubbleOwnFallback());
        } else {
            color = ModClientConfig.parseHexColor(ModClientConfig.CONFIG.bubbleColorOther.get(), Theme.bubbleOtherFallback());
        }

        guiGraphics.fill(x, y, x + width, y + height, color);

        int textX = x + BUBBLE_PADDING;
        if (ModClientConfig.CONFIG.showAvatar.get()) {
            int avatarX = x + BUBBLE_PADDING;
            int avatarY = y + (height - AVATAR_SIZE) / 2;
            drawAvatar(guiGraphics, mc, msg, avatarX, avatarY);
            textX = avatarX + AVATAR_SIZE + 4;
        }

        int textColor = Theme.bubbleTextOn(color);
        if (itemRendered && !itemStack.isEmpty()) {
            int iconY = y + (height - 16) / 2;
            guiGraphics.renderItem(itemStack, textX, iconY);
            int nameX = textX + 18;
            int nameY = y + (height - 9) / 2;
            guiGraphics.drawString(mc.font, bubbleText, nameX, nameY, textColor, false);
        } else {
            int textY = y + (height - 9) / 2;
            guiGraphics.drawString(mc.font, bubbleText, textX, textY, textColor, false);
        }
    }

    private void drawAvatar(GuiGraphics guiGraphics, Minecraft mc, ChatMessageData msg, int x, int y) {
        PlayerFaceRenderer.draw(guiGraphics, PlayerSkinCache.getSkin(msg.senderUuid()), x, y, AVATAR_SIZE);
    }

    private void drawIcon(GuiGraphics guiGraphics, Minecraft mc, int screenWidth, int screenHeight,
                          ChatHistoryManager history, long now) {
        int iconX = ICON_PADDING;
        int iconY = screenHeight - ICON_PADDING - ICON_SIZE;

        if (history.consumeNewMessageFlag()) {
            if (ModClientConfig.CONFIG.notificationSound.get()) {
                mc.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.0f);
            }
            if (ModClientConfig.CONFIG.notificationFlash.get()) {
                flashing = true;
                flashStartTime = now;
            }
        }

        float alpha = 1.0f;
        if (flashing) {
            long elapsed = now - flashStartTime;
            if (elapsed < 2000) {
                alpha = (float) Math.sin(elapsed * 0.005) * 0.5f + 0.5f;
                alpha = 0.5f + alpha * 0.5f;
            } else {
                flashing = false;
            }
        }

        guiGraphics.setColor(1f, 1f, 1f, alpha);
        guiGraphics.blit(CHAT_ICON, iconX, iconY, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        guiGraphics.setColor(1f, 1f, 1f, 1f);
        // Batching is per render type, so flush the icon or the badge can land underneath
        guiGraphics.flush();

        if (ModClientConfig.CONFIG.notificationBadge.get()) {
            int totalUnread = history.getTotalUnreadCount();
            if (totalUnread > 0) {
                String badge = totalUnread > 99 ? "99+" : String.valueOf(totalUnread);
                int badgeWidth = mc.font.width(badge) + 3;
                int badgeHeight = 8;
                int bx = iconX + ICON_SIZE - badgeWidth + 1;
                int by = iconY - 1;

                if (!badgeFlashing) { badgeFlashing = true; badgeFlashStartTime = now; }
                long elapsed = now - badgeFlashStartTime;
                float flashAlpha = 1.0f;
                if (elapsed < 1000) {
                    flashAlpha = (float) Math.sin(elapsed * 0.008) * 0.3f + 0.7f;
                }
                int bgColor = (int)(0xCC * flashAlpha) << 24 | 0xFF4444;
                guiGraphics.fill(bx, by, bx + badgeWidth, by + badgeHeight, bgColor);
                // Same reason: the label is a text draw, so flush the plate first
                guiGraphics.flush();
                guiGraphics.drawString(mc.font, badge, bx + 1, by, 0xFFFFFFFF, false);
            } else {
                badgeFlashing = false;
            }
        }
    }
}
