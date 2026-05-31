package com.lukeharbour.tradingmod;

import com.lukeharbour.tradingmod.trade.TradeManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradingMod implements ModInitializer {
	public static final String MOD_ID = "tradingmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final TradeManager TRADE_MANAGER = new TradeManager();

	@Override
	public void onInitialize() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TRADE_MANAGER.onDisconnect(handler.getPlayer()));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> TRADE_MANAGER.registerCommands(dispatcher));
	}
}
