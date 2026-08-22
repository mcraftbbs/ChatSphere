package cn.sarskin.ChatSphere.client;

import cn.sarskin.ChatSphere.client.emoji.EmojiRegistry;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.util.ItemSerialization;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

public class ChatMessageData {
    private final Component senderName;
    private final UUID senderUuid;
    private final Component content;
    private final Component renderedContent;
    private final long timestamp;
    private final String conversationId;
    private final ConversationType conversationType;
    private final boolean isOwn;
    private final boolean isInput;
    private final UUID messageId;
    private int duplicateCount;
    private String replyContent;
    private String replySender;
    private String itemNbt;
    private ItemStack cachedItem;

    public ChatMessageData(Component senderName, UUID senderUuid, Component content,
                           long timestamp, String conversationId,
                           ConversationType conversationType, boolean isOwn) {
        this(senderName, senderUuid, content, timestamp, conversationId, conversationType, isOwn, null, false);
    }

    public ChatMessageData(Component senderName, UUID senderUuid, Component content,
                           long timestamp, String conversationId,
                           ConversationType conversationType, boolean isOwn, UUID messageId) {
        this(senderName, senderUuid, content, timestamp, conversationId, conversationType, isOwn, messageId, false);
    }

    public ChatMessageData(Component senderName, UUID senderUuid, Component content,
                           long timestamp, String conversationId,
                           ConversationType conversationType, boolean isOwn, UUID messageId, boolean isInput) {
        this.senderName = senderName;
        this.senderUuid = senderUuid;
        this.content = content;
        this.timestamp = timestamp;
        this.conversationId = conversationId;
        this.conversationType = conversationType;
        this.isOwn = isOwn;
        this.isInput = isInput;
        this.messageId = messageId != null ? messageId : UUID.randomUUID();
        this.duplicateCount = 1;
        String raw = content.getString();
        boolean richText = conversationType != ConversationType.COMMAND
                && ModClientConfig.CONFIG.renderRichText.get()
                && (RichTextParser.containsMarkup(raw) || RichTextParser.containsUrl(raw));
        if (richText) {
            this.renderedContent = RichTextParser.parse(raw);
        } else {
            this.renderedContent = ModClientConfig.CONFIG.renderEmojiShortcodes.get()
                    ? EmojiRegistry.toComponent(EmojiRegistry.replaceShortcodes(raw))
                    : content;
        }
    }

    public ChatMessageData withReply(String replyContent, String replySender) {
        ChatMessageData copy = new ChatMessageData(senderName, senderUuid, content,
                timestamp, conversationId, conversationType, isOwn, messageId, isInput);
        copy.duplicateCount = this.duplicateCount;
        copy.replyContent = replyContent;
        copy.replySender = replySender;
        copy.itemNbt = this.itemNbt;
        return copy;
    }

    public ChatMessageData withItemNbt(String itemNbt) {
        this.itemNbt = itemNbt;
        this.cachedItem = null;
        return this;
    }

    public ItemStack parsedItem() {
        if (cachedItem == null) {
            cachedItem = (itemNbt != null && !itemNbt.isEmpty())
                    ? ItemSerialization.deserialize(itemNbt)
                    : ItemStack.EMPTY;
        }
        return cachedItem;
    }

    public Component senderName() { return senderName; }
    public UUID senderUuid() { return senderUuid; }
    public Component content() { return content; }
    public Component renderedContent() { return renderedContent; }
    public long timestamp() { return timestamp; }
    public String conversationId() { return conversationId; }
    public ConversationType conversationType() { return conversationType; }
    public boolean isOwn() { return isOwn; }
    public boolean isInput() { return isInput; }
    public UUID messageId() { return messageId; }
    public int duplicateCount() { return duplicateCount; }
    public void setDuplicateCount(int count) { this.duplicateCount = count; }
    public String replyContent() { return replyContent; }
    public String replySender() { return replySender; }
    public String itemNbt() { return itemNbt; }
    public void setItemNbt(String nbt) { this.itemNbt = nbt; }

    public enum ConversationType {
        CHANNEL, PRIVATE, COMMAND
    }
}
