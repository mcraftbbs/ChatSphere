# Discord bridge

ChatSphere 2.4.0 can mirror one ChatSphere channel to one Discord channel, in one or both
directions. A webhook is enough for the Minecraft → Discord direction; the Discord → Minecraft
direction needs a bot token because Discord has no webhook-based read API.

The bridge is off until an operator enables it.

## What is mirrored

- Regular channel chat from the one configured ChatSphere channel.
- Private messages and the command console never leave the server.
- Voice messages arrive on Discord as `[voice message]`, item shares get `[item]` appended.
- Messages pulled from Discord show up in the channel prefixed with `[Discord]`.

## Outbound with a webhook

1. In Discord, open the channel settings → Integrations → Webhooks → New Webhook → Copy Webhook URL.
2. Open the server config screen, Discord tab, and paste it into **Webhook URL**. Alternatively edit
   `config/chatsphere-server.json` and set `discordWebhookUrl`.
3. Set **Mirrored ChatSphere Channel** to the channel id as it is written in the client, for example
   `#general`. The match is exact, so `general` without the `#` will not work.
4. Turn on **Enable Discord Bridge**. Configuration is re-read on the fly: outbound starts working on
   the next message, no restart needed.
5. Say something in the channel. It should appear in Discord with the player name as the webhook
   display name.

The webhook URL is a credential. Anyone who has it can post to that Discord channel, so treat it like
a password and do not paste it into bug reports.

## Outbound and inbound with a bot token

1. Create an application at https://discord.com/developers/applications, open the **Bot** tab and
   reset/copy the token.
2. On the same page, under **Privileged Gateway Intents**, turn on **Message Content Intent** and
   save. Discord ships it off. The poller reads messages through the REST API and works either way,
   but leaving it on avoids surprises if you point other tools at the same bot later.
3. Invite the bot from **OAuth2 → URL Generator**: scope `bot`, permissions `View Channels`,
   `Send Messages`, `Read Message History`. Open the generated URL and pick your server.
4. In Discord, enable **Developer Mode** under Advanced settings, then right click the target channel
   and choose **Copy Channel ID**.
5. Fill in **Bot Token** and **Discord Channel ID** in the Discord tab, then enable the bridge.
6. For the Discord → Minecraft direction, turn on **Receive Discord Messages**. The poll interval
   defaults to 5 seconds and accepts 2 to 600. Higher values mean fewer API requests and more delay.
7. Run `/chatsphere doctor` to see which send method is active, the mirrored channel, and the last
   transfer error.

How the poller behaves:

- On startup it reads the newest message id in the channel and only picks up messages that arrive
  after it. Nothing older is replayed.
- The cursor is kept in memory only. After a restart, anything posted to Discord while the server was
  offline is skipped.
- Messages from bots and from webhooks are ignored. That is what stops a mirrored message from
  bouncing back and forth between the two sides.
- Messages are posted in id order, and messages already seen are skipped even if Discord returns them
  again.
- A `429` reply is honoured: the bridge reads `retry_after` and pauses both directions for that long.

## Configuration keys

Server side, in `config/chatsphere-server.json`:

| Key | Default | Meaning |
| --- | --- | --- |
| `discordEnabled` | `false` | Master switch. Nothing is sent or polled while this is off. |
| `discordWebhookUrl` | empty | Webhook used for outbound. Wins over the bot token when both are set. |
| `discordBotToken` | empty | Bot token, used for outbound when there is no webhook, and always used for inbound. |
| `discordChannelId` | empty | Discord channel the bot posts to and reads from. |
| `discordMirrorChannel` | `#general` | The ChatSphere channel that is mirrored. |
| `discordRelayInbound` | `false` | Pull new Discord messages into the ChatSphere channel. |
| `discordPollSeconds` | `5` | Inbound poll interval, clamped to 2..600. |

`discordWebhookUrl` and `discordBotToken` are stored on the server and are never sent to clients, not
even to operators. The Discord tab shows those two fields empty: type a value to replace the stored
one, leave the box alone to keep it. To clear a credential, edit the json file directly.

## Limits

- Text only. Attachments, embeds, reactions and Discord edits are not transferred.
- ChatSphere markup (`[b]`, `[color]`, `[url]`, ...) is sent as written, it is not converted to
  Discord markdown, and Discord markdown arrives as plain text.
- Mentions do not translate: a Discord `<@id>` shows up literally in Minecraft.
- One ChatSphere channel to one Discord channel. There is no per-channel mapping list.
- Each side keeps its own history. Purging a ChatSphere channel does not remove anything from
  Discord, and deleting a Discord message does not remove it from the ChatSphere history.

## Troubleshooting

- **Nothing arrives in Discord.** `discordEnabled` is off, `discordMirrorChannel` does not match the
  channel id exactly, or the channel has no webhook and no bot token plus channel id.
- **HTTP 401.** Wrong bot token.
- **HTTP 403.** The bot cannot post in that channel, check the channel permissions.
- **HTTP 404.** Wrong channel id or webhook URL, or the bot cannot see the channel.
- **Discord messages do not arrive in Minecraft.** `discordRelayInbound` is off, the bot lacks Read
  Message History, or the message was written by a bot or a webhook. Enable **Message Content Intent**
  in the Developer Portal if you also run other software against the same bot.
- **Duplicate messages.** Check `/chatsphere doctor`; if a second copy of the bridge is running (for
  example two servers sharing one bot token and channel), disable one of them.
