package com.lukeharbour.tradingmod;

import com.lukeharbour.tradingmod.trade.TradeManager;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public final class TradingPlugin extends JavaPlugin {
	private TradeManager tradeManager;

	@Override
	public void onEnable() {
		tradeManager = new TradeManager(this);
		getServer().getPluginManager().registerEvents(tradeManager, this);
		Objects.requireNonNull(getCommand("trade"), "trade command is not declared in plugin.yml")
				.setExecutor(tradeManager);
		Objects.requireNonNull(getCommand("trade"), "trade command is not declared in plugin.yml")
				.setTabCompleter(tradeManager);
	}

	@Override
	public void onDisable() {
		if (tradeManager != null) {
			tradeManager.cancelAll("The server is shutting down.");
		}
	}
}
