package com.lukeharbour.tradingmod.client;

import com.lukeharbour.tradingmod.TradingMod;
import com.lukeharbour.tradingmod.client.screen.TradeScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.MenuScreens;

public class TradingModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		MenuScreens.register(TradingMod.TRADE_MENU, TradeScreen::new);
	}
}
