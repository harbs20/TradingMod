package com.lukeharbour.tradingmod.trade;

import com.lukeharbour.tradingmod.TradingMod;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class TradeMenu extends AbstractContainerMenu {
	public static final int OFFER_COLUMNS = 4;
	public static final int OFFER_ROWS = 3;
	public static final int OFFER_SLOT_COUNT = OFFER_COLUMNS * OFFER_ROWS;
	public static final int IMAGE_WIDTH = 226;
	public static final int IMAGE_HEIGHT = 222;
	public static final int REMOTE_OFFER_X = 24;
	public static final int LOCAL_OFFER_X = 132;
	public static final int OFFER_Y = 38;
	public static final int PLAYER_INV_X = 33;
	public static final int PLAYER_INV_Y = 130;

	private static final int REMOTE_SLOT_START = 0;
	private static final int REMOTE_SLOT_END = REMOTE_SLOT_START + OFFER_SLOT_COUNT;
	private static final int LOCAL_SLOT_START = REMOTE_SLOT_END;
	private static final int LOCAL_SLOT_END = LOCAL_SLOT_START + OFFER_SLOT_COUNT;
	private static final int PLAYER_SLOT_START = LOCAL_SLOT_END;
	private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 36;

	private final int sessionId;
	private final String partnerName;
	private final TradeSession session;
	private final UUID viewerId;
	private final ContainerData data;

	public TradeMenu(int syncId, Inventory playerInventory, TradeMenuData openingData) {
		this(syncId, playerInventory, openingData.sessionId(), openingData.partnerName(), new SimpleContainer(OFFER_SLOT_COUNT),
				new SimpleContainer(OFFER_SLOT_COUNT), null, null);
	}

	TradeMenu(int syncId, Inventory playerInventory, TradeSession session, ServerPlayer viewer) {
		this(syncId, playerInventory, session.id(), session.partnerName(viewer), session.remoteOfferFor(viewer),
				session.localOfferFor(viewer), session, viewer.getUUID());
	}

	private TradeMenu(int syncId, Inventory playerInventory, int sessionId, String partnerName, Container remoteOffer,
			Container localOffer, TradeSession session, UUID viewerId) {
		super(TradingMod.TRADE_MENU, syncId);
		this.sessionId = sessionId;
		this.partnerName = partnerName;
		this.session = session;
		this.viewerId = viewerId;
		this.data = session == null ? new SimpleContainerData(3) : new TradeContainerData(session, viewerId);

		addOfferSlots(remoteOffer, localOffer);
		addStandardInventorySlots(playerInventory, PLAYER_INV_X, PLAYER_INV_Y);
		addDataSlots(this.data);
	}

	private void addOfferSlots(Container remoteOffer, Container localOffer) {
		for (int row = 0; row < OFFER_ROWS; row++) {
			for (int column = 0; column < OFFER_COLUMNS; column++) {
				int index = column + row * OFFER_COLUMNS;
				addSlot(new ReadOnlyTradeSlot(remoteOffer, index, REMOTE_OFFER_X + column * 18, OFFER_Y + row * 18));
			}
		}

		for (int row = 0; row < OFFER_ROWS; row++) {
			for (int column = 0; column < OFFER_COLUMNS; column++) {
				int index = column + row * OFFER_COLUMNS;
				addSlot(new LocalTradeSlot(localOffer, index, LOCAL_OFFER_X + column * 18, OFFER_Y + row * 18, this));
			}
		}
	}

	@Override
	public ItemStack quickMoveStack(Player player, int slotIndex) {
		if (slotIndex < 0 || slotIndex >= slots.size()) {
			return ItemStack.EMPTY;
		}

		Slot slot = slots.get(slotIndex);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getItem();
		ItemStack original = stack.copy();

		if (slotIndex >= REMOTE_SLOT_START && slotIndex < REMOTE_SLOT_END) {
			return ItemStack.EMPTY;
		}

		if (slotIndex >= LOCAL_SLOT_START && slotIndex < LOCAL_SLOT_END) {
			if (!moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (slotIndex >= PLAYER_SLOT_START && slotIndex < PLAYER_SLOT_END) {
			if (!canModifyLocalOffer() || !moveItemStackTo(stack, LOCAL_SLOT_START, LOCAL_SLOT_END, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}

		if (stack.getCount() == original.getCount()) {
			return ItemStack.EMPTY;
		}

		slot.onTake(player, stack);
		return original;
	}

	@Override
	public boolean stillValid(Player player) {
		return session == null || session.isActiveFor(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);

		if (session != null && player instanceof ServerPlayer serverPlayer) {
			session.onMenuClosed(serverPlayer);
		}
	}

	public int getSessionId() {
		return sessionId;
	}

	public String getPartnerName() {
		return partnerName;
	}

	public boolean isLocalConfirmed() {
		return data.get(0) == 1;
	}

	public boolean isRemoteConfirmed() {
		return data.get(1) == 1;
	}

	public boolean isComplete() {
		return data.get(2) != TradeSession.STATE_ACTIVE;
	}

	boolean canModifyLocalOffer() {
		return !isComplete() && !isLocalConfirmed();
	}

	private static class LocalTradeSlot extends Slot {
		private final TradeMenu menu;

		LocalTradeSlot(Container container, int slot, int x, int y, TradeMenu menu) {
			super(container, slot, x, y);
			this.menu = menu;
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return menu.canModifyLocalOffer();
		}

		@Override
		public boolean mayPickup(Player player) {
			return menu.canModifyLocalOffer();
		}

		@Override
		public boolean allowModification(Player player) {
			return menu.canModifyLocalOffer();
		}
	}

	private static class ReadOnlyTradeSlot extends Slot {
		ReadOnlyTradeSlot(Container container, int slot, int x, int y) {
			super(container, slot, x, y);
		}

		@Override
		public boolean mayPlace(ItemStack stack) {
			return false;
		}

		@Override
		public boolean mayPickup(Player player) {
			return false;
		}

		@Override
		public boolean allowModification(Player player) {
			return false;
		}
	}

	private static class TradeContainerData implements ContainerData {
		private final TradeSession session;
		private final UUID viewerId;

		TradeContainerData(TradeSession session, UUID viewerId) {
			this.session = session;
			this.viewerId = viewerId;
		}

		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> session.isConfirmed(viewerId) ? 1 : 0;
				case 1 -> session.isOtherConfirmed(viewerId) ? 1 : 0;
				case 2 -> session.state();
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int getCount() {
			return 3;
		}
	}
}
