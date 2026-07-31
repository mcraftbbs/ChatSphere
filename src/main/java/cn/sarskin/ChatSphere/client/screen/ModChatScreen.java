package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.ModMain;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ChatMessageData;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.client.widget.EmojiPanel;
import cn.sarskin.ChatSphere.client.widget.EmojiAutoComplete;
import cn.sarskin.ChatSphere.client.emoji.EmojiRegistry;
import cn.sarskin.ChatSphere.client.widget.MentionPopup;
import cn.sarskin.ChatSphere.client.widget.QuickPhrasesPanel;
import cn.sarskin.ChatSphere.client.widget.ReplyBarWidget;
import cn.sarskin.ChatSphere.client.widget.CopyToast;
import cn.sarskin.ChatSphere.client.widget.ItemPickerPanel;
import cn.sarskin.ChatSphere.client.PlayerSkinCache;
import cn.sarskin.ChatSphere.client.ModVoiceMessagesIntegration;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import cn.sarskin.ChatSphere.network.ServerboundCommandMessagePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public class ModChatScreen extends Screen {
    private static final String COMMAND_CONVERSATION_ID = "__commands__";
    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final int SIDEBAR_WIDTH = 100;
    private static final int HEADER_BAR_HEIGHT = 14;
    private static final int MUTE_BAR_H = 14;
    private static final int AVATAR_SIZE = 10;
    private static final int SIDEBAR_AVATAR_SIZE = 12;
    private static final int BUBBLE_HPAD = 8;
    private static final int BUBBLE_VPAD = 4;
    private static final int SIDEBAR_ITEM_HEIGHT = 18;
    private static final int CONFIG_ICON_SIZE = 10;
    private static final int TOOLBAR_HEIGHT = 14;
    private static final int NOTIF_BAR_H = 18;
    private static final int MESSAGE_BOTTOM_PAD = 10;
    private static final ResourceLocation SETTINGS_ICON = ResourceLocation.fromNamespaceAndPath(
            ModMain.MODID, "textures/gui/settings_gear.png");
    private static final ResourceLocation EMOJI_ICON = ResourceLocation.fromNamespaceAndPath(
            ModMain.MODID, "textures/gui/emoji.png");
    private static final ResourceLocation QUICK_PHRASES_ICON = ResourceLocation.fromNamespaceAndPath(
            ModMain.MODID, "textures/gui/quick_phrases.png");
    private static final ResourceLocation SEARCH_ICON = ResourceLocation.fromNamespaceAndPath(
            ModMain.MODID, "textures/gui/search.png");
    private static final ResourceLocation JOIN_CHANNEL_ICON = ResourceLocation.fromNamespaceAndPath(
            ModMain.MODID, "textures/gui/join_channel.png");
    private static final ResourceLocation CREATE_CHANNEL_ICON = ResourceLocation.fromNamespaceAndPath(
            ModMain.MODID, "textures/gui/create_channel.png");
    private static final ResourceLocation BLOCK_ICON = ResourceLocation.fromNamespaceAndPath(
            ModMain.MODID, "textures/gui/block.png");
    private static final ResourceLocation ITEM_ICON = ResourceLocation.fromNamespaceAndPath(
            ModMain.MODID, "textures/gui/item_chest.png");
    private static final ResourceLocation VOICE_ICON = ResourceLocation.fromNamespaceAndPath(
            ModMain.MODID, "textures/gui/voice.png");

    private EditBox input;
    private String initial;
    private int historyPos = -1;
    private final List<String> sentHistory = new ArrayList<>();
    private CommandSuggestions commandSuggestions;
    private final List<String> cmdHistoryEntries = new ArrayList<>();
    private int cmdHistoryPos = -1;
    private String currentConversation = ChatHistoryManager.DEFAULT_CHANNEL_ID;
    private int scrollOffset;

    private final List<SidebarEntry> sidebarEntries = new ArrayList<>();
    private final Map<String, PlayerInfo> onlinePlayers = new LinkedHashMap<>();

    private final EmojiPanel emojiPanel = new EmojiPanel();
    private final EmojiAutoComplete emojiAutoComplete = new EmojiAutoComplete();
    private final QuickPhrasesPanel quickPhrasesPanel = new QuickPhrasesPanel();
    private final MentionPopup mentionPopup = new MentionPopup();
    private final ReplyBarWidget replyBar = new ReplyBarWidget();
    private final CopyToast copyToast = new CopyToast();
    private final ItemPickerPanel itemPickerPanel = new ItemPickerPanel();
    private int notifBarY;
    private String pendingItemNbt;
    private boolean showSearch;
    private EditBox searchInput;
    private String searchQuery = "";
    private List<Integer> searchResults = new ArrayList<>();
    private int searchResultIndex = -1;
    private int contextMsgIndex = -1;
    private static final int CTX_NONE = 0;
    private static final int CTX_BUBBLE = 1;
    private int contextType = CTX_NONE;
    private int contextMenuX;
    private int contextMenuY;
    private int replyHighlightTarget = -1;
    private final List<CommandHit> cmdHitBoxes = new ArrayList<>();
    private final List<VoiceHit> voiceHitBoxes = new ArrayList<>();
    private final List<BubbleHit> bubbleHitBoxes = new ArrayList<>();
    private final List<ReplyQuoteHit> replyQuoteHitBoxes = new ArrayList<>();
    private final List<BubbleItemHit> itemHitBoxes = new ArrayList<>();
    private final Map<java.util.UUID, Object> voicePlayerCache = new HashMap<>();


    private record CommandHit(int x, int y, int w, int h, Component component) {}
    private record VoiceHit(int x, int y, int w, int h, java.util.UUID voiceUuid, Object playbackPlayer) {}
    private record BubbleHit(int x, int y, int w, int h, int globalIndex) {}
    private record ReplyQuoteHit(int x, int y, int w, int h, String replySender, String replyContent) {}
    private record BubbleItemHit(int x, int y, int w, int h, ItemStack itemStack) {}

    public ModChatScreen(String initial) {
        super(Component.translatable("screen.chatsphere.mod_chat.title"));
        this.initial = initial;
        this.scrollOffset = 0;
        ChatHistoryManager.getInstance().load();
    }

    @Override
    protected void init() {
        voicePlayerCache.clear();
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        history.load();
        history.ensureDefaultChannel();
        history.refreshPrivateConversationDisplayNames();
        if (!history.getChannels().contains(currentConversation)
                && !COMMAND_CONVERSATION_ID.equals(currentConversation)
                && history.getConversationType(currentConversation) != ChatMessageData.ConversationType.PRIVATE) {
            List<String> channels = history.getChannels();
            currentConversation = channels.isEmpty() ? ChatHistoryManager.DEFAULT_CHANNEL_ID : channels.get(0);
        }

        if (this.initial.startsWith("/")) {
            if (!COMMAND_CONVERSATION_ID.equals(currentConversation)) {
                currentConversation = COMMAND_CONVERSATION_ID;
                cmdHistoryEntries.clear();
                cmdHistoryEntries.addAll(history.getCommandHistory(this.minecraft.player.getUUID()));
                cmdHistoryPos = cmdHistoryEntries.size();
                this.initial = this.initial.substring(1);
            }
            history.markConversationRead(COMMAND_CONVERSATION_ID);
        } else if (this.initial.isEmpty() && ModClientConfig.CONFIG.preserveInput.get()) {
            String saved = history.getSavedInput();
            if (saved != null && !saved.isEmpty()) this.initial = saved;
        }

        int inputX = SIDEBAR_WIDTH + 2;
        int inputWidth = this.width - SIDEBAR_WIDTH - 6;
        this.input = new EditBox(this.minecraft.font, inputX, this.height - 12,
                inputWidth, 12,
                Component.translatable("chat.editBox"));
        this.input.setMaxLength(MAX_MESSAGE_LENGTH);
        this.input.setBordered(false);
        this.input.setValue(this.initial);
        if (!COMMAND_CONVERSATION_ID.equals(currentConversation))
            this.input.setTextColor(0xFFFFFFFF);
        this.input.setResponder(this::onCommandInputChanged);
        this.input.setFormatter((text, cursor) -> EmojiRegistry.toFormattedCharSequence(text));
        this.addWidget(this.input);
        this.setInitialFocus(this.input);

        this.commandSuggestions = new CommandSuggestions(this.minecraft, this, this.input, this.font,
                true, false, 1, 10, true, -805306368);
        this.commandSuggestions.setAllowHiding(false);
        if (COMMAND_CONVERSATION_ID.equals(currentConversation))
            this.commandSuggestions.updateCommandInfo();

        this.searchInput = new EditBox(this.font, SIDEBAR_WIDTH + 4, HEADER_BAR_HEIGHT + 8,
                Math.max(80, width - SIDEBAR_WIDTH - 30), 14,
                Component.translatable("screen.chatsphere.search.hint"));
        this.searchInput.setMaxLength(64);
        this.searchInput.setBordered(true);
        this.searchInput.setVisible(false);
        this.searchInput.setResponder(this::onSearchChanged);
        this.addWidget(this.searchInput);

        refreshOnlinePlayers();
        try { minecraft.gameRenderer.loadEffect(ResourceLocation.fromNamespaceAndPath("minecraft", "shaders/post/blur.json")); } catch (Exception ignored) {}
    }

    private void refreshOnlinePlayers() {
        onlinePlayers.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && mc.player != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                onlinePlayers.put(info.getProfile().getId().toString(), info);
            }
        }
    }

    private void onCommandInputChanged(String value) {
        if (this.commandSuggestions != null && COMMAND_CONVERSATION_ID.equals(currentConversation)) {
            this.commandSuggestions.setAllowSuggestions(true);
            this.commandSuggestions.updateCommandInfo();
        }
        mentionPopup.update(value, onlinePlayers);
    }

    private void onSearchChanged(String query) {
        searchQuery = query;
        if (query.isEmpty()) {
            searchResults = List.of();
            searchResultIndex = -1;
            return;
        }
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        searchResults = history.searchMessages(currentConversation, query);
        searchResultIndex = searchResults.isEmpty() ? -1 : 0;
    }

    @Override
    public void tick() {
        super.tick();
        refreshOnlinePlayers();
        ChatHistoryManager.getInstance().refreshPrivateConversationDisplayNames();
        copyToast.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            // Search bar interactions
            if (showSearch && searchInput != null && searchInput.isVisible()) {
                int barY = HEADER_BAR_HEIGHT + 6;
                int barH = 20;
                int areaLeft = SIDEBAR_WIDTH + 2;
                int areaRight = this.width;
                if (mouseX >= searchInput.getX() && mouseX <= searchInput.getX() + searchInput.getWidth()
                        && mouseY >= searchInput.getY() && mouseY <= searchInput.getY() + searchInput.getHeight()) {
                    setFocused(searchInput);
                    input.setFocused(false);
                    return true;
                }
                int closeX = areaRight - 18;
                if (mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= barY + 3 && mouseY <= barY + 17) {
                    showSearch = false;
                    searchInput.setVisible(false);
                    searchQuery = "";
                    searchResults = List.of();
                    searchResultIndex = -1;
                    setFocused(input);
                    return true;
                }
                if (!searchResults.isEmpty() && mouseY >= barY && mouseY <= barY + barH && mouseX >= areaLeft && mouseX <= closeX) {
                    searchResultIndex = (searchResultIndex + 1) % searchResults.size();
                    scrollToMessageIndex(searchResults.get(searchResultIndex));
                    return true;
                }
            }

            // Toolbar buttons
            int toolbarY = this.height - 14 - TOOLBAR_HEIGHT;
            int btnH = TOOLBAR_HEIGHT - 2;
            int btnY = toolbarY + 1;
            if (mouseY >= toolbarY && mouseY <= toolbarY + TOOLBAR_HEIGHT) {
                int btnX = SIDEBAR_WIDTH + 4;
                if (mouseX >= btnX && mouseX <= btnX + btnH) {
                    emojiPanel.toggle();
                    quickPhrasesPanel.visible = false;
                    return true;
                }
                btnX += btnH + 2;
                if (mouseX >= btnX && mouseX <= btnX + btnH) {
                    quickPhrasesPanel.toggle();
                    emojiPanel.visible = false;
                    return true;
                }
                btnX += btnH + 2;
                if (mouseX >= btnX && mouseX <= btnX + btnH) {
                    showSearch = !showSearch;
                    searchInput.setVisible(showSearch);
                    if (showSearch) { setFocused(searchInput); input.setFocused(false); }
                    else { setFocused(input); }
                    emojiPanel.visible = false;
                    quickPhrasesPanel.visible = false;
                    return true;
                }
                btnX += btnH + 2;
                if (mouseX >= btnX && mouseX <= btnX + btnH) {
                    if (minecraft != null) {
                        minecraft.setScreen(new BlockListScreen(this));
                    }
                    return true;
                }
                btnX += btnH + 2;
                if (mouseX >= btnX && mouseX <= btnX + btnH) {
                    itemPickerPanel.toggle();
                    emojiPanel.visible = false;
                    quickPhrasesPanel.visible = false;
                    return true;
                }
                if (ModVoiceMessagesIntegration.canSendVoiceMessages()) {
                    btnX += btnH + 2;
                    if (mouseX >= btnX && mouseX <= btnX + btnH) {
                        ChatHistoryManager history = ChatHistoryManager.getInstance();
                        ChatMessageData.ConversationType convType = history.getConversationType(currentConversation);
                        if (convType == ChatMessageData.ConversationType.CHANNEL && !ChatHistoryManager.DEFAULT_CHANNEL_ID.equals(currentConversation)) {
                            ModVoiceMessagesIntegration.setPendingVoice(currentConversation, "CHANNEL");
                            ModVoiceMessagesIntegration.openRecordingScreen(this, btnX, this.height - btnY + 1, "chatsphere_internal");
                        } else if (convType == ChatMessageData.ConversationType.PRIVATE) {
                            ModVoiceMessagesIntegration.setPendingVoice(currentConversation, "PRIVATE");
                            ModVoiceMessagesIntegration.openRecordingScreen(this, btnX, this.height - btnY + 1, "chatsphere_internal");
                        } else {
                            String target = resolveVoiceTarget();
                            ModVoiceMessagesIntegration.openRecordingScreen(this, btnX, this.height - btnY + 1, target);
                        }
                        return true;
                    }
                }
            }

            // Emoji panel click
            int emojiPanelX = SIDEBAR_WIDTH + 4;
            int emojiPanelY = this.height - 14 - TOOLBAR_HEIGHT - EmojiPanel.PANEL_H - 4;
            if (emojiPanel.mouseClicked(mouseX, mouseY, button, emojiPanelX, emojiPanelY, input)) return true;

            // Item picker panel click
            if (itemPickerPanel.visible) {
                int ipY = this.height - 14 - TOOLBAR_HEIGHT - ItemPickerPanel.ITEM_PICKER_PANEL_H - 4;
                if (itemPickerPanel.mouseClicked(mouseX, mouseY, button, SIDEBAR_WIDTH + 4, ipY)) {
                    if (itemPickerPanel.selectedItemNbt != null && !itemPickerPanel.selectedItemNbt.isEmpty()) {
                        pendingItemNbt = itemPickerPanel.selectedItemNbt;
                        input.setValue("[" + (itemPickerPanel.selectedSlotIndex + 1) + "]");
                    }
                    return true;
                }
            }

            // Quick phrases panel click
            int qpPanelY = this.height - 14 - TOOLBAR_HEIGHT - 4 - Math.min(ModClientConfig.CONFIG.quickPhrases.get().size(), 6) * 18;
            if (quickPhrasesPanel.mouseClicked(mouseX, mouseY, button, SIDEBAR_WIDTH + 4, qpPanelY, input)) return true;

            // Reply bar
            if (replyBar.mouseClicked(mouseX, mouseY, SIDEBAR_WIDTH, this.width, 0, false)) {
                replyHighlightTarget = -1;
                return true;
            }
            if (replyBar.isOnBody(mouseX, mouseY, SIDEBAR_WIDTH, this.width, 0, false)) {
                replyHighlightTarget = replyBar.targetIndex;
                scrollToMessageIndex(replyBar.targetIndex);
                return true;
            }

            // Reply quote click inside message bubbles
            synchronized (replyQuoteHitBoxes) {
                for (ReplyQuoteHit hit : replyQuoteHitBoxes) {
                    if (mouseX >= hit.x && mouseX <= hit.x + hit.w && mouseY >= hit.y && mouseY <= hit.y + hit.h) {
                        ChatHistoryManager history = ChatHistoryManager.getInstance();
                        List<ChatMessageData> msgs = history.getMessagesByConversation(currentConversation);
                        for (int i = msgs.size() - 1; i >= 0; i--) {
                            ChatMessageData m = msgs.get(i);
                            if (m.senderName().getString().equals(hit.replySender) && m.content().getString().equals(hit.replyContent)) {
                                replyHighlightTarget = history.getMessageIndex(m);
                                scrollToMessageIndex(replyHighlightTarget);
                                return true;
                            }
                        }
                    }
                }
            }

            // Mention popup click
            if (mentionPopup.mouseClicked(mouseX, mouseY, button, input)) return true;

            // Emoji autocomplete click
            if (emojiAutoComplete.mouseClicked(mouseX, mouseY, button, input)) return true;
        }

        // Clickable command components (button == 0)
        if (button == 0 && COMMAND_CONVERSATION_ID.equals(currentConversation)) {
            synchronized (cmdHitBoxes) {
                for (CommandHit hit : cmdHitBoxes) {
                    if (mouseX >= hit.x && mouseX <= hit.x + hit.w && mouseY >= hit.y && mouseY <= hit.y + hit.h) {
                        int relX = (int) mouseX - hit.x;
                        Style style = minecraft.font.getSplitter().componentStyleAtWidth(hit.component, relX);
                        if (style != null && style.getClickEvent() != null) {
                            net.minecraft.network.chat.ClickEvent click = style.getClickEvent();
                            switch (click.getAction()) {
                                case OPEN_URL -> net.minecraft.Util.getPlatform().openUri(click.getValue());
                                case OPEN_FILE -> net.minecraft.Util.getPlatform().openFile(new java.io.File(click.getValue()));
                                case RUN_COMMAND -> {
                                    String cmd = click.getValue();
                                    minecraft.player.connection.sendCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
                                }
                                case SUGGEST_COMMAND -> {
                                    input.setValue(click.getValue());
                                    input.moveCursorToEnd(false);
                                    setFocused(input);
                                }
                                case COPY_TO_CLIPBOARD -> minecraft.keyboardHandler.setClipboard(click.getValue());
                            }
                            return true;
                        }
                    }
                }
            }
        }

        // Voice message PlaybackPlayer click
        if (button == 0) {
            synchronized (voiceHitBoxes) {
                for (VoiceHit hit : voiceHitBoxes) {
                    if (mouseX >= hit.x && mouseX <= hit.x + hit.w && mouseY >= hit.y && mouseY <= hit.y + hit.h) {
                        if (ModVoiceMessagesIntegration.handlePlaybackClick(hit.playbackPlayer, (int) mouseX, (int) mouseY, button))
                            return true;
                    }
                }
            }
        }

        // Right-click context menu on bubbles (uses bubbleHitBoxes built during renderMessages)
        if (button == 1) {
            synchronized (bubbleHitBoxes) {
                for (int i = bubbleHitBoxes.size() - 1; i >= 0; i--) {
                    BubbleHit hit = bubbleHitBoxes.get(i);
                    if (mouseX >= hit.x && mouseX <= hit.x + hit.w && mouseY >= hit.y && mouseY <= hit.y + hit.h) {
                        contextType = CTX_BUBBLE;
                        contextMsgIndex = hit.globalIndex;
                        contextMenuX = (int) mouseX;
                        contextMenuY = (int) mouseY;
                        return true;
                    }
                }
            }
        }

        // Context menu click (button 0)
        if (button == 0 && contextType == CTX_BUBBLE) {
            handleContextMenuClick((int) mouseX, (int) mouseY);
            return true;
        }
        if (contextType != CTX_NONE) { contextType = CTX_NONE; contextMsgIndex = -1; return true; }

        // Command suggestions
        if (COMMAND_CONVERSATION_ID.equals(currentConversation)
                && this.commandSuggestions != null
                && this.commandSuggestions.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // Sidebar click
        if (button == 0 && mouseX < SIDEBAR_WIDTH) {
            int yOffset = 10;
            int headerIdx = 0;
            for (int i = 0; i < sidebarEntries.size(); i++) {
                SidebarEntry entry = sidebarEntries.get(i);
                if (entry.isHeader) {
                    if (headerIdx == 0) {
                        int plusX = SIDEBAR_WIDTH - 6 - 10;
                        int plusY = yOffset + (SIDEBAR_ITEM_HEIGHT - 8) / 2;
                        if (mouseX >= plusX && mouseX <= plusX + 10 && mouseY >= plusY && mouseY <= plusY + 10) {
                            if (this.minecraft != null) { this.onClose(); this.minecraft.setScreen(new CreateChannelScreen(this)); }
                            return true;
                        }
                        int joinX = plusX - 14;
                        if (mouseX >= joinX && mouseX <= joinX + 10 && mouseY >= plusY && mouseY <= plusY + 10) {
                            if (this.minecraft != null) { this.onClose(); this.minecraft.setScreen(new JoinChannelScreen(this)); }
                            return true;
                        }
                        int exploreX = joinX - 14;
                        if (mouseX >= exploreX && mouseX <= exploreX + 10 && mouseY >= plusY && mouseY <= plusY + 10) {
                            if (this.minecraft != null) { this.onClose(); this.minecraft.setScreen(new ExploreServersScreen(this)); }
                            return true;
                        }
                    }
                    headerIdx++;
                    yOffset += SIDEBAR_ITEM_HEIGHT;
                    continue;
                }
                if (mouseY >= yOffset && mouseY < yOffset + SIDEBAR_ITEM_HEIGHT) {
                    if (entry.type == ChatMessageData.ConversationType.CHANNEL && entry.conversationId != null) {
                        int gearX = SIDEBAR_WIDTH - 6 - CONFIG_ICON_SIZE;
                        if (mouseX >= gearX && mouseX <= gearX + CONFIG_ICON_SIZE) {
                            if (this.minecraft != null) {
                                UUID playerUuid = this.minecraft.player.getUUID();
                                ChatHistoryManager hist = ChatHistoryManager.getInstance();
                                if (hist.isOwner(entry.conversationId, playerUuid) || hist.isAdmin(entry.conversationId, playerUuid)) {
                                    this.onClose(); this.minecraft.setScreen(new ChannelConfigScreen(this, entry.conversationId));
                                } else {
                                    this.onClose(); this.minecraft.setScreen(new ChannelInfoScreen(this, entry.conversationId));
                                }
                            }
                            return true;
                        }
                    }
                    if (entry.conversationId != null && !entry.conversationId.equals(currentConversation)) {
                        ChatHistoryManager history = ChatHistoryManager.getInstance();
                        if (entry.type == ChatMessageData.ConversationType.PRIVATE && !history.hasConversation(entry.conversationId)) {
                            history.addPrivateConversation(entry.conversationId, entry.displayName);
                        }
                        if (entry.type == ChatMessageData.ConversationType.COMMAND) {
                            cmdHistoryEntries.clear();
                            cmdHistoryEntries.addAll(history.getCommandHistory(this.minecraft.player.getUUID()));
                            cmdHistoryPos = cmdHistoryEntries.size();
                            history.markConversationRead(COMMAND_CONVERSATION_ID);
                        }
                        if (entry.type == ChatMessageData.ConversationType.CHANNEL && history.isServerConnected()) {
                            sendChannelPacket(ServerboundChannelActionPayload.Action.JOIN_MEMBER, entry.conversationId, this.minecraft.player.getUUID());
                        }
                        currentConversation = entry.conversationId;
                        voicePlayerCache.clear();
                        scrollOffset = 0;
                        if (!searchQuery.isEmpty()) {
                            searchResults = history.searchMessages(currentConversation, searchQuery);
                            searchResultIndex = searchResults.isEmpty() ? -1 : 0;
                        }
                    }
                    return true;
                }
                yOffset += SIDEBAR_ITEM_HEIGHT;
            }
        }
        if (button == 0 && mouseY >= notifBarY && mouseY < notifBarY + NOTIF_BAR_H && mouseX >= SIDEBAR_WIDTH) {
            handleNotifBarClick();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleNotifBarClick() {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        history.markConversationRead(currentConversation);
        scrollOffset = 0;
    }

    @Override
    public void onClose() {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        if (ModClientConfig.CONFIG.preserveInput.get()) {
            history.setSavedInput(input.getValue());
        }
        history.saveNow();
        super.onClose();
    }

    @Override
    public void removed() {
        try { minecraft.gameRenderer.loadEffect(null); } catch (Exception ignored) {}
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (COMMAND_CONVERSATION_ID.equals(currentConversation)
                && this.commandSuggestions != null
                && this.commandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        // Search mode
        if (showSearch && searchInput != null && searchInput.isFocused()) {
            if (keyCode == 256) {
                showSearch = false;
                searchInput.setVisible(false);
                searchQuery = "";
                searchResults = List.of();
                searchResultIndex = -1;
                setFocused(input);
                return true;
            }
            if ((keyCode == 257 || keyCode == 335) && !searchResults.isEmpty()) {
                searchResultIndex = (searchResultIndex + 1) % searchResults.size();
                scrollToMessageIndex(searchResults.get(searchResultIndex));
                return true;
            }
        }

        // Emoji autocomplete
        if (emojiAutoComplete.keyPressed(keyCode, input)) return true;

        // Emoji panel search
        if (emojiPanel.keyPressed(keyCode, scanCode, modifiers)) return true;

        // Mention popup
        if (mentionPopup.keyPressed(keyCode, input)) return true;

        // Quick phrases add input
        if (quickPhrasesPanel.keyPressed(keyCode, scanCode, modifiers)) return true;

        // ESC - close panels first, then screen
        if (keyCode == 256) {
            if (emojiPanel.visible) { emojiPanel.visible = false; return true; }
            if (emojiAutoComplete.visible) { emojiAutoComplete.visible = false; return true; }
            if (quickPhrasesPanel.visible) { quickPhrasesPanel.visible = false; return true; }
            this.onClose();
            this.minecraft.setScreen(null);
            return true;
        }

        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            emojiAutoComplete.update(input.getValue());
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            sendMessage(this.input.getValue());
            return true;
        }
        if (keyCode == 265) {
            moveInHistory(-1);
            return true;
        }
        if (keyCode == 264) {
            moveInHistory(1);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (emojiPanel.charTyped(codePoint)) return true;
        if (quickPhrasesPanel.charTyped(codePoint, modifiers)) return true;
        boolean handled = super.charTyped(codePoint, modifiers);
        if (input != null && input.isFocused()) {
            emojiAutoComplete.update(input.getValue());
        }
        return handled;
    }

    private void sendMessage(String text) {
        text = cn.sarskin.ChatSphere.client.emoji.EmojiRegistry.shortcodesToUnicode(text);
        text = text.trim();
        if (text.isEmpty()) return;

        if (text.startsWith("#") && text.length() > 1 && ModServerConfig.CONFIG.enableChannels.get()) {
            String newChannel = text.substring(1).trim();
            if (!newChannel.isEmpty()) {
                String channelId = "#" + newChannel;
                ChatHistoryManager history = ChatHistoryManager.getInstance();
                UUID ownerUuid = this.minecraft != null && this.minecraft.player != null
                        ? this.minecraft.player.getUUID() : null;
                if (ownerUuid != null && history.isServerConnected()) {
                    sendChannelPacket(ServerboundChannelActionPayload.Action.CREATE, channelId, ownerUuid);
                } else {
                    history.addChannel(channelId, ownerUuid);
                }
                currentConversation = channelId;
                voicePlayerCache.clear();
                if (this.minecraft.player != null) {
                    this.minecraft.player.displayClientMessage(
                            Component.translatable("screen.chatsphere.mod_chat.switched_channel", newChannel), false);
                }
            }
            this.input.setValue("");
            return;
        }

        String stripped = text.startsWith("/") ? text.substring(1) : text;
        if (stripped.startsWith("msg ") || stripped.startsWith("tell ") || stripped.startsWith("w ")) {
            String[] parts = stripped.split(" ", 3);
            if (parts.length >= 3) {
                String targetName = parts[1];
                String msgText = parts[2];
                UUID localUuid = this.minecraft.player.getUUID();
                PlayerInfo targetInfo = null;
                for (PlayerInfo info : onlinePlayers.values()) {
                    if (info.getProfile().getName().equalsIgnoreCase(targetName)) {
                        targetInfo = info;
                        break;
                    }
                }
                if (targetInfo != null) {
                    UUID targetUuid = targetInfo.getProfile().getId();
                    String convId = localUuid.compareTo(targetUuid) < 0
                            ? localUuid + ":" + targetUuid
                            : targetUuid + ":" + localUuid;
                    currentConversation = convId;
                    voicePlayerCache.clear();
                    ChatHistoryManager history = ChatHistoryManager.getInstance();
                    history.addPrivateConversation(convId, Component.literal(targetInfo.getProfile().getName()));
                    this.minecraft.player.connection.sendCommand("msg " + targetName + " " + msgText);
                    history.addMessage(
                            Component.literal(this.minecraft.player.getName().getString()),
                            localUuid,
                            Component.literal(msgText),
                            convId,
                            ChatMessageData.ConversationType.PRIVATE,
                            true);
                    this.input.setValue("");
                    this.scrollOffset = 0;
                    return;
                }
            }
        }

        ChatHistoryManager history = ChatHistoryManager.getInstance();
        ChatMessageData.ConversationType currentType = history.getConversationType(currentConversation);
        String replyText = replyBar.replyText;
        String replySender = replyBar.replySender;
        String localItemNbt = pendingItemNbt;

        if (currentType == ChatMessageData.ConversationType.COMMAND) {
            this.minecraft.player.connection.sendCommand(stripped);
            history.addCommandEntry(this.minecraft.player.getUUID(), stripped);
            cmdHistoryEntries.add(stripped);
            cmdHistoryPos = cmdHistoryEntries.size();
            cn.sarskin.ChatSphere.client.ModClientEvents.lastCommandTime = System.currentTimeMillis();
            history.addCommandMessage(
                    Component.literal(stripped),
                    this.minecraft.player.getUUID(),
                    Component.literal(""),
                    true);
            // Persist to server for cross-session sync
            if (history.isServerConnected()) {
                this.minecraft.player.connection.getConnection().send(
                    new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                            new ServerboundCommandMessagePayload(
                                    Component.Serializer.toJson(Component.literal(stripped), RegistryAccess.EMPTY),
                                    this.minecraft.player.getUUID())));
            }
        } else if (currentType == ChatMessageData.ConversationType.PRIVATE) {
            sentHistory.add(text);
            historyPos = sentHistory.size();
            if (history.isServerConnected()) {
                sendChannelChatPacket(currentConversation, text, replyText, replySender);
            } else {
                String targetUuidStr = currentConversation;
                if (currentConversation.contains(":")) {
                    String[] parts = currentConversation.split(":");
                    String localStr = this.minecraft.player.getUUID().toString();
                    targetUuidStr = parts[0].equals(localStr) ? parts[1] : parts[0];
                }
                PlayerInfo info = targetUuidStr.length() == 36 ? onlinePlayers.get(targetUuidStr) : null;
                String targetName = info != null ? info.getProfile().getName() : targetUuidStr;
                this.minecraft.player.connection.sendCommand("msg " + targetName + " " + text);
            }
            history.addMessage(
                    Component.literal(this.minecraft.player.getName().getString()),
                    this.minecraft.player.getUUID(),
                    Component.literal(text),
                    currentConversation,
                    ChatMessageData.ConversationType.PRIVATE,
                    true,
                    replyText, replySender,
                    localItemNbt);
        } else {
            if (history.isServerConnected() && currentType == ChatMessageData.ConversationType.CHANNEL) {
                var cfg = history.getChannelConfig(currentConversation);
                if (cfg.mutedPlayers.contains(this.minecraft.player.getUUID().toString())) {
                    this.minecraft.player.displayClientMessage(
                        Component.translatable("chatsphere.mute.feedback"), false);
                    this.input.setValue("");
                    return;
                }
            }
            sentHistory.add(text);
            historyPos = sentHistory.size();
            if (history.isServerConnected()) {
                sendChannelChatPacket(currentConversation, text, replyText, replySender);
            } else {
                this.minecraft.player.connection.sendChat(text);
            }
            history.addMessage(
                    Component.literal(this.minecraft.player.getName().getString()),
                    this.minecraft.player.getUUID(),
                    Component.literal(text),
                    currentConversation,
                    ChatMessageData.ConversationType.CHANNEL,
                    true,
                    replyText, replySender,
                    localItemNbt);
        }

        replyBar.clear();
        replyHighlightTarget = -1;
        this.input.setValue("");
        this.scrollOffset = 0;
    }

    private void moveInHistory(int direction) {
        if (COMMAND_CONVERSATION_ID.equals(currentConversation)) {
            int newPos = cmdHistoryPos + direction;
            if (newPos < 0 || newPos > cmdHistoryEntries.size()) return;
            if (newPos == cmdHistoryEntries.size()) {
                cmdHistoryPos = newPos;
                this.input.setValue("");
            } else {
                if (cmdHistoryPos == cmdHistoryEntries.size()) {
                    this.initial = this.input.getValue();
                }
                cmdHistoryPos = newPos;
                this.input.setValue(cmdHistoryEntries.get(newPos));
            }
        } else {
            int newPos = historyPos + direction;
            if (newPos < 0 || newPos > sentHistory.size()) return;
            if (newPos == sentHistory.size()) {
                historyPos = newPos;
                this.input.setValue("");
            } else {
                if (historyPos == sentHistory.size()) {
                    this.initial = this.input.getValue();
                }
                historyPos = newPos;
                this.input.setValue(sentHistory.get(newPos));
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int emojiPanelX = SIDEBAR_WIDTH + 4;
        int emojiPanelY = this.height - 14 - TOOLBAR_HEIGHT - EmojiPanel.PANEL_H - 4;
        if (emojiPanel.mouseScrolled(mouseX, mouseY, emojiPanelX, emojiPanelY, scrollY)) return true;
        if (quickPhrasesPanel.mouseScrolled(scrollY)) return true;

        if (COMMAND_CONVERSATION_ID.equals(currentConversation)
                && this.commandSuggestions != null
                && this.commandSuggestions.mouseScrolled(scrollY)) {
            return true;
        }
        if (mouseY >= chatAreaTop() && mouseY < height - 14 - TOOLBAR_HEIGHT - MESSAGE_BOTTOM_PAD && mouseX >= SIDEBAR_WIDTH) {
            scrollOffset += (int) scrollY;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Sync dimensions when rendered as parent of an overlay screen
        Screen curScreen = minecraft.screen;
        if (curScreen != null && curScreen != this) {
            this.width = curScreen.width;
            this.height = curScreen.height;
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        buildSidebarEntries();
        drawSidebar(guiGraphics, mouseX, mouseY);

        int screenWidth = this.width;
        int screenHeight = this.height;

        drawHeaderBar(guiGraphics);
        renderMessages(guiGraphics, screenWidth, screenHeight);
        renderNotificationBar(guiGraphics, screenHeight);

        guiGraphics.fill(SIDEBAR_WIDTH, screenHeight - 14 - TOOLBAR_HEIGHT, screenWidth, screenHeight, 0x88000000);
        this.input.render(guiGraphics, mouseX, mouseY, partialTick);
        drawToolbar(guiGraphics, mouseX, mouseY, screenHeight, screenWidth);
        drawWidgetPanels(guiGraphics, mouseX, mouseY);

        if (COMMAND_CONVERSATION_ID.equals(currentConversation) && this.commandSuggestions != null) {
            this.commandSuggestions.render(guiGraphics, mouseX, mouseY);
        }

        renderTooltips(guiGraphics, mouseX, mouseY, screenWidth, screenHeight);

        // Command component hover tooltips (for clickable/hoverable text)
        if (COMMAND_CONVERSATION_ID.equals(currentConversation)) {
            synchronized (cmdHitBoxes) {
                for (CommandHit hit : cmdHitBoxes) {
                    if (mouseX >= hit.x && mouseX <= hit.x + hit.w && mouseY >= hit.y && mouseY <= hit.y + hit.h) {
                        int relX = (int) mouseX - hit.x;
                        Style style = minecraft.font.getSplitter().componentStyleAtWidth(hit.component, relX);
                        if (style != null && style.getHoverEvent() != null) {
                            var hoverEvent = style.getHoverEvent();
                            var action = hoverEvent.getAction();
                            Object val = hoverEvent.getValue(action);
                            if (val instanceof Component tooltip) {
                                String raw = tooltip.getString();
                                if (raw.contains("\n")) {
                                    List<Component> lines = new ArrayList<>();
                                    MutableComponent[] curRef = { Component.literal("") };
                                    tooltip.visit((sty, text) -> {
                                        int start = 0;
                                        while (true) {
                                            int idx = text.indexOf('\n', start);
                                            if (idx < 0) {
                                                if (start < text.length())
                                                    curRef[0] = curRef[0].append(Component.literal(text.substring(start)).withStyle(sty));
                                                break;
                                            }
                                            if (start < idx)
                                                curRef[0] = curRef[0].append(Component.literal(text.substring(start, idx)).withStyle(sty));
                                            lines.add(curRef[0]);
                                            curRef[0] = Component.literal("");
                                            start = idx + 1;
                                        }
                                        return Optional.empty();
                                    }, Style.EMPTY);
                                    if (!curRef[0].getString().isEmpty() || lines.isEmpty())
                                        lines.add(curRef[0]);
                                    guiGraphics.renderTooltip(minecraft.font, lines, Optional.empty(), mouseX, mouseY);
                                } else {
                                    guiGraphics.renderTooltip(minecraft.font, tooltip, mouseX, mouseY);
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    private String resolveVoiceTarget() {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        ChatMessageData.ConversationType type = history.getConversationType(currentConversation);
        if (type == ChatMessageData.ConversationType.PRIVATE) {
            String localUuid = minecraft.player.getUUID().toString();
            if (currentConversation.contains(":")) {
                String[] parts = currentConversation.split(":");
                String otherUuid = parts[0].equals(localUuid) ? parts[1] : parts[0];
                PlayerInfo info = otherUuid.length() == 36 ? onlinePlayers.get(otherUuid) : null;
                if (info != null) return info.getProfile().getName();
            }
            Component displayName = history.getConversationDisplayName(currentConversation);
            return displayName.getString();
        }
        return "all";
    }

    private void drawToolbar(GuiGraphics g, int mouseX, int mouseY, int screenHeight, int screenWidth) {
        int ty = screenHeight - 14 - TOOLBAR_HEIGHT;
        g.fill(SIDEBAR_WIDTH, ty, screenWidth + 2, ty + TOOLBAR_HEIGHT, 0x88000000);

        int btnX = SIDEBAR_WIDTH + 4;
        int btnY = ty + 1;
        int iconSize = TOOLBAR_HEIGHT - 2;

        int hc = 0x66333388;
        int nc = 0x44000000;

        boolean emojiHover = mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize;
        g.fill(btnX, btnY, btnX + iconSize, btnY + iconSize, emojiHover || emojiPanel.visible ? hc : nc);
        g.blit(EMOJI_ICON, btnX + 1, btnY + 1, 0, 0, iconSize - 2, iconSize - 2, iconSize - 2, iconSize - 2);

        btnX += iconSize + 2;
        boolean qpHover = mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize;
        g.fill(btnX, btnY, btnX + iconSize, btnY + iconSize, qpHover || quickPhrasesPanel.visible ? hc : nc);
        g.blit(QUICK_PHRASES_ICON, btnX + 1, btnY + 1, 0, 0, iconSize - 2, iconSize - 2, iconSize - 2, iconSize - 2);

        btnX += iconSize + 2;
        boolean searchHover = mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize;
        g.fill(btnX, btnY, btnX + iconSize, btnY + iconSize, searchHover || showSearch ? hc : nc);
        g.blit(SEARCH_ICON, btnX + 1, btnY + 1, 0, 0, iconSize - 2, iconSize - 2, iconSize - 2, iconSize - 2);

        btnX += iconSize + 2;
        boolean blockHover = mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize;
        g.fill(btnX, btnY, btnX + iconSize, btnY + iconSize, blockHover ? hc : nc);
        g.blit(BLOCK_ICON, btnX + 1, btnY + 1, 0, 0, iconSize - 2, iconSize - 2, iconSize - 2, iconSize - 2);

        btnX += iconSize + 2;
        boolean itemHover = mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize;
        g.fill(btnX, btnY, btnX + iconSize, btnY + iconSize, itemHover || itemPickerPanel.visible ? hc : nc);
        g.blit(ITEM_ICON, btnX + 1, btnY + 1, 0, 0, iconSize - 2, iconSize - 2, iconSize - 2, iconSize - 2);

        if (ModVoiceMessagesIntegration.canSendVoiceMessages()) {
            btnX += iconSize + 2;
            boolean vmHover = mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize;
            g.fill(btnX, btnY, btnX + iconSize, btnY + iconSize, vmHover ? hc : nc);
            g.blit(VOICE_ICON, btnX + 1, btnY + 1, 0, 0, iconSize - 2, iconSize - 2, iconSize - 2, iconSize - 2);
        }
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int ty = screenHeight - 14 - TOOLBAR_HEIGHT;
        int btnY = ty + 1;
        int iconSize = TOOLBAR_HEIGHT - 2;
        int btnX = SIDEBAR_WIDTH + 4;

        // Emoji button tooltip
        if (mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize) {
            g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.emoji"), mouseX, mouseY);
            return;
        }
        btnX += iconSize + 2;
        // Quick phrases tooltip
        if (mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize) {
            g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.quick_phrases"), mouseX, mouseY);
            return;
        }
        btnX += iconSize + 2;
        // Search button tooltip
        if (mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize) {
            g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.search"), mouseX, mouseY);
            return;
        }
        btnX += iconSize + 2;
        // Blocklist button tooltip
        if (mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize) {
            g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.blocklist"), mouseX, mouseY);
            return;
        }
        btnX += iconSize + 2;
        // Item picker button tooltip
        if (mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize) {
            g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.pick_item"), mouseX, mouseY);
            return;
        }

        // Voice mic button tooltip
        if (ModVoiceMessagesIntegration.canSendVoiceMessages()) {
            btnX += iconSize + 2;
            if (mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize) {
                g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.voice_message"), mouseX, mouseY);
                return;
            }
        }

        // Search close button tooltip
        if (showSearch) {
            int muteOffset = isCurrentChannelMuted() ? MUTE_BAR_H : 0;
            int barY = HEADER_BAR_HEIGHT + 6 + muteOffset;
            int areaRight = screenWidth;
            int closeX = areaRight - 18;
            if (mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= barY + 3 && mouseY <= barY + 17) {
                g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.search_close"), mouseX, mouseY);
                return;
            }
        }

        // Sidebar header icons
        if (mouseX < SIDEBAR_WIDTH) {
            int y = 10;
            int headerIdx = 0;
            for (int i = 0; i < sidebarEntries.size(); i++) {
                SidebarEntry entry = sidebarEntries.get(i);
                if (entry.isHeader) {
                    if (headerIdx == 0) {
                        int plusX = SIDEBAR_WIDTH - 6 - 10;
                        int plusY = y + (SIDEBAR_ITEM_HEIGHT - 8) / 2;
                        if (mouseX >= plusX && mouseX <= plusX + 10 && mouseY >= plusY && mouseY <= plusY + 10) {
                            g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.create_channel"), mouseX, mouseY);
                            return;
                        }
                        int joinX = plusX - 14;
                        if (mouseX >= joinX && mouseX <= joinX + 10 && mouseY >= plusY && mouseY <= plusY + 10) {
                            g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.join_channel"), mouseX, mouseY);
                            return;
                        }
                        int exploreX = joinX - 14;
                        if (mouseX >= exploreX && mouseX <= exploreX + 10 && mouseY >= plusY && mouseY <= plusY + 10) {
                            g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.explore_servers"), mouseX, mouseY);
                            return;
                        }
                    }
                    headerIdx++;
                    y += SIDEBAR_ITEM_HEIGHT;
                } else {
                    // Gear icon tooltip
                    if (entry.type == ChatMessageData.ConversationType.CHANNEL && entry.conversationId != null) {
                        int gearX = SIDEBAR_WIDTH - 6 - CONFIG_ICON_SIZE;
                        int gearY = y + (SIDEBAR_ITEM_HEIGHT - CONFIG_ICON_SIZE) / 2;
                        if (mouseX >= gearX && mouseX <= gearX + CONFIG_ICON_SIZE && mouseY >= gearY && mouseY <= gearY + CONFIG_ICON_SIZE) {
                            g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.channel_settings"), mouseX, mouseY);
                            return;
                        }
                    }
                    y += SIDEBAR_ITEM_HEIGHT;
                }
            }
        }

        // Item hover tooltip
        synchronized (itemHitBoxes) {
            for (BubbleItemHit hit : itemHitBoxes) {
                if (mouseX >= hit.x && mouseX <= hit.x + hit.w && mouseY >= hit.y && mouseY <= hit.y + hit.h) {
                    g.renderTooltip(font, hit.itemStack, mouseX, mouseY);
                    return;
                }
            }
        }

        // Context menu tooltips
        if (contextType == CTX_BUBBLE) {
            int menuH = 16 * 3 + 4;
            int menuX = Math.min(contextMenuX, this.width - 80 - 10);
            int menuY = contextMenuY - menuH;
            if (menuY < HEADER_BAR_HEIGHT + 6) menuY = contextMenuY + 4;

            if (mouseX >= menuX && mouseX <= menuX + 80) {
                if (mouseY >= menuY && mouseY <= menuY + 16) {
                    g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.copy"), mouseX, mouseY);
                } else if (mouseY >= menuY + 18 && mouseY <= menuY + 34) {
                    g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.reply"), mouseX, mouseY);
                } else if (mouseY >= menuY + 36 && mouseY <= menuY + 52) {
                    g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.blocklist"), mouseX, mouseY);
                }
            }
        }
    }

    private void drawWidgetPanels(GuiGraphics g, int mouseX, int mouseY) {
        // Search bar
        if (showSearch && searchInput != null) {
            int muteOffset = isCurrentChannelMuted() ? MUTE_BAR_H : 0;
            int barY = HEADER_BAR_HEIGHT + 6 + muteOffset;
            int barH = 20;
            int areaLeft = SIDEBAR_WIDTH + 2;
            int areaRight = this.width;
            g.fill(areaLeft, barY, areaRight, barY + barH, 0xBB1A1A2E);
            g.fill(areaLeft, barY + barH - 1, areaRight, barY + barH, 0x445A4A7E);

            searchInput.setX(areaLeft + 4);
            searchInput.setY(barY + 3);
            searchInput.setWidth(areaRight - areaLeft - 60);
            searchInput.render(g, mouseX, mouseY, 0);

            int closeX = areaRight - 18;
            boolean closeHover = mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= barY + 3 && mouseY <= barY + 17;
            g.fill(closeX, barY + 3, closeX + 14, barY + 17, closeHover ? 0x44FF4444 : 0x22333333);
            g.drawString(font, "X", closeX + 5, barY + 5, 0xFFFF6666, false);

            if (!searchResults.isEmpty()) {
                String counter = (searchResultIndex + 1) + "/" + searchResults.size();
                g.drawString(font, counter, searchInput.getX() + searchInput.getWidth() + 4, barY + 5, 0xFF8888FF, false);
            } else if (!searchQuery.isEmpty()) {
                g.drawString(font, Component.translatable("screen.chatsphere.search.no_match"), searchInput.getX() + searchInput.getWidth() + 4, barY + 5, 0xFF888888, false);
            }
        }

        // Emoji panel
        if (emojiPanel.visible) {
            emojiPanel.render(g, SIDEBAR_WIDTH + 4, this.height - 14 - TOOLBAR_HEIGHT - EmojiPanel.PANEL_H - 4, mouseX, mouseY);
        }

        // Quick phrases panel
        if (quickPhrasesPanel.visible) {
            int qpY = this.height - 14 - TOOLBAR_HEIGHT - 4 - Math.min(Math.min(ModClientConfig.CONFIG.quickPhrases.get().size(), 6) * 18, 112);
            quickPhrasesPanel.render(g, SIDEBAR_WIDTH + 4, qpY, mouseX, mouseY);
        }

        // Item picker panel
        if (itemPickerPanel.visible) {
            int ipY = this.height - 14 - TOOLBAR_HEIGHT - ItemPickerPanel.ITEM_PICKER_PANEL_H - 4;
            itemPickerPanel.render(g, mouseX, mouseY, SIDEBAR_WIDTH + 4, ipY);
        }

        // Mention popup
        if (mentionPopup.visible) {
            mentionPopup.render(g, input, mouseX, mouseY);
        }

        // Emoji autocomplete
        if (emojiAutoComplete.visible) {
            emojiAutoComplete.render(g, input, mouseX, mouseY);
        }

        // Context menu
        if (contextType == CTX_BUBBLE) {
            drawContextMenu(g, mouseX, mouseY);
        }

        replyBar.render(g, mouseX, mouseY, SIDEBAR_WIDTH, this.width, 0, false);

        // Copy toast
        copyToast.render(g, SIDEBAR_WIDTH, this.width);
    }

    private void drawContextMenu(GuiGraphics g, int mouseX, int mouseY) {
        int menuH = 16 * 3 + 4;
        int menuX = Math.min(contextMenuX, this.width - 80 - 10);
        int menuY = contextMenuY - menuH;
        if (menuY < HEADER_BAR_HEIGHT + 6) menuY = contextMenuY + 4;

        g.fill(menuX, menuY, menuX + 80, menuY + menuH, 0xDD2A2A4E);
        g.renderOutline(menuX, menuY, 80, menuH, 0xFF6666AA);

        boolean hoverCopy = mouseY >= menuY && mouseY <= menuY + 16;
        g.fill(menuX + 1, menuY + 1, menuX + 79, menuY + 17, hoverCopy ? 0x44448888 : 0);
        g.drawString(font, Component.translatable("screen.chatsphere.context.copy"), menuX + 8, menuY + 3, 0xCCCCCC, false);

        boolean hoverReply = mouseY >= menuY + 18 && mouseY <= menuY + 34;
        g.fill(menuX + 1, menuY + 18, menuX + 79, menuY + 34, hoverReply ? 0x44448888 : 0);
        g.drawString(font, Component.translatable("screen.chatsphere.context.reply"), menuX + 8, menuY + 20, 0xCCCCCC, false);

        boolean hoverBlock = mouseY >= menuY + 36 && mouseY <= menuY + 52;
        g.fill(menuX + 1, menuY + 36, menuX + 79, menuY + 52, hoverBlock ? 0x44884444 : 0);
        g.drawString(font, Component.translatable("screen.chatsphere.context.block"), menuX + 8, menuY + 38, 0xFFAA6666, false);
    }

    private void handleContextMenuClick(int mx, int my) {
        int menuH = 16 * 3 + 4;
        int menuX = Math.min(contextMenuX, this.width - 80 - 10);
        int menuY = contextMenuY - menuH;
        if (menuY < HEADER_BAR_HEIGHT + 6) menuY = contextMenuY + 4;

        if (mx >= menuX && mx <= menuX + 80) {
            ChatHistoryManager history = ChatHistoryManager.getInstance();
            ChatMessageData msg = history.getMessageByIndex(contextMsgIndex);
            if (msg == null) { contextType = CTX_NONE; contextMsgIndex = -1; return; }
            if (my >= menuY && my <= menuY + 16) {
                minecraft.keyboardHandler.setClipboard(msg.content().getString());
                copyToast.show();
            } else if (my >= menuY + 18 && my <= menuY + 34) {
                replyBar.targetIndex = contextMsgIndex;
                replyBar.replyText = msg.content().getString();
                replyBar.replySender = msg.senderName().getString();
            } else if (my >= menuY + 36 && my <= menuY + 52) {
                if (msg.senderUuid() != null && !msg.isOwn()) {
                    history.blockPlayer(msg.senderUuid().toString());
                }
            }
        }
        contextType = CTX_NONE;
        contextMsgIndex = -1;
    }

    private void scrollToMessageIndex(int targetIdx) {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        List<ChatMessageData> msgs = history.getMessagesByConversation(currentConversation);
        for (int i = 0; i < msgs.size(); i++) {
            if (history.getMessageIndex(msgs.get(i)) == targetIdx) {
                int visible = Math.max(1, (height - chatAreaTop() - 14 - TOOLBAR_HEIGHT - MESSAGE_BOTTOM_PAD) / 30);
                scrollOffset = Math.max(0, msgs.size() - 1 - i - visible / 2);
                scrollOffset = Math.min(scrollOffset, maxScrollOffset());
                break;
            }
        }
    }

    private void buildSidebarEntries() {
        sidebarEntries.clear();
        ChatHistoryManager history = ChatHistoryManager.getInstance();

        sidebarEntries.add(new SidebarEntry(null,
                Component.translatable("screen.chatsphere.mod_chat.channels_header"),
                null, true, null));

        List<String> channelIds = history.getChannels();
        for (String id : channelIds) {
            if (id == null || id.isEmpty() || id.equals("null")) continue;
            Component display = history.getConversationDisplayName(id);
            if (display == null) display = Component.literal(id);
            sidebarEntries.add(new SidebarEntry(id, display,
                    ChatMessageData.ConversationType.CHANNEL, false, null));
        }
        if (channelIds.isEmpty()) {
            sidebarEntries.add(new SidebarEntry(ChatHistoryManager.DEFAULT_CHANNEL_ID,
                    Component.translatable("screen.chatsphere.mod_chat.general_channel"),
                    ChatMessageData.ConversationType.CHANNEL, false, null));
        }

        sidebarEntries.add(new SidebarEntry(null,
                Component.translatable("screen.chatsphere.mod_chat.private_header"),
                null, true, null));

        for (String id : history.getConversationIds()) {
            if (id == null || id.isEmpty() || id.equals("null")) continue;
            if (id.equals(ChatHistoryManager.DEFAULT_CHANNEL_ID)) continue; // 跳过默认频道被误加入私聊
            if (history.getConversationType(id) == ChatMessageData.ConversationType.PRIVATE) {
                Component name = history.getConversationDisplayName(id);
                if (name == null) continue;
                UUID targetUuid = null;
                if (id.contains(":") && this.minecraft != null && this.minecraft.player != null) {
                    try {
                        String[] parts = id.split(":");
                        String localStr = this.minecraft.player.getUUID().toString();
                        String targetStr = parts[0].equals(localStr) ? parts[1] : parts[0];
                        if (targetStr.length() == 36) {
                            targetUuid = UUID.fromString(targetStr);
                        }
                    } catch (Exception ignored) {}
                }
                sidebarEntries.add(new SidebarEntry(id, name,
                        ChatMessageData.ConversationType.PRIVATE, false, targetUuid));
            }
        }

        sidebarEntries.add(new SidebarEntry(null,
                Component.translatable("screen.chatsphere.mod_chat.commands_header"),
                null, true, null));
        sidebarEntries.add(new SidebarEntry(COMMAND_CONVERSATION_ID,
                Component.translatable("screen.chatsphere.mod_chat.console_name"),
                ChatMessageData.ConversationType.COMMAND, false, null));
    }

    private void drawSidebar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.fill(0, 0, SIDEBAR_WIDTH, this.height, 0xDD1A1A2E);

        int y = 10;
        int headerIdx = 0;
        for (int i = 0; i < sidebarEntries.size(); i++) {
            SidebarEntry entry = sidebarEntries.get(i);
            boolean hovered = mouseX < SIDEBAR_WIDTH && mouseX >= 0
                    && mouseY >= y && mouseY < y + SIDEBAR_ITEM_HEIGHT;
            boolean active = entry.conversationId != null && entry.conversationId.equals(currentConversation);

            if (entry.isHeader) {
                if (headerIdx > 0 && y > 20) {
                    guiGraphics.fill(4, y - 3, SIDEBAR_WIDTH - 4, y - 2, 0x44FFFFFF);
                }
                guiGraphics.drawString(font, entry.displayName, 8, y + 3, 0xFF888888, false);
                if (headerIdx == 0) {
                    int plusX = SIDEBAR_WIDTH - 6 - 10;
                    int plusY = y + (SIDEBAR_ITEM_HEIGHT - 8) / 2;
                    boolean plusHovered = mouseX >= plusX && mouseX <= plusX + 10
                            && mouseY >= plusY && mouseY <= plusY + 10;
                    guiGraphics.fill(plusX, plusY, plusX + 10, plusY + 10,
                            plusHovered ? 0xFF44AA44 : 0xFF333388);
                    guiGraphics.blit(CREATE_CHANNEL_ICON, plusX + 1, plusY + 1, 0, 0, 8, 8, 8, 8);

                    int joinX = plusX - 14;
                    int joinY = plusY;
                    boolean joinHovered = mouseX >= joinX && mouseX <= joinX + 10
                            && mouseY >= joinY && mouseY <= joinY + 10;
                    guiGraphics.fill(joinX, joinY, joinX + 10, joinY + 10,
                            joinHovered ? 0xFF4488AA : 0xFF333366);
                    guiGraphics.blit(JOIN_CHANNEL_ICON, joinX + 1, joinY + 1, 0, 0, 8, 8, 8, 8);

                    int exploreX = joinX - 14;
                    boolean exploreHovered = mouseX >= exploreX && mouseX <= exploreX + 10
                            && mouseY >= joinY && mouseY <= joinY + 10;
                    guiGraphics.fill(exploreX, joinY, exploreX + 10, joinY + 10,
                            exploreHovered ? 0xFF44AA88 : 0xFF334466);
                    guiGraphics.blit(SEARCH_ICON, exploreX + 1, joinY + 1, 0, 0, 8, 8, 8, 8);
                }
                headerIdx++;
                y += SIDEBAR_ITEM_HEIGHT;
            } else {
                int bgColor = active ? 0x66333388 : (hovered ? 0x44333388 : 0x00000000);
                if (bgColor != 0) {
                    guiGraphics.fill(2, y, SIDEBAR_WIDTH - 2, y + SIDEBAR_ITEM_HEIGHT, bgColor);
                }
                if (entry.type == ChatMessageData.ConversationType.COMMAND) {
                    guiGraphics.drawString(font, ">", 4, y + 4, 0xFF66AA66, false);
                    guiGraphics.drawString(font, entry.displayName, 12, y + 4,
                            active ? 0xFFFFFFFF : 0xFFAAAAAA, false);
                } else if (entry.type == ChatMessageData.ConversationType.PRIVATE) {
                    drawPlayerFace(guiGraphics, entry.targetUuid, 4, y + 3, SIDEBAR_AVATAR_SIZE);
                    guiGraphics.drawString(font, entry.displayName, 4 + SIDEBAR_AVATAR_SIZE + 2, y + 4,
                            0xFFCCCCCC, false);
                    boolean online = entry.targetUuid != null && onlinePlayers.containsKey(entry.targetUuid.toString());
                    int dotColor = online ? 0xFF44FF44 : 0xFF666666;
                    guiGraphics.fill(SIDEBAR_WIDTH - 8, y + SIDEBAR_ITEM_HEIGHT - 6, SIDEBAR_WIDTH - 4, y + SIDEBAR_ITEM_HEIGHT - 2, dotColor);
                } else {
                    MutableComponent label = Component.literal("# ");
                    label.append(entry.displayName);
                    guiGraphics.drawString(font, label, 6, y + 4,
                            active ? 0xFFFFFFFF : 0xFFAAAAAA, false);

                    int gearX = SIDEBAR_WIDTH - 6 - CONFIG_ICON_SIZE;
                    int gearY = y + (SIDEBAR_ITEM_HEIGHT - CONFIG_ICON_SIZE) / 2;
                    boolean gearHovered = mouseX >= gearX && mouseX <= gearX + CONFIG_ICON_SIZE
                            && mouseY >= gearY && mouseY <= gearY + CONFIG_ICON_SIZE;
                    int tint = gearHovered ? 0xFFFFFFFF : 0xCCAAAAAA;
                    guiGraphics.setColor(
                            ((tint >> 16) & 0xFF) / 255f,
                            ((tint >> 8) & 0xFF) / 255f,
                            (tint & 0xFF) / 255f,
                            ((tint >> 24) & 0xFF) / 255f);
                    guiGraphics.blit(SETTINGS_ICON, gearX, gearY, 0, 0, CONFIG_ICON_SIZE, CONFIG_ICON_SIZE, CONFIG_ICON_SIZE, CONFIG_ICON_SIZE);
                    guiGraphics.setColor(1f, 1f, 1f, 1f);
                }
                y += SIDEBAR_ITEM_HEIGHT;
            }
        }
    }

    private void drawPlayerFace(GuiGraphics guiGraphics, UUID uuid, int x, int y, int size) {
        if (uuid == null) return;
        PlayerFaceRenderer.draw(guiGraphics, PlayerSkinCache.getSkin(uuid), x, y, size);
    }

    private void drawHeaderBar(GuiGraphics guiGraphics) {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        ChatMessageData.ConversationType type = history.getConversationType(currentConversation);

        Component header;
        if (type == ChatMessageData.ConversationType.COMMAND) {
            header = Component.literal("> ")
                    .append(history.getConversationDisplayName(currentConversation))
                    .withStyle(ChatFormatting.BOLD);
        } else if (type == ChatMessageData.ConversationType.CHANNEL) {
            header = Component.literal("# ").append(history.getConversationDisplayName(currentConversation))
                    .withStyle(ChatFormatting.BOLD);
        } else {
            header = Component.literal("").append(history.getConversationDisplayName(currentConversation))
                    .withStyle(ChatFormatting.BOLD);
        }

        int hw = font.width(header);
        int headerX = SIDEBAR_WIDTH + (this.width - SIDEBAR_WIDTH - hw) / 2;
        guiGraphics.drawString(font, header, headerX, 3, 0xFFFFFFFF, false);

        if (type == ChatMessageData.ConversationType.PRIVATE) {
            boolean online = currentConversation != null && isTargetOnline(currentConversation);
            int dotX = SIDEBAR_WIDTH + 4;
            int dotY = 6;
            int dotR = 3;
            int dotColor = online ? 0xFF44FF44 : 0xFF666666;
            guiGraphics.fill(dotX - dotR, dotY - dotR, dotX + dotR, dotY + dotR, dotColor);
        }

        if (isCurrentChannelMuted()) {
            int barY = HEADER_BAR_HEIGHT + 2;
            guiGraphics.fill(SIDEBAR_WIDTH, barY, width, barY + MUTE_BAR_H, 0xCC661111);
            guiGraphics.fill(SIDEBAR_WIDTH, barY + MUTE_BAR_H - 1, width, barY + MUTE_BAR_H, 0xFF441111);
            Component muteText = Component.translatable("chatsphere.mute.feedback");
            guiGraphics.drawString(font, muteText, SIDEBAR_WIDTH + 6, barY + 3, 0xFFFF8888, false);
        }
    }

    private boolean isTargetOnline(String convId) {
        if (minecraft == null || minecraft.player == null || convId == null || !convId.contains(":"))
            return false;
        String[] parts = convId.split(":");
        String localStr = minecraft.player.getUUID().toString();
        String targetStr = parts[0].equals(localStr) ? parts[1] : parts[0];
        return onlinePlayers.containsKey(targetStr);
    }

    private boolean isCurrentChannelMuted() {
        if (minecraft == null || minecraft.player == null) return false;
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        if (history.getConversationType(currentConversation) != ChatMessageData.ConversationType.CHANNEL)
            return false;
        var cfg = history.getChannelConfig(currentConversation);
        return cfg.mutedPlayers.contains(minecraft.player.getUUID().toString());
    }

    private void renderNotificationBar(GuiGraphics g, int screenHeight) {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        int unread = history.getUnreadCount(currentConversation);
        boolean atBottom = scrollOffset <= 1;
        if (atBottom) {
            if (unread > 0) history.markConversationRead(currentConversation);
            unread = 0;
        }
        if (unread <= 0) return;

        int barY = screenHeight - 14 - TOOLBAR_HEIGHT - NOTIF_BAR_H;
        notifBarY = barY;
        int barH = NOTIF_BAR_H;

        g.fillGradient(SIDEBAR_WIDTH, barY, width, barY + barH, 0xCC1A1A3E, 0xCC2A2A4E);
        g.fill(SIDEBAR_WIDTH, barY, width, barY + 1, 0x665A4A7E);
        g.fill(SIDEBAR_WIDTH, barY + barH - 1, width, barY + barH, 0x665A4A7E);

        String text = Component.translatable("chatsphere.notif.new_messages", unread).getString() + " \u25BD";
        int textW = font.width(text);
        int textX = SIDEBAR_WIDTH + (width - SIDEBAR_WIDTH - textW) / 2;
        g.drawString(font, text, textX + 1, barY + 5, 0xAA000000, false);
        g.drawString(font, text, textX, barY + 4, 0xFFFFCC66, false);
    }

    private int chatAreaTop() {
        return HEADER_BAR_HEIGHT + 6 + (isCurrentChannelMuted() ? MUTE_BAR_H : 0);
    }

    private void renderMessages(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        int chatAreaLeft = SIDEBAR_WIDTH + 4;
        int chatAreaRight = screenWidth - 4;
        int chatAreaTop = chatAreaTop();
        int chatAreaBottom = screenHeight - 14 - TOOLBAR_HEIGHT - MESSAGE_BOTTOM_PAD - (replyBar.targetIndex >= 0 ? ReplyBarWidget.BAR_HEIGHT + 2 : 0);

        ChatHistoryManager history = ChatHistoryManager.getInstance();
        List<ChatMessageData> messages = history.getMessagesByConversation(currentConversation);
        int totalMessages = messages.size();
        if (totalMessages == 0) return;
        synchronized (cmdHitBoxes) { cmdHitBoxes.clear(); }
        synchronized (voiceHitBoxes) { voiceHitBoxes.clear(); }
        synchronized (bubbleHitBoxes) { bubbleHitBoxes.clear(); }
        synchronized (replyQuoteHitBoxes) { replyQuoteHitBoxes.clear(); }
        synchronized (itemHitBoxes) { itemHitBoxes.clear(); }

        int yOffset = chatAreaBottom;
        int idx = Math.max(0, totalMessages - 1 - scrollOffset);
        int sepInterval = ModClientConfig.CONFIG.timeSeparatorMinutes.get();
        String lastTimeKey = null;

        ChatHistoryManager historyMgr = history;
        for (int i = idx; i >= 0; i--) {
            ChatMessageData msg = messages.get(i);

            if (msg.senderUuid() != null && historyMgr.isPlayerBlocked(msg.senderUuid().toString())) {
                yOffset -= 2;
                continue;
            }

            // Time separator
            if (sepInterval > 0) {
                String key = ChatHistoryManager.timeSeparatorKey(msg.timestamp(), sepInterval);
                if (lastTimeKey != null && !key.equals(lastTimeKey)) {
                    yOffset -= 16;
                    if (yOffset < chatAreaTop) break;
                    String sepText = ChatHistoryManager.formatTimestamp(msg.timestamp());
                    int sepW = font.width(sepText);
                    int sepX = chatAreaLeft + (chatAreaRight - chatAreaLeft - sepW) / 2;
                    guiGraphics.drawString(font, sepText, sepX, yOffset + 1, 0xFF888888, false);
                    guiGraphics.fill(chatAreaLeft + 8, yOffset + 10, chatAreaRight - 8, yOffset + 11, 0x44FFFFFF);
                }
                lastTimeKey = key;
            }

            int bubbleHeight = renderMessageBubble(guiGraphics, msg, chatAreaLeft, chatAreaRight, yOffset);
            synchronized (bubbleHitBoxes) {
                bubbleHitBoxes.add(new BubbleHit(chatAreaLeft, yOffset - bubbleHeight, chatAreaRight - chatAreaLeft, bubbleHeight, history.getMessageIndex(msg)));
            }
            if (showSearch && !searchQuery.isEmpty() && !searchResults.isEmpty()) {
                int globalIdx = history.getMessageIndex(msg);
                if (searchResults.contains(globalIdx)) {
                    boolean isCurrent = searchResultIndex >= 0 && searchResultIndex < searchResults.size()
                            && searchResults.get(searchResultIndex) == globalIdx;
                    int hlColor = isCurrent ? 0x44FFAA00 : 0x228888FF;
                    guiGraphics.fill(chatAreaLeft, yOffset - bubbleHeight + 2, chatAreaRight, yOffset + 2, hlColor);
                }
            }
            if (replyHighlightTarget >= 0) {
                int globalIdx = history.getMessageIndex(msg);
                if (globalIdx == replyHighlightTarget) {
                    guiGraphics.fill(chatAreaLeft, yOffset - bubbleHeight + 2, chatAreaRight, yOffset + 2, 0x4433AA33);
                }
            }
            yOffset -= bubbleHeight + 2;
            if (yOffset < chatAreaTop) break;
        }
    }

    private int renderMessageBubble(GuiGraphics guiGraphics, ChatMessageData msg,
                                    int areaLeft, int areaRight, int y) {
        Minecraft mc = Minecraft.getInstance();
        boolean showName = ModClientConfig.CONFIG.showSenderName.get();
        boolean showTime = ModClientConfig.CONFIG.showTimestamp.get();
        boolean showAvatar = ModClientConfig.CONFIG.showAvatar.get();
        boolean isDark = ModClientConfig.CONFIG.themeDark.get();

        boolean isCommand = msg.conversationType() == ChatMessageData.ConversationType.COMMAND;

        Component contentText;
        MutableComponent infoLine = Component.literal("");
        if (isCommand) {
            contentText = msg.senderName().copy();
            if (msg.isOwn()) {
                infoLine.append(Component.literal("\u8F93\u5165").withStyle(ChatFormatting.DARK_GREEN));
            } else {
                infoLine.append(Component.literal("\u8F93\u51FA").withStyle(ChatFormatting.GRAY));
            }
        } else {
            contentText = msg.renderedContent();
            if (showName) {
                infoLine.append(msg.senderName().copy().withStyle(ChatFormatting.AQUA));
            }
        }
        // Replace raw VoiceMessage#UUID text with a short label when VM mod is absent
        if (contentText.getString().startsWith("VoiceMessage#") && !ModVoiceMessagesIntegration.isVoiceMessagesLoaded()) {
            contentText = Component.translatable("chatsphere.voice.received").withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        if (showTime && !isCommand) {
            String ts = ChatHistoryManager.formatTimestamp(msg.timestamp());
            if (showName) infoLine.append("  ");
            infoLine.append(Component.literal(ts).withStyle(ChatFormatting.GRAY));
        }

        int dupW = 0;
        String dupLabel = null;
        if (msg.duplicateCount() > 1) {
            dupLabel = "x" + msg.duplicateCount();
            dupW = mc.font.width(dupLabel) + 3;
        }
        int replyW = msg.replyContent() != null ? mc.font.width(" ↑ " + msg.replySender()) : 0;

        int infoWidth = mc.font.width(infoLine);
        int maxLineWidth = infoWidth;
        if (isCommand) {
            int prefixWidth = mc.font.width(msg.isOwn() ? "> " : "→ ");
            String raw = contentText.getString();
            if (raw.contains("\n")) {
                int maxW = 0;
                for (String l : raw.split("\n", -1))
                    maxW = Math.max(maxW, mc.font.width(l));
                maxLineWidth = Math.max(maxLineWidth, maxW + prefixWidth);
            } else {
                int contentW = mc.font.width(contentText);
                maxLineWidth = Math.max(maxLineWidth, contentW + prefixWidth);
            }
        } else {
            int contentW = mc.font.width(contentText);
            maxLineWidth = Math.max(maxLineWidth, contentW);
        }

        int lineH = mc.font.lineHeight;
        int lines;
        if (isCommand) {
            String raw = contentText.getString();
            if (raw.contains("\n")) {
                lines = raw.split("\n", -1).length;
            } else {
                lines = 1;
            }
        } else {
            lines = 2;
        }
        if (msg.replyContent() != null) lines++;
        if (!isCommand && msg.content().getString().startsWith("VoiceMessage#") && ModVoiceMessagesIntegration.isVoiceMessagesLoaded()) lines += 2;
        boolean hasItem = msg.itemNbt() != null && !msg.itemNbt().isEmpty();

        int contentH = lines * lineH;
        if (hasItem) contentH += 18 - lineH; // item line is taller than normal line

        int avReserve = (showAvatar && !isCommand) ? AVATAR_SIZE + 4 : 0;
        int bubbleW = Math.min(maxLineWidth + replyW + BUBBLE_HPAD * 2, areaRight - areaLeft - avReserve - 30);
        int bubbleH = contentH + BUBBLE_VPAD * 2 + 1;

        int bubbleX;

        if (isCommand) {
            bubbleX = areaRight - bubbleW;
        } else if (msg.isOwn()) {
            bubbleX = Math.max(areaLeft, areaRight - bubbleW - avReserve);
        } else {
            bubbleX = Math.max(areaLeft + avReserve, areaLeft);
        }
        int bubbleY = y - bubbleH;
        if (bubbleY < HEADER_BAR_HEIGHT + 6) return bubbleH + 2;

        int bgColor;
        if (isCommand) {
            bgColor = msg.isOwn() ? 0xFF2D2D2D : 0xFF1E1E2E;
        } else if (msg.isOwn()) {
            bgColor = ModClientConfig.parseHexColor(ModClientConfig.CONFIG.bubbleColorOwn.get(), isDark ? 0xFF1D3B5C : 0xFFD9E8FF);
        } else {
            bgColor = ModClientConfig.parseHexColor(ModClientConfig.CONFIG.bubbleColorOther.get(), isDark ? 0xFF26262E : 0xFFFFFFFF);
        }
        fillRoundedRect(guiGraphics, bubbleX, bubbleY, bubbleW, bubbleH,
                ModClientConfig.CONFIG.bubbleCornerRadius.get(), bgColor);

        if (dupLabel != null) {
            int dupColor = 0xFFFFAA00;
            int dupX, dupY = bubbleY + (bubbleH - lineH) / 2;
            if (msg.isOwn()) {
                dupX = bubbleX - dupW;
            } else {
                dupX = bubbleX + bubbleW + 2;
            }
            if (dupX >= SIDEBAR_WIDTH + 4) {
                guiGraphics.drawString(mc.font, dupLabel, dupX, dupY, dupColor, false);
            }
        }

        if (showAvatar && !isCommand) {
            UUID senderUuid = msg.senderUuid();
            if (senderUuid != null) {
                int avX = msg.isOwn() ? areaRight - AVATAR_SIZE : areaLeft;
                drawPlayerFace(guiGraphics, senderUuid, avX, bubbleY + 4, AVATAR_SIZE);
            }
        }

        int textX = bubbleX + BUBBLE_HPAD;
        int textY = bubbleY + BUBBLE_VPAD;

        // Reply quote line
        if (msg.replyContent() != null) {
            String replyPrefix = "↑ " + msg.replySender() + ": ";
            String rawReply = replyPrefix + msg.replyContent();
            String truncated = mc.font.plainSubstrByWidth(rawReply, bubbleW - BUBBLE_HPAD * 2);
            Component replyComponent = EmojiRegistry.toComponent(truncated);
            int emojiOff = EmojiRegistry.containsPua(replyComponent) ? EmojiRegistry.EMOJI_Y_OFFSET : 0;
            int replyQuoteW = mc.font.width(truncated);
            int quoteY = textY - 2;
            guiGraphics.drawString(mc.font, replyComponent, textX, quoteY + emojiOff, 0xFF8888FF, false);
            synchronized (replyQuoteHitBoxes) {
                replyQuoteHitBoxes.add(new ReplyQuoteHit(textX, quoteY, replyQuoteW, lineH, msg.replySender(), msg.replyContent()));
            }
            textY += lineH;
        }

        // Item display
        boolean itemRendered = false;
        if (hasItem) {
            ItemStack itemStack = cn.sarskin.ChatSphere.util.ItemSerialization.deserialize(msg.itemNbt());
            if (!itemStack.isEmpty()) {
                guiGraphics.renderItem(itemStack, textX, textY);
                Component itemName = itemStack.getHoverName();
                int maxNameW = bubbleW - BUBBLE_HPAD * 2 - 18;
                String nameStr = mc.font.plainSubstrByWidth(itemName.getString(), Math.max(maxNameW, 0));
                int nameX = textX + 18;
                guiGraphics.drawString(mc.font, nameStr, nameX, textY + 4, 0xFFFFAA00, false);
                int itemW = 18 + mc.font.width(nameStr);
                synchronized (itemHitBoxes) {
                    itemHitBoxes.add(new BubbleItemHit(textX, textY, itemW, 16, itemStack));
                }
                textY += 18;
                itemRendered = true;
            }
        }

        if (isCommand) {
            boolean isVoice = false;
            java.util.UUID vmUuid = null;
            if (msg.content().getString().startsWith("VoiceMessage#") && ModVoiceMessagesIntegration.isVoiceMessagesLoaded()) {
                try {
                    vmUuid = java.util.UUID.fromString(msg.content().getString().substring("VoiceMessage#".length()));
                    isVoice = true;
                } catch (Exception ignored) {}
            }
            if (isVoice) {
                int vmBg = 0x00000000;
                int vmH = 20;
                int vmY = textY;
                if (msg.isOwn()) vmY = textY - 2;
                int vmX = textX;
                int vmW = bubbleW - BUBBLE_HPAD * 2;
                Object pp = voicePlayerCache.computeIfAbsent(vmUuid, u -> ModVoiceMessagesIntegration.createPlaybackPlayer(u, vmBg));
                if (pp != null) {
                    ModVoiceMessagesIntegration.setupPlaybackPlayer(pp, vmX, vmY, vmW, vmH);
                    ModVoiceMessagesIntegration.renderPlaybackPlayer(pp, guiGraphics);
                    synchronized (voiceHitBoxes) { voiceHitBoxes.add(new VoiceHit(vmX, vmY, vmW, vmH, vmUuid, pp)); }
                }
            } else {
                guiGraphics.enableScissor(bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH);
                String raw = contentText.getString();
                boolean multiLine = raw.contains("\n");
                if (multiLine) {
                    List<Component> lineComps = splitCommandLines(contentText);
                    for (int li = 0; li < lineComps.size(); li++) {
                        Component line = lineComps.get(li);
                        if (line.getString().isEmpty()) continue;
                        Component displayLine = msg.isOwn()
                                ? Component.literal("> ").withStyle(ChatFormatting.GREEN).append(line)
                                : Component.literal("\u2192 ").withStyle(ChatFormatting.GRAY).append(line);
                        int ly = textY + li * lineH;
                        guiGraphics.drawString(mc.font, displayLine, textX, ly, 0xFFFFFFFF, false);
                        synchronized (cmdHitBoxes) { cmdHitBoxes.add(new CommandHit(textX, ly, bubbleW - BUBBLE_HPAD * 2, lineH, displayLine)); }
                    }
                } else {
                    Component displayText = msg.isOwn()
                            ? Component.literal("> ").withStyle(ChatFormatting.GREEN).append(contentText)
                            : Component.literal("\u2192 ").withStyle(ChatFormatting.GRAY).append(contentText);
                    guiGraphics.drawString(mc.font, displayText, textX, textY, 0xFFFFFFFF, false);
                    synchronized (cmdHitBoxes) { cmdHitBoxes.add(new CommandHit(textX, textY, bubbleW - BUBBLE_HPAD * 2, lineH, displayText)); }
                }
                guiGraphics.disableScissor();
            }
        } else {
            int textColor = msg.isOwn() ? 0xFFF0F0F0 : 0xFF1A1A1A;
            guiGraphics.drawString(mc.font, infoLine, textX, textY, 0xFF555555, false);
            textY += lineH + 1;
            boolean isVoice = false;
            java.util.UUID vmUuid = null;
            if (msg.content().getString().startsWith("VoiceMessage#") && ModVoiceMessagesIntegration.isVoiceMessagesLoaded()) {
                try {
                    vmUuid = java.util.UUID.fromString(msg.content().getString().substring("VoiceMessage#".length()));
                    isVoice = true;
                } catch (Exception ignored) {}
            }
            if (isVoice) {
                int vmBg = msg.isOwn() ? 0x44000000 : 0x44FFFFFF;
                Object pp = voicePlayerCache.computeIfAbsent(vmUuid, u -> ModVoiceMessagesIntegration.createPlaybackPlayer(u, vmBg));
                if (pp != null) {
                    int vmW = bubbleW - BUBBLE_HPAD * 2;
                    int vmH = 20;
                    ModVoiceMessagesIntegration.setupPlaybackPlayer(pp, textX, textY, vmW, vmH);
                    ModVoiceMessagesIntegration.renderPlaybackPlayer(pp, guiGraphics);
                    synchronized (voiceHitBoxes) { voiceHitBoxes.add(new VoiceHit(textX, textY, vmW, vmH, vmUuid, pp)); }
                }
            } else if (!itemRendered || !contentText.getString().matches("\\[\\d+\\]")) {
                int emojiOff = EmojiRegistry.containsPua(contentText) ? EmojiRegistry.EMOJI_Y_OFFSET : 0;
                guiGraphics.drawString(mc.font, contentText, textX, textY + emojiOff, textColor, false);
            }
        }

        return bubbleH + 2;
    }

    private void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { g.fill(x, y, x + w, y + h, color); return; }
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int dy = 0; dy < r; dy++) {
            int dx = (int) Math.sqrt(r * r - dy * dy);
            int sy1 = y + r - dy - 1;
            g.fill(x + r - dx, sy1, x + r + dx, sy1 + 1, color);
            g.fill(x + w - r - dx, sy1, x + w - r + dx, sy1 + 1, color);
            int sy2 = y + h - r + dy;
            g.fill(x + r - dx, sy2, x + r + dx, sy2 + 1, color);
            g.fill(x + w - r - dx, sy2, x + w - r + dx, sy2 + 1, color);
        }
    }

    private int computeBubbleX(ChatMessageData msg, int areaRight) {
        boolean showAvatar = ModClientConfig.CONFIG.showAvatar.get();
        if (msg.conversationType() == ChatMessageData.ConversationType.COMMAND || msg.isOwn()) {
            int bw = computeBubbleWidth(msg, areaRight - SIDEBAR_WIDTH);
            return areaRight - bw;
        }
        int offset = (showAvatar ? AVATAR_SIZE + 4 : 0);
        return Math.max(SIDEBAR_WIDTH + 4 + offset, SIDEBAR_WIDTH + 4);
    }

    private int computeBubbleWidth(ChatMessageData msg, int maxAreaWidth) {
        return estimateBubbleWidth(msg, maxAreaWidth);
    }

    private int estimateBubbleWidth(ChatMessageData msg, int maxAreaWidth) {
        Minecraft mc = Minecraft.getInstance();
        String raw = msg.content().getString();
        String displayText = msg.renderedContent().getString();
        int textW = mc.font.width(displayText);
        int nameW = mc.font.width(msg.senderName());
        boolean isCommand = msg.conversationType() == ChatMessageData.ConversationType.COMMAND;
        int prefixW = isCommand ? mc.font.width(msg.isOwn() ? "> " : "\u2192 ") : 0;
        int maxTextW = Math.max(textW + prefixW, nameW);
        int dupW = msg.duplicateCount() > 1 ? mc.font.width(" x" + msg.duplicateCount()) : 0;
        int replyW = msg.replyContent() != null ? mc.font.width(" \u2191 " + msg.replySender()) : 0;
        return Math.min(maxTextW + dupW + replyW + BUBBLE_HPAD * 2, maxAreaWidth - 30);
    }

    private int estimateBubbleHeight(ChatMessageData msg, int maxAreaWidth) {
        Minecraft mc = Minecraft.getInstance();
        int lines = msg.conversationType() == ChatMessageData.ConversationType.COMMAND ? 1 : 2;
        if (msg.replyContent() != null) lines++;
        int h = lines * mc.font.lineHeight;
        boolean hasItem = msg.itemNbt() != null && !msg.itemNbt().isEmpty();
        if (hasItem) h += 18 - mc.font.lineHeight;
        return h + BUBBLE_VPAD * 2 + 1;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int getMessageCount() {
        return ChatHistoryManager.getInstance().getMessagesByConversation(currentConversation).size();
    }

    private int getVisibleMessageCount() {
        int avail = (height - 14 - TOOLBAR_HEIGHT - MESSAGE_BOTTOM_PAD - chatAreaTop()) / (font.lineHeight + 10);
        return Math.max(1, avail);
    }

    private int maxScrollOffset() {
        int limit = ModClientConfig.CONFIG.scrollHistoryLimit.get();
        int total = getMessageCount();
        int capped = Math.min(total, limit);
        return Math.max(0, capped - getVisibleMessageCount());
    }

    private void sendChannelChatPacket(String channelId, String text, String replyContent, String replySender) {
        if (this.minecraft == null || this.minecraft.getConnection() == null) return;
        String itemNbt = pendingItemNbt;
        pendingItemNbt = null;
        var conn = this.minecraft.getConnection().getConnection();
        conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new ServerboundChannelActionPayload(
                        ServerboundChannelActionPayload.Action.SEND_CHAT,
                        channelId, this.minecraft.player.getUUID(),
                        true, text, "", List.<String>of(), List.<String>of(), List.<String>of(), "", true,
                        replyContent, replySender, itemNbt)));
    }

    private void sendChannelPacket(ServerboundChannelActionPayload.Action action, String channelId, UUID ownerUuid) {
        if (this.minecraft == null || this.minecraft.getConnection() == null) return;
        var conn = this.minecraft.getConnection().getConnection();
        conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new ServerboundChannelActionPayload(action, channelId, ownerUuid,
                        true, "", "", List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "")));
    }

    private static List<Component> splitCommandLines(Component component) {
        List<Component> lines = new ArrayList<>();
        MutableComponent[] cur = { Component.literal("") };
        component.visit((style, text) -> {
            int start = 0;
            while (true) {
                int idx = text.indexOf('\n', start);
                if (idx < 0) {
                    if (start < text.length())
                        cur[0] = cur[0].append(Component.literal(text.substring(start)).withStyle(style));
                    break;
                }
                if (start < idx)
                    cur[0] = cur[0].append(Component.literal(text.substring(start, idx)).withStyle(style));
                lines.add(cur[0]);
                cur[0] = Component.literal("");
                start = idx + 1;
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        if (!cur[0].getString().isEmpty())
            lines.add(cur[0]);
        return lines;
    }

    private static class SidebarEntry {
        final String conversationId;
        final Component displayName;
        final ChatMessageData.ConversationType type;
        final boolean isHeader;
        final UUID targetUuid;

        SidebarEntry(String conversationId, Component displayName,
                     ChatMessageData.ConversationType type, boolean isHeader, UUID targetUuid) {
            this.conversationId = conversationId;
            this.displayName = displayName;
            this.type = type;
            this.isHeader = isHeader;
            this.targetUuid = targetUuid;
        }
    }
}