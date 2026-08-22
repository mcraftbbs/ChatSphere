![Views](https://komarev.com/ghpvc/?username=mcraftbbs&repo=ChatSphere&label=Views&color=brightgreen)

# ChatSphere

![ChatSphere](https://cdn.modrinth.com/data/cached_images/8cc6c14cc43b82f8053acb11d80eed267154bdab_0.webp)

A modern instant-messaging chat mod for Minecraft **Fabric (1.20.1)**. Replaces vanilla chat with channels, private messaging, voice rooms, emoji, and a full GUI.

> **License:** GNU LGPLv3
> **Mod ID:** `chatsphere`
> **Version:** 2.3.0-1.20.1

---

## Features

- Channels: public/private, invite codes, sub-channels, explore screen
- Private messaging, `/msg` and `/tell`
- Custom emoji — local or server-shared, png/gif, animated
- 349 built-in emoji with search and `:shortcode:` autocomplete
- Voice rooms via Simple Voice Chat or PlasmoVoice
- Command console with history inside the chat window
- Message search, quote reply, right-click menu
- Member management: admins, mute, kick, invites, ownership
- Rich text markup (`[b]`, `[color]`, `[gradient]`, `[url]`, `[code]`), bare URLs auto-linkified
- Anti-spam duplicate merging
- 4 corner styles (square / pixel / rounded / stream) and custom `.ctheme` themes
- No Chat Reports compatible

---

## Installation

1. Install **Fabric Loader 0.16.x** (+ Fabric API) for Minecraft 1.20.1
2. Drop the ChatSphere `.jar` into `mods/`
3. (Optional) Install **Simple Voice Chat** and/or **PlasmoVoice** for voice rooms

The mod works client-only in local storage mode. Install on both server and client for channels, cross-player messaging and voice.

---

## Key Bindings

- `T` — open chat
- `/` — open chat in command mode
- `F7` — open settings

---

## Usage

- **Channels** — create with `+` or `#name`, join with `→` and an invite code, browse public ones from the explore screen (search icon). Gear icon opens per-channel settings (general, sub-channels, members, voice, delete)
- **Private messaging** — click a player name (or right-click an avatar → Private Message), or use `/msg` / `/tell`
- **Command console** — "Commands → Console" in the sidebar; up/down arrows recall history
- **Emoji** — click the emoji button in the input bar, or type `:smile:` for autocomplete. Custom png/gif can be uploaded locally or shared to the server (public or per-channel)
- **Voice** — join a voice room from a channel's settings; both SVC and PlasmoVoice are detected automatically

---

## Settings

**Client** — `F7` or edit `config/chatsphere-client.json`. Tabs:

- **UI** — timestamps, sender name, avatar, dark theme, blur, popup border, bubble colors/radius, sounds
- **Corner Style** — square / pixel / rounded / stream presets
- **Behavior** — custom emoji & limits, chat/command history, URL filter, preserve input
- **Skin** — avatar cache, custom skin API
- **Advanced** — channels, network, voice cache
- **Custom Themes** — enable and browse installed `.ctheme` files

**Server** — in-game Server Config screen, or edit `config/chatsphere-server.json`. Notable options: `antiSpam`, `maxChatHistory`, `maxCommandMessages`, `enableChannels`, `channelHistoryEnabled`, `exploreEnabled`/`exploreMinMembers`, `backupIntervalMinutes`/`backupKeepMax`, `bannedWords`, `voiceOfflineStorage`/`voiceStorageMax`, `preventsChatReports`, `emojiSharingEnabled`, `emojiUploadRequiresOp`, `emojiUploadCooldownSeconds`, `emojiMaxTotal`.

---

## Commands

- `/chatsphere help`
- `/chatsphere list`
- `/chatsphere info <name>`
- `#channelname` — quick-switch in the input field

---

## Storage

```
{gamedir}/ChatSphere/
├── client/
│   ├── singleplayer/<world>/
│   └── multiplayer/<server>/
└── server/
    ├── channels.json
    └── backups/
```

Client data saves asynchronously; server data is backed up automatically every N minutes (default 30, keeping 20).

---

## Compatibility

- **Mod Menu** — settings screen integration
- **Simple Voice Chat** / **PlasmoVoice** — auto-detected voice rooms
- **No Chat Reports** — security status display
- **Vanilla chat** — replaced by ChatSphere (vanilla rendering disabled)

---

## Building

```bash
./gradlew build
```

Emoji sprite sheet (requires internet):

```bash
./gradlew runEmojiSheetGenerator
```

---

## Network Protocol

Version `"1.0"`, Fabric custom payload API. All payload decoders enforce size/count limits; identity always comes from the verified player connection.

---

## Credits

- **Author:** xwwsdd
- **Contributors:** [Spagles](https://github.com/Spagles)
- **Emoji:** [twemoji](https://twemoji.twitter.com/) by Twitter (CC-BY 4.0)
- **License:** GNU LGPLv3
