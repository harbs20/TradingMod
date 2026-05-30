# TradingMod

TradingMod is a Fabric mod that adds a secure two-player item trading GUI for Minecraft SMPs. Instead of dropping items on the ground and trusting the other player to follow through, both players place their offers into a shared trade screen and the server only swaps the items after both sides confirm.

## Features

- Two-player `/trade` command flow for sending and accepting trade requests.
- Dedicated trade screen with "Their Offer" and "Your Offer" sections.
- 12 offer slots per player, arranged as a 4 by 3 grid.
- Read-only view of the other player's offer.
- Confirmation button that locks your own offer while confirmed.
- Automatic confirmation reset whenever either offer changes.
- Server-side completion check that verifies each player has room for the incoming items.
- Safe cancellation when either player closes the menu, runs `/trade cancel`, or disconnects.
- Offered items are returned to their original owner when a trade is cancelled.

## Compatibility

TradingMod currently targets the Fabric toolchain used by Minecraft 26.1.x.

| Requirement | Version |
| --- | --- |
| Minecraft | Built against 26.1.2 |
| Fabric Loader | 0.19.2 or newer |
| Fabric API | 0.149.1+26.1.2 |
| Java | 25 or newer |

Both the server and participating clients should have the mod installed. The server owns the trade session and item transfer logic, while the client provides the custom trade screen.

Supporting older Fabric or Minecraft versions should be done with separate branches/builds, because the 26.1 toolchain uses newer mappings and a newer Java baseline than many older releases.

## Installation

1. Install Minecraft with Fabric Loader.
2. Install Fabric API for the same Minecraft version.
3. Download or build the TradingMod jar.
4. Place the TradingMod jar in the `mods` folder on the server and on each participating client.
5. Start the game/server and verify that the mod loads without Fabric dependency errors.

## Usage

### Sending a trade request

Run:

```mcfunction
/trade <player>
```

Example:

```mcfunction
/trade Steve
```

The target player receives a message telling them who wants to trade and how to accept.

### Accepting a trade request

The target player accepts by sending a matching request back:

```mcfunction
/trade <requester>
```

Once both players have requested each other, the trade screen opens for both players.

### Cancelling

Run:

```mcfunction
/trade cancel
```

This cancels your pending outgoing request or your active trade. If you are in an active trade, all offered items are returned to their original owners.

Closing the trade screen also cancels the active trade.

## How Trades Work

1. Player A runs `/trade <player-b>`.
2. Player B runs `/trade <player-a>` to accept.
3. TradingMod opens a secure trade screen for both players.
4. Each player can place items only in their own offer slots.
5. The other player's offer slots are visible but read-only.
6. When a player clicks `Confirm`, their own offer becomes locked.
7. If either player changes their offer, both confirmations are cleared.
8. When both players are confirmed, the server checks whether each player can receive the other offer.
9. If both inventories have enough room, the server swaps the items and closes the trade.
10. If an inventory does not have enough room, the trade remains open and both confirmations are reset.

## Safety Behavior

TradingMod is designed so item movement is controlled by the server:

- Players cannot remove items from the other player's offer slots.
- Shift-clicking from the player's inventory can only move items into that player's own offer.
- Confirming prevents the confirmed player from changing their own offer until they unconfirm or the trade resets.
- Any offer change resets both confirmations, preventing last-second item swaps after the other player has already confirmed.
- A completed trade only happens after both confirmations are active at the same time.
- Cancelled trades return each player's offer to that same player.
- If returned items do not fit in the player's inventory, Minecraft's normal drop behavior is used as a fallback.

## Commands

| Command | Description |
| --- | --- |
| `/trade <player>` | Send a trade request, or accept if that player has already requested you. |
| `/trade cancel` | Cancel your pending request or active trade. |

## Project Layout

```text
src/main/java/com/lukeharbour/tradingmod/
  TradingMod.java                         Main mod initializer, menu registration, networking, commands
  network/ConfirmTradePayload.java        Client-to-server confirm/unconfirm packet
  trade/TradeManager.java                 Trade requests, active sessions, cancellation, disconnect cleanup
  trade/TradeSession.java                 Offer containers, confirmations, completion, item transfer
  trade/TradeMenu.java                    Server/client container menu and slot rules
  trade/TradeMenuData.java                Data sent when opening the trade screen
  trade/TradeMenuProvider.java            Server-side screen provider

src/client/java/com/lukeharbour/tradingmod/
  client/TradingModClient.java            Client initializer and screen registration
  client/screen/TradeScreen.java          Custom trade GUI rendering and confirm button

src/main/resources/
  fabric.mod.json                         Fabric metadata and dependency declarations
  assets/tradingmod/lang/en_us.json       English translations
```

## Development

This project uses Gradle with Fabric Loom.

### Build

```sh
./gradlew build
```

Build outputs are written to `build/libs/`. The main mod jar uses the archive base name from `gradle.properties`, currently `tradingmod`.

### Useful Gradle Tasks

```sh
./gradlew runClient
./gradlew runServer
./gradlew build
```

Use `runClient` for local client testing and `runServer` for dedicated-server behavior. The generated local run directory is ignored by git.

### Version Settings

Important version values live in `gradle.properties`:

```properties
minecraft_version=26.1.2
loader_version=0.19.2
loom_version=1.16-SNAPSHOT
mod_version=1.0.0
fabric_api_version=0.149.1+26.1.2
```

Update these values together when moving the mod to a new Minecraft/Fabric version.

## Known Limitations

- Trade requests do not currently expire automatically.
- There is no configuration file yet.
- Trades are limited to one active session per player.
- Offer space is fixed at 12 slots per player.
- The mod currently provides one English translation entry and uses literal text for most in-game messages.

## Troubleshooting

### The mod does not load

Check that Minecraft, Fabric Loader, Fabric API, and Java match the versions listed above. A Java version mismatch is especially likely if the game starts with class version errors.

### The trade screen does not open

Make sure both players have accepted the request by running `/trade <other-player>`. Also verify that neither player is already in another active trade.

### A trade will not complete

Both players must press `Confirm`, and each player must have enough inventory room for the other player's offer. If a player lacks space, the trade remains open and both confirmations are reset.

### Items return when the screen closes

That is expected. Closing the trade screen cancels the trade so players do not accidentally leave items locked in an unfinished session.

## License

This project is marked as ARR in `fabric.mod.json`.
