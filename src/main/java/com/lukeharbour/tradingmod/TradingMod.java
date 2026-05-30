package com.lukeharbour.tradingmod;

import com.lukeharbour.tradingmod.network.ConfirmTradePayload;
import com.lukeharbour.tradingmod.trade.TradeManager;
import com.lukeharbour.tradingmod.trade.TradeMenu;
import com.lukeharbour.tradingmod.trade.TradeMenuData;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradingMod implements ModInitializer {
	public static final String MOD_ID = "tradingmod";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final TradeManager TRADE_MANAGER = new TradeManager();

	public static final MenuType<TradeMenu> TRADE_MENU = Registry.register(
			BuiltInRegistries.MENU,
			id("trade"),
			new ExtendedMenuType<>((syncId, inventory, data) -> new TradeMenu(syncId, inventory, data), TradeMenuData.STREAM_CODEC)
	);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(ConfirmTradePayload.TYPE, ConfirmTradePayload.STREAM_CODEC);
		ServerPlayNetworking.registerGlobalReceiver(ConfirmTradePayload.TYPE, (payload, context) -> context.server().executeIfPossible(
				() -> TRADE_MANAGER.confirm(context.player(), payload.sessionId(), payload.confirmed())
		));

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> TRADE_MANAGER.onDisconnect(handler.getPlayer()));
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> TRADE_MANAGER.registerCommands(dispatcher));
	}
}
