package com.lukeharbour.tradingmod.trade;

import net.minecraft.world.MenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

class TradeMenuProvider implements MenuProvider {
	private final TradeSession session;
	private final Component title;

	TradeMenuProvider(TradeSession session, ServerPlayer viewer) {
		this.session = session;
		this.title = Component.literal("Trade with " + session.partnerName(viewer));
	}

	@Override
	public Component getDisplayName() {
		return title;
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		if (player instanceof ServerPlayer serverPlayer && session.contains(serverPlayer)) {
			return new TradeMenu(syncId, inventory, session, serverPlayer);
		}

		return null;
	}
}
