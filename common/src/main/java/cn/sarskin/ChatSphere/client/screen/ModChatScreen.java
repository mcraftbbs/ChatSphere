package cn.sarskin.ChatSphere.client.screen;

import cn.sarskin.ChatSphere.ModInfo;
import cn.sarskin.ChatSphere.client.ChatHistoryManager;
import cn.sarskin.ChatSphere.client.ChatMessageData;
import cn.sarskin.ChatSphere.client.ChatDataStore;
import cn.sarskin.ChatSphere.config.ModClientConfig;
import cn.sarskin.ChatSphere.config.ModServerConfig;
import cn.sarskin.ChatSphere.client.widget.EmojiPanel;
import cn.sarskin.ChatSphere.client.widget.EmojiAutoComplete;
import cn.sarskin.ChatSphere.client.emoji.CustomEmojiRegistry;
import cn.sarskin.ChatSphere.client.emoji.EmojiEntry;
import cn.sarskin.ChatSphere.client.emoji.EmojiRegistry;
import cn.sarskin.ChatSphere.client.widget.MentionPopup;
import cn.sarskin.ChatSphere.client.widget.QuickPhrasesPanel;
import cn.sarskin.ChatSphere.client.widget.ReplyBarWidget;
import cn.sarskin.ChatSphere.client.widget.CopyToast;
import cn.sarskin.ChatSphere.client.widget.ItemPickerPanel;
import cn.sarskin.ChatSphere.client.PlayerSkinCache;
import cn.sarskin.ChatSphere.client.ModVoiceMessagesIntegration;
import cn.sarskin.ChatSphere.client.ui.BackgroundBlur;
import cn.sarskin.ChatSphere.client.ui.Theme;
import cn.sarskin.ChatSphere.client.ui.ThemeAnim;
import cn.sarskin.ChatSphere.client.ui.Ui;
import cn.sarskin.ChatSphere.style.CustomTheme;
import cn.sarskin.ChatSphere.style.ThemeSpec.AnimSpec;
import cn.sarskin.ChatSphere.network.ServerboundChannelActionPayload;
import org.lwjgl.glfw.GLFW;
import cn.sarskin.ChatSphere.network.ServerboundCommandMessagePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
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
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.regex.Pattern;

public class ModChatScreen extends Screen {
    private static final String COMMAND_CONVERSATION_ID = "__commands__";
    private static final int MAX_MESSAGE_LENGTH = 256;
    private int sidebarWidth() {
        return Math.min(Theme.sidebarWidth(), compactMode() ? 160 : 240);
    }

    /** Stream-only rail width; 0 in other styles. */
    private int railW() {
        return Theme.stream() ? (compactMode() ? 44 : RAIL_W) : 0;
    }

    private int railIcon() {
        return compactMode() ? 24 : RAIL_ICON;
    }

    private int railGap() {
        return compactMode() ? 8 : RAIL_GAP;
    }

    private int railSlot() {
        return railIcon() + railGap();
    }

    private int rowAvatarSize() {
        return compactMode() ? 20 : ROW_AVATAR_SIZE;
    }

    private int rowAvatarCol() {
        return compactMode() ? 36 : ROW_AVATAR_COL;
    }

    private boolean compactMode() {
        return height < 400;
    }

    private int sidebarLeft() {
        return railW();
    }

    private int chatLeft() {
        return railW() + sidebarWidth();
    }
    private static final int HEADER_BAR_HEIGHT = 14;
    private static final int MUTE_BAR_H = 14;
    private static final int AVATAR_SIZE = 10;
    private static final int SIDEBAR_AVATAR_SIZE = 12;
    private static final int BUBBLE_HPAD = 8;
    private static final int BUBBLE_VPAD = 4;
    private static final int SIDEBAR_ITEM_HEIGHT = 18;
    private static final int SIDEBAR_HEADER_HEIGHT = SIDEBAR_ITEM_HEIGHT + 2;
    private static final int CONFIG_ICON_SIZE = 10;
    private static final int TOOLBAR_HEIGHT = 14;
    private static final int NOTIF_BAR_H = 18;
    private static final int MESSAGE_BOTTOM_PAD = 10;
    private static final ResourceLocation SETTINGS_ICON = ResourceLocation.fromNamespaceAndPath(
            ModInfo.MODID, "textures/gui/settings_gear.png");
    private static final ResourceLocation EMOJI_ICON = ResourceLocation.fromNamespaceAndPath(
            ModInfo.MODID, "textures/gui/emoji.png");
    private static final ResourceLocation QUICK_PHRASES_ICON = ResourceLocation.fromNamespaceAndPath(
            ModInfo.MODID, "textures/gui/quick_phrases.png");
    private static final ResourceLocation SEARCH_ICON = ResourceLocation.fromNamespaceAndPath(
            ModInfo.MODID, "textures/gui/search.png");
    private static final ResourceLocation JOIN_CHANNEL_ICON = ResourceLocation.fromNamespaceAndPath(
            ModInfo.MODID, "textures/gui/join_channel.png");
    private static final Pattern ITEM_REF_PATTERN = Pattern.compile("\\[\\d+\\]");
    private static final ResourceLocation CREATE_CHANNEL_ICON = ResourceLocation.fromNamespaceAndPath(
            ModInfo.MODID, "textures/gui/create_channel.png");
    private static final ResourceLocation BLOCK_ICON = ResourceLocation.fromNamespaceAndPath(
            ModInfo.MODID, "textures/gui/block.png");
    private static final ResourceLocation ITEM_ICON = ResourceLocation.fromNamespaceAndPath(
            ModInfo.MODID, "textures/gui/item_chest.png");
    private static final ResourceLocation VOICE_ICON = ResourceLocation.fromNamespaceAndPath(
            ModInfo.MODID, "textures/gui/voice.png");

    private EditBox input;
    private String initial;
    private int historyPos = -1;
    private final List<String> sentHistory = new ArrayList<>();
    private CommandSuggestions commandSuggestions;
    private final List<String> cmdHistoryEntries = new ArrayList<>();
    private int cmdHistoryPos = -1;
    private String currentConversation = ChatHistoryManager.DEFAULT_CHANNEL_ID;
    private int scrollOffset;
    private boolean restoredConversation;

    /** Slow mode (client-side countdown): channelId|playerUuid -> last send time (ms). */
    private final Map<String, Long> slowModeLastSent = new HashMap<>();

    private static int linesContentH(List<Component> displayLines, int lineH) {
        int h = 0;
        for (Component ln : displayLines) {
            int emojiH = CustomEmojiRegistry.lineHeightFor(ln.getString());
            h += emojiH > 0 ? Math.max(lineH, emojiH + 2) : lineH;
        }
        return h;
    }

    /** Spawn timestamp for entrance animations (0 = pre-existing). */
    private final Map<ChatMessageData, Long> msgSpawnMs = new HashMap<>();

    private final List<SidebarEntry> sidebarEntries = new ArrayList<>();
    private final Map<String, PlayerInfo> onlinePlayers = new LinkedHashMap<>();
    private int refreshTimerTicks;

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
    private static final int CTX_AVATAR = 2;
    private int contextType = CTX_NONE;
    private int contextMenuX;
    private int contextMenuY;
    private String contextMenuName;
    private java.util.UUID contextMenuUuid;
    private int replyHighlightTarget = -1;
    private int replyHighlightTicks;
    private final List<CommandHit> cmdHitBoxes = new ArrayList<>();
    private final List<VoiceHit> voiceHitBoxes = new ArrayList<>();
    private final List<BubbleHit> bubbleHitBoxes = new ArrayList<>();
    private final List<ReplyQuoteHit> replyQuoteHitBoxes = new ArrayList<>();
    private final List<BubbleItemHit> itemHitBoxes = new ArrayList<>();
    private final List<RichTextHit> richTextHitBoxes = new ArrayList<>();
    private static final int ROW_AVATAR_COL = 44;
    private static final int ROW_AVATAR_SIZE = 24;
    private static final int RAIL_W = 60;
    private static final int RAIL_ICON = 32;
    private static final int RAIL_GAP = 12;
    private static final int RAIL_SLOT = RAIL_ICON + RAIL_GAP;
    private static final int RAIL_MAX_ITEMS = 64;
    private final boolean[] railHoverOn = new boolean[RAIL_MAX_ITEMS];
    private final long[] railHoverStart = new long[RAIL_MAX_ITEMS];
    private static final int RK_HOME = 0;
    private static final int RK_GROUP = 1;
    private static final int RK_CONSOLE = 2;
    private static final int RK_EXPLORE = 3;
    private static final int RK_JOIN = 4;
    private static final int RK_CREATE = 5;
    private final List<RailItem> railItems = new ArrayList<>();
    private String railSelectedGroup;
    private boolean sidebarPrivateMode;
    private record RailItem(int kind, String channelId) {}
    private record RowPaint(int height, boolean emojiOnly, int emojiX, int emojiY, int emojiW, int emojiH, boolean hovered) {}
    private record AvatarHit(int x, int y, int w, int h, String senderName, java.util.UUID senderUuid) {}
    private final List<AvatarHit> avatarHitBoxes = new ArrayList<>();
    private static final int MAX_VOICE_PLAYER_CACHE = 256;
    private final Map<ChatMessageData, WrappedLines> wrappedLinesCache = new HashMap<>();
    private final boolean openedWhileSleeping;
    private final Map<java.util.UUID, Object> voicePlayerCache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<java.util.UUID, Object> eldest) {
            return size() > MAX_VOICE_PLAYER_CACHE;
        }
    };


    private record CommandHit(int x, int y, int w, int h, Component component) {}

    private static final class WrappedLines {
        final int width;
        final boolean command;
        final List<Component> lines;

        WrappedLines(int width, boolean command, List<Component> lines) {
            this.width = width;
            this.command = command;
            this.lines = lines;
        }
    }
    private record VoiceHit(int x, int y, int w, int h, java.util.UUID voiceUuid, Object playbackPlayer) {}
    private record BubbleHit(int x, int y, int w, int h, int globalIndex) {}
    private record ReplyQuoteHit(int x, int y, int w, int h, String replySender, String replyContent) {}
    private record BubbleItemHit(int x, int y, int w, int h, ItemStack itemStack) {}
    private record RichTextHit(int x, int y, int w, int h, Component component) {}

    public ModChatScreen(String initial) {
        super(Component.translatable("screen.chatsphere.mod_chat.title"));
        this.initial = initial;
        this.scrollOffset = 0;
        Minecraft mc = Minecraft.getInstance();
        this.openedWhileSleeping = mc.player != null && mc.player.isSleeping();
        ChatHistoryManager.getInstance().load();
    }

    @Override
    protected void init() {
        voicePlayerCache.clear();
        CustomEmojiRegistry.ensureScanned();
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        history.load();
        history.ensureDefaultChannel();
        history.refreshPrivateConversationDisplayNames();
        // Restore last conversation only on first init (async save may not have hit disk yet)
        if (!restoredConversation) {
            restoredConversation = true;
            String cached = history.getLastConversation();
            if (cached != null && !cached.isEmpty()
                    && (history.getChannels().contains(cached)
                        || history.hasConversation(cached)
                        || COMMAND_CONVERSATION_ID.equals(cached))) {
                currentConversation = cached;
                if (COMMAND_CONVERSATION_ID.equals(cached)) {
                    cmdHistoryEntries.clear();
                    cmdHistoryEntries.addAll(history.getCommandHistory(
                            this.minecraft != null && this.minecraft.player != null
                                    ? this.minecraft.player.getUUID() : java.util.UUID.randomUUID()));
                    cmdHistoryPos = cmdHistoryEntries.size();
                }
            }
        }
        seedMsgSpawns();
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

        int inputX = chatLeft() + 2;
        int inputWidth = this.width - chatLeft() - 6;
        int inputY = this.height - 12;
        this.input = new EditBox(this.minecraft.font, inputX, inputY,
                inputWidth, 12,
                Component.translatable("chat.editBox"));
        this.input.setMaxLength(MAX_MESSAGE_LENGTH);
        this.input.setBordered(false);
        this.input.setValue(this.initial);
        if (!COMMAND_CONVERSATION_ID.equals(currentConversation))
            this.input.setTextColor(Theme.inputText());
        this.input.setResponder(this::onCommandInputChanged);
        this.input.setFormatter((text, cursor) -> EmojiRegistry.toFormattedCharSequence(text));
        this.addWidget(this.input);
        this.setInitialFocus(this.input);

        this.commandSuggestions = new CommandSuggestions(this.minecraft, this, this.input, this.font,
                true, false, 1, 10, true, -805306368);
        this.commandSuggestions.setAllowHiding(false);
        if (COMMAND_CONVERSATION_ID.equals(currentConversation))
            this.commandSuggestions.updateCommandInfo();

        this.searchInput = new EditBox(this.font, chatLeft() + 4, HEADER_BAR_HEIGHT + 8,
                Math.max(80, width - chatLeft() - 30), 14,
                Component.translatable("screen.chatsphere.search.hint"));
        this.searchInput.setTextColor(Theme.inputText());
        this.searchInput.setMaxLength(64);
        this.searchInput.setBordered(true);
        this.searchInput.setVisible(false);
        this.searchInput.setResponder(this::onSearchChanged);
        this.addWidget(this.searchInput);

        refreshOnlinePlayers();
        ChatHistoryManager.getInstance().refreshPrivateConversationDisplayNames();
    }

    /** Messages already loaded are pre-existing and never animate. */
    private void seedMsgSpawns() {
        msgSpawnMs.clear();
        for (ChatMessageData m : ChatHistoryManager.getInstance().snapshotAllMessages()) {
            msgSpawnMs.put(m, 0L);
        }
    }

    private void refreshOnlinePlayers() {
        Minecraft mc = Minecraft.getInstance();
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        onlinePlayers.clear();
        if (mc.getConnection() != null && mc.player != null) {
            history.syncOnlinePlayers(mc.getConnection().getOnlinePlayers());
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                onlinePlayers.put(info.getProfile().getId().toString(), info);
            }
        } else {
            history.syncOnlinePlayers(List.of());
        }
    }

    private void onCommandInputChanged(String value) {
        if (this.commandSuggestions != null && COMMAND_CONVERSATION_ID.equals(currentConversation)) {
            this.commandSuggestions.setAllowSuggestions(true);
            this.commandSuggestions.updateCommandInfo();
        }
        // No mention popup in the console (@a/@e/@p are command selectors)
        if (!COMMAND_CONVERSATION_ID.equals(currentConversation)) {
            mentionPopup.update(value, onlinePlayers);
        }
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
        if (openedWhileSleeping && minecraft.player != null && !minecraft.player.isSleeping()) {
            onClose();
            return;
        }
        if (++refreshTimerTicks >= 20) {
            refreshTimerTicks = 0;
            refreshOnlinePlayers();
            ChatHistoryManager.getInstance().refreshPrivateConversationDisplayNames();
        }
        if (scrollOffset <= 1
                && ChatHistoryManager.getInstance().getUnreadCount(currentConversation) > 0) {
            ChatHistoryManager.getInstance().markConversationRead(currentConversation);
        }
        if (replyHighlightTicks > 0 && --replyHighlightTicks == 0) {
            replyHighlightTarget = -1;
        }
        copyToast.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (showSearch && searchInput != null && searchInput.isVisible()) {
                int barY = HEADER_BAR_HEIGHT + 6;
                int barH = 20;
                int areaLeft = chatLeft() + 2;
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

            int toolbarY = this.height - 14 - TOOLBAR_HEIGHT;
            int btnH = TOOLBAR_HEIGHT - 2;
            int btnY = toolbarY + 1;
            if (mouseY >= toolbarY && mouseY <= toolbarY + TOOLBAR_HEIGHT) {
                int btnX = chatLeft() + 4;
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
                        if (isMainChatLocked(currentConversation)) {
                            if (this.minecraft != null && this.minecraft.player != null) {
                                this.minecraft.player.displayClientMessage(
                                    Component.translatable("screen.chatsphere.mod_chat.main_chat_disabled"), false);
                            }
                            return true;
                        }
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

            int emojiPanelX = chatLeft() + 4;
            int emojiPanelY = this.height - 14 - TOOLBAR_HEIGHT - emojiPanel.panelH() - 4;
            if (emojiPanel.mouseClicked(mouseX, mouseY, button, emojiPanelX, emojiPanelY, input)) return true;

            if (itemPickerPanel.visible) {
                int ipY = this.height - 14 - TOOLBAR_HEIGHT - ItemPickerPanel.ITEM_PICKER_PANEL_H - 4;
                if (itemPickerPanel.mouseClicked(mouseX, mouseY, button, chatLeft() + 4, ipY)) {
                    if (itemPickerPanel.selectedItemNbt != null && !itemPickerPanel.selectedItemNbt.isEmpty()) {
                        pendingItemNbt = itemPickerPanel.selectedItemNbt;
                        input.setValue("[" + (itemPickerPanel.selectedSlotIndex + 1) + "]");
                    }
                    return true;
                }
            }

            int qpPanelY = this.height - 14 - TOOLBAR_HEIGHT - 4 - Math.min(ModClientConfig.CONFIG.quickPhrases.get().size(), 6) * 18;
            if (quickPhrasesPanel.mouseClicked(mouseX, mouseY, button, chatLeft() + 4, qpPanelY, input)) return true;

            if (replyBar.mouseClicked(mouseX, mouseY, chatLeft(), this.width, 0, false)) {
                replyHighlightTarget = -1;
                return true;
            }
            if (replyBar.isOnBody(mouseX, mouseY, chatLeft(), this.width, 0, false)) {
                replyHighlightTarget = replyBar.targetIndex;
                replyHighlightTicks = 100;
                scrollToMessageIndex(replyBar.targetIndex);
                return true;
            }

            synchronized (replyQuoteHitBoxes) {
                for (ReplyQuoteHit hit : replyQuoteHitBoxes) {
                    if (mouseX >= hit.x && mouseX <= hit.x + hit.w && mouseY >= hit.y && mouseY <= hit.y + hit.h) {
                        ChatHistoryManager history = ChatHistoryManager.getInstance();
                        List<ChatMessageData> msgs = history.getMessagesByConversation(currentConversation);
                        for (int i = msgs.size() - 1; i >= 0; i--) {
                            ChatMessageData m = msgs.get(i);
                            if (m.senderName().getString().equals(hit.replySender)) {
                                String mContent = m.content().getString();
                                if (mContent.equals(hit.replyContent) || quoteTextFor(m).equals(hit.replyContent)) {
                                    replyHighlightTarget = history.getMessageIndex(m);
                                    replyHighlightTicks = 100;
                                    scrollToMessageIndex(replyHighlightTarget);
                                    return true;
                                }
                            }
                        }
                    }
                }
            }

            if (!COMMAND_CONVERSATION_ID.equals(currentConversation)
                    && mentionPopup.mouseClicked(mouseX, mouseY, button, input)) return true;

            if (!COMMAND_CONVERSATION_ID.equals(currentConversation)
                    && emojiAutoComplete.mouseClicked(mouseX, mouseY, button, input)) return true;
        }

        if (button == 0 && COMMAND_CONVERSATION_ID.equals(currentConversation)) {
            synchronized (cmdHitBoxes) {
                for (CommandHit hit : cmdHitBoxes) {
                    if (mouseX >= hit.x && mouseX <= hit.x + hit.w && mouseY >= hit.y && mouseY <= hit.y + hit.h) {
                        if (handleComponentClick(hit.component, (int) mouseX - hit.x)) return true;
                    }
                }
            }
        }

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

        if (button == 1 && Theme.stream()) {
            synchronized (avatarHitBoxes) {
                for (AvatarHit hit : avatarHitBoxes) {
                    if (mouseX >= hit.x && mouseX <= hit.x + hit.w && mouseY >= hit.y && mouseY <= hit.y + hit.h) {
                        contextType = CTX_AVATAR;
                        contextMsgIndex = 0;
                        contextMenuName = hit.senderName();
                        contextMenuUuid = hit.senderUuid();
                        contextMenuX = (int) mouseX;
                        contextMenuY = (int) mouseY;
                        return true;
                    }
                }
            }
        }

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

        if (button == 0 && contextType == CTX_BUBBLE) {
            handleContextMenuClick((int) mouseX, (int) mouseY);
            return true;
        }
        if (button == 0 && contextType == CTX_AVATAR) {
            handleContextMenuClick((int) mouseX, (int) mouseY);
            return true;
        }
        if (contextType != CTX_NONE) { contextType = CTX_NONE; contextMsgIndex = -1; return true; }

        if (button == 0) {
            synchronized (richTextHitBoxes) {
                for (RichTextHit hit : richTextHitBoxes) {
                    if (mouseX >= hit.x && mouseX <= hit.x + hit.w && mouseY >= hit.y && mouseY <= hit.y + hit.h) {
                        if (handleComponentClick(hit.component, (int) mouseX - hit.x)) return true;
                    }
                }
            }
        }

        if (COMMAND_CONVERSATION_ID.equals(currentConversation)
                && this.commandSuggestions != null
                && this.commandSuggestions.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0 && Theme.stream() && mouseX < railW()) {
            int rw = railW();
            int icon = railIcon();
            int iconX = (rw - icon) / 2;
            for (int i = 0; i < railItems.size(); i++) {
                RailItem item = railItems.get(i);
                int y = 12 + i * railSlot();
                if (mouseX >= iconX && mouseX <= iconX + icon && mouseY >= y && mouseY <= y + icon) {
                    switch (item.kind) {
                        case RK_HOME -> {
                            sidebarPrivateMode = !sidebarPrivateMode;
                            if (sidebarPrivateMode) {
                                ChatHistoryManager hist = ChatHistoryManager.getInstance();
                                // Select a private conv so the console loses its highlight
                                if (COMMAND_CONVERSATION_ID.equals(currentConversation)) {
                                    String firstPrivate = firstPrivateConversation(hist);
                                    if (firstPrivate != null) {
                                        currentConversation = firstPrivate;
                                        hist.markConversationRead(firstPrivate);
                                    }
                                }
                                voicePlayerCache.clear();
                                scrollOffset = 0;
                            }
                        }
                        case RK_GROUP -> {
                            if (item.channelId == null) break;
                            ChatHistoryManager hist = ChatHistoryManager.getInstance();
                            String target = item.channelId;
                            ChatDataStore.ChannelConfig cfg = hist.getChannelConfig(target);
                            if (!cfg.mainChatEnabled && !cfg.defaultSubChannel.isEmpty()) {
                                String def = target + "/" + cfg.defaultSubChannel;
                                if (hist.getChannels().contains(def) || hist.hasConversation(def)) {
                                    target = def;
                                }
                            }
                            railSelectedGroup = item.channelId;
                            sidebarPrivateMode = false;
                            currentConversation = target;
                            hist.markConversationRead(target);
                            voicePlayerCache.clear();
                            scrollOffset = 0;
                        }
                        case RK_CONSOLE -> {
                            ChatHistoryManager hist = ChatHistoryManager.getInstance();
                            currentConversation = COMMAND_CONVERSATION_ID;
                            cmdHistoryEntries.clear();
                            cmdHistoryEntries.addAll(hist.getCommandHistory(this.minecraft != null && this.minecraft.player != null ? this.minecraft.player.getUUID() : java.util.UUID.randomUUID()));
                            cmdHistoryPos = cmdHistoryEntries.size();
                            hist.markConversationRead(COMMAND_CONVERSATION_ID);
                            voicePlayerCache.clear();
                            scrollOffset = 0;
                        }
                        case RK_EXPLORE -> {
                            if (this.minecraft != null) { this.onClose(); this.minecraft.setScreen(new ExploreServersScreen(this)); }
                        }
                        case RK_JOIN -> {
                            if (this.minecraft != null) { this.onClose(); this.minecraft.setScreen(new JoinChannelScreen(this)); }
                        }
                        case RK_CREATE -> {
                            if (this.minecraft != null) { this.onClose(); this.minecraft.setScreen(new CreateChannelScreen(this)); }
                        }
                        default -> { }
                    }
                    return true;
                }
            }
        }

        if (button == 0 && mouseX >= sidebarLeft() && mouseX < chatLeft()) {
            int yOffset = 10;
            int headerIdx = 0;
            for (int i = 0; i < sidebarEntries.size(); i++) {
                SidebarEntry entry = sidebarEntries.get(i);
                if (entry.isHeader) {
                    if (headerIdx == 0 && !(Theme.stream() && sidebarPrivateMode)) {
                        int plusX = chatLeft() - 6 - 10;
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
                    yOffset += SIDEBAR_HEADER_HEIGHT;
                    continue;
                }
                int rowTop = yOffset;
                int rowBottom = yOffset + SIDEBAR_ITEM_HEIGHT;
                if (entry.indent > 0) {
                    rowTop = yOffset + 2;
                    rowBottom = yOffset + 2 + (SIDEBAR_ITEM_HEIGHT - 4);
                }
                if (mouseY >= rowTop && mouseY < rowBottom) {
                    if (entry.type == ChatMessageData.ConversationType.CHANNEL && entry.conversationId != null) {
                        int gearX = chatLeft() - 6 - CONFIG_ICON_SIZE;
                        if (mouseX >= gearX && mouseX <= gearX + CONFIG_ICON_SIZE) {
                            if (this.minecraft != null) {
                                // Everyone opens the detail card; admins edit inside it
                                this.onClose(); this.minecraft.setScreen(new ChannelInfoScreen(this, entry.conversationId));
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
                        String targetConversation = entry.conversationId;
                        if (entry.type == ChatMessageData.ConversationType.CHANNEL) {
                            ChatDataStore.ChannelConfig cfg = history.getChannelConfig(entry.conversationId);
                            if (!cfg.mainChatEnabled && !cfg.defaultSubChannel.isEmpty()) {
                                String def = ChatHistoryManager.subParentOf(entry.conversationId) + "/" + cfg.defaultSubChannel;
                                if (!ChatHistoryManager.isSubChannel(entry.conversationId)) {
                                    def = entry.conversationId + "/" + cfg.defaultSubChannel;
                                }
                                if (history.getChannels().contains(def) || history.hasConversation(def)) {
                                    targetConversation = def;
                                }
                            }
                        }
                        currentConversation = targetConversation;
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
        if (button == 0 && mouseY >= notifBarY && mouseY < notifBarY + NOTIF_BAR_H && mouseX >= chatLeft()) {
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

    private boolean closed;

    /** Persist input, last conversation and history; safe to run once. */
    private void persistState() {
        if (minecraft.player != null && minecraft.player.connection != null && minecraft.player.isSleeping()) {
            minecraft.player.connection.send(new ServerboundPlayerCommandPacket(
                    minecraft.player, ServerboundPlayerCommandPacket.Action.STOP_SLEEPING));
        }
        wrappedLinesCache.clear();
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        if (ModClientConfig.CONFIG.preserveInput.get()) {
            history.setSavedInput(input.getValue());
        }
        history.setLastConversation(currentConversation);
        history.saveNow();
    }

    @Override
    public void onClose() {
        if (closed) return;
        closed = true;
        persistState();
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        // setScreen() bypasses onClose(); persist here too, without setScreen(null)
        if (!closed) {
            closed = true;
            persistState();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && minecraft.player != null && minecraft.player.isSleeping()) {
            if (minecraft.player.connection != null) {
                minecraft.player.connection.send(new ServerboundPlayerCommandPacket(
                        minecraft.player, ServerboundPlayerCommandPacket.Action.STOP_SLEEPING));
            }
            if (!openedWhileSleeping) {
                onClose();
            }
            return true;
        }
        if (COMMAND_CONVERSATION_ID.equals(currentConversation)
                && this.commandSuggestions != null
                && this.commandSuggestions.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

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

        if (!COMMAND_CONVERSATION_ID.equals(currentConversation)
                && emojiAutoComplete.keyPressed(keyCode, input)) return true;

        if (emojiPanel.keyPressed(keyCode, scanCode, modifiers)) return true;

        if (!COMMAND_CONVERSATION_ID.equals(currentConversation)
                && mentionPopup.keyPressed(keyCode, input)) return true;

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
            if (!COMMAND_CONVERSATION_ID.equals(currentConversation)) {
                emojiAutoComplete.update(input.getValue());
            }
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
        if (input != null && input.isFocused() && !COMMAND_CONVERSATION_ID.equals(currentConversation)) {
            emojiAutoComplete.update(input.getValue());
        }
        return handled;
    }

    /** True when the channel's main chat is disabled and no usable sub-channel exists. */
    private boolean isMainChatLocked(String conversationId) {
        if (conversationId == null) return false;
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        if (history.getConversationType(conversationId) != ChatMessageData.ConversationType.CHANNEL) return false;
        if (ChatHistoryManager.isSubChannel(conversationId)) return false;
        ChatDataStore.ChannelConfig cfg = history.getChannelConfig(conversationId);
        return !cfg.mainChatEnabled && cfg.defaultSubChannel.isEmpty();
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
                                    this.minecraft.player.getUUID(), true)));
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
                if (isMutedEntry(cfg.mutedPlayers, this.minecraft.player.getUUID().toString())) {
                    this.minecraft.player.displayClientMessage(
                        Component.translatable("chatsphere.mute.feedback"), false);
                    this.input.setValue("");
                    return;
                }
                if (isMainChatLocked(currentConversation)) {
                    this.minecraft.player.displayClientMessage(
                        Component.translatable("screen.chatsphere.mod_chat.main_chat_disabled"), false);
                    this.input.setValue("");
                    return;
                }
                // Local countdown mirror; the server re-checks authoritatively
                if (cfg.slowModeSeconds > 0) {
                    String smKey = currentConversation + "|" + this.minecraft.player.getUUID();
                    Long smLast = slowModeLastSent.get(smKey);
                    long smRemain = smLast == null ? 0
                            : cfg.slowModeSeconds * 1000L - (System.currentTimeMillis() - smLast);
                    if (smRemain > 0) {
                        this.minecraft.player.displayClientMessage(
                            Component.translatable("chatsphere.slowmode.feedback", (smRemain + 999) / 1000), false);
                        this.input.setValue("");
                        return;
                    }
                    slowModeLastSent.put(smKey, System.currentTimeMillis());
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
        int emojiPanelX = chatLeft() + 4;
        int emojiPanelY = this.height - 14 - TOOLBAR_HEIGHT - emojiPanel.panelH() - 4;
        if (emojiPanel.mouseScrolled(mouseX, mouseY, emojiPanelX, emojiPanelY, scrollY)) return true;
        if (quickPhrasesPanel.mouseScrolled(scrollY)) return true;

        if (COMMAND_CONVERSATION_ID.equals(currentConversation)
                && this.commandSuggestions != null
                && this.commandSuggestions.mouseScrolled(scrollY)) {
            return true;
        }
        if (mouseY >= chatAreaTop() && mouseY < height - 14 - TOOLBAR_HEIGHT - MESSAGE_BOTTOM_PAD && mouseX >= chatLeft()) {
            scrollOffset += scrollY > 0 ? 1 : (scrollY < 0 ? -1 : 0);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (Theme.originalStyle() || ModClientConfig.CONFIG.backgroundBlur.get()) {
            BackgroundBlur.blurScreen(guiGraphics, width, height);
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
            if (!Theme.originalStyle()) {
                guiGraphics.fill(0, 0, this.width, this.height, Theme.screenBg());
            }
        } else {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
            guiGraphics.fill(0, 0, this.width, this.height, Theme.screenBgSolid());
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Theme.beginFrame();
        CustomEmojiRegistry.setCurrentChannel(currentConversation);
        // Sync dimensions when rendered as parent of an overlay screen
        Screen curScreen = minecraft.screen;
        if (curScreen != null && curScreen != this) {
            this.width = curScreen.width;
            this.height = curScreen.height;
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        buildSidebarEntries();
        drawRail(guiGraphics, mouseX, mouseY, System.currentTimeMillis());
        drawSidebar(guiGraphics, mouseX, mouseY);

        int screenWidth = this.width;
        int screenHeight = this.height;

        drawHeaderBar(guiGraphics);
        renderMessages(guiGraphics, mouseX, mouseY, screenWidth, screenHeight);
        renderNotificationBar(guiGraphics, screenHeight);

        guiGraphics.fill(chatLeft(), screenHeight - 14 - TOOLBAR_HEIGHT, screenWidth, screenHeight, Theme.toolbarBg());
        this.input.render(guiGraphics, mouseX, mouseY, partialTick);
        drawToolbar(guiGraphics, mouseX, mouseY, screenHeight, screenWidth);

        if (COMMAND_CONVERSATION_ID.equals(currentConversation) && this.commandSuggestions != null) {
            this.commandSuggestions.render(guiGraphics, mouseX, mouseY);
        }

        renderTooltips(guiGraphics, mouseX, mouseY, screenWidth, screenHeight);

        if (COMMAND_CONVERSATION_ID.equals(currentConversation)) {
            synchronized (cmdHitBoxes) {
                for (CommandHit hit : cmdHitBoxes) {
                    if (mouseX >= hit.x && mouseX <= hit.x + hit.w && mouseY >= hit.y && mouseY <= hit.y + hit.h) {
                        renderHoverTooltip(guiGraphics, mouseX, mouseY, hit.component, (int) mouseX - hit.x);
                        break;
                    }
                }
            }
        }

        if (!emojiPanel.visible && !itemPickerPanel.visible) {
            synchronized (richTextHitBoxes) {
                for (RichTextHit hit : richTextHitBoxes) {
                    if (mouseX >= hit.x && mouseX <= hit.x + hit.w && mouseY >= hit.y && mouseY <= hit.y + hit.h) {
                        renderHoverTooltip(guiGraphics, mouseX, mouseY, hit.component, (int) mouseX - hit.x);
                        break;
                    }
                }
            }
        }

        // Panels render above messages and tooltips so nothing covers them
        drawWidgetPanels(guiGraphics, mouseX, mouseY);
    }

    private void renderHoverTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY,
                                    Component component, int relX) {
        Style style = minecraft.font.getSplitter().componentStyleAtWidth(component, relX);
        if (style == null || style.getHoverEvent() == null) return;
        var hoverEvent = style.getHoverEvent();
        var action = hoverEvent.getAction();
        Object val = hoverEvent.getValue(action);
        if (!(val instanceof Component tooltip)) return;
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
        g.fill(chatLeft(), ty, screenWidth + 2, ty + TOOLBAR_HEIGHT, Theme.toolbarBg());

        int btnX = chatLeft() + 4;
        int btnY = ty + 1;
        int iconSize = TOOLBAR_HEIGHT - 2;

        int hc = Theme.iconBtnHover();
        int nc = Theme.isDark() ? Theme.iconBtnBg() : 0x00000000;

        boolean emojiHover = mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize;
        Ui.fillRoundedRect(g, btnX, btnY, iconSize, iconSize, 3, emojiHover || emojiPanel.visible ? hc : nc);
        g.blit(EMOJI_ICON, btnX + 1, btnY + 1, 0, 0, iconSize - 2, iconSize - 2, iconSize - 2, iconSize - 2);

        btnX += iconSize + 2;
        boolean qpHover = mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize;
        Ui.fillRoundedRect(g, btnX, btnY, iconSize, iconSize, 3, qpHover || quickPhrasesPanel.visible ? hc : nc);
        g.blit(QUICK_PHRASES_ICON, btnX + 1, btnY + 1, 0, 0, iconSize - 2, iconSize - 2, iconSize - 2, iconSize - 2);

        btnX += iconSize + 2;
        boolean searchHover = mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize;
        Ui.fillRoundedRect(g, btnX, btnY, iconSize, iconSize, 3, searchHover || showSearch ? hc : nc);
        g.blit(SEARCH_ICON, btnX + 1, btnY + 1, 0, 0, iconSize - 2, iconSize - 2, iconSize - 2, iconSize - 2);

        btnX += iconSize + 2;
        boolean blockHover = mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize;
        Ui.fillRoundedRect(g, btnX, btnY, iconSize, iconSize, 3, blockHover ? hc : nc);
        g.blit(BLOCK_ICON, btnX + 1, btnY + 1, 0, 0, iconSize - 2, iconSize - 2, iconSize - 2, iconSize - 2);

        btnX += iconSize + 2;
        boolean itemHover = mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize;
        Ui.fillRoundedRect(g, btnX, btnY, iconSize, iconSize, 3, itemHover || itemPickerPanel.visible ? hc : nc);
        g.blit(ITEM_ICON, btnX + 1, btnY + 1, 0, 0, iconSize - 2, iconSize - 2, iconSize - 2, iconSize - 2);

        if (ModVoiceMessagesIntegration.canSendVoiceMessages()) {
            btnX += iconSize + 2;
            boolean vmHover = mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize;
            Ui.fillRoundedRect(g, btnX, btnY, iconSize, iconSize, 3, vmHover ? hc : nc);
            g.blit(VOICE_ICON, btnX + 1, btnY + 1, 0, 0, iconSize - 2, iconSize - 2, iconSize - 2, iconSize - 2);
        }
    }

    private void renderTooltips(GuiGraphics g, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (Theme.stream() && mouseX < railW()) {
            int rw = railW();
            int icon = railIcon();
            int iconX = (rw - icon) / 2;
            for (int i = 0; i < railItems.size(); i++) {
                RailItem item = railItems.get(i);
                int y = 12 + i * railSlot();
                if (mouseX >= iconX && mouseX <= iconX + icon && mouseY >= y && mouseY <= y + icon) {
                    Component tip;
                    switch (item.kind) {
                        case RK_HOME -> tip = minecraft != null && minecraft.player != null
                                ? Component.literal(minecraft.player.getGameProfile().getName())
                                : Component.empty();
                        case RK_GROUP -> tip = item.channelId != null
                                ? ChatHistoryManager.getInstance().getConversationDisplayName(item.channelId)
                                : Component.empty();
                        case RK_CONSOLE -> tip = Component.translatable("screen.chatsphere.mod_chat.console_name");
                        case RK_EXPLORE -> tip = Component.translatable("screen.chatsphere.rail.explore");
                        case RK_JOIN -> tip = Component.translatable("screen.chatsphere.rail.join");
                        default -> tip = Component.translatable("screen.chatsphere.rail.create");
                    }
                    g.renderTooltip(font, tip, rw + 8, mouseY);
                    return;
                }
            }
        }
        int ty = screenHeight - 14 - TOOLBAR_HEIGHT;
        int btnY = ty + 1;
        int iconSize = TOOLBAR_HEIGHT - 2;
        int btnX = chatLeft() + 4;

        if (mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize) {
            g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.emoji"), mouseX, mouseY);
            return;
        }
        btnX += iconSize + 2;
        if (mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize) {
            g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.quick_phrases"), mouseX, mouseY);
            return;
        }
        btnX += iconSize + 2;
        if (mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize) {
            g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.search"), mouseX, mouseY);
            return;
        }
        btnX += iconSize + 2;
        if (mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize) {
            g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.blocklist"), mouseX, mouseY);
            return;
        }
        btnX += iconSize + 2;
        if (mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize) {
            g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.pick_item"), mouseX, mouseY);
            return;
        }

        if (ModVoiceMessagesIntegration.canSendVoiceMessages()) {
            btnX += iconSize + 2;
            if (mouseX >= btnX && mouseX <= btnX + iconSize && mouseY >= btnY && mouseY <= btnY + iconSize) {
                g.renderTooltip(font, Component.translatable("screen.chatsphere.tip.voice_message"), mouseX, mouseY);
                return;
            }
        }

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

        if (mouseX >= sidebarLeft() && mouseX < chatLeft()) {
            int y = 10;
            int headerIdx = 0;
            for (int i = 0; i < sidebarEntries.size(); i++) {
                SidebarEntry entry = sidebarEntries.get(i);
                if (entry.isHeader) {
                    if (headerIdx == 0 && !Theme.stream()) {
                        int plusX = chatLeft() - 6 - 10;
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
                    y += SIDEBAR_HEADER_HEIGHT;
                } else {
                    if (entry.type == ChatMessageData.ConversationType.CHANNEL && entry.conversationId != null) {
                        int gearX = chatLeft() - 6 - CONFIG_ICON_SIZE;
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

        if (contextType == CTX_NONE && !emojiPanel.visible && !itemPickerPanel.visible) {
            synchronized (itemHitBoxes) {
                for (BubbleItemHit hit : itemHitBoxes) {
                    if (mouseX >= hit.x && mouseX <= hit.x + hit.w && mouseY >= hit.y && mouseY <= hit.y + hit.h) {
                        g.renderTooltip(font, hit.itemStack, mouseX, mouseY);
                        return;
                    }
                }
            }
        }

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
        // z=200: above item icons (z=150), below tooltips (z=400)
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        if (showSearch && searchInput != null) {
            int muteOffset = isCurrentChannelMuted() ? MUTE_BAR_H : 0;
            int barY = HEADER_BAR_HEIGHT + 6 + muteOffset;
            int barH = 20;
            int areaLeft = chatLeft() + 2;
            int areaRight = this.width;
            Ui.fillRoundedRect(g, areaLeft, barY, areaRight - areaLeft, barH, 4, Theme.searchBg());
            g.fill(areaLeft, barY + barH - 1, areaRight, barY + barH, Theme.accentLine());

            searchInput.setX(areaLeft + 4);
            searchInput.setY(barY + 3);
            searchInput.setWidth(areaRight - areaLeft - 60);
            searchInput.render(g, mouseX, mouseY, 0);

            int closeX = areaRight - 18;
            boolean closeHover = mouseX >= closeX && mouseX <= closeX + 14 && mouseY >= barY + 3 && mouseY <= barY + 17;
            Ui.fillRoundedRect(g, closeX, barY + 3, 14, 14, 3, closeHover ? 0x44FF4444 : Theme.searchCloseBg());
            g.drawString(font, "X", closeX + 5, barY + 5, 0xFFFF6666, false);

            if (!searchResults.isEmpty()) {
                String counter = (searchResultIndex + 1) + "/" + searchResults.size();
                g.drawString(font, counter, searchInput.getX() + searchInput.getWidth() + 4, barY + 5, Theme.accent(), false);
            } else if (!searchQuery.isEmpty()) {
                g.drawString(font, Component.translatable("screen.chatsphere.search.no_match"), searchInput.getX() + searchInput.getWidth() + 4, barY + 5, Theme.floatingTextDim(), false);
            }
        }

        // Below the floating panels so menus never cover them
        if (contextType == CTX_BUBBLE) {
            drawContextMenu(g, mouseX, mouseY);
        } else if (contextType == CTX_AVATAR) {
            drawAvatarContextMenu(g, mouseX, mouseY);
        }

        if (emojiPanel.visible) {
            emojiPanel.render(g, chatLeft() + 4, this.height - 14 - TOOLBAR_HEIGHT - emojiPanel.panelH() - 4, mouseX, mouseY);
        }

        if (quickPhrasesPanel.visible) {
            int qpY = this.height - 14 - TOOLBAR_HEIGHT - 4 - quickPhrasesPanel.panelH();
            quickPhrasesPanel.render(g, chatLeft() + 4, qpY, mouseX, mouseY);
        }

        if (itemPickerPanel.visible) {
            int ipY = this.height - 14 - TOOLBAR_HEIGHT - ItemPickerPanel.ITEM_PICKER_PANEL_H - 4;
            itemPickerPanel.render(g, mouseX, mouseY, chatLeft() + 4, ipY);
        }

        // Mention popup (command console has no @player recognition)
        if (!COMMAND_CONVERSATION_ID.equals(currentConversation) && mentionPopup.visible) {
            mentionPopup.render(g, input, mouseX, mouseY);
        }

        // Emoji autocomplete (command console is commands only)
        if (emojiAutoComplete.visible && !COMMAND_CONVERSATION_ID.equals(currentConversation)) {
            emojiAutoComplete.render(g, input, mouseX, mouseY);
        }

        replyBar.render(g, mouseX, mouseY, chatLeft(), this.width, 0, false);

        copyToast.render(g, sidebarLeft(), this.width);
        g.pose().popPose();
    }

    /** Right-click menu action directory: label + style + enabled state + action. */
    private record MenuRow(Component label, int textColor, int hoverColor, boolean enabled, Runnable action) {}

    private static final int MENU_ROW_H = 16;
    private static final int MENU_PAD = 4;

    private List<MenuRow> messageMenuRows(ChatMessageData msg, ChatHistoryManager history) {
        List<MenuRow> rows = new ArrayList<>();
        rows.add(new MenuRow(Component.translatable("screen.chatsphere.context.copy"),
                0xCCCCCC, Theme.menuHover(), msg != null,
                () -> {
                    if (msg == null) return;
                    minecraft.keyboardHandler.setClipboard(msg.content().getString());
                    copyToast.show();
                }));
        rows.add(new MenuRow(Component.translatable("screen.chatsphere.context.reply"),
                0xCCCCCC, Theme.menuHover(), msg != null,
                () -> {
                    if (msg == null) return;
                    replyBar.targetIndex = contextMsgIndex;
                    replyBar.replyText = quoteTextFor(msg);
                    replyBar.replySender = msg.senderName().getString();
                }));
        boolean blockOk = msg != null && msg.senderUuid() != null && !msg.isOwn();
        rows.add(new MenuRow(Component.translatable("screen.chatsphere.context.block"),
                0xFFAA6666, 0x44884444, blockOk,
                () -> {
                    if (msg != null && msg.senderUuid() != null && !msg.isOwn()) {
                        history.blockPlayer(msg.senderUuid().toString());
                    }
                }));
        return rows;
    }

    private List<MenuRow> avatarMenuRows(String name, UUID uuid) {
        String label = "@" + name;
        List<MenuRow> rows = new ArrayList<>();
        rows.add(new MenuRow(Component.literal(label),
                0xCCCCCC, Theme.menuHover(), true,
                () -> {
                    if (name.isEmpty()) return;
                    String current = input.getValue();
                    String prefix = current.isEmpty() ? "" : (current.endsWith(" ") ? "" : " ");
                    input.setValue(current + prefix + "@" + name + " ");
                    input.moveCursorToEnd(false);
                    setFocused(input);
                }));
        rows.add(new MenuRow(Component.translatable("screen.chatsphere.context.private_message"),
                0xCCCCCC, Theme.menuHover(), true,
                () -> {
                    if (uuid != null && minecraft != null && minecraft.player != null) {
                        UUID local = minecraft.player.getUUID();
                        String convId = local.compareTo(uuid) < 0 ? local + ":" + uuid : uuid + ":" + local;
                        ChatHistoryManager h = ChatHistoryManager.getInstance();
                        currentConversation = convId;
                        sidebarPrivateMode = true;
                        voicePlayerCache.clear();
                        h.addPrivateConversation(convId, Component.literal(name));
                        scrollOffset = 0;
                    }
                }));
        return rows;
    }

    private int menuHeight(List<MenuRow> rows) {
        return MENU_PAD + rows.size() * MENU_ROW_H;
    }

    private int menuWidth(List<MenuRow> rows) {
        int w = 80;
        for (MenuRow row : rows) {
            w = Math.max(w, font.width(row.label()) + 16);
        }
        return w;
    }

    private int[] menuPlacement(List<MenuRow> rows, int menuW) {
        int menuH = menuHeight(rows);
        int menuX = Math.min(contextMenuX, this.width - menuW - 10);
        int menuY = contextMenuY - menuH;
        if (menuY < HEADER_BAR_HEIGHT + 6) menuY = contextMenuY + 4;
        return new int[]{ menuX, menuY, menuH };
    }

    private void drawMenuRows(GuiGraphics g, int menuX, int menuY, int menuW, List<MenuRow> rows, int mouseY) {
        for (int i = 0; i < rows.size(); i++) {
            MenuRow row = rows.get(i);
            int ry = menuY + MENU_PAD / 2 + i * MENU_ROW_H;
            boolean hover = row.enabled() && mouseY >= ry && mouseY < ry + MENU_ROW_H;
            Ui.fillRoundedRect(g, menuX + 1, ry, menuW - 2, MENU_ROW_H - 1, 3, hover ? row.hoverColor() : 0);
            g.drawString(font, row.label(), menuX + 8, ry + 3,
                    row.enabled() ? row.textColor() : 0x66666666, false);
        }
    }

    private void drawMenuBox(GuiGraphics g, int mouseX, int mouseY, List<MenuRow> rows) {
        if (rows.isEmpty()) return;
        // Above item renders (z=150), below tooltips (z=400)
        g.pose().pushPose();
        g.pose().translate(0, 0, 200);
        int menuW = menuWidth(rows);
        int[] p = menuPlacement(rows, menuW);
        int menuX = p[0], menuY = p[1], menuH = p[2];

        Ui.fillRoundedRect(g, menuX, menuY, menuW, menuH, 4, Theme.panelBg());
        Ui.renderRoundedOutline(g, menuX, menuY, menuW, menuH, 4, Theme.popupOutline());
        drawMenuRows(g, menuX, menuY, menuW, rows, mouseY);
        g.pose().popPose();
    }

    private boolean handleMenuClick(int mx, int my, List<MenuRow> rows) {
        if (rows.isEmpty()) return false;
        int menuW = menuWidth(rows);
        int[] p = menuPlacement(rows, menuW);
        int menuX = p[0], menuY = p[1], menuH = p[2];
        if (mx < menuX || mx > menuX + menuW || my < menuY || my >= menuY + menuH) return false;
        int idx = (my - menuY - MENU_PAD / 2) / MENU_ROW_H;
        if (idx >= 0 && idx < rows.size()) {
            MenuRow row = rows.get(idx);
            if (row.enabled()) row.action().run();
        }
        return true;
    }

    private void drawContextMenu(GuiGraphics g, int mouseX, int mouseY) {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        ChatMessageData msg = history.getMessageByIndex(contextMsgIndex);
        drawMenuBox(g, mouseX, mouseY, messageMenuRows(msg, history));
    }

    private void drawAvatarContextMenu(GuiGraphics g, int mouseX, int mouseY) {
        drawMenuBox(g, mouseX, mouseY, avatarMenuRows(
                contextMenuName != null ? contextMenuName : "", contextMenuUuid));
    }

    private void handleContextMenuClick(int mx, int my) {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        if (contextType == CTX_AVATAR) {
            handleMenuClick(mx, my, avatarMenuRows(
                    contextMenuName != null ? contextMenuName : "", contextMenuUuid));
            contextType = CTX_NONE;
            contextMenuName = null;
            contextMenuUuid = null;
            return;
        }
        ChatMessageData msg = history.getMessageByIndex(contextMsgIndex);
        if (msg != null) {
            handleMenuClick(mx, my, messageMenuRows(msg, history));
        }
        contextType = CTX_NONE;
        contextMsgIndex = -1;
    }

    /** Human-readable quote text for an item-show message: slot ref becomes the item name. */
    private static String quoteTextFor(ChatMessageData msg) {
        String content = msg.content().getString();
        if (msg.itemNbt() != null && !msg.itemNbt().isEmpty()
                && ITEM_REF_PATTERN.matcher(content).matches()) {
            ItemStack stack = msg.parsedItem();
            if (!stack.isEmpty()) {
                return "[" + stack.getHoverName().getString() + "]";
            }
        }
        return content;
    }

    /** Fallback for reply quotes whose original item NBT is unavailable (legacy/remote). */
    private static String quoteTextForDisplay(String replyContent) {
        if (replyContent != null && ITEM_REF_PATTERN.matcher(replyContent).matches()) {
            return Component.translatable("screen.chatsphere.item_ref").getString();
        }
        return replyContent;
    }

    private boolean handleComponentClick(Component component, int relX) {
        if (component == null || minecraft == null || minecraft.player == null) return false;
        Style style = minecraft.font.getSplitter().componentStyleAtWidth(component, relX);
        if (style == null || style.getClickEvent() == null) return false;
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

    private static boolean hasClickable(Component component) {
        if (component == null) return false;
        if (component.getStyle() != null && component.getStyle().getClickEvent() != null) return true;
        for (Component child : component.getSiblings()) {
            if (hasClickable(child)) return true;
        }
        return false;
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
        boolean streamMode = Theme.stream();

        List<String> channelIds = history.getChannels();
        List<String> topLevel = new ArrayList<>();
        List<String> subIds = new ArrayList<>();
        for (String id : channelIds) {
            if (id == null || id.isEmpty() || id.equals("null")) continue;
            if (ChatHistoryManager.isSubChannel(id)) subIds.add(id);
            else topLevel.add(id);
        }

        List<String> shownTop = new ArrayList<>();
        List<String> shownSubs = new ArrayList<>();
        boolean commandConv = COMMAND_CONVERSATION_ID.equals(currentConversation);

        if (streamMode) {
            if (sidebarPrivateMode) {
            } else if (commandConv) {
                // console header lives in the rail
            } else {
                if (railSelectedGroup != null && !topLevel.contains(railSelectedGroup)) railSelectedGroup = null;
                if (railSelectedGroup == null) {
                    if (topLevel.contains(currentConversation)) railSelectedGroup = currentConversation;
                    else if (currentConversation != null && ChatHistoryManager.isSubChannel(currentConversation)) {
                        String root = currentConversation;
                        while (ChatHistoryManager.isSubChannel(root)) root = ChatHistoryManager.subParentOf(root);
                        if (topLevel.contains(root)) railSelectedGroup = root;
                    }
                }
                if (railSelectedGroup == null) {
                    railSelectedGroup = !topLevel.isEmpty() ? topLevel.get(0) : ChatHistoryManager.DEFAULT_CHANNEL_ID;
                }
                shownTop.add(railSelectedGroup);
                for (String sid : subIds) {
                    if (sid.startsWith(railSelectedGroup + "/")) shownSubs.add(sid);
                }
            }
        } else {
            shownTop.addAll(topLevel);
            shownSubs.addAll(subIds);
        }

        if (!(streamMode && (sidebarPrivateMode || commandConv))) {
            sidebarEntries.add(new SidebarEntry(null,
                    Component.translatable("screen.chatsphere.mod_chat.channels_header"),
                    null, true, null));
        }

        for (String id : shownTop) {
            Component display = history.getConversationDisplayName(id);
            if (display == null) display = Component.literal(id);
            sidebarEntries.add(new SidebarEntry(id, display,
                    ChatMessageData.ConversationType.CHANNEL, false, null, 0));
        }
        for (String id : shownSubs) {
            Component display = history.getConversationDisplayName(id);
            if (display == null) display = Component.literal(ChatHistoryManager.subNameOf(id));
            int depth = 0;
            for (int j = 0; j < id.length(); j++) {
                if (id.charAt(j) == '/') depth++;
            }
            sidebarEntries.add(new SidebarEntry(id, display,
                    ChatMessageData.ConversationType.CHANNEL, false, null, depth));
        }
        if (shownTop.isEmpty() && shownSubs.isEmpty()
                && !(streamMode && (sidebarPrivateMode || commandConv))) {
            sidebarEntries.add(new SidebarEntry(ChatHistoryManager.DEFAULT_CHANNEL_ID,
                    Component.translatable("screen.chatsphere.mod_chat.general_channel"),
                    ChatMessageData.ConversationType.CHANNEL, false, null, 0));
        }

        boolean showPrivate = !Theme.stream() || sidebarPrivateMode;
        if (showPrivate) {
            sidebarEntries.add(new SidebarEntry(null,
                    Component.translatable("screen.chatsphere.mod_chat.private_header"),
                    null, true, null));

            for (String id : history.getConversationIds()) {
                if (id == null || id.isEmpty() || id.equals("null")) continue;
                if (id.equals(ChatHistoryManager.DEFAULT_CHANNEL_ID)) continue; // default channel is not a private chat
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
        }

        // Stream mode: private list and console page are mutually exclusive
        if (!Theme.stream() || (Theme.stream() && commandConv && !sidebarPrivateMode)) {
            sidebarEntries.add(new SidebarEntry(null,
                    Component.translatable("screen.chatsphere.mod_chat.commands_header"),
                    null, true, null));
            sidebarEntries.add(new SidebarEntry(COMMAND_CONVERSATION_ID,
                    Component.translatable("screen.chatsphere.mod_chat.console_name"),
                    ChatMessageData.ConversationType.COMMAND, false, null));
        }
        buildRailItems(history, topLevel);
    }

    private void buildRailItems(ChatHistoryManager history, List<String> topLevel) {
        railItems.clear();
        if (!Theme.stream()) return;
        railItems.add(new RailItem(RK_HOME, null));
        for (String id : topLevel) {
            if (railItems.size() >= RAIL_MAX_ITEMS - 4) break;
            railItems.add(new RailItem(RK_GROUP, id));
        }
        if (railItems.size() < RAIL_MAX_ITEMS - 3) {
            railItems.add(new RailItem(RK_CONSOLE, COMMAND_CONVERSATION_ID));
        }
        railItems.add(new RailItem(RK_EXPLORE, null));
        railItems.add(new RailItem(RK_JOIN, null));
        railItems.add(new RailItem(RK_CREATE, null));
    }

    private void drawRail(GuiGraphics g, int mouseX, int mouseY, long now) {
        int rw = railW();
        if (rw == 0) return;
        int icon = railIcon();
        g.fill(0, 0, rw, this.height, Theme.railBg());
        g.fill(rw - 1, 0, rw, this.height, Theme.railSep());
        int iconX = (rw - icon) / 2;
        boolean overRail = mouseX < rw;
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        for (int i = 0; i < railItems.size(); i++) {
            RailItem item = railItems.get(i);
            int y = 12 + i * railSlot();
            boolean over = overRail && mouseX >= iconX && mouseX <= iconX + icon
                    && mouseY >= y && mouseY <= y + icon;
            if (over != railHoverOn[i]) {
                railHoverOn[i] = over;
                railHoverStart[i] = now;
            }
            int drawY = y;
            if (over) drawY -= 2;
            int r = 8;

            if (item.kind == RK_HOME) {
                if (minecraft != null && minecraft.player != null) {
                    // Player avatar (no rounded background)
                    drawPlayerFace(g, minecraft.player.getUUID(), iconX, y, icon, Theme.railBg());
                    if (privateUnread(history) > 0) {
                        g.fill(iconX + icon - 8, y + 2, iconX + icon - 4, y + 6, 0xFFFF3F42);
                    }
                }
                continue;
            }

            boolean active = false;
            if (item.kind == RK_GROUP && item.channelId != null) {
                if (item.channelId.equals(currentConversation)) active = true;
                else if (currentConversation != null && currentConversation.startsWith(item.channelId + "/")) active = true;
            } else if (item.kind == RK_CONSOLE && COMMAND_CONVERSATION_ID.equals(currentConversation)) {
                active = true;
            }

            int bg = over ? Theme.accent() : Theme.railIconBg();
            Ui.fillRoundedRectStyle(g, 1, iconX, drawY, icon, icon, r, bg);

            if (active) {
                g.fill(2, drawY + 8, 4, drawY + icon - 8, 0xFFFFFFFF);
            }

            int iconColor = over ? 0xFFFFFFFF : Theme.textDim();
            switch (item.kind) {
                case RK_GROUP -> {
                    if (item.channelId != null) {
                        String label = channelIconLabel(history, item.channelId);
                        g.drawString(font, label, iconX + (icon - font.width(label)) / 2, drawY + (icon - 8) / 2, iconColor, false);
                        int unread = groupUnread(history, item.channelId);
                        if (unread > 0 && !active) {
                            g.fill(iconX + icon - 8, drawY + 2, iconX + icon - 4, drawY + 6, 0xFFFF3F42);
                        }
                    }
                }
                case RK_CONSOLE -> {
                    g.drawString(font, ">", iconX + (icon - font.width(">")) / 2, drawY + (icon - 8) / 2, active ? 0xFF66FF66 : 0xFF66AA66, false);
                    int unread = history.getUnreadCount(COMMAND_CONVERSATION_ID);
                    if (unread > 0 && !active) {
                        g.fill(iconX + icon - 8, drawY + 2, iconX + icon - 4, drawY + 6, 0xFFFF3F42);
                    }
                }
                case RK_EXPLORE -> g.blit(SEARCH_ICON, iconX + (icon - 16) / 2, drawY + (icon - 16) / 2, 0, 0, 16, 16, 16, 16);
                case RK_JOIN -> g.blit(JOIN_CHANNEL_ICON, iconX + (icon - 16) / 2, drawY + (icon - 16) / 2, 0, 0, 16, 16, 16, 16);
                case RK_CREATE -> g.blit(CREATE_CHANNEL_ICON, iconX + (icon - 16) / 2, drawY + (icon - 16) / 2, 0, 0, 16, 16, 16, 16);
                default -> { }
            }
        }
    }

    private static String channelIconLabel(ChatHistoryManager history, String channelId) {
        Component name = history.getConversationDisplayName(channelId);
        String str = name != null ? name.getString() : "";
        if (str.isEmpty()) str = channelId.startsWith("#") ? channelId.substring(1) : channelId;
        return str.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
    }

    private static int groupUnread(ChatHistoryManager history, String groupId) {
        int total = history.getUnreadCount(groupId);
        if (total <= 0) {
            for (String id : history.getChannels()) {
                if (id.startsWith(groupId + "/")) total += history.getUnreadCount(id);
            }
        }
        return total;
    }

    private static int privateUnread(ChatHistoryManager history) {
        int total = 0;
        for (String id : history.getConversationIds()) {
            if (history.getConversationType(id) == ChatMessageData.ConversationType.PRIVATE) {
                total += history.getUnreadCount(id);
            }
        }
        return total;
    }

    private static String firstPrivateConversation(ChatHistoryManager history) {
        for (String id : history.getConversationIds()) {
            if (id == null || id.isEmpty()) continue;
            if (history.getConversationType(id) == ChatMessageData.ConversationType.PRIVATE) {
                return id;
            }
        }
        return null;
    }

    private void drawSidebar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean shifted = railW() > 0;
        if (shifted) guiGraphics.pose().pushPose();
        if (shifted) guiGraphics.pose().translate(sidebarLeft(), 0, 0);
        int xo = sidebarLeft();
        guiGraphics.fill(0, 0, sidebarWidth(), this.height, Theme.sidebarBg());

        int y = 10;
        int headerIdx = 0;
        for (int i = 0; i < sidebarEntries.size(); i++) {
            SidebarEntry entry = sidebarEntries.get(i);
            int hoverTop = entry.indent > 0 ? y + 2 : y;
            int hoverBottom = entry.indent > 0 ? y + 2 + (SIDEBAR_ITEM_HEIGHT - 4) : y + SIDEBAR_ITEM_HEIGHT;
            boolean hovered = mouseX >= sidebarLeft() && mouseX < chatLeft() && mouseX >= 0
                    && mouseY >= hoverTop && mouseY < hoverBottom;
            boolean active = entry.conversationId != null && entry.conversationId.equals(currentConversation);
            if (!active && entry.conversationId != null && entry.indent == 0
                    && entry.type == ChatMessageData.ConversationType.CHANNEL
                    && currentConversation != null
                    && currentConversation.startsWith(entry.conversationId + "/")) {
                active = true;
            }

            if (entry.isHeader) {
                if (Theme.stream()) {
                    String title = entry.displayName.getString().toUpperCase(java.util.Locale.ROOT);
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(8, y + 5, 0);
                    guiGraphics.pose().scale(0.8f, 0.8f, 1f);
                    guiGraphics.drawString(font, title, 0, 0, Theme.textFaint(), false);
                    guiGraphics.pose().popPose();
                } else {
                    if (headerIdx > 0) {
                        guiGraphics.fill(4, y + 1, sidebarWidth() - 4, y + 2, Theme.sectionLine());
                    }
                    guiGraphics.drawString(font, entry.displayName, 8, y + 5, Theme.textDim(), false);
                }
                if (headerIdx == 0 && !Theme.stream()) {
                    int plusX = sidebarWidth() - 6 - 10;
                    int plusY = y + (SIDEBAR_ITEM_HEIGHT - 8) / 2;
                    boolean plusHovered = mouseX >= plusX + xo && mouseX <= plusX + 10 + xo
                            && mouseY >= plusY && mouseY <= plusY + 10;
                    Ui.fillRoundedRect(guiGraphics, plusX, plusY, 10, 10, 2,
                            plusHovered ? 0xFF44AA44 : 0xFF333388);
                    guiGraphics.blit(CREATE_CHANNEL_ICON, plusX + 1, plusY + 1, 0, 0, 8, 8, 8, 8);

                    int joinX = plusX - 14;
                    int joinY = plusY;
                    boolean joinHovered = mouseX >= joinX + xo && mouseX <= joinX + 10 + xo
                            && mouseY >= joinY && mouseY <= joinY + 10;
                    Ui.fillRoundedRect(guiGraphics, joinX, joinY, 10, 10, 2,
                            joinHovered ? 0xFF4488AA : 0xFF333366);
                    guiGraphics.blit(JOIN_CHANNEL_ICON, joinX + 1, joinY + 1, 0, 0, 8, 8, 8, 8);

                    int exploreX = joinX - 14;
                    boolean exploreHovered = mouseX >= exploreX + xo && mouseX <= exploreX + 10 + xo
                            && mouseY >= joinY && mouseY <= joinY + 10;
                    Ui.fillRoundedRect(guiGraphics, exploreX, joinY, 10, 10, 2,
                            exploreHovered ? 0xFF44AA88 : 0xFF334466);
                    guiGraphics.blit(SEARCH_ICON, exploreX + 1, joinY + 1, 0, 0, 8, 8, 8, 8);
                }
                headerIdx++;
                y += SIDEBAR_HEADER_HEIGHT;
            } else {
                if (entry.indent == 0) {
                    int bgColor = active ? Theme.activeRow() : (hovered ? Theme.hoverRow() : 0x00000000);
                    if (bgColor != 0) {
                        Ui.fillRoundedRect(guiGraphics, 2, y, sidebarWidth() - 4, SIDEBAR_ITEM_HEIGHT, 4, bgColor);
                    }
                    if (active) {
                        Ui.fillRoundedRect(guiGraphics, 2, y + 4, 3, SIDEBAR_ITEM_HEIGHT - 8, 2, Theme.accent());
                    }
                }
                if (entry.type == ChatMessageData.ConversationType.COMMAND) {
                    guiGraphics.drawString(font, ">", 6, y + 4, 0xFF66AA66, false);
                    guiGraphics.drawString(font, entry.displayName, 14, y + 4,
                            active ? Theme.text() : Theme.textInactive(), false);
                    if (ModClientConfig.CONFIG.notificationBadge.get()) {
                        int unread = ChatHistoryManager.getInstance().getUnreadCount(COMMAND_CONVERSATION_ID);
                        if (unread > 0) {
                            String badge = unread > 99 ? "99+" : String.valueOf(unread);
                            int bw = font.width(badge) + 3;
                            int bx = 14 + font.width(entry.displayName) + 5;
                            guiGraphics.fill(bx, y + 4, bx + bw, y + 12, 0xCCFF4444);
                            guiGraphics.drawString(font, badge, bx + 1, y + 4, 0xFFFFFFFF, false);
                        }
                    }
                } else if (entry.type == ChatMessageData.ConversationType.PRIVATE) {
                    drawPlayerFace(guiGraphics, entry.targetUuid, 6, y + 3, SIDEBAR_AVATAR_SIZE, Theme.sidebarBg());
                    guiGraphics.drawString(font, entry.displayName, 6 + SIDEBAR_AVATAR_SIZE + 2, y + 4,
                            Theme.textMain(), false);
                    boolean online = entry.targetUuid != null && onlinePlayers.containsKey(entry.targetUuid.toString());
                    int dotColor = online ? 0xFF44FF44 : 0xFF666666;
                    guiGraphics.fill(sidebarWidth() - 8, y + SIDEBAR_ITEM_HEIGHT - 6, sidebarWidth() - 4, y + SIDEBAR_ITEM_HEIGHT - 2, dotColor);
                } else {
                    if (entry.indent > 0) {
                        renderSubSidebarEntry(guiGraphics, entry, y, mouseX, mouseY, active, hovered);
                    } else {
                        int textX = 8 + entry.indent * 10;
                        MutableComponent label = Component.literal(entry.indent > 0 ? "" : "# ");
                        label.append(entry.displayName);
                        guiGraphics.drawString(font, label, textX, y + 4,
                                active ? Theme.text() : (entry.indent > 0 ? Theme.textDim() : Theme.textInactive()), false);

                        int gearX = sidebarWidth() - 6 - CONFIG_ICON_SIZE;
                        int gearY = y + (SIDEBAR_ITEM_HEIGHT - CONFIG_ICON_SIZE) / 2;
                        boolean gearHovered = mouseX >= gearX + xo && mouseX <= gearX + CONFIG_ICON_SIZE + xo
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
                }
                y += SIDEBAR_ITEM_HEIGHT;
            }
        }
        if (shifted) guiGraphics.pose().popPose();
    }

    private void renderSubSidebarEntry(GuiGraphics guiGraphics, SidebarEntry entry, int y,
                                       int mouseX, int mouseY, boolean active, boolean hovered) {
        int depth = Math.max(1, entry.indent);
        int subH = SIDEBAR_ITEM_HEIGHT - 4;
        int bgColor = active ? 0x33FFFFFF : (hovered ? Theme.hoverRow() : 0x00000000);
        if (bgColor != 0) {
            Ui.fillRoundedRect(guiGraphics, 8, y + 2, sidebarWidth() - 8, subH, 3, bgColor);
        }
        if (active) {
            int barX = 8 + (depth - 1) * 6;
            Ui.fillRoundedRect(guiGraphics, barX, y + 4, 2, subH - 4, 1, Theme.accent());
        }
        int textX = 14 + depth * 10;
        int textY = y + 5;
        float scale = depth >= 3 ? 0.68f : (depth == 2 ? 0.74f : 0.8f);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(textX, textY, 0);
        guiGraphics.pose().scale(scale, scale, 1);
        int color = active ? Theme.text() : Theme.textInactive();
        guiGraphics.drawString(font, entry.displayName, 0, 0, color, false);
        guiGraphics.pose().popPose();

        int gearX = sidebarWidth() - 6 - CONFIG_ICON_SIZE;
        int gearY = y + (subH - CONFIG_ICON_SIZE) / 2 + 1;
        boolean gearHovered = mouseX >= gearX + sidebarLeft() && mouseX <= gearX + CONFIG_ICON_SIZE + sidebarLeft()
                && mouseY >= gearY && mouseY <= gearY + CONFIG_ICON_SIZE;
        int tint = gearHovered ? 0xFFFFFFFF : 0xAA999999;
        guiGraphics.setColor(
                ((tint >> 16) & 0xFF) / 255f,
                ((tint >> 8) & 0xFF) / 255f,
                (tint & 0xFF) / 255f,
                ((tint >> 24) & 0xFF) / 255f);
        guiGraphics.blit(SETTINGS_ICON, gearX, gearY, 0, 0, CONFIG_ICON_SIZE, CONFIG_ICON_SIZE, CONFIG_ICON_SIZE, CONFIG_ICON_SIZE);
        guiGraphics.setColor(1f, 1f, 1f, 1f);
    }

    private void drawPlayerFace(GuiGraphics guiGraphics, UUID uuid, int x, int y, int size, int cornerColor) {
        if (uuid == null) return;
        PlayerFaceRenderer.draw(guiGraphics, PlayerSkinCache.getSkin(uuid), x, y, size);
        int r = Theme.avatarRadius();
        if (r > 0) Ui.fillAvatarCorners(guiGraphics, x, y, size, r, cornerColor);
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
        int headerX = sidebarLeft() + (this.width - chatLeft() - hw) / 2;
        guiGraphics.drawString(font, header, headerX, 3, Theme.floatingText(), false);

        if (type == ChatMessageData.ConversationType.PRIVATE) {
            boolean online = currentConversation != null && isTargetOnline(currentConversation);
            int dotX = chatLeft() + 4;
            int dotY = 6;
            int dotR = 3;
            int dotColor = online ? 0xFF44FF44 : 0xFF666666;
            guiGraphics.fill(dotX - dotR, dotY - dotR, dotX + dotR, dotY + dotR, dotColor);
        }

        if (isCurrentChannelMuted()) {
            int barY = HEADER_BAR_HEIGHT + 2;
            guiGraphics.fill(chatLeft(), barY, width, barY + MUTE_BAR_H, 0xCC661111);
            guiGraphics.fill(chatLeft(), barY + MUTE_BAR_H - 1, width, barY + MUTE_BAR_H, 0xFF441111);
            Component muteText = Component.translatable("chatsphere.mute.feedback");
            guiGraphics.drawString(font, muteText, chatLeft() + 6, barY + 3, 0xFFFF8888, false);
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
        return isMutedEntry(cfg.mutedPlayers, minecraft.player.getUUID().toString());
    }

    /** Mute entries are "uuid" (permanent) or "uuid:untilMillis" (timed). */
    private static boolean isMutedEntry(List<String> mutedPlayers, String playerUuid) {
        long now = System.currentTimeMillis();
        for (String e : mutedPlayers) {
            String uuidPart = e;
            long until = Long.MAX_VALUE;
            int colon = e.indexOf(':');
            if (colon > 0) {
                uuidPart = e.substring(0, colon);
                try {
                    until = Long.parseLong(e.substring(colon + 1));
                } catch (NumberFormatException ignored) {
                    until = 0;
                }
            }
            if (uuidPart.equals(playerUuid) && until > now) return true;
        }
        return false;
    }

    private void renderNotificationBar(GuiGraphics g, int screenHeight) {
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        int unread = history.getUnreadCount(currentConversation);
        boolean atBottom = scrollOffset <= 1;
        if (atBottom) {
            unread = 0;
        }
        if (unread <= 0) return;

        int barY = screenHeight - 14 - TOOLBAR_HEIGHT - NOTIF_BAR_H;
        notifBarY = barY;
        int barH = NOTIF_BAR_H;

        g.fillGradient(chatLeft(), barY, width, barY + barH, Theme.notifGradTop(), Theme.notifGradBot());

        // notificationPulse animation
        float pulse = ThemeAnim.pulseFactor(CustomTheme.INSTANCE.anim("notificationPulse"));
        int lineColor;
        int textColor = 0xFFFFCC66;
        if (pulse >= 0) {
            int a = (int) (0x33 + 0xCC * pulse);
            lineColor = (a << 24) | (Theme.accentLine() & 0xFFFFFF);
            if (pulse > 0.8f) textColor = 0xFFFFFFFF;
        } else {
            lineColor = Theme.accentLine();
        }
        g.fill(chatLeft(), barY, width, barY + 1, lineColor);
        g.fill(chatLeft(), barY + barH - 1, width, barY + barH, lineColor);

        String text = Component.translatable("chatsphere.notif.new_messages", unread).getString() + " \u25BD";
        int textW = font.width(text);
        int textX = sidebarLeft() + (width - chatLeft() - textW) / 2;
        g.drawString(font, text, textX + 1, barY + 5, 0xAA000000, false);
        g.drawString(font, text, textX, barY + 4, textColor, false);
    }

    private int chatAreaTop() {
        return HEADER_BAR_HEIGHT + 6 + (isCurrentChannelMuted() ? MUTE_BAR_H : 0);
    }

    private void renderMessages(GuiGraphics guiGraphics, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int chatAreaLeft = chatLeft() + 4;
        int chatAreaRight = screenWidth - 4;
        int chatAreaTop = chatAreaTop();
        int chatAreaBottom = screenHeight - 14 - TOOLBAR_HEIGHT - MESSAGE_BOTTOM_PAD - (replyBar.targetIndex >= 0 ? ReplyBarWidget.BAR_HEIGHT + 2 : 0);

        ChatHistoryManager history = ChatHistoryManager.getInstance();
        List<ChatMessageData> messages = history.getMessagesByConversation(currentConversation);
        int totalMessages = messages.size();
        if (totalMessages == 0) return;
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset()));
        synchronized (cmdHitBoxes) { cmdHitBoxes.clear(); }
        synchronized (voiceHitBoxes) { voiceHitBoxes.clear(); }
        synchronized (bubbleHitBoxes) { bubbleHitBoxes.clear(); }
        synchronized (replyQuoteHitBoxes) { replyQuoteHitBoxes.clear(); }
        synchronized (itemHitBoxes) { itemHitBoxes.clear(); }
        synchronized (richTextHitBoxes) { richTextHitBoxes.clear(); }
        synchronized (avatarHitBoxes) { avatarHitBoxes.clear(); }

        int yOffset = chatAreaBottom;
        int idx = Math.max(0, totalMessages - 1 - scrollOffset);
        int sepInterval = ModClientConfig.CONFIG.timeSeparatorMinutes.get();
        String lastTimeKey = null;

        ChatHistoryManager historyMgr = history;
        boolean streamRows = Theme.stream();
        int unreadCount = streamRows ? history.getUnreadCount(currentConversation) : 0;
        Set<Integer> searchSet = showSearch && !searchResults.isEmpty() ? new HashSet<>(searchResults) : Set.of();
        boolean commandConv = COMMAND_CONVERSATION_ID.equals(currentConversation);
        Map<ChatMessageData, Integer> globalIndexMap = null;
        if (!commandConv) {
            List<ChatMessageData> all = history.snapshotAllMessages();
            globalIndexMap = new HashMap<>(all.size());
            for (int j = 0; j < all.size(); j++) {
                globalIndexMap.putIfAbsent(all.get(j), j);
            }
        }
        for (int i = idx; i >= 0; i--) {
            ChatMessageData msg = messages.get(i);
            int globalIdx = commandConv ? i : globalIndexMap.getOrDefault(msg, -1);

            if (unreadCount > 0 && i == totalMessages - unreadCount) {
                yOffset -= 18;
                if (yOffset < chatAreaTop) break;
                String label = Component.translatable("screen.chatsphere.unread_divider").getString();
                int lw = font.width(label);
                int cy = yOffset + 8;
                int lineColor = 0xFFF23F42;
                int centerX = chatAreaLeft + (chatAreaRight - chatAreaLeft - lw) / 2;
                guiGraphics.drawString(font, label, centerX, yOffset + 1, lineColor, false);
                guiGraphics.fill(chatAreaLeft + 4, cy, centerX - 6, cy + 1, lineColor);
                guiGraphics.fill(centerX + lw + 6, cy, chatAreaRight - 4, cy + 1, lineColor);
            }

            if (msg.senderUuid() != null && historyMgr.isPlayerBlocked(msg.senderUuid().toString())) {
                yOffset -= 2;
                continue;
            }

            if (sepInterval > 0 && !streamRows) {
                String key = ChatHistoryManager.timeSeparatorKey(msg.timestamp(), sepInterval);
                if (lastTimeKey != null && !key.equals(lastTimeKey)) {
                    yOffset -= 16;
                    if (yOffset < chatAreaTop) break;
                    String sepText = ChatHistoryManager.formatTimestampSmart(msg.timestamp());
                    int sepW = font.width(sepText);
                    int sepX = chatAreaLeft + (chatAreaRight - chatAreaLeft - sepW) / 2;
                    guiGraphics.drawString(font, sepText, sepX, yOffset + 1, Theme.floatingTextDim(), false);
                    guiGraphics.fill(chatAreaLeft + 8, yOffset + 10, chatAreaRight - 8, yOffset + 11, Theme.sectionLine());
                }
                lastTimeKey = key;
            } else if (streamRows && i + 1 < totalMessages
                    && !ChatHistoryManager.isSameDay(messages.get(i + 1).timestamp(), msg.timestamp())) {
                yOffset -= 26;
                if (yOffset < chatAreaTop) break;
                String sepText = ChatHistoryManager.formatDateHeader(messages.get(i + 1).timestamp());
                int sepW = font.width(sepText);
                int sepX = chatAreaLeft + (chatAreaRight - chatAreaLeft - sepW) / 2;
                guiGraphics.drawString(font, sepText, sepX, yOffset + 1, Theme.floatingTextDim(), false);
                guiGraphics.fill(chatAreaLeft + 8, yOffset + 10, sepX - 8, yOffset + 11, Theme.sectionLine());
                guiGraphics.fill(sepX + sepW + 8, yOffset + 10, chatAreaRight - 8, yOffset + 11, Theme.sectionLine());
            }

            int bubbleHeight;
            RowPaint paint;
            if (streamRows && msg.conversationType() != ChatMessageData.ConversationType.COMMAND) {
                // Merge into the older row
                ChatMessageData older = i > 0 ? messages.get(i - 1) : null;
                paint = renderMessageRow(guiGraphics, msg, chatAreaLeft, chatAreaRight, yOffset, older, globalIdx, mouseX, mouseY);
            } else {
                paint = new RowPaint(renderMessageBubble(guiGraphics, msg, chatAreaLeft, chatAreaRight, yOffset), false, 0, 0, 0, 0, false);
            }
            bubbleHeight = paint.height();
            int rowTop = yOffset - bubbleHeight;
            synchronized (bubbleHitBoxes) {
                bubbleHitBoxes.add(new BubbleHit(chatAreaLeft, rowTop, chatAreaRight - chatAreaLeft, bubbleHeight, globalIdx));
            }
            if (showSearch && !searchQuery.isEmpty() && !searchResults.isEmpty()) {
                if (searchSet.contains(globalIdx)) {
                    boolean isCurrent = searchResultIndex >= 0 && searchResultIndex < searchResults.size()
                            && searchResults.get(searchResultIndex) == globalIdx;
                    int hlColor = isCurrent ? 0x44FFAA00 : 0x228888FF;
                    if (paint.emojiOnly()) {
                        Ui.fillRoundedRect(guiGraphics, paint.emojiX() - 6, paint.emojiY() - 2, paint.emojiW() + 12, paint.emojiH() + 4, 8, hlColor);
                    } else {
                        guiGraphics.fill(chatAreaLeft, rowTop + 2, chatAreaRight, yOffset + 2, hlColor);
                    }
                }
            }
            if (replyHighlightTarget >= 0) {
                if (globalIdx == replyHighlightTarget) {
                    if (paint.emojiOnly()) {
                        Ui.fillRoundedRect(guiGraphics, paint.emojiX() - 6, paint.emojiY() - 2, paint.emojiW() + 12, paint.emojiH() + 4, 8, 0x4433AA33);
                    } else {
                        guiGraphics.fill(chatAreaLeft, rowTop + 2, chatAreaRight, yOffset + 2, 0x4433AA33);
                    }
                }
            }
            yOffset -= bubbleHeight + 2;
            if (yOffset < chatAreaTop) break;
        }
    }

    private RowPaint renderMessageRow(GuiGraphics g, ChatMessageData msg, int areaLeft, int areaRight, int y,
                                      ChatMessageData prev, int globalIdx, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        boolean showName = ModClientConfig.CONFIG.showSenderName.get();
        boolean showAvatar = ModClientConfig.CONFIG.showAvatar.get();

        boolean merge = prev != null
                && prev.senderUuid() != null && msg.senderUuid() != null
                && prev.senderUuid().equals(msg.senderUuid())
                && Math.abs(msg.timestamp() - prev.timestamp()) < 5L * 60L * 1000L;

        int lineH = mc.font.lineHeight + 1;
        int dupW = msg.duplicateCount() > 1 ? mc.font.width("x" + msg.duplicateCount()) : 0;
        int textX = areaLeft + rowAvatarCol();
        int textAreaW = Math.max(120, areaRight - textX - 4 - (merge && dupW > 0 ? dupW + 6 : 0));

        Component contentText = msg.renderedContent();
        boolean isVoice = msg.content().getString().startsWith("VoiceMessage#")
                && ModVoiceMessagesIntegration.isVoiceMessagesLoaded();
        java.util.UUID vmUuid = null;
        if (isVoice) {
            try {
                vmUuid = java.util.UUID.fromString(msg.content().getString().substring("VoiceMessage#".length()));
            } catch (Exception ignored) {
                isVoice = false;
            }
        }

        List<Component> displayLines = getWrappedLines(mc, msg, contentText, false, textAreaW);
        int contentH = linesContentH(displayLines, lineH);
        if (msg.replyContent() != null) contentH += lineH;
        if (msg.itemNbt() != null && !msg.itemNbt().isEmpty()) contentH += 18;
        if (isVoice) contentH = Math.max(contentH, 20);

        boolean pureEmoji = displayLines.size() == 1
                && !displayLines.get(0).getString().isEmpty()
                && EmojiRegistry.isEmojiOnly(displayLines.get(0).getString())
                && msg.replyContent() == null
                && (msg.itemNbt() == null || msg.itemNbt().isEmpty())
                && !isVoice;
        int emojiBlockX = 0, emojiBlockY = 0, emojiBlockW = 0, emojiBlockH = 0;
        if (pureEmoji) {
            contentH = 26;
            emojiBlockW = mc.font.width(displayLines.get(0)) + 8;
        }

        int headerH = merge ? 0 : lineH;
        int contentPaintH = headerH + contentH;
        int rowH = Math.max(contentPaintH, (merge ? 0 : rowAvatarSize()) + 4);
        int rowTop = y - rowH;
        if (rowTop < HEADER_BAR_HEIGHT + 6) return new RowPaint(rowH + 2, false, 0, 0, 0, 0, false);

        long spawnMs = msgSpawnMs.computeIfAbsent(msg, k -> System.currentTimeMillis());
        AnimSpec slideIn = CustomTheme.INSTANCE.anim("messageSlideIn");
        AnimSpec fadeIn = CustomTheme.INSTANCE.anim("bubbleFadeIn");
        float slideP = ThemeAnim.progress(spawnMs, slideIn);
        float fadeP = ThemeAnim.progress(spawnMs, fadeIn);
        if (slideP < 0 && fadeP < 0) {
            Long stamp = msgSpawnMs.get(msg);
            if (stamp != null && stamp != 0L) msgSpawnMs.put(msg, 0L);
        }
        boolean animating = slideP >= 0;
        if (animating) {
            g.pose().pushPose();
            g.pose().translate(0, (1 - slideP) * 10, 0);
        }
        if (fadeP >= 0) g.setColor(1f, 1f, 1f, 0.2f + 0.8f * fadeP);

        boolean hovered = mouseX >= areaLeft && mouseX <= areaRight && mouseY >= rowTop && mouseY < y;

        if (!merge && showAvatar && msg.senderUuid() != null) {
            int avSize = rowAvatarSize();
            int avX = textX - avSize - 8;
            int avY = rowTop + 2;
            drawPlayerFace(g, msg.senderUuid(), avX, avY, avSize, Theme.sidebarBg());
            // Avatar hit area (includes name/action column)
            synchronized (avatarHitBoxes) {
                avatarHitBoxes.add(new AvatarHit(areaLeft, rowTop, Math.min(textX - areaLeft, rowAvatarCol() - 4), rowH,
                        msg.senderName().getString(), msg.senderUuid()));
            }
        }

        int textY = rowTop + headerH;
        if (pureEmoji) {
            emojiBlockX = textX - 2;
            emojiBlockY = textY + EmojiRegistry.EMOJI_Y_OFFSET;
            emojiBlockH = 22;
        }
        if (!merge && showName) {
            String nameStr = msg.senderName().getString();
            g.drawString(mc.font, nameStr, textX, rowTop, Theme.nameColor(nameStr), false);
        }
        if (!merge) {
            String ts = ChatHistoryManager.formatTimestampSmart(msg.timestamp());
            int tsX = areaRight - mc.font.width(ts) - 4;
            if (msg.duplicateCount() > 1) {
                String dupLabel = "x" + msg.duplicateCount();
                g.drawString(mc.font, dupLabel, tsX - mc.font.width(dupLabel) - 3, rowTop, 0xFFAA66AA, false);
            }
            g.drawString(mc.font, ts, tsX, rowTop, Theme.textDim(), false);
        } else if (msg.duplicateCount() > 1) {
            String dupLabel = "x" + msg.duplicateCount();
            g.drawString(mc.font, dupLabel, areaRight - mc.font.width(dupLabel) - 4, rowTop + 2, 0xFFAA66AA, false);
        }

        if (msg.replyContent() != null) {
            String rawReply = "\u2191 " + msg.replySender() + ": " + quoteTextForDisplay(msg.replyContent());
            String truncated = mc.font.plainSubstrByWidth(rawReply, textAreaW);
            Component replyLine = EmojiRegistry.toComponent(truncated);
            int emojiOff = EmojiRegistry.containsPua(replyLine) ? EmojiRegistry.EMOJI_Y_OFFSET : 0;
            if (CustomEmojiRegistry.containsToken(replyLine)) {
                CustomEmojiRegistry.renderLine(g, replyLine, textX, textY + emojiOff, Theme.accent(), false);
            } else {
                g.drawString(mc.font, replyLine, textX, textY + emojiOff, Theme.accent(), false);
            }
            synchronized (replyQuoteHitBoxes) {
                replyQuoteHitBoxes.add(new ReplyQuoteHit(textX, textY, mc.font.width(truncated), lineH,
                        msg.replySender(), msg.replyContent()));
            }
            textY += lineH;
        }

        boolean itemRendered = false;
        if (msg.itemNbt() != null && !msg.itemNbt().isEmpty()) {
            ItemStack stack = msg.parsedItem();
            if (!stack.isEmpty()) {
                g.renderItem(stack, textX, textY);
                String nameStr = mc.font.plainSubstrByWidth(stack.getHoverName().getString(), Math.max(textAreaW - 18, 0));
                g.drawString(mc.font, nameStr, textX + 18, textY + 4, 0xFFFFAA00, false);
                synchronized (itemHitBoxes) {
                    itemHitBoxes.add(new BubbleItemHit(textX, textY, 18 + mc.font.width(nameStr), 16, stack));
                }
                textY += 18;
                itemRendered = true;
            }
        }

        if (pureEmoji) {
            int emojiOff = EmojiRegistry.containsPua(contentText) ? EmojiRegistry.EMOJI_Y_OFFSET : 0;
            Component line = displayLines.get(0);
            g.drawString(mc.font, line, textX, textY + emojiOff, Theme.text(), false);
            emojiBlockY = textY + emojiOff;
        } else if (isVoice) {
            int vmW = textAreaW, vmH = 20;
            Object pp = voicePlayerCache.computeIfAbsent(vmUuid, u -> ModVoiceMessagesIntegration.createPlaybackPlayer(u, 0x00000000));
            if (pp != null) {
                ModVoiceMessagesIntegration.setupPlaybackPlayer(pp, textX, textY, vmW, vmH);
                ModVoiceMessagesIntegration.renderPlaybackPlayer(pp, g);
                synchronized (voiceHitBoxes) { voiceHitBoxes.add(new VoiceHit(textX, textY, vmW, vmH, vmUuid, pp)); }
            } else {
                g.drawString(mc.font, Component.translatable("chatsphere.voice.received"), textX, textY, Theme.textDim(), false);
            }
        } else if (!itemRendered || !ITEM_REF_PATTERN.matcher(contentText.getString()).matches()) {
            int emojiOff = EmojiRegistry.containsPua(contentText) ? EmojiRegistry.EMOJI_Y_OFFSET : 0;
            int ly = textY + emojiOff;
            for (int li = 0; li < displayLines.size(); li++) {
                Component line = displayLines.get(li);
                if (line.getString().isEmpty()) {
                    ly += lineH;
                    continue;
                }
                int emojiH = CustomEmojiRegistry.lineHeightFor(line.getString());
                int lh = emojiH > 0 ? Math.max(lineH, emojiH + 2) : lineH;
                if (CustomEmojiRegistry.containsToken(line)) {
                    CustomEmojiRegistry.renderLine(g, line, textX, ly, Theme.text(), false);
                } else {
                    g.drawString(mc.font, line, textX, ly, Theme.text(), false);
                }
                if (hasClickable(line)) {
                    synchronized (richTextHitBoxes) {
                        richTextHitBoxes.add(new RichTextHit(textX, ly,
                                mc.font.width(line), lineH, line));
                    }
                }
                ly += lh;
            }
        }

        if (fadeP >= 0) g.setColor(1f, 1f, 1f, 1f);
        if (animating) g.pose().popPose();
        return new RowPaint(rowH + 2, pureEmoji, emojiBlockX, emojiBlockY, emojiBlockW, emojiBlockH, hovered);
    }

    private int renderMessageBubble(GuiGraphics guiGraphics, ChatMessageData msg,
                                    int areaLeft, int areaRight, int y) {
        Minecraft mc = Minecraft.getInstance();
        boolean showName = ModClientConfig.CONFIG.showSenderName.get();
        boolean showTime = ModClientConfig.CONFIG.showTimestamp.get();
        boolean showAvatar = ModClientConfig.CONFIG.showAvatar.get();

        boolean isCommand = msg.conversationType() == ChatMessageData.ConversationType.COMMAND;

        // Entrance animation tween (messageSlideIn / bubblePopIn)
        long spawnMs = msgSpawnMs.computeIfAbsent(msg, k -> System.currentTimeMillis());
        AnimSpec slideIn = CustomTheme.INSTANCE.anim("messageSlideIn");
        AnimSpec popIn = CustomTheme.INSTANCE.anim("bubblePopIn");
        AnimSpec fadeIn = CustomTheme.INSTANCE.anim("bubbleFadeIn");
        float slideP = isCommand ? -1 : ThemeAnim.progress(spawnMs, slideIn);
        float popP = isCommand ? -1 : ThemeAnim.progress(spawnMs, popIn);
        float fadeP = ThemeAnim.progress(spawnMs, fadeIn);
        boolean animating = slideP >= 0 || popP >= 0;
        if (!animating) {
            Long stamp = msgSpawnMs.get(msg);
            if (stamp != null && stamp != 0L) msgSpawnMs.put(msg, 0L);
        }

        Component contentText;
        MutableComponent infoLine = Component.literal("");
        if (isCommand) {
            contentText = msg.senderName().copy();
            if (msg.isInput()) {
                infoLine.append(Component.translatable("screen.chatsphere.cmd_input").withStyle(ChatFormatting.DARK_GREEN));
            } else {
                infoLine.append(Component.translatable("screen.chatsphere.cmd_output").withStyle(ChatFormatting.GRAY));
            }
        } else {
            contentText = msg.renderedContent();
            if (showName) {
                infoLine.append(msg.senderName().copy().withStyle(ChatFormatting.AQUA));
            }
        }
        // Short label for VoiceMessage#UUID when VM mod is absent
        if (contentText.getString().startsWith("VoiceMessage#") && !ModVoiceMessagesIntegration.isVoiceMessagesLoaded()) {
            contentText = Component.translatable("chatsphere.voice.received").withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        if (showTime && !isCommand) {
            String ts = ChatHistoryManager.formatTimestampSmart(msg.timestamp());
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
            int prefixWidth = mc.font.width(msg.isInput() ? "> " : "\u2192 ");
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

        int lineH = mc.font.lineHeight + Theme.messageLineSpacing();
        int avReserve = (showAvatar && !isCommand) ? AVATAR_SIZE + 4 : 0;
        int bubbleW = Math.min(maxLineWidth + replyW + BUBBLE_HPAD * 2, areaRight - areaLeft - avReserve - 30);
        int textAreaW = Math.max(bubbleW - BUBBLE_HPAD * 2, 40);

        List<Component> displayLines = getWrappedLines(mc, msg, contentText, isCommand, textAreaW);

        int lines = displayLines.size() + (isCommand ? 0 : 1);
        if (msg.replyContent() != null) lines++;
        if (!isCommand && msg.content().getString().startsWith("VoiceMessage#") && ModVoiceMessagesIntegration.isVoiceMessagesLoaded()) lines += 2;
        boolean hasItem = msg.itemNbt() != null && !msg.itemNbt().isEmpty();

        int contentH = linesContentH(displayLines, lineH) + (lines - displayLines.size()) * lineH;
        if (hasItem) contentH += 18 - lineH; // item line is taller than normal line

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

        if (animating) {
            guiGraphics.pose().pushPose();
            if (slideP >= 0) guiGraphics.pose().translate(0, (1 - slideP) * 12, 0);
            if (popP >= 0) {
                float s = 0.85f + 0.15f * popP;
                float cx = bubbleX + bubbleW / 2f, cy = bubbleY + bubbleH / 2f;
                guiGraphics.pose().translate(cx, cy, 0);
                guiGraphics.pose().scale(s, s, 1);
                guiGraphics.pose().translate(-cx, -cy, 0);
            }
        }
        boolean fading = fadeP >= 0;
        if (fading) {
            float alpha = 0.15f + 0.85f * fadeP;
            guiGraphics.setColor(1f, 1f, 1f, alpha);
        }

        int bgColor;
        if (isCommand) {
            bgColor = msg.isInput() ? Theme.cmdBubbleOwnBg() : Theme.cmdBubbleOtherBg();
        } else if (msg.isOwn()) {
            bgColor = Theme.bubbleOwnFallback();
        } else {
            bgColor = Theme.bubbleOtherFallback();
        }
        boolean gradient = !isCommand && Theme.bubbleGradientEnabled();
        if (gradient) {
            Ui.fillBubbleGradient(guiGraphics, bubbleX, bubbleY, bubbleW, bubbleH, Theme.bubbleCornerRadius(),
                    Theme.bubbleGradientTop(), Theme.bubbleGradientBottom());
        } else {
            Ui.fillBubbleRect(guiGraphics, bubbleX, bubbleY, bubbleW, bubbleH,
                    Theme.bubbleCornerRadius(), bgColor);
        }

        if (dupLabel != null) {
            int dupColor = 0xFFFFAA00;
            int dupX, dupY = bubbleY + (bubbleH - lineH) / 2;
            if (msg.isOwn()) {
                dupX = bubbleX - dupW;
            } else {
                dupX = bubbleX + bubbleW + 2;
            }
            if (dupX >= chatLeft() + 4) {
                guiGraphics.drawString(mc.font, dupLabel, dupX, dupY, dupColor, false);
            }
        }

        if (showAvatar && !isCommand) {
            UUID senderUuid = msg.senderUuid();
            if (senderUuid != null) {
                int avX = msg.isOwn() ? areaRight - AVATAR_SIZE : areaLeft;
                drawPlayerFace(guiGraphics, senderUuid, avX, bubbleY + 4, AVATAR_SIZE, bgColor);
            }
        }

        int textX = bubbleX + BUBBLE_HPAD;
        int textY = bubbleY + BUBBLE_VPAD;

        if (msg.replyContent() != null) {
            String replyPrefix = "↑ " + msg.replySender() + ": ";
            String rawReply = replyPrefix + quoteTextForDisplay(msg.replyContent());
            String truncated = mc.font.plainSubstrByWidth(rawReply, bubbleW - BUBBLE_HPAD * 2);
            Component replyComponent = EmojiRegistry.toComponent(truncated);
            int emojiOff = EmojiRegistry.containsPua(replyComponent) ? EmojiRegistry.EMOJI_Y_OFFSET : 0;
            int replyQuoteW = mc.font.width(truncated);
            int quoteY = textY - 2;
            if (CustomEmojiRegistry.containsToken(replyComponent)) {
                CustomEmojiRegistry.renderLine(guiGraphics, replyComponent, textX, quoteY + emojiOff, Theme.accent(), false);
            } else {
                guiGraphics.drawString(mc.font, replyComponent, textX, quoteY + emojiOff, Theme.accent(), false);
            }
            synchronized (replyQuoteHitBoxes) {
                replyQuoteHitBoxes.add(new ReplyQuoteHit(textX, quoteY, replyQuoteW, lineH, msg.replySender(), msg.replyContent()));
            }
            textY += lineH;
        }

        boolean itemRendered = false;
        if (hasItem) {
            ItemStack itemStack = msg.parsedItem();
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
                } else {
                    guiGraphics.drawString(mc.font, Component.translatable("chatsphere.voice.received"),
                            vmX, vmY, Theme.textDim(), false);
                }
            } else {
                guiGraphics.enableScissor(bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH);
                int ly = textY;
                for (int li = 0; li < displayLines.size(); li++) {
                    Component line = displayLines.get(li);
                    if (line.getString().isEmpty()) {
                        ly += lineH;
                        continue;
                    }
                    int emojiH = CustomEmojiRegistry.lineHeightFor(line.getString());
                    int lh = emojiH > 0 ? Math.max(lineH, emojiH + 2) : lineH;
                    if (CustomEmojiRegistry.containsToken(line)) {
                        CustomEmojiRegistry.renderLine(guiGraphics, line, textX, ly, Theme.bubbleTextOn(bgColor), false);
                    } else {
                        guiGraphics.drawString(mc.font, line, textX, ly, Theme.bubbleTextOn(bgColor), false);
                    }
                    synchronized (cmdHitBoxes) { cmdHitBoxes.add(new CommandHit(textX, ly, textAreaW, lineH, line)); }
                    ly += lh;
                }
                guiGraphics.disableScissor();
            }
        } else {
            int textColor = Theme.bubbleTextOn(bgColor);
            guiGraphics.drawString(mc.font, infoLine, textX, textY, Theme.bubbleInfoLine(), false);
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
                } else {
                    guiGraphics.drawString(mc.font, Component.translatable("chatsphere.voice.received"),
                            textX, textY, Theme.textDim(), false);
                }
            } else if (!itemRendered || !ITEM_REF_PATTERN.matcher(contentText.getString()).matches()) {
                int emojiOff = EmojiRegistry.containsPua(contentText) ? EmojiRegistry.EMOJI_Y_OFFSET : 0;
                int ly = textY + emojiOff;
                for (int li = 0; li < displayLines.size(); li++) {
                    Component line = displayLines.get(li);
                    if (line.getString().isEmpty()) {
                        ly += lineH;
                        continue;
                    }
                    int emojiH = CustomEmojiRegistry.lineHeightFor(line.getString());
                    int lh = emojiH > 0 ? Math.max(lineH, emojiH + 2) : lineH;
                    if (CustomEmojiRegistry.containsToken(line)) {
                        CustomEmojiRegistry.renderLine(guiGraphics, line, textX, ly, textColor, false);
                    } else {
                        guiGraphics.drawString(mc.font, line, textX, ly, textColor, false);
                    }
                    if (hasClickable(line)) {
                        synchronized (richTextHitBoxes) {
                            richTextHitBoxes.add(new RichTextHit(textX, ly,
                                    mc.font.width(line), lineH, line));
                        }
                    }
                    ly += lh;
                }
            }
        }

        if (fading) guiGraphics.setColor(1f, 1f, 1f, 1f);
        if (animating) guiGraphics.pose().popPose();
        return bubbleH + 2;
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

    /** Height of one rendered message (no drawing), mirroring renderMessageRow/Bubble layout math. */
    private int measureRowHeight(ChatMessageData msg, ChatMessageData prev, int areaLeft, int areaRight, boolean stream) {
        Minecraft mc = Minecraft.getInstance();
        int lineH = mc.font.lineHeight + 1;
        boolean merge = prev != null
                && prev.senderUuid() != null && msg.senderUuid() != null
                && prev.senderUuid().equals(msg.senderUuid())
                && Math.abs(msg.timestamp() - prev.timestamp()) < 5L * 60L * 1000L;
        if (stream) {
            int dupW = msg.duplicateCount() > 1 ? mc.font.width("x" + msg.duplicateCount()) : 0;
            int textX = areaLeft + rowAvatarCol();
            int textAreaW = Math.max(120, areaRight - textX - 4 - (merge && dupW > 0 ? dupW + 6 : 0));
            Component contentText = msg.renderedContent();
            boolean isVoice = msg.content().getString().startsWith("VoiceMessage#")
                    && ModVoiceMessagesIntegration.isVoiceMessagesLoaded();
            List<Component> displayLines = getWrappedLines(mc, msg, contentText, false, textAreaW);
            int contentH = linesContentH(displayLines, lineH);
            if (msg.replyContent() != null) contentH += lineH;
            if (msg.itemNbt() != null && !msg.itemNbt().isEmpty()) contentH += 18;
            if (isVoice) contentH = Math.max(contentH, 20);
            boolean pureEmoji = displayLines.size() == 1
                    && !displayLines.get(0).getString().isEmpty()
                    && EmojiRegistry.isEmojiOnly(displayLines.get(0).getString())
                    && msg.replyContent() == null
                    && (msg.itemNbt() == null || msg.itemNbt().isEmpty())
                    && !isVoice;
            if (pureEmoji) contentH = 26;
            int headerH = merge ? 0 : lineH;
            int rowH = Math.max(headerH + contentH, (merge ? 0 : rowAvatarSize()) + 4);
            return rowH + 2;
        }
        boolean isCommand = msg.conversationType() == ChatMessageData.ConversationType.COMMAND;
        boolean showName = ModClientConfig.CONFIG.showSenderName.get();
        boolean showTime = ModClientConfig.CONFIG.showTimestamp.get();
        boolean showAvatar = ModClientConfig.CONFIG.showAvatar.get();
        Component contentText;
        MutableComponent infoLine = Component.literal("");
        if (isCommand) {
            contentText = msg.senderName().copy();
        } else {
            contentText = msg.renderedContent();
            if (showName) {
                infoLine.append(msg.senderName().copy().withStyle(ChatFormatting.AQUA));
            }
        }
        if (contentText.getString().startsWith("VoiceMessage#") && !ModVoiceMessagesIntegration.isVoiceMessagesLoaded()) {
            contentText = Component.translatable("chatsphere.voice.received").withStyle(ChatFormatting.LIGHT_PURPLE);
        }
        if (showTime && !isCommand) {
            String ts = ChatHistoryManager.formatTimestampSmart(msg.timestamp());
            if (showName) infoLine.append("  ");
            infoLine.append(Component.literal(ts).withStyle(ChatFormatting.GRAY));
        }
        int replyW = msg.replyContent() != null ? mc.font.width(" \u2191 " + msg.replySender()) : 0;
        int maxLineWidth = mc.font.width(infoLine);
        if (isCommand) {
            int prefixWidth = mc.font.width(msg.isInput() ? "> " : "\u2192 ");
            String raw = contentText.getString();
            if (raw.contains("\n")) {
                int maxW = 0;
                for (String l : raw.split("\n", -1))
                    maxW = Math.max(maxW, mc.font.width(l));
                maxLineWidth = Math.max(maxLineWidth, maxW + prefixWidth);
            } else {
                maxLineWidth = Math.max(maxLineWidth, mc.font.width(contentText) + prefixWidth);
            }
        } else {
            maxLineWidth = Math.max(maxLineWidth, mc.font.width(contentText));
        }
        int lineHb = mc.font.lineHeight + Theme.messageLineSpacing();
        int avReserve = (showAvatar && !isCommand) ? AVATAR_SIZE + 4 : 0;
        int bubbleW = Math.min(maxLineWidth + replyW + BUBBLE_HPAD * 2, areaRight - areaLeft - avReserve - 30);
        int textAreaW = Math.max(bubbleW - BUBBLE_HPAD * 2, 40);
        List<Component> displayLines = getWrappedLines(mc, msg, contentText, isCommand, textAreaW);
        int lines = displayLines.size() + (isCommand ? 0 : 1);
        if (msg.replyContent() != null) lines++;
        if (!isCommand && msg.content().getString().startsWith("VoiceMessage#") && ModVoiceMessagesIntegration.isVoiceMessagesLoaded()) lines += 2;
        boolean hasItem = msg.itemNbt() != null && !msg.itemNbt().isEmpty();
        int contentH = linesContentH(displayLines, lineHb) + (lines - displayLines.size()) * lineHb;
        if (hasItem) contentH += 18 - lineHb;
        int bubbleH = contentH + BUBBLE_VPAD * 2 + 1;
        return bubbleH + 2;
    }

    /** Largest scroll offset that still fills the chat area with the oldest messages. */
    private int maxScrollOffset() {
        int limit = ModClientConfig.CONFIG.scrollHistoryLimit.get();
        ChatHistoryManager history = ChatHistoryManager.getInstance();
        List<ChatMessageData> messages = history.getMessagesByConversation(currentConversation);
        int total = messages.size();
        int capped = Math.min(total, limit);
        if (capped <= 1) return 0;
        boolean stream = Theme.stream();
        int sepInterval = ModClientConfig.CONFIG.timeSeparatorMinutes.get();
        int unreadCount = stream ? history.getUnreadCount(currentConversation) : 0;
        int areaTop = chatAreaTop();
        int areaBottom = height - 14 - TOOLBAR_HEIGHT - MESSAGE_BOTTOM_PAD
                - (replyBar.targetIndex >= 0 ? ReplyBarWidget.BAR_HEIGHT + 2 : 0);
        int areaLeft = chatLeft() + 4;
        int areaRight = width - 4;
        int space = areaBottom - areaTop;
        if (space <= 0) return 0;
        int accumulated = 0;
        int idx = 0;
        while (idx < capped) {
            ChatMessageData msg = messages.get(idx);
            if (stream && unreadCount > 0 && idx == total - unreadCount) {
                accumulated += 18;
                if (accumulated >= space) break;
            }
            if (sepInterval > 0 && !stream) {
                String key = ChatHistoryManager.timeSeparatorKey(msg.timestamp(), sepInterval);
                if (idx + 1 < total) {
                    String nextKey = ChatHistoryManager.timeSeparatorKey(messages.get(idx + 1).timestamp(), sepInterval);
                    if (!key.equals(nextKey)) {
                        accumulated += 16;
                        if (accumulated >= space) break;
                    }
                }
            } else if (stream && idx + 1 < total
                    && !ChatHistoryManager.isSameDay(messages.get(idx + 1).timestamp(), msg.timestamp())) {
                accumulated += 26;
                if (accumulated >= space) break;
            }
            int h = measureRowHeight(msg, idx > 0 ? messages.get(idx - 1) : null, areaLeft, areaRight, stream);
            accumulated += h + 2;
            idx++;
            if (accumulated >= space) break;
        }
        return Math.max(0, capped - idx);
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
                        replyContent, replySender, itemNbt, false, "")));
    }

    private void sendChannelPacket(ServerboundChannelActionPayload.Action action, String channelId, UUID ownerUuid) {
        if (this.minecraft == null || this.minecraft.getConnection() == null) return;
        var conn = this.minecraft.getConnection().getConnection();
        conn.send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                new ServerboundChannelActionPayload(action, channelId, ownerUuid,
                        true, "", "", List.<String>of(), List.<String>of(), List.<String>of(), "", true, "", "", "", false, "")));
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

    private List<Component> getWrappedLines(Minecraft mc, ChatMessageData msg, Component content,
                                            boolean isCommand, int width) {
        WrappedLines cached = wrappedLinesCache.get(msg);
        if (cached == null || cached.width != width || cached.command != isCommand) {
            cached = new WrappedLines(width, isCommand, computeWrappedLines(mc, msg, content, isCommand, width));
            wrappedLinesCache.put(msg, cached);
        }
        return cached.lines;
    }

    private List<Component> computeWrappedLines(Minecraft mc, ChatMessageData msg, Component content,
                                                boolean isCommand, int width) {
        String raw = content.getString();
        boolean multiLine = raw.contains("\n");
        if (!isCommand) {
            if (multiLine) {
                if (allLinesFit(mc, raw, width)) return splitCommandLines(content);
            } else if (mc.font.width(raw) <= width) {
                return List.of(content);
            }
            return toComponentList(mc.font.split(content, width));
        }
        Component prefix = msg.isInput()
                ? Component.literal("> ").withStyle(ChatFormatting.GREEN)
                : Component.literal("\u2192 ").withStyle(ChatFormatting.GRAY);
        if (multiLine) {
            if (allLinesFit(mc, raw, width)) {
                List<Component> out = new ArrayList<>();
                for (Component line : splitCommandLines(content)) out.add(prefix.copy().append(line));
                return out;
            }
            return toComponentList(mc.font.split(prefix.copy().append(content), width));
        }
        if (mc.font.width(raw) + mc.font.width(prefix) <= width) {
            return List.of(prefix.copy().append(content));
        }
        return toComponentList(mc.font.split(prefix.copy().append(content), width));
    }

    private boolean allLinesFit(Minecraft mc, String raw, int width) {
        for (String line : raw.split("\n", -1)) {
            if (mc.font.width(line) > width) return false;
        }
        return true;
    }

    private static List<Component> toComponentList(List<FormattedCharSequence> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (FormattedCharSequence line : lines) out.add(toComponent(line));
        return out;
    }

    private static Component toComponent(FormattedCharSequence sequence) {
        MutableComponent comp = Component.literal("");
        sequence.accept((index, style, codePoint) -> {
            comp.append(Component.literal(new String(Character.toChars(codePoint))).withStyle(style));
            return true;
        });
        return comp;
    }

    private static class SidebarEntry {
        final String conversationId;
        final Component displayName;
        final ChatMessageData.ConversationType type;
        final boolean isHeader;
        final UUID targetUuid;
        final int indent;

        SidebarEntry(String conversationId, Component displayName,
                     ChatMessageData.ConversationType type, boolean isHeader, UUID targetUuid) {
            this(conversationId, displayName, type, isHeader, targetUuid, 0);
        }

        SidebarEntry(String conversationId, Component displayName,
                     ChatMessageData.ConversationType type, boolean isHeader, UUID targetUuid, int indent) {
            this.conversationId = conversationId;
            this.displayName = displayName;
            this.type = type;
            this.isHeader = isHeader;
            this.targetUuid = targetUuid;
            this.indent = indent;
        }
    }
}
