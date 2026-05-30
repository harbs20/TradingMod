package com.lukeharbour.tradingmod.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

class TradeSession {
	static final int STATE_ACTIVE = 0;
	static final int STATE_COMPLETED = 1;
	static final int STATE_CANCELLED = 2;

	private final TradeManager manager;
	private final int id;
	private final ServerPlayer first;
	private final ServerPlayer second;
	private final TradeOfferContainer firstOffer;
	private final TradeOfferContainer secondOffer;

	private boolean firstConfirmed;
	private boolean secondConfirmed;
	private int state = STATE_ACTIVE;

	TradeSession(TradeManager manager, int id, ServerPlayer first, ServerPlayer second) {
		this.manager = manager;
		this.id = id;
		this.first = first;
		this.second = second;
		this.firstOffer = new TradeOfferContainer(this, first.getUUID());
		this.secondOffer = new TradeOfferContainer(this, second.getUUID());
	}

	int id() {
		return id;
	}

	int state() {
		return state;
	}

	boolean open() {
		if (first.openMenu(new TradeMenuProvider(this)).isEmpty()) {
			cancel(Component.literal("Could not open the trade for " + first.getGameProfile().name() + "."));
			return false;
		}

		if (second.openMenu(new TradeMenuProvider(this)).isEmpty()) {
			cancel(Component.literal("Could not open the trade for " + second.getGameProfile().name() + "."));
			return false;
		}

		first.sendSystemMessage(Component.literal("Trade opened with " + second.getGameProfile().name() + "."));
		second.sendSystemMessage(Component.literal("Trade opened with " + first.getGameProfile().name() + "."));
		return true;
	}

	boolean contains(Player player) {
		return player != null && contains(player.getUUID());
	}

	boolean contains(UUID playerId) {
		return first.getUUID().equals(playerId) || second.getUUID().equals(playerId);
	}

	boolean isActiveFor(Player player) {
		return state == STATE_ACTIVE && contains(player);
	}

	String partnerName(ServerPlayer viewer) {
		return partnerOf(viewer).getGameProfile().name();
	}

	Container localOfferFor(ServerPlayer viewer) {
		return first.getUUID().equals(viewer.getUUID()) ? firstOffer : secondOffer;
	}

	Container remoteOfferFor(ServerPlayer viewer) {
		return first.getUUID().equals(viewer.getUUID()) ? secondOffer : firstOffer;
	}

	boolean isConfirmed(UUID playerId) {
		if (first.getUUID().equals(playerId)) {
			return firstConfirmed;
		}

		if (second.getUUID().equals(playerId)) {
			return secondConfirmed;
		}

		return false;
	}

	boolean isOtherConfirmed(UUID playerId) {
		if (first.getUUID().equals(playerId)) {
			return secondConfirmed;
		}

		if (second.getUUID().equals(playerId)) {
			return firstConfirmed;
		}

		return false;
	}

	void setConfirmed(ServerPlayer player, boolean confirmed) {
		if (!isActiveFor(player)) {
			return;
		}

		if (first.getUUID().equals(player.getUUID())) {
			firstConfirmed = confirmed;
		} else {
			secondConfirmed = confirmed;
		}

		broadcastChanges();

		if (firstConfirmed && secondConfirmed) {
			completeIfPossible();
		}
	}

	void offerChanged(UUID ownerId) {
		if (state != STATE_ACTIVE || !contains(ownerId)) {
			return;
		}

		if (firstConfirmed || secondConfirmed) {
			firstConfirmed = false;
			secondConfirmed = false;
			first.sendSystemMessage(Component.literal("Trade confirmations reset because an offer changed."));
			second.sendSystemMessage(Component.literal("Trade confirmations reset because an offer changed."));
		}

		broadcastChanges();
	}

	void onMenuClosed(ServerPlayer player) {
		if (state != STATE_ACTIVE || !contains(player)) {
			return;
		}

		cancel(Component.literal(player.getGameProfile().name() + " closed the trade."));
	}

	void cancel(Component reason) {
		if (state != STATE_ACTIVE) {
			return;
		}

		state = STATE_CANCELLED;
		firstConfirmed = false;
		secondConfirmed = false;

		moveOfferToPlayer(first, firstOffer);
		moveOfferToPlayer(second, secondOffer);
		manager.remove(this);

		first.sendSystemMessage(reason);
		second.sendSystemMessage(reason);
		first.closeContainer();
		second.closeContainer();
	}

	private void completeIfPossible() {
		if (!canFitAll(first, secondOffer)) {
			resetAfterFailedCompletion(first, "Your inventory needs room for " + second.getGameProfile().name() + "'s offer.");
			return;
		}

		if (!canFitAll(second, firstOffer)) {
			resetAfterFailedCompletion(second, "Your inventory needs room for " + first.getGameProfile().name() + "'s offer.");
			return;
		}

		state = STATE_COMPLETED;
		moveOfferToPlayer(first, secondOffer);
		moveOfferToPlayer(second, firstOffer);
		manager.remove(this);

		first.sendSystemMessage(Component.literal("Trade completed with " + second.getGameProfile().name() + "."));
		second.sendSystemMessage(Component.literal("Trade completed with " + first.getGameProfile().name() + "."));
		first.closeContainer();
		second.closeContainer();
	}

	private void resetAfterFailedCompletion(ServerPlayer player, String message) {
		firstConfirmed = false;
		secondConfirmed = false;
		player.sendSystemMessage(Component.literal(message));
		partnerOf(player).sendSystemMessage(Component.literal(player.getGameProfile().name() + " needs more inventory space."));
		broadcastChanges();
	}

	private ServerPlayer partnerOf(ServerPlayer player) {
		return first.getUUID().equals(player.getUUID()) ? second : first;
	}

	private void moveOfferToPlayer(ServerPlayer player, SimpleContainer offer) {
		for (ItemStack stack : offer.removeAllItems()) {
			if (!stack.isEmpty()) {
				giveOrDrop(player, stack);
			}
		}
	}

	private void giveOrDrop(ServerPlayer player, ItemStack stack) {
		ItemStack remaining = stack.copy();
		player.getInventory().add(remaining);

		if (!remaining.isEmpty()) {
			player.drop(remaining, false);
		}

		player.getInventory().setChanged();
	}

	private boolean canFitAll(ServerPlayer player, SimpleContainer offer) {
		List<ItemStack> simulated = copyStorage(player.getInventory());

		for (int index = 0; index < offer.getContainerSize(); index++) {
			ItemStack offered = offer.getItem(index);

			if (!offered.isEmpty() && !insertInto(simulated, offered.copy())) {
				return false;
			}
		}

		return true;
	}

	private List<ItemStack> copyStorage(Inventory inventory) {
		List<ItemStack> simulated = new ArrayList<>();

		for (ItemStack stack : inventory.getNonEquipmentItems()) {
			simulated.add(stack.copy());
		}

		return simulated;
	}

	private boolean insertInto(List<ItemStack> inventory, ItemStack stack) {
		for (ItemStack existing : inventory) {
			if (stack.isEmpty()) {
				return true;
			}

			if (canStacksCombine(existing, stack)) {
				int moved = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
				if (moved > 0) {
					existing.grow(moved);
					stack.shrink(moved);
				}
			}
		}

		for (int index = 0; index < inventory.size(); index++) {
			if (stack.isEmpty()) {
				return true;
			}

			if (inventory.get(index).isEmpty()) {
				int moved = Math.min(stack.getCount(), stack.getMaxStackSize());
				inventory.set(index, stack.copyWithCount(moved));
				stack.shrink(moved);
			}
		}

		return stack.isEmpty();
	}

	private boolean canStacksCombine(ItemStack existing, ItemStack incoming) {
		return !existing.isEmpty()
				&& existing.isStackable()
				&& existing.getCount() < existing.getMaxStackSize()
				&& ItemStack.isSameItemSameComponents(existing, incoming);
	}

	private void broadcastChanges() {
		if (first.containerMenu instanceof TradeMenu firstMenu && firstMenu.getSessionId() == id) {
			firstMenu.broadcastChanges();
		}

		if (second.containerMenu instanceof TradeMenu secondMenu && secondMenu.getSessionId() == id) {
			secondMenu.broadcastChanges();
		}
	}

	private static class TradeOfferContainer extends SimpleContainer {
		private final TradeSession session;
		private final UUID ownerId;

		TradeOfferContainer(TradeSession session, UUID ownerId) {
			super(TradeMenu.OFFER_SLOT_COUNT);
			this.session = session;
			this.ownerId = ownerId;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			session.offerChanged(ownerId);
		}
	}
}
