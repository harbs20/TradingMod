package com.lukeharbour.tradingmod.trade;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

class TradeMenuProvider implements ExtendedMenuProvider<TradeMenuData> {
	private final TradeSession session;

	TradeMenuProvider(TradeSession session) {
		this.session = session;
	}

	@Override
	public TradeMenuData getScreenOpeningData(ServerPlayer player) {
		return new TradeMenuData(session.id(), session.partnerName(player));
	}

	@Override
	public Component getDisplayName() {
		return Component.literal("Secure Trade");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory inventory, Player player) {
		if (player instanceof ServerPlayer serverPlayer && session.contains(serverPlayer)) {
			return new TradeMenu(syncId, inventory, session, serverPlayer);
		}

		return null;
	}
}
