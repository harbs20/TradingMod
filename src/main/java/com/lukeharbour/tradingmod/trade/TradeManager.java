package com.lukeharbour.tradingmod.trade;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TradeManager implements CommandExecutor, TabCompleter, Listener {
	private final JavaPlugin plugin;
	private final Map<UUID, TradeSession> sessionsByPlayer = new HashMap<>();
	private final Map<UUID, UUID> requests = new HashMap<>();
	private int nextSessionId = 1;

	public TradeManager(JavaPlugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
			@NotNull String[] args) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage("Only players can use /trade.");
			return true;
		}

		if (args.length != 1) {
			player.sendMessage(Component.text("Usage: /trade <player|confirm|unconfirm|cancel>", NamedTextColor.RED));
			return true;
		}

		String argument = args[0].toLowerCase(Locale.ROOT);
		switch (argument) {
			case "cancel" -> cancel(player);
			case "confirm" -> confirm(player, true);
			case "unconfirm" -> confirm(player, false);
			default -> {
				Player target = Bukkit.getPlayerExact(args[0]);
				if (target == null) {
					player.sendMessage(Component.text("That player is not online.", NamedTextColor.RED));
					return true;
				}

				request(player, target);
			}
		}

		return true;
	}

	@Override
	public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
			@NotNull String alias, @NotNull String[] args) {
		if (args.length != 1) {
			return List.of();
		}

		String prefix = args[0].toLowerCase(Locale.ROOT);
		List<String> suggestions = new ArrayList<>(List.of("cancel", "confirm", "unconfirm"));
		Bukkit.getOnlinePlayers().stream()
				.map(Player::getName)
				.filter(name -> !(sender instanceof Player player) || !player.getName().equals(name))
				.forEach(suggestions::add);

		return suggestions.stream()
				.filter(suggestion -> suggestion.toLowerCase(Locale.ROOT).startsWith(prefix))
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.toList();
	}

	public void request(Player requester, Player target) {
		if (requester.getUniqueId().equals(target.getUniqueId())) {
			requester.sendMessage(Component.text("You cannot trade with yourself.", NamedTextColor.RED));
			return;
		}

		if (sessionsByPlayer.containsKey(requester.getUniqueId())) {
			requester.sendMessage(Component.text("You are already in a trade.", NamedTextColor.RED));
			return;
		}

		if (sessionsByPlayer.containsKey(target.getUniqueId())) {
			requester.sendMessage(Component.text(target.getName() + " is already in a trade.", NamedTextColor.RED));
			return;
		}

		UUID requesterId = requester.getUniqueId();
		UUID targetId = target.getUniqueId();

		if (requesterId.equals(requests.get(targetId))) {
			requests.remove(targetId);
			requests.remove(requesterId);
			startSession(requester, target);
			return;
		}

		requests.put(requesterId, targetId);
		requester.sendMessage(Component.text("Trade Request Sent to " + target.getName() + ".", NamedTextColor.GREEN));
		target.sendMessage(tradeRequestMessage(requester));
	}

	public void confirm(Player player, boolean confirmed) {
		TradeSession session = sessionsByPlayer.get(player.getUniqueId());

		if (session == null) {
			player.sendMessage(Component.text("You are not in an active trade.", NamedTextColor.RED));
			return;
		}

		session.setConfirmed(player, confirmed);
		if (session.isActive()) {
			player.sendMessage(Component.text(confirmed ? "Trade confirmed." : "Trade confirmation removed.",
					NamedTextColor.YELLOW));
		}
	}

	public void cancel(Player player) {
		TradeSession session = sessionsByPlayer.get(player.getUniqueId());

		if (session != null) {
			session.cancel(Component.text(player.getName() + " cancelled the trade.", NamedTextColor.RED));
			return;
		}

		if (requests.remove(player.getUniqueId()) != null) {
			player.sendMessage(Component.text("Trade request cancelled.", NamedTextColor.YELLOW));
			return;
		}

		removeRequestsTargeting(player.getUniqueId());
		player.sendMessage(Component.text("No active trade request found.", NamedTextColor.RED));
	}

	public void cancelAll(String reason) {
		Set<TradeSession> sessions = new HashSet<>(sessionsByPlayer.values());
		for (TradeSession session : sessions) {
			session.cancel(Component.text(reason, NamedTextColor.RED));
		}

		requests.clear();
	}

	void add(TradeSession session, Player first, Player second) {
		sessionsByPlayer.put(first.getUniqueId(), session);
		sessionsByPlayer.put(second.getUniqueId(), session);
	}

	void remove(TradeSession session) {
		sessionsByPlayer.entrySet().removeIf(entry -> entry.getValue() == session);
	}

	BukkitTask runNextTick(Runnable task) {
		return Bukkit.getScheduler().runTask(plugin, task);
	}

	@EventHandler(ignoreCancelled = true)
	public void onInventoryClick(InventoryClickEvent event) {
		TradeSession session = sessionFrom(event.getView().getTopInventory().getHolder());
		if (session != null) {
			session.handleClick(event);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onInventoryDrag(InventoryDragEvent event) {
		TradeSession session = sessionFrom(event.getView().getTopInventory().getHolder());
		if (session != null) {
			session.handleDrag(event);
		}
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		TradeSession session = sessionFrom(event.getInventory().getHolder());
		if (session != null && event.getPlayer() instanceof Player player) {
			session.handleClose(player);
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		TradeSession session = sessionsByPlayer.get(player.getUniqueId());

		if (session != null) {
			session.cancel(Component.text(player.getName() + " disconnected.", NamedTextColor.RED));
		}

		requests.remove(player.getUniqueId());
		removeRequestsTargeting(player.getUniqueId());
	}

	private void startSession(Player first, Player second) {
		TradeSession session = new TradeSession(this, nextSessionId++, first, second);
		add(session, first, second);
		session.open();
	}

	private void removeRequestsTargeting(UUID targetId) {
		requests.entrySet().removeIf(entry -> entry.getValue().equals(targetId));
	}

	private Component tradeRequestMessage(Player requester) {
		String requesterName = requester.getName();
		return Component.text("Trade Request Received from " + requesterName + ". ", NamedTextColor.YELLOW)
				.append(Component.text("CLICK HERE TO ACCEPT", NamedTextColor.GREEN)
						.decorate(TextDecoration.UNDERLINED)
						.clickEvent(ClickEvent.runCommand("/trade " + requesterName)));
	}

	private TradeSession sessionFrom(@Nullable org.bukkit.inventory.InventoryHolder holder) {
		if (holder instanceof TradeInventoryHolder tradeHolder) {
			return tradeHolder.session();
		}

		return null;
	}
}
