package com.lukeharbour.tradingmod.trade;

import java.util.UUID;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

final class TradeInventoryHolder implements InventoryHolder {
	private final TradeSession session;
	private final UUID viewerId;
	private Inventory inventory;

	TradeInventoryHolder(TradeSession session, UUID viewerId) {
		this.session = session;
		this.viewerId = viewerId;
	}

	TradeSession session() {
		return session;
	}

	UUID viewerId() {
		return viewerId;
	}

	void setInventory(Inventory inventory) {
		this.inventory = inventory;
	}

	@Override
	public @NotNull Inventory getInventory() {
		return inventory;
	}
}
