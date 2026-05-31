package com.lukeharbour.tradingmod.trade;

import java.util.Arrays;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

public class TradeMenu extends AbstractContainerMenu {
	public static final int OFFER_COLUMNS = 4;
	public static final int OFFER_ROWS = 3;
	public static final int OFFER_SLOT_COUNT = OFFER_COLUMNS * OFFER_ROWS;

	private static final int CHEST_COLUMNS = 9;
	private static final int CHEST_ROWS = 6;
	private static final int CHEST_SLOT_COUNT = CHEST_COLUMNS * CHEST_ROWS;
	private static final int PLAYER_SLOT_START = CHEST_SLOT_COUNT;
	private static final int PLAYER_SLOT_END = PLAYER_SLOT_START + 36;

	private static final int[] REMOTE_OFFER_SLOTS = {
			9, 10, 11, 12,
			18, 19, 20, 21,
			27, 28, 29, 30
	};
	private static final int[] LOCAL_OFFER_SLOTS = {
			14, 15, 16, 17,
			23, 24, 25, 26,
			32, 33, 34, 35
	};

	private static final int THEIR_LABEL_SLOT = 0;
	private static final int THEIR_STATUS_SLOT = 1;
	private static final int YOUR_STATUS_SLOT = 7;
	private static final int YOUR_LABEL_SLOT = 8;
	private static final int CANCEL_SLOT = 45;
	private static final int HELP_SLOT = 49;
	private static final int CONFIRM_SLOT = 53;

	private final int sessionId;
	private final String partnerName;
	private final TradeSession session;
	private final UUID viewerId;
	private final SimpleContainer display = new SimpleContainer(CHEST_SLOT_COUNT);

	TradeMenu(int syncId, Inventory playerInventory, TradeSession session, ServerPlayer viewer) {
		super(MenuType.GENERIC_9x6, syncId);
		this.sessionId = session.id();
		this.partnerName = session.partnerName(viewer);
		this.session = session;
		this.viewerId = viewer.getUUID();

		updateDisplaySlots();
		addTradeSlots(session.remoteOfferFor(viewer), session.localOfferFor(viewer));
		addStandardInventorySlots(playerInventory, 8, 140);
	}

	private void addTradeSlots(Container remoteOffer, Container localOffer) {
		for (int slot = 0; slot < CHEST_SLOT_COUNT; slot++) {
			int x = 8 + slot % CHEST_COLUMNS * 18;
			int y = 18 + slot / CHEST_COLUMNS * 18;
			int remoteOfferIndex = offerIndexFor(slot, REMOTE_OFFER_SLOTS);
			int localOfferIndex = offerIndexFor(slot, LOCAL_OFFER_SLOTS);

			if (remoteOfferIndex >= 0) {
				addSlot(new ReadOnlyTradeSlot(remoteOffer, remoteOfferIndex, x, y));
			} else if (localOfferIndex >= 0) {
				addSlot(new LocalTradeSlot(localOffer, localOfferIndex, x, y, this));
			} else {
				addSlot(new DisplaySlot(display, slot, x, y));
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

		if (offerIndexFor(slotIndex, REMOTE_OFFER_SLOTS) >= 0 || isDisplaySlot(slotIndex)) {
			return ItemStack.EMPTY;
		}

		if (offerIndexFor(slotIndex, LOCAL_OFFER_SLOTS) >= 0) {
			if (!moveItemStackTo(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
				return ItemStack.EMPTY;
			}
		} else if (slotIndex >= PLAYER_SLOT_START && slotIndex < PLAYER_SLOT_END) {
			if (!canModifyLocalOffer() || !moveToLocalOffer(stack)) {
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
	public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
		if (slotIndex == CONFIRM_SLOT && player instanceof ServerPlayer serverPlayer && session.isActiveFor(player)) {
			session.setConfirmed(serverPlayer, !isLocalConfirmed());
			broadcastChanges();
			return;
		}

		if (slotIndex == CANCEL_SLOT && player instanceof ServerPlayer serverPlayer && session.isActiveFor(player)) {
			session.cancel(Component.literal(serverPlayer.getGameProfile().name() + " cancelled the trade."));
			return;
		}

		super.clicked(slotIndex, button, input, player);
	}

	@Override
	public boolean stillValid(Player player) {
		return session.isActiveFor(player);
	}

	@Override
	public void removed(Player player) {
		super.removed(player);

		if (player instanceof ServerPlayer serverPlayer) {
			session.onMenuClosed(serverPlayer);
		}
	}

	@Override
	public void broadcastChanges() {
		updateDisplaySlots();
		super.broadcastChanges();
	}

	@Override
	public void broadcastFullState() {
		updateDisplaySlots();
		super.broadcastFullState();
	}

	public int getSessionId() {
		return sessionId;
	}

	public String getPartnerName() {
		return partnerName;
	}

	boolean canModifyLocalOffer() {
		return session.state() == TradeSession.STATE_ACTIVE && !isLocalConfirmed();
	}

	private boolean isLocalConfirmed() {
		return session.isConfirmed(viewerId);
	}

	private boolean isRemoteConfirmed() {
		return session.isOtherConfirmed(viewerId);
	}

	private boolean moveToLocalOffer(ItemStack stack) {
		return moveToSlots(stack, LOCAL_OFFER_SLOTS);
	}

	private boolean moveToSlots(ItemStack stack, int[] slotIndexes) {
		boolean moved = false;

		if (stack.isStackable()) {
			for (int slotIndex : slotIndexes) {
				Slot slot = slots.get(slotIndex);
				ItemStack existing = slot.getItem();

				if (!existing.isEmpty() && slot.mayPlace(stack) && ItemStack.isSameItemSameComponents(existing, stack)) {
					int movedCount = Math.min(stack.getCount(), slot.getMaxStackSize(stack) - existing.getCount());

					if (movedCount > 0) {
						existing.grow(movedCount);
						stack.shrink(movedCount);
						slot.setChanged();
						moved = true;
					}

					if (stack.isEmpty()) {
						return true;
					}
				}
			}
		}

		for (int slotIndex : slotIndexes) {
			Slot slot = slots.get(slotIndex);

			if (slot.getItem().isEmpty() && slot.mayPlace(stack)) {
				int movedCount = Math.min(stack.getCount(), slot.getMaxStackSize(stack));
				slot.setByPlayer(stack.split(movedCount));
				slot.setChanged();
				moved = true;

				if (stack.isEmpty()) {
					return true;
				}
			}
		}

		return moved;
	}

	private void updateDisplaySlots() {
		for (int slot = 0; slot < CHEST_SLOT_COUNT; slot++) {
			if (isDisplaySlot(slot)) {
				display.setItem(slot, filler(slot));
			}
		}

		display.setItem(THEIR_LABEL_SLOT, named(Items.CHEST, "Their Offer", partnerName + "'s offered items are read-only."));
		display.setItem(THEIR_STATUS_SLOT, statusItem(partnerName, isRemoteConfirmed()));
		display.setItem(YOUR_STATUS_SLOT, statusItem("You", isLocalConfirmed()));
		display.setItem(YOUR_LABEL_SLOT, named(Items.CHEST, "Your Offer", "Place your offered items in the right-side slots."));
		display.setItem(CANCEL_SLOT, named(Items.BARRIER, "Cancel Trade", "Click to cancel and return offered items."));
		display.setItem(HELP_SLOT, named(Items.PAPER, "How Trading Works",
				"Left side: their offer.",
				"Right side: your offer.",
				"Both players must click Confirm."));
		display.setItem(CONFIRM_SLOT, confirmItem());
	}

	private ItemStack statusItem(String name, boolean confirmed) {
		return named(
				confirmed ? Items.GREEN_WOOL : Items.GRAY_WOOL,
				name + ": " + (confirmed ? "Confirmed" : "Waiting"),
				confirmed ? "This side is locked in." : "Waiting for confirmation."
		);
	}

	private ItemStack confirmItem() {
		if (isLocalConfirmed()) {
			return named(Items.RED_WOOL, "Click to Unconfirm", "Unlock your offer before changing items.");
		}

		return named(Items.EMERALD, "Click to Confirm", "Locks your offer until you unconfirm or an offer changes.");
	}

	private ItemStack filler(int slot) {
		if (slot % CHEST_COLUMNS == 4) {
			return named(Items.BLACK_STAINED_GLASS_PANE, " ");
		}

		return named(Items.GRAY_STAINED_GLASS_PANE, " ");
	}

	private boolean isDisplaySlot(int slot) {
		return slot >= 0
				&& slot < CHEST_SLOT_COUNT
				&& offerIndexFor(slot, REMOTE_OFFER_SLOTS) < 0
				&& offerIndexFor(slot, LOCAL_OFFER_SLOTS) < 0;
	}

	private static int offerIndexFor(int slot, int[] offerSlots) {
		for (int index = 0; index < offerSlots.length; index++) {
			if (offerSlots[index] == slot) {
				return index;
			}
		}

		return -1;
	}

	private static ItemStack named(Item item, String name, String... lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(ChatFormatting.WHITE));

		if (lore.length > 0) {
			stack.set(DataComponents.LORE, new ItemLore(Arrays.stream(lore)
					.<Component>map(line -> Component.literal(line).withStyle(ChatFormatting.GRAY))
					.toList()));
		}

		return stack;
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

	private static class DisplaySlot extends Slot {
		DisplaySlot(Container container, int slot, int x, int y) {
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
}
