## 2.1.1

### Added
- Corner style system — square / pixel-rounded / original, applied to all UI components in real time
- Dedicated "Corner Style" config category with three selectable cards, each showing a live miniature preview that scales with window size; click to apply immediately
- Background blur toggle for config and list screens (`backgroundBlur`)
- Pixel-rounded corners via per-pixel coverage rendering (1px transition)
- Pending server config queue — toggling server options (strong hint, anti-spam, enable channels, etc.) from the mod list menu no longer requires a loaded world; changes are staged and applied on joining a world (singleplayer: written to config file; multiplayer: sent via config update packet)

### Changed
- Config screen reorganized into 6 categories; advanced settings grouped into collapsible sections
- Original style preserves the UI appearance of versions below this one, kept as a retro option
- Toggle widgets keep their rounded slider look in all corner styles
- Config/list screens use `screenBg` layered over the vanilla blur
- Popup outlines neutralized (no longer purple solid line)
- Floating text on the chat screen (title, time separators, empty search result) and chat/search inputs locked to light colors so they stay readable in light mode
- Config page option rows fully clipped to the content area — scrolling can no longer draw rows above the category bar or below the action buttons

### Fixed
- Crash when toggling server-side options from the mod list menu before joining a world
- Option labels/buttons rendering outside the clipped content area

## 2.1.0

### Added
- VoiceMessages mod compatibility — offline delivery, local cache, chat bubble playback, custom packet routing
- Command console persistence — input/output stored on server, restored on reconnect
- HUD unread count badge on chat icon (configurable toggle)
- Multi-line command output rendering with per-line click/hover support

### Changed
- Color and formatting now preserved across reconnects (JSON serialization)
- System messages use correct UUID to fix green `"> "` prefix on reconnect
- Voice messages routed through ChatSphere custom packets (VM used as library only)
- Version bumped to 2.1.0

### Fixed
- Color codes lost on reconnect
- Multi-line command output shown as separate bubbles instead of one message
- Text overflow outside bubble in narrow windows
- Banned words input extending past screen edge
- Missing PV addon localization

## 2.0.2

### Added
- Item NBT sharing — pick an item from inventory via the new item picker panel (item_chest icon); item NBT serialized and sent with chat messages; displayed as item icon + name in chat bubbles (ModChatScreen) and HUD overlay (ChatHudOverlay)
- ItemSerialization utility for NBT-based item serialization/deserialization
- ItemPickerPanel widget for inventory item selection

### Changed
- ChatMessageData, StoredMessage records now carry an optional `itemNbt` field through the entire pipeline: client send → server channel action → bridge relay → message sync → client display
- Duplicate message detection now compares `itemNbt` to avoid merging different items
- HUD bubble rendering adapts height and layout when an item is present
- `addMessage()` skips reply bar rendering when `replyContent`/`replySender` is empty
- ModChatScreen preserves `currentConversation` for PRIVATE conversations on window resize
- Input placeholder uses dynamic `[slotNumber]` format when an item is selected
- Version bumped to 2.0.2

### Fixed
- Empty replyContent no longer creates a visible reply bar in chat bubbles (fix applied during 2.0.2 development)
- Window resize no longer resets current conversation from PRIVATE to default channel (fix applied during 2.0.2 development)
- Sender now sees their own item-share message immediately (pendingItemNbt captured before sendChannelChatPacket — fix applied during 2.0.2 development)
- Missing `bridge_info` payload registration causing handshake failure on proxy setups (fix applied during 2.0.2 development)
- ServerboundConfigUpdatePayload registration accidentally dropped during development, causing ClassCastException on config save (fix applied during 2.0.2 development)

### Notes
All fixes listed above were identified and resolved during the 2.0.2 development cycle, not inherited from 2.0.1.

## 2.0.1

### Added
- Right-click context menu: Block Messages from a player
- BlockListScreen — manage blocked players (click to unblock), blur background
- Online status indicator — green/gray dot in private chat header, sidebar, and member list
- Banned words library — server-side regex filtering in SEND_CHAT handler
- Skin cache refresh button in settings UI
- Context menu type system (CTX_BUBBLE) for future extensibility
- Block icon SVG resource

### Changed
- Context menu: now only activates on bubble hit area, third option "Block Messages" added
- PlayerSkinCache: network I/O moved to background thread; `pendingFetches` removal after successful cache
- `drawContextMenu`/`handleContextMenuClick` use `contextType` instead of raw `contextMsgIndex`
- Skin config screen: "Refresh Skin Cache" button added
- Duplicate message "xN" label no longer shifts own message bubble position
- Translation: blocklist empty state text corrected

### Fixed
- Channel config not persisting after `applyServerChannels()` — missing `loaded = true`
- Reply/quote data not transmitted over network — missing fields in 3 payload classes
- "N new messages" bar scrolled to top instead of bottom — reversed scroll direction
- Skin race condition causing duplicate fetches — `pendingFetches.remove()` before cache populated
- Server crash on startup — `ModNetworkSetup` reading client config on server side
- Duplicate-detected own message bubbles shifted left — `dupW` wrongly subtracted from `bubbleX`

---

## 2.0.0

### Added
- Full IM-style chat GUI with left sidebar (channels/DMs) and right sidebar (online members)
- Emoji picker — 349 twemoji, category tabs, search, `:shortcode:` autocomplete
- Voice chat — Simple Voice Chat and PlasmoVoice integration
- Channel explore/discovery screen
- Chat search, quote reply, right-click context menu
- Member management: admins, mute, invite, transfer ownership
- Server configuration GUI
- Quick phrases panel, @mention autocomplete
- NCR compatibility settings tab

### Changed
- Complete UI rewrite — tabbed settings, bubble customization, per-type sound toggles
- Network protocol updated (requires matching server version)

### Fixed
- Various chat history and synchronization issues
