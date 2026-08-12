![Views](https://komarev.com/ghpvc/?username=mcraftbbs&repo=ChatSphere&label=Views&color=brightgreen)

# ChatSphere

![ChatSphere](https://cdn.modrinth.com/data/cached_images/8cc6c14cc43b82f8053acb11d80eed267154bdab_0.webp)

A modern instant-messaging chat mod for Minecraft **Fabric (1.20.1)**. Replaces the vanilla chat with channels, private messaging, voice rooms, emoji, and a full GUI.

> **License:** GNU LGPLv3
> **Mod ID:** `chatsphere`
> **Version:** 2.2.1-1.20.1

---

## Table of Contents

- [Features](#features)
- [Installation](#installation)
- [Key Bindings](#key-bindings)
- [Chat Overview](#chat-overview)
- [Channel System](#channel-system)
- [Private Messaging](#private-messaging)
- [Command Console](#command-console)
- [Emoji System](#emoji-system)
- [Voice Chat Integration](#voice-chat-integration)
- [Client Settings](#client-settings)
- [Server Configuration](#server-configuration)
- [Commands](#commands)
- [Storage](#storage)
- [Compatibility](#compatibility)
- [Building from Source](#building-from-source)
- [Network Protocol](#network-protocol)

---

## Features

| Category | Details |
|----------|---------|
| **Full GUI Chat** | Replaces vanilla chat with an IM-style interface — left sidebar (channels/DMs), right sidebar (online members), scrollable message area, input bar |
| **4 Corner Styles** | Square / pixel-rounded / rounded / **Stream** (icon rail + flat rows); each with a live preview card in the config screen; Stream auto-compacts rail, icons, avatars and sidebar on small windows |
| **Custom Themes** | `.ctheme` files in `config/chatsphere/themes/` — full dark/light color overrides, numeric styles, animations; theme gallery with real-color preview cards and load error hints; 4 bundled presets |
| **Channel System** | Public/private channels with invite codes, configurable names, descriptions, and explore visibility |
| **Sub-Channels** | Create/rename/delete/drag-reorder sub-channels; inherit parent members/admins/mute state; cascade delete; parent main-chat toggle with default sub-channel routing |
| **Private Messaging** | Click any player name (or avatar → Private Message) to start a DM; `/msg` and `/tell` support; grouped under "Private" in sidebar |
| **Command Console** | Built-in command console with history recall (up/down arrows) |
| **Emoji Picker** | 349 emoji from twemoji, rendered via custom font (PUA glyphs); category tabs, search, `:shortcode:` autocomplete |
| **Rich Text** | `[b]`, `[color]`, `[gradient]`, `[url]`, `[code]` markup in messages (toggleable); **bare URLs are auto-linkified** (clickable) with a whitelist filter |
| **Voice Chat** | Dual integration — supports **Simple Voice Chat** (ISOLATED groups) and **PlasmoVoice** (broadcast source lines); voice messages replay with local cache (size-limit configurable); without the VoiceMessages mod, voice messages show as a placeholder |
| **Chat Search** | Search within current conversation with result count and jump navigation |
| **Reply System** | Quote-reply to any message; referenced in input bar |
| **Context Menu** | Right-click → Copy, Quote Reply, Block; avatar menu → @mention or Private Message |
| **Mention System** | `@username` autocomplete popup with online player filtering |
| **Quick Phrases** | User-defined message shortcuts; add/remove via panel |
| **Anti-Spam** | Duplicate message collapsing (server-side toggle) |
| **Chat Bubbles (HUD)** | Heads-up overlay showing recent messages with avatar, name, timestamp, and configurable colors/radius |
| **Message History** | Persistent history per conversation (server stores up to `maxChatHistory`, default 200, max 1000); server backups every 30 min |
| **Explore / Discovery** | Browse public channels with member/online counts |
| **No Chat Reports Compat** | Displays NCR security status in the UI |
| **Dark Theme** | Full dark theme with configurable bubble colors |
| **Notifications** | Per-type sound toggles (mention, whisper, system, public), icon flash, screen popup |
| **Mixin: Vanilla Chat Cancel** | Completely disables vanilla chat rendering — all messages go through ChatSphere |

---

## Installation

1. Install **Fabric Loader 0.16.x** (+ Fabric API) for Minecraft 1.20.1
2. Drop the ChatSphere `.jar` into the `mods/` folder (client & server)
3. (Optional) Install **Simple Voice Chat** and/or **PlasmoVoice** for voice room features

Requires mod on **both server and client** for full functionality. Without server mod, client falls back to local-only storage mode.

---

## Key Bindings

| Key | Default | Action |
|-----|---------|--------|
| `T` | `T` | Opens the main chat interface |
| `/` | `/` | Opens chat in command mode |
| `F7` | `F7` | Opens client settings menu |

---

## Chat Overview

The main chat screen (`ModChatScreen`) is divided into:

- **Left sidebar** — conversation list: channels grouped by category, DMs under "Private"; shows avatar, name, unread mention count; quick-switch on click; header buttons for explore (search icon), join by code (`→`), create (`+`) 
- **Stream icon rail** (Stream style only) — 60px rail with player avatar (toggles private list), channel group icons, console, explore/join/create; hover animations, unread dots, tooltips
- **Message area** — scrollable history with timestamps, sender avatar/name, message content (emoji rendered), duplicate count badge, time/date separators; flat rows in Stream style
- **Right sidebar** — online member list for the active channel; shows skin avatar + name; includes voice room join/leave button
- **Input bar** — text field with emoji button, quick phrases toggle, reply preview; supports `:shortcode:` and `@mention` autocomplete
- **Search bar** — filter messages by keyword; up/down arrows to jump between matches

---

## Channel System

### Creating
- Click `+` next to "Channels" header, or type `#name` in input and press Enter
- Set display name, description, and public/private status on creation

### Joining
- Click `→` and enter the invite code
- Click the search icon to browse public channels via the **Explore** screen

### Management (gear icon ⚙)
- **General tab:** display name, description, public/private toggle, show in explore toggle, invite code (regen)
- **Sub-channels tab:** create, rename, delete and drag-to-reorder sub-channels; sub-channels inherit parent members/admins/mute state; parent main-chat toggle + default chat sub-channel redirect
- **Members tab:** member list with admin badges, online indicators; promote/demote admin, mute/unmute, kick, transfer ownership
- **Voice tab:** create/delete voice rooms; join/leave per room
- **Delete tab:** confirm delete (owner) or leave channel (non-owner)
- **Invite Players:** searchable player list; send/revoke invites

### Explore Screen
Browse public channels across the server: name, description, member count, online count, join button. Server operators can configure minimum members to appear.

---

## Private Messaging

- Click any player name in the right sidebar → opens a DM session
- Right-click a message avatar → **Private Message** (Stream style) → opens a DM
- `/msg <player> <message>` or `/tell <player> <message>` → auto-creates DM
- All DMs grouped under "Private" in left sidebar for quick access
- Private conversations are preserved across reconnects

---

## Command Console

- Click **"Commands" → "Console"** in the left sidebar
- Type commands with or without leading `/`
- Output appears in the console session
- Up/down arrow keys recall recent commands

---

## Emoji System

ChatSphere ships with **349 emoji** (351 total, 2 hidden) sourced from [twemoji](https://twemoji.twitter.com/).- **Picker:** Click the emoji button in the input bar → category tabs (Smileys, People, Animals, Food, Travel, Activities, Objects, Symbols, Flags) + search bar → click to insert
- **Shortcodes:** Type `:smile:` in the input → autocomplete popup appears (up to 12 candidates, keyboard navigable) → press Enter or click to insert
- **Rendering:** Emoji rendered via custom bitmap font with PUA codepoints; resolved at display time per-message
- **Font sheet:** Generated by `EmojiSheetGenerator` build tool (16×16 grid sprite sheet)

---

## Styles & Custom Themes

- **Corner styles** (config → Corner Style): square / pixel-rounded / rounded / Stream, each with a live miniature preview card
- **Custom themes**: `.ctheme` files in `config/chatsphere/themes/` (white-listed keys, strict parsing — invalid themes are rejected and keep the previous one). Browse and preview them in the Custom Themes gallery
- **Stream style** specifics: 60px icon rail, flat message rows, compact auto-layout on small windows, avatar right-click menu (@mention / Private Message)

---

## Voice Chat Integration

ChatSphere supports **two** voice chat mods simultaneously. No extra configuration needed — it auto-detects which mods are installed.

### Simple Voice Chat (SVC)

| Feature | Support |
|---------|:-------:|
| Plugin entry point | `ChatSphereSvcPlugin` (`@ForgeVoicechatPlugin`) |
| Group type | `ISOLATED` — per-channel voice groups |
| Join/Leave | `VoiceIntegration.joinSvcGroup()` / `leaveSvcGroup()` via reflection |
| API version | `voicechat-api:2.1.12` |

- Each channel room gets an isolated SVC group tied to the channel ID
- Players are moved into/out of the group on voice room join/leave
- No server-side addon required beyond the SVC mod itself

### PlasmoVoice (PV)

| Feature | Support |
|---------|:-------:|
| Addon entry point | `PlasmoRoomAddon` (`@Addon`, `AddonInitializer`) |
| Audio routing | Custom `ServerActivation` + `ServerSourceLine` → `ServerBroadcastSource` per player |
| Join/Leave | Reflective call via `VoiceIntegration.joinPlasmoBroadcast()` |
| API version | `server:2.1.13` |

- Creates a `chatsphere_room` activation and source line
- Each player in a voice room gets a broadcast source filtered to other room members
- Room membership tracked in `ConcurrentHashMap`; auto-cleanup on empty

### Dual Detection

`VoiceIntegration` detects both mods at startup:

```java
svcAvailable = ModList.get().isLoaded("voicechat");
plasmoAvailable = ModList.get().isLoaded("plasmovoice");
```

When a player joins/leaves a voice room, **both** integrations activate if the respective mod is present.

---

## Client Settings

Press `F7` or use **Mod Menu** to open client settings.

### General (UI)
| Option | Default | Description |
|--------|---------|-------------|
| Show Timestamp | `true` | Time next to each message |
| Show Sender Name | `true` | Sender name above message |
| Show Avatar | `true` | Player skin avatar in sidebar |
| Dark Theme | `true` | Dark mode UI |
| Show Strong Hints | `true` | Enhanced UI hints |
| Preserve Input | `true` | Keep typed text when reopening chat |
| Show Right Sidebar | — | Toggle online member list |
| Enable Channels | — | Disable to revert to vanilla chat |

### Bubbles
| Option | Default | Description |
|--------|---------|-------------|
| Own Bubble Color | `0x80000000` | Background color of own messages (ARGB) |
| Other Bubble Color | `0x80404040` | Background color of other messages (ARGB) |
| Corner Radius | `8` | Bubble corner radius in pixels |

### Behavior
| Option | Default | Description |
|--------|---------|-------------|
| Anti-Spam | `true` (server) | Collapse duplicate messages |
| Max Chat History | `200` (server) | Messages stored per conversation (max 1000) |
| Scroll History Limit | `200` | Max scrollable history lines (max 1000) |
| Rich Text Markup | `true` | Render `[b]`/`[color]`/`[gradient]` etc. markup |
| Preserve Input | `true` | Keep typed text when reopening chat |

### Voice Cache
| Option | Default | Description |
|--------|---------|-------------|
| Voice Cache | `true` | Cache voice messages for replay after restart |
| Max Age | `24` h | Cache entry expiry |
| Size Limit | `512` MB | Cache size cap (oldest entries evicted) |

### Sound Settings
| Option | Default | Description |
|--------|---------|-------------|
| Sound Enabled | `true` | Master toggle |
| @Mention Sound | `true` | Sound when mentioned |
| Private Message Sound | `true` | Sound on DM |
| System Message Sound | `true` | Sound on system message |
| Public Chat Sound | `false` | Sound on public channel messages |
| Icon Flash | `true` | Flash icon on new message |
| Screen Popup | `true` | Popup on new message |

### Avatars
| Option | Default | Description |
|--------|---------|-------------|
| Avatar Cache | `true` | Cache player skins to disk |
| Custom Skin API URL | Mojang API | Override skin resolution endpoint |

### NCR (No Chat Reports)
| Option | Default | Description |
|--------|---------|-------------|
| NCR Compat | `true` | Enable NCR compatibility features (shown when NCR is installed) |
| NCR Safety Status | — | Displays current NCR safety level (SECURE / INSECURE / SINGLEPLAYER / UNKNOWN) |
| Prevents Chat Reports | `true` | Advertise `preventsChatReports` in server status (server config, togglable from client UI) |

---

## Server Configuration

Server operators can configure via in-game **Server Config** screen or `config/chatsphere-server.toml`.

| Option | Default | Description |
|--------|---------|-------------|
| Anti-Spam | `true` | Collapse duplicate messages automatically |
| Enable Channels | `true` | Enable the channel system |
| Max Chat History | `200` | Max messages per conversation (max 1000) |
| Sync Default Channel | `true` | Auto-join new players to `#general` |
| Channel History Enabled | `true` | Persist message history (membership-filtered on sync) |
| Explore Enabled | `true` | Enable public channel discovery |
| Explore Min Members | `2` | Minimum members to appear in explore |
| Banned Words | — | Regex patterns; matching messages are rejected |
| Backup Interval | `30` min | Channel data backup frequency |
| Backup Keep | `10` | Max backup files to retain |
| Show Strong Hint | `true` | Hint about encryption status |
| Prevents Chat Reports | `true` | Advertises `preventsChatReports` in server status |

---

## Commands

| Command | Description |
|---------|-------------|
| `/chatsphere help` | Show help menu |
| `/chatsphere list` | List all available channels |
| `/chatsphere info <name>` | Show channel details |
| `#channelname` | Quick-switch to channel (in input field) |

---

## Storage

```
{gamedir}/ChatSphere/
├── client/
│   ├── singleplayer/<world-name>/
│   │   ├── chatsphere_data.json      # Per-world: channels, invites, mute list, voice rooms
│   │   └── (avatar cache)
│   └── multiplayer/<server-ip>/
│       ├── chatsphere_data.json      # Per-server: same structure
│       └── (avatar cache)
└── server/
    ├── channels.json                 # All channel data (server-authoritative)
    └── backups/
        └── channels_<timestamp>.json # Periodic backups
```

Client data is saved asynchronously on changes. Server data is auto-backed up every N minutes (configurable, default 30, max 10 backups).

---

## Compatibility

| Mod | Status | Details |
|-----|--------|---------|
| **Mod Menu** | ✅ | `IConfigScreenFactory` registered for client settings |
| **Simple Voice Chat** | ✅ | Auto-detected; creates isolated voice groups per channel |
| **PlasmoVoice** | ✅ | Auto-detected; voice rooms via broadcast source lines |
| **No Chat Reports** | ✅ | Shows NCR security status colored indicator |
| **Vanilla Chat** | 🔄 Replaced | `ChatComponentMixin` cancels all vanilla chat display |
| **Server Status** | 🔄 Patched | `ServerStatusSerializerMixin` injects `preventsChatReports:true` |

### Mixins

| Target | Purpose |
|--------|---------|
| `ChatComponent.addMessage(Component)` | Cancel vanilla chat rendering; route to ChatSphere history |
| `ServerStatus.getInstance()` | Inject `preventsChatReports: true` into server status JSON |

---

## Building from Source

```bash
./gradlew build
```

For the emoji sprite sheet (requires internet):
```bash
./gradlew runEmojiSheetGenerator
```
Downloads twemoji assets from CDN and generates `emoji.png` + `emoji.json` for the custom font provider.

---

## Network Protocol

Version: `"1.0"`. All payloads use the Fabric custom payload API (`CustomPayloadPacket`).

| Payload | ID | Direction | Purpose |
|---------|----|-----------|---------|
| `ClientboundChatPayload` | `chatsphere:chat` | S→C | Relay a new chat message |
| `ClientboundChannelSyncPayload` | `chatsphere:channel_sync` | S→C | Full channel list + player name map |
| `ClientboundMessageSyncPayload` | `chatsphere:message_sync` | S→C | Message history replay on login (membership-filtered) |
| `ClientboundPublicChannelListPayload` | `chatsphere:public_channel_list` | S→C | Discoverable channels for explore screen |
| `ClientboundPermissionResponsePayload` | `chatsphere:perm_response` | S→C | Boolean permission check response |
| `ClientboundChannelRenamedPayload` | `chatsphere:channel_renamed` | S→C | Sub-channel rename notification |
| `ClientboundVoicePacket` | `chatsphere:voice_s2c` | S→C | Voice message audio relay |
| `ClientboundBridgeInfoPayload` | `chatsphere:bridge_info` | S→C | Server capability/bridge info |
| `ServerboundChannelActionPayload` | `chatsphere:channel_action` | C→S | Channel CRUD (19 action types) |
| `ServerboundPermissionCheckPayload` | `chatsphere:perm_check` | C→S | Permission check request (OP level 2) |
| `ServerboundConfigUpdatePayload` | `chatsphere:config_update` | C→S | Runtime server config update (OP only) |
| `ServerboundVoicePacket` | `chatsphere:voice_c2s` | C→S | Voice message upload |
| `ServerboundCommandMessagePayload` | `chatsphere:command_message` | C→S | Command console message sync |

All decoders enforce size/count limits; identity is always taken from the verified player connection.

### Channel Actions

`ServerboundChannelActionPayload.Action` enum:

`CREATE`, `UPDATE_CONFIG`, `JOIN_MEMBER`, `JOIN_BY_CODE`, `SEND_CHAT`, `REMOVE_CHANNEL`, `TOGGLE_MUTE`, `TOGGLE_ADMIN`, `TOGGLE_INVITE`, `KICK_MEMBER`, `LEAVE_CHANNEL`, `LIST_PUBLIC`, `CREATE_VOICE_ROOM`, `DELETE_VOICE_ROOM`, `JOIN_VOICE_ROOM`, `LEAVE_VOICE_ROOM`, `RENAME_SUBCHANNEL`, `REORDER_CHANNEL`, `MOVE_CHANNEL`

---

## Screens Reference

| Screen | Access | Purpose |
|--------|--------|---------|
| **ModChatScreen** | `T` key | Main chat UI |
| **ConfigScreen** | `F7` key | Client settings (tabs: UI, Bubbles, Skin, NCR, Behavior, Sound) |
| **ServerConfigScreen** | ConfigScreen → Server | Server operator settings |
| **ChannelConfigScreen** | Gear icon ⚙ on channel | Per-channel settings (General, Members, Voice, Delete) |
| **CreateChannelScreen** | `+` button | Create new channel |
| **JoinChannelScreen** | `→` button | Join by invite code |
| **ExploreServersScreen** | Search icon | Browse public channels |
| **InvitePlayerScreen** | ChannelConfig → Invite | Send/revoke player invites |
| **ChannelMemberScreen** | ChannelConfig → Members | Manage members (admin, mute, kick, transfer) |
| **ChannelInfoScreen** | Channel context | Read-only channel details |
| **ConfirmDeleteChannelScreen** | ChannelConfig → Delete | Confirm delete/leave channel |

---

## Widgets

| Widget | Purpose |
|--------|---------|
| `StyledButton` | Configurable button with builder; multiple style presets (toggle on/off, normal, small, confirm/cancel) |
| `ReplyBarWidget` | "Replying to <name>: <text>" bar above input; close button |
| `QuickPhrasesPanel` | User-defined message shortcuts; add/edit/remove |
| `MentionPopup` | `@username` autocomplete: player list, keyboard nav, click/enter to insert |
| `EmojiPanel` | Full emoji picker: category tabs, search, scrollable grid (8×5), click to insert |
| `EmojiAutoComplete` | `:shortcode:` popup: up to 12 candidates, keyboard selectable |
| `ChatSearchWidget` | Search bar with match count + up/down navigation |
| `ChatContextMenu` | Right-click menu: Copy, Reply |
| `CopyToast` | "Copied!" toast with fade-out |

---

## Credits

- **Author:** xwwsdd
- **Contributors:** [Spagles](https://github.com/Spagles) — voice message parsing crash fix, file/listener leak fixes, emoji rendering glitch fixes (PR #1)
- **Emoji:** [twemoji](https://twemoji.twitter.com/) by Twitter (CC-BY 4.0)
- **License:** GNU LGPLv3
