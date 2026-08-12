## 2.2.1

### Added
- Multi-platform build (Architectury): **Fabric** support alongside NeoForge for Minecraft 1.21.1, plus a **Fabric 1.20.1** port
- Bare URLs in messages are auto-linkified and clickable, subject to the URL whitelist config
- Quoting an item-show message now displays the item name instead of the raw slot placeholder
- Server config: `voiceStorageMax` (voice retention cap, 16–10000)
- Voice history re-delivery: players who join receive recent voice messages from their conversations

### Changed
- Voice playback resolution is event-driven (audio arrival registers the playback directly) with a failure short-circuit — no per-frame reflection in the render loop
- Voice uploads deduplicated server-side: multiple recipients uploading the same message are recorded and relayed once
- Config screens debounce per-keystroke saves / config-update packets
- Bundled presets carry a version marker; stale or corrupt presets are reinstalled from the jar automatically
- Date formatters precompiled (removes per-frame allocations)
- Hardcoded UI strings moved to lang keys

### Fixed
- Server security: voice packets validated before history writes, private-channel join requires membership/invite, forged command-message UUIDs dropped, config-update ints range-checked, item NBT capped on the server and parsed with a size-limited `NbtAccounter` on the client
- Corrupted config JSON no longer crashes the game (backed up as `.corrupt`, defaults used)
- Server data files (channels/messages/voice index) written atomically (tmp + atomic move)
- Lock-order inversion between message history and channel state removed
- Theme `preset-stream.ctheme` had invalid 10-digit hex values in its light block, rejecting the whole theme
- Malformed voice frames rejected (frame length validated, no OOM)

## 2.2.0

### Added
- Stream style (4th corner style, icon-rail layout): 60px icon rail with player avatar / channel groups / console / explore / join / create, flat message rows with row avatars, avatar right-click menu (@mention and Private Message), rail hover animations, unread indicators, date group headers, smart timestamps; compact layout automatically shrinks rail/icons/avatars/sidebar on small windows
- Sub-channels: create, rename, delete, drag-to-reorder and cascade delete; inherit members/admins/mute state from parent; indented in the sidebar; old save files load automatically
- Rich text markup in chat messages (toggleable via `renderRichText`): `[b]`, `[i]`, `[u]`, `[s]`, `[o]`, `[color]`, `[gradient]`, `[code]`, `[url]` with escaping and URL whitelist; `bubbleFadeIn` animation
- Custom theme gallery with real-color preview cards and a load-error banner showing the failing file; parse failures keep the previous theme
- Voice cache size limit config (`voiceCacheMaxMB`, default 512 MB) with oldest-first eviction
- Chat history now respects `maxChatHistory` on the server (was hardcoded to 200); scroll range matches (`scrollHistoryLimit` up to 1000)

### Changed
- Corner style cards show a live miniature preview per style (square / pixel / rounded / stream)
- Long messages wrap to fit the bubble instead of being truncated (incl. command console)
- Performance: per-conversation message list caching, message index lookups no longer O(n²) per frame, theme gallery and emoji/component rendering allocation reduced, reply bar component cached
- Data reliability: corrupt save entries are skipped individually, fully corrupt files are backed up as `.corrupt`; voice cache index written atomically; server channels/messages parse into temp structures; server instances and voice cleaner threads released on ServerStopping

### Fixed
- Server security: payload decoders enforce size/count limits (no more OOM DoS), identity/authorization always uses the server-verified player UUID (packet-supplied UUIDs ignored), channel chat and login history are filtered by channel membership, sub-channel voice messages reach members (were silently dropped)
- Stream rows now show the anti-spam duplicate label (`xN`) without overlapping the timestamp
- History visible after reconnect matches the configured limit; long VoiceMessages (>60 s) are no longer dropped by the audio size cap
- Crash when a system message with an item hover reached the command console

## 2.1.2

### Added
- Custom theme system: `.ctheme` files in `config/chatsphere/themes/` — `dark`/`light` color blocks, `styles` numeric block, `animations` block; white-listed keys, strict parsing, size/property limits, rejected themes keep the previous one
- All built-in colors overridable per dark/light mode; new numerics: bubble gradient, sidebar width, message line spacing, avatar radius, blur intensity
- Three bundled style presets as editable `.ctheme` files (auto-installed on first run); `uiCornerStyle` editable from the config screen
- `colorSeed` tonal derivation: unset colors derive harmonized tints from one seed; explicit colors always win
- Custom Themes gallery screen (Config -> Custom Theme -> Browse): real-color preview cards, enable toggle
- Animations now render: `messageSlideIn`, `bubblePopIn`, `notificationPulse`, with configurable duration and easing
- Config screen linkage — bubble corner radius, bubble colors and the corner style cards write through to the active theme file (presets and custom themes included)
- Command console unread badge — the sidebar console entry shows the same digit count as the HUD icon for new command output

### Changed
- Background blur switched from the vanilla post-effect shader to a self-drawn GL blit blur (no global post slot, Iris-friendly)
- Voice playback cache bounded (LRU, 256 entries)
- Parsed item stacks cached — the HUD no longer re-deserializes item NBT per frame
- Lying in bed opens the mod's chat screen instead of the vanilla sleeping chat bar
- Chat background follows the corner style (solid/blurred variants; original style unchanged)
- Floating text and inputs follow the theme again instead of being locked light; command bubbles keep their dark terminal look in light mode
- HUD bubbles now include new command console messages (`>` / `→` prefixed)

### Fixed
- Chat data never falls back to the config folder when the storage context is unresolved
- Emoji panel bottom hint truncated and clipped instead of overflowing the panel

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
- Toggle slider shape follows the selected corner style (square in square mode, rounded in pixel-rounded mode)
- Config/list screens use `screenBg` layered over the vanilla blur
- Popup outlines neutralized (no longer purple solid line)
- Floating text on the chat screen (title, time separators, empty search result) and chat/search inputs locked to light colors so they stay readable in light mode
- Config page option rows fully clipped to the content area — scrolling can no longer draw rows above the category bar or below the action buttons
- Pixel-rounded corners use a cached corner alpha table — per-pixel distance math replaced with table lookup, fully transparent pixels skipped
- Chat data file saved atomically (temp file + atomic move, cross-platform fallback) — interrupted saves can no longer corrupt data, recovery on next launch

### Fixed
- Crash when toggling server-side options from the mod list menu before joining a world
- Option labels/buttons rendering outside the clipped content area
- Voice message parsing crash — invalid/non-UUID message IDs no longer crash the chat screen (PR #1)
- File stream leak in backup pruning — `Files.list` now closed properly (PR #1)
- Channel config screen registered a duplicate config change listener on reopen (PR #1)
- Multi-codepoint emoji (ZWJ sequences etc.) now parse correctly — parser advances by full Unicode sequence, not single code point (PR #1)
- Corner style picker cards overflowed the content area on small windows — card height/preview bounds fixed

### Credits
- Special thanks to [Spagles](https://github.com/Spagles) for contributing PR #1 — voice message parsing crash fix, file/listener leak fixes, and emoji rendering glitch fixes

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
