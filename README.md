# TradingMod Paper Plugin

TradingMod is a Paper/Bukkit plugin that adds secure two-player item trading for Minecraft SMPs. Players send trade requests with `/trade`, place items in a vanilla inventory GUI, and the server swaps items only after both players confirm.

This branch is the plugin version. The Fabric server-side mod remains on the `main` branch.

## Features

- Two-player `/trade` command flow for sending and accepting trade requests.
- Vanilla inventory trade GUI, so players do not install anything locally.
- 12 offer slots per player, arranged as a 4 by 3 grid.
- Read-only view of the other player's offer.
- Clickable confirm/cancel items that lock your own offer while confirmed.
- Automatic confirmation reset whenever either offer changes.
- Server-side completion check that verifies each player has room for incoming items.
- Safe cancellation when either player closes the menu, runs `/trade cancel`, or disconnects.
- Offered items are returned to their original owner when a trade is cancelled.

## Compatibility

| Requirement | Version |
| --- | --- |
| Server | Paper, Bukkit, or compatible forks |
| Minecraft API | Built against Paper API 1.20 |
| Tested target range | 1.20.x, 1.21.x, and 26.x |
| Java | 17 or newer |

Players can join with vanilla clients.

## Installation

1. Build the plugin jar with `./gradlew build`.
2. Copy `build/libs/tradingmod-1.0.1.jar` into the server's `plugins` folder.
3. Restart the server.
4. Confirm that `TradingMod` appears in `/plugins`.

Do not use the `-sources.jar` file on the server.

## Usage

### Sending a trade request

```mcfunction
/trade <player>
```

Example:

```mcfunction
/trade Steve
```

The target player receives `Trade Request Received from <player>. CLICK HERE TO ACCEPT`.

### Accepting

Click the accept message, or run:

```mcfunction
/trade <requester>
```

Once both players have requested each other, the trade GUI opens for both players.

### Confirming

Click `Click to Confirm` in the bottom-right of the trade GUI, or run:

```mcfunction
/trade confirm
```

Run `/trade unconfirm` or click `Click to Unconfirm` to unlock your offer before changing it.

### Cancelling

```mcfunction
/trade cancel
```

Closing the trade GUI also cancels the active trade.

## Commands

| Command | Description |
| --- | --- |
| `/trade <player>` | Send a trade request, or accept if that player has already requested you. |
| `/trade confirm` | Confirm your current active trade offer. |
| `/trade unconfirm` | Remove your confirmation so you can edit your offer. |
| `/trade cancel` | Cancel your pending request or active trade. |

## Development

```sh
./gradlew build
```

Build outputs are written to `build/libs/`.

Important version values live in `gradle.properties`:

```properties
minecraft_version=1.20
paper_api_version=1.20-R0.1-SNAPSHOT
plugin_version=1.0.1
```

## Notes

- Trade requests do not currently expire automatically.
- Trades are limited to one active session per player.
- Offer space is fixed at 12 slots per player.
- If returned items do not fit in the player's inventory, Minecraft's normal drop behavior is used as a fallback.
