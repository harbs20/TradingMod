package com.lukeharbour.tradingmod.trade;

import com.mojang.brigadier.CommandDispatcher;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.server.level.ServerPlayer;

public class TradeManager {
	private final Map<UUID, TradeSession> sessionsByPlayer = new HashMap<>();
	private final Map<UUID, UUID> requests = new HashMap<>();
	private int nextSessionId = 1;

	public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("trade")
				.then(Commands.literal("cancel")
						.executes(context -> {
							ServerPlayer player = context.getSource().getPlayerOrException();
							cancel(player);
							return 1;
						}))
				.then(Commands.literal("confirm")
						.executes(context -> {
							ServerPlayer player = context.getSource().getPlayerOrException();
							confirm(player, true);
							return 1;
						}))
				.then(Commands.literal("unconfirm")
						.executes(context -> {
							ServerPlayer player = context.getSource().getPlayerOrException();
							confirm(player, false);
							return 1;
						}))
				.then(Commands.argument("player", EntityArgument.player())
						.executes(context -> {
							ServerPlayer requester = context.getSource().getPlayerOrException();
							ServerPlayer target = EntityArgument.getPlayer(context, "player");
							request(requester, target);
							return 1;
						})));
	}

	public void request(ServerPlayer requester, ServerPlayer target) {
		if (requester.getUUID().equals(target.getUUID())) {
			requester.sendSystemMessage(Component.literal("You cannot trade with yourself."));
			return;
		}

		if (sessionsByPlayer.containsKey(requester.getUUID())) {
			requester.sendSystemMessage(Component.literal("You are already in a trade."));
			return;
		}

		if (sessionsByPlayer.containsKey(target.getUUID())) {
			requester.sendSystemMessage(Component.literal(target.getGameProfile().name() + " is already in a trade."));
			return;
		}

		UUID requesterId = requester.getUUID();
		UUID targetId = target.getUUID();

		if (requesterId.equals(requests.get(targetId))) {
			requests.remove(targetId);
			requests.remove(requesterId);
			startSession(requester, target);
			return;
		}

		requests.put(requesterId, targetId);
		requester.sendSystemMessage(Component.literal("Trade Request Sent to " + target.getGameProfile().name() + "."));
		target.sendSystemMessage(tradeRequestMessage(requester));
	}

	public void confirm(ServerPlayer player, boolean confirmed) {
		TradeSession session = sessionsByPlayer.get(player.getUUID());

		if (session == null) {
			player.sendSystemMessage(Component.literal("You are not in an active trade."));
			return;
		}

		session.setConfirmed(player, confirmed);
		if (session.state() == TradeSession.STATE_ACTIVE) {
			player.sendSystemMessage(Component.literal(confirmed ? "Trade confirmed." : "Trade confirmation removed."));
		}
	}

	public void cancel(ServerPlayer player) {
		TradeSession session = sessionsByPlayer.get(player.getUUID());

		if (session != null) {
			session.cancel(Component.literal(player.getGameProfile().name() + " cancelled the trade."));
			return;
		}

		if (requests.remove(player.getUUID()) != null) {
			player.sendSystemMessage(Component.literal("Trade request cancelled."));
			return;
		}

		removeRequestsTargeting(player.getUUID());
		player.sendSystemMessage(Component.literal("No active trade request found."));
	}

	public void onDisconnect(ServerPlayer player) {
		TradeSession session = sessionsByPlayer.get(player.getUUID());

		if (session != null) {
			session.cancel(Component.literal(player.getGameProfile().name() + " disconnected."));
		}

		requests.remove(player.getUUID());
		removeRequestsTargeting(player.getUUID());
	}

	void remove(TradeSession session) {
		Iterator<Map.Entry<UUID, TradeSession>> iterator = sessionsByPlayer.entrySet().iterator();
		while (iterator.hasNext()) {
			if (iterator.next().getValue() == session) {
				iterator.remove();
			}
		}
	}

	private void startSession(ServerPlayer first, ServerPlayer second) {
		TradeSession session = new TradeSession(this, nextSessionId++, first, second);
		sessionsByPlayer.put(first.getUUID(), session);
		sessionsByPlayer.put(second.getUUID(), session);

		if (!session.open()) {
			remove(session);
		}
	}

	private void removeRequestsTargeting(UUID targetId) {
		requests.entrySet().removeIf(entry -> entry.getValue().equals(targetId));
	}

	private Component tradeRequestMessage(ServerPlayer requester) {
		String requesterName = requester.getGameProfile().name();
		return Component.literal("Trade Request Received from " + requesterName + ". ")
				.append(Component.literal("CLICK HERE TO ACCEPT")
						.withStyle(style -> style
								.withColor(ChatFormatting.GREEN)
								.withUnderlined(true)
								.withClickEvent(new ClickEvent.RunCommand("/trade " + requesterName))));
	}
}
