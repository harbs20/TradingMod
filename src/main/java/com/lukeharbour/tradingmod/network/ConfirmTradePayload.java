package com.lukeharbour.tradingmod.network;

import com.lukeharbour.tradingmod.TradingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ConfirmTradePayload(int sessionId, boolean confirmed) implements CustomPacketPayload {
	public static final Type<ConfirmTradePayload> TYPE = new Type<>(TradingMod.id("confirm_trade"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ConfirmTradePayload> STREAM_CODEC = StreamCodec.ofMember(
			ConfirmTradePayload::write,
			ConfirmTradePayload::read
	);

	private void write(RegistryFriendlyByteBuf buffer) {
		buffer.writeVarInt(sessionId);
		buffer.writeBoolean(confirmed);
	}

	private static ConfirmTradePayload read(RegistryFriendlyByteBuf buffer) {
		return new ConfirmTradePayload(buffer.readVarInt(), buffer.readBoolean());
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
