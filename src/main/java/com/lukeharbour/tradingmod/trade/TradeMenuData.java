package com.lukeharbour.tradingmod.trade;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record TradeMenuData(int sessionId, String partnerName) {
	public static final StreamCodec<RegistryFriendlyByteBuf, TradeMenuData> STREAM_CODEC = StreamCodec.ofMember(
			TradeMenuData::write,
			TradeMenuData::read
	);

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(sessionId);
		buffer.writeUtf(partnerName, 64);
	}

	private static TradeMenuData read(RegistryFriendlyByteBuf buffer) {
		return new TradeMenuData(buffer.readVarInt(), buffer.readUtf(64));
	}
}
