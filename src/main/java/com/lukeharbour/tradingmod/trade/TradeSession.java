package com.lukeharbour.tradingmod.trade;

import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

final class TradeSession {
	private enum State {
		ACTIVE,
		COMPLETED,
		CANCELLED
	}

	private static final int MENU_SIZE = 54;
	private static final int CHEST_COLUMNS = 9;
	private static final int OFFER_SLOT_COUNT = 12;
	private static final int CONFIRM_SLOT = 53;
	private static final int CANCEL_SLOT = 45;
	private static final int HELP_SLOT = 49;
	private static final int THEIR_LABEL_SLOT = 0;
	private static final int THEIR_STATUS_SLOT = 1;
	private static final int YOUR_STATUS_SLOT = 7;
	private static final int YOUR_LABEL_SLOT = 8;

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

	private final TradeManager manager;
	private final int id;
	private final Player first;
	private final Player second;
	private final UUID firstId;
	private final UUID secondId;

	private Inventory firstMenu;
	private Inventory secondMenu;
	private ItemStack[] firstOffer = new ItemStack[OFFER_SLOT_COUNT];
	private ItemStack[] secondOffer = new ItemStack[OFFER_SLOT_COUNT];
	private boolean firstConfirmed;
	private boolean secondConfirmed;
	private boolean closing;
	private State state = State.ACTIVE;

	TradeSession(TradeManager manager, int id, Player first, Player second) {
		this.manager = manager;
		this.id = id;
		this.first = first;
		this.second = second;
		this.firstId = first.getUniqueId();
		this.secondId = second.getUniqueId();
	}

	int id() {
		return id;
	}

	boolean isActive() {
		return state == State.ACTIVE;
	}

	void open() {
		firstMenu = createMenu(first, second);
		secondMenu = createMenu(second, first);
		updateMenus();

		first.openInventory(firstMenu);
		second.openInventory(secondMenu);
		first.sendMessage(Component.text("Trade Request Accepted with " + second.getName() + ".", NamedTextColor.GREEN));
		second.sendMessage(Component.text("Trade Request Accepted with " + first.getName() + ".", NamedTextColor.GREEN));
	}

	void handleClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player) || !contains(player)) {
			return;
		}

		if (state != State.ACTIVE) {
			event.setCancelled(true);
			return;
		}

		if (event.getClick() == ClickType.DOUBLE_CLICK) {
			event.setCancelled(true);
			return;
		}

		int rawSlot = event.getRawSlot();
		if (rawSlot < 0) {
			return;
		}

		if (rawSlot < MENU_SIZE) {
			handleTopClick(event, player, rawSlot);
			return;
		}

		handlePlayerInventoryClick(event, player);
	}

	void handleDrag(InventoryDragEvent event) {
		if (!(event.getWhoClicked() instanceof Player player) || !contains(player)) {
			return;
		}

		if (state != State.ACTIVE) {
			event.setCancelled(true);
			return;
		}

		boolean touchesTradeMenu = event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < MENU_SIZE);
		if (!touchesTradeMenu) {
			return;
		}

		boolean touchesBlockedSlot = event.getRawSlots().stream()
				.anyMatch(slot -> slot >= 0 && slot < MENU_SIZE && !isLocalOfferSlot(slot));
		if (touchesBlockedSlot || isConfirmed(player)) {
			event.setCancelled(true);
			return;
		}

		scheduleOfferChanged(player);
	}

	void handleClose(Player player) {
		if (closing || state != State.ACTIVE || !contains(player)) {
			return;
		}

		cancel(Component.text(player.getName() + " closed the trade.", NamedTextColor.RED));
	}

	void setConfirmed(Player player, boolean confirmed) {
		if (state != State.ACTIVE || !contains(player)) {
			return;
		}

		syncOffersFromMenus();
		if (firstId.equals(player.getUniqueId())) {
			firstConfirmed = confirmed;
		} else {
			secondConfirmed = confirmed;
		}

		updateMenus();

		if (firstConfirmed && secondConfirmed) {
			completeIfPossible();
		}
	}

	void cancel(Component reason) {
		if (state != State.ACTIVE) {
			return;
		}

		syncOffersFromMenus();
		ItemStack[] returnFirst = cloneOffer(firstOffer);
		ItemStack[] returnSecond = cloneOffer(secondOffer);

		state = State.CANCELLED;
		closing = true;
		firstConfirmed = false;
		secondConfirmed = false;
		clearTradeMenus();
		manager.remove(this);

		first.sendMessage(reason);
		second.sendMessage(reason);
		first.closeInventory();
		second.closeInventory();
		giveOrDrop(first, returnFirst);
		giveOrDrop(second, returnSecond);
	}

	private Inventory createMenu(Player viewer, Player partner) {
		TradeInventoryHolder holder = new TradeInventoryHolder(this, viewer.getUniqueId());
		Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, "Trade with " + partner.getName());
		holder.setInventory(inventory);
		return inventory;
	}

	private void handleTopClick(InventoryClickEvent event, Player player, int rawSlot) {
		if (rawSlot == CONFIRM_SLOT) {
			event.setCancelled(true);
			setConfirmed(player, !isConfirmed(player));
			return;
		}

		if (rawSlot == CANCEL_SLOT) {
			event.setCancelled(true);
			cancel(Component.text(player.getName() + " cancelled the trade.", NamedTextColor.RED));
			return;
		}

		if (isRemoteOfferSlot(rawSlot) || isDisplaySlot(rawSlot)) {
			event.setCancelled(true);
			return;
		}

		if (isLocalOfferSlot(rawSlot)) {
			if (isConfirmed(player)) {
				event.setCancelled(true);
				return;
			}

			scheduleOfferChanged(player);
			return;
		}

		event.setCancelled(true);
	}

	private void handlePlayerInventoryClick(InventoryClickEvent event, Player player) {
		if (!event.isShiftClick()) {
			return;
		}

		ItemStack clicked = event.getCurrentItem();
		if (isEmpty(clicked)) {
			return;
		}

		event.setCancelled(true);
		if (isConfirmed(player)) {
			return;
		}

		ItemStack remaining = clicked.clone();
		int originalAmount = remaining.getAmount();
		moveIntoLocalOffer(player, remaining);

		if (remaining.getAmount() != originalAmount) {
			event.setCurrentItem(emptyToNull(remaining));
			handleOfferChanged(player);
		}
	}

	private void scheduleOfferChanged(Player owner) {
		manager.runNextTick(() -> handleOfferChanged(owner));
	}

	private void handleOfferChanged(Player owner) {
		if (state != State.ACTIVE || !contains(owner)) {
			return;
		}

		ItemStack[] before = firstId.equals(owner.getUniqueId()) ? cloneOffer(firstOffer) : cloneOffer(secondOffer);
		syncOffersFromMenus();
		ItemStack[] after = firstId.equals(owner.getUniqueId()) ? firstOffer : secondOffer;

		if (!offersEqual(before, after) && (firstConfirmed || secondConfirmed)) {
			firstConfirmed = false;
			secondConfirmed = false;
			first.sendMessage(Component.text("Trade confirmations reset because an offer changed.", NamedTextColor.YELLOW));
			second.sendMessage(Component.text("Trade confirmations reset because an offer changed.", NamedTextColor.YELLOW));
		}

		updateMenus();
	}

	private void completeIfPossible() {
		syncOffersFromMenus();

		if (!canFitAll(first, secondOffer)) {
			resetAfterFailedCompletion(first, "Your inventory needs room for " + second.getName() + "'s offer.");
			return;
		}

		if (!canFitAll(second, firstOffer)) {
			resetAfterFailedCompletion(second, "Your inventory needs room for " + first.getName() + "'s offer.");
			return;
		}

		ItemStack[] firstOutgoing = cloneOffer(firstOffer);
		ItemStack[] secondOutgoing = cloneOffer(secondOffer);
		state = State.COMPLETED;
		closing = true;
		clearTradeMenus();
		manager.remove(this);

		first.closeInventory();
		second.closeInventory();
		giveOrDrop(first, secondOutgoing);
		giveOrDrop(second, firstOutgoing);
		first.sendMessage(Component.text("Trade completed with " + second.getName() + ".", NamedTextColor.GREEN));
		second.sendMessage(Component.text("Trade completed with " + first.getName() + ".", NamedTextColor.GREEN));
	}

	private void resetAfterFailedCompletion(Player player, String message) {
		firstConfirmed = false;
		secondConfirmed = false;
		player.sendMessage(Component.text(message, NamedTextColor.RED));
		partnerOf(player).sendMessage(Component.text(player.getName() + " needs more inventory space.", NamedTextColor.RED));
		updateMenus();
	}

	private void moveIntoLocalOffer(Player player, ItemStack stack) {
		Inventory inventory = inventoryFor(player);
		if (inventory == null || isEmpty(stack)) {
			return;
		}

		for (int slot : LOCAL_OFFER_SLOTS) {
			ItemStack existing = inventory.getItem(slot);
			if (!isEmpty(existing) && existing.isSimilar(stack)) {
				int moved = Math.min(stack.getAmount(), existing.getMaxStackSize() - existing.getAmount());
				if (moved > 0) {
					existing.setAmount(existing.getAmount() + moved);
					stack.setAmount(stack.getAmount() - moved);
					inventory.setItem(slot, existing);
				}

				if (isEmpty(stack)) {
					return;
				}
			}
		}

		for (int slot : LOCAL_OFFER_SLOTS) {
			if (isEmpty(inventory.getItem(slot))) {
				int moved = Math.min(stack.getAmount(), stack.getMaxStackSize());
				ItemStack placed = stack.clone();
				placed.setAmount(moved);
				inventory.setItem(slot, placed);
				stack.setAmount(stack.getAmount() - moved);

				if (isEmpty(stack)) {
					return;
				}
			}
		}
	}

	private void syncOffersFromMenus() {
		firstOffer = readLocalOffer(firstMenu);
		secondOffer = readLocalOffer(secondMenu);
	}

	private ItemStack[] readLocalOffer(Inventory inventory) {
		ItemStack[] offer = new ItemStack[OFFER_SLOT_COUNT];
		if (inventory == null) {
			return offer;
		}

		for (int index = 0; index < LOCAL_OFFER_SLOTS.length; index++) {
			offer[index] = cloneOrNull(inventory.getItem(LOCAL_OFFER_SLOTS[index]));
		}

		return offer;
	}

	private void updateMenus() {
		updateMenu(firstMenu, first, secondOffer, firstOffer, second.getName(), firstConfirmed, secondConfirmed);
		updateMenu(secondMenu, second, firstOffer, secondOffer, first.getName(), secondConfirmed, firstConfirmed);
	}

	private void updateMenu(Inventory inventory, Player viewer, ItemStack[] remoteOffer, ItemStack[] localOffer,
			String partnerName, boolean localConfirmed, boolean remoteConfirmed) {
		if (inventory == null) {
			return;
		}

		for (int slot = 0; slot < MENU_SIZE; slot++) {
			if (isDisplaySlot(slot)) {
				inventory.setItem(slot, filler(slot));
			}
		}

		for (int index = 0; index < OFFER_SLOT_COUNT; index++) {
			inventory.setItem(REMOTE_OFFER_SLOTS[index], cloneOrNull(remoteOffer[index]));
			inventory.setItem(LOCAL_OFFER_SLOTS[index], cloneOrNull(localOffer[index]));
		}

		inventory.setItem(THEIR_LABEL_SLOT, named(Material.CHEST, "Their Offer",
				partnerName + "'s offered items are read-only."));
		inventory.setItem(THEIR_STATUS_SLOT, statusItem(partnerName, remoteConfirmed));
		inventory.setItem(YOUR_STATUS_SLOT, statusItem(viewer.getName(), localConfirmed));
		inventory.setItem(YOUR_LABEL_SLOT, named(Material.CHEST, "Your Offer",
				"Place your offered items in the right-side slots."));
		inventory.setItem(CANCEL_SLOT, named(Material.BARRIER, "Cancel Trade",
				"Click to cancel and return offered items."));
		inventory.setItem(HELP_SLOT, named(Material.PAPER, "How Trading Works",
				"Left side: their offer.",
				"Right side: your offer.",
				"Both players must click Confirm."));
		inventory.setItem(CONFIRM_SLOT, localConfirmed
				? named(Material.RED_WOOL, "Click to Unconfirm", "Unlock your offer before changing items.")
				: named(Material.EMERALD, "Click to Confirm", "Locks your offer until you unconfirm or an offer changes."));
	}

	private ItemStack statusItem(String name, boolean confirmed) {
		return named(confirmed ? Material.GREEN_WOOL : Material.GRAY_WOOL,
				name + ": " + (confirmed ? "Confirmed" : "Waiting"),
				confirmed ? "This side is locked in." : "Waiting for confirmation.");
	}

	private ItemStack filler(int slot) {
		return named(slot % CHEST_COLUMNS == 4 ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
				" ");
	}

	private void clearTradeMenus() {
		clearTradeMenu(firstMenu);
		clearTradeMenu(secondMenu);
	}

	private void clearTradeMenu(Inventory inventory) {
		if (inventory == null) {
			return;
		}

		for (int slot : REMOTE_OFFER_SLOTS) {
			inventory.setItem(slot, null);
		}

		for (int slot : LOCAL_OFFER_SLOTS) {
			inventory.setItem(slot, null);
		}
	}

	private boolean canFitAll(Player player, ItemStack[] incoming) {
		ItemStack[] simulated = cloneContents(player.getInventory().getStorageContents());

		for (ItemStack item : incoming) {
			if (!isEmpty(item) && !insertInto(simulated, item.clone())) {
				return false;
			}
		}

		return true;
	}

	private boolean insertInto(ItemStack[] inventory, ItemStack stack) {
		for (ItemStack existing : inventory) {
			if (isEmpty(stack)) {
				return true;
			}

			if (!isEmpty(existing) && existing.isSimilar(stack)) {
				int moved = Math.min(stack.getAmount(), existing.getMaxStackSize() - existing.getAmount());
				if (moved > 0) {
					existing.setAmount(existing.getAmount() + moved);
					stack.setAmount(stack.getAmount() - moved);
				}
			}
		}

		for (int index = 0; index < inventory.length; index++) {
			if (isEmpty(stack)) {
				return true;
			}

			if (isEmpty(inventory[index])) {
				int moved = Math.min(stack.getAmount(), stack.getMaxStackSize());
				ItemStack placed = stack.clone();
				placed.setAmount(moved);
				inventory[index] = placed;
				stack.setAmount(stack.getAmount() - moved);
			}
		}

		return isEmpty(stack);
	}

	private void giveOrDrop(Player player, ItemStack[] items) {
		PlayerInventory inventory = player.getInventory();

		for (ItemStack item : items) {
			if (isEmpty(item)) {
				continue;
			}

			HashMap<Integer, ItemStack> leftovers = inventory.addItem(item.clone());
			for (ItemStack leftover : leftovers.values()) {
				player.getWorld().dropItemNaturally(player.getLocation(), leftover);
			}
		}
	}

	private boolean contains(Player player) {
		return firstId.equals(player.getUniqueId()) || secondId.equals(player.getUniqueId());
	}

	private Player partnerOf(Player player) {
		return firstId.equals(player.getUniqueId()) ? second : first;
	}

	private Inventory inventoryFor(Player player) {
		if (firstId.equals(player.getUniqueId())) {
			return firstMenu;
		}

		if (secondId.equals(player.getUniqueId())) {
			return secondMenu;
		}

		return null;
	}

	private boolean isConfirmed(Player player) {
		return firstId.equals(player.getUniqueId()) ? firstConfirmed : secondConfirmed;
	}

	private boolean isDisplaySlot(int slot) {
		return slot >= 0 && slot < MENU_SIZE && !isRemoteOfferSlot(slot) && !isLocalOfferSlot(slot);
	}

	private static boolean isRemoteOfferSlot(int slot) {
		return containsSlot(REMOTE_OFFER_SLOTS, slot);
	}

	private static boolean isLocalOfferSlot(int slot) {
		return containsSlot(LOCAL_OFFER_SLOTS, slot);
	}

	private static boolean containsSlot(int[] slots, int slot) {
		for (int candidate : slots) {
			if (candidate == slot) {
				return true;
			}
		}

		return false;
	}

	private static ItemStack named(Material material, String name, String... lore) {
		ItemStack stack = new ItemStack(material);
		ItemMeta meta = stack.getItemMeta();
		meta.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));

		if (lore.length > 0) {
			meta.lore(Arrays.stream(lore)
					.map(line -> Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
					.toList());
		}

		stack.setItemMeta(meta);
		return stack;
	}

	private static boolean offersEqual(ItemStack[] first, ItemStack[] second) {
		for (int index = 0; index < OFFER_SLOT_COUNT; index++) {
			if (!sameStack(first[index], second[index])) {
				return false;
			}
		}

		return true;
	}

	private static boolean sameStack(ItemStack first, ItemStack second) {
		if (isEmpty(first) && isEmpty(second)) {
			return true;
		}

		if (isEmpty(first) || isEmpty(second)) {
			return false;
		}

		return first.getAmount() == second.getAmount() && first.isSimilar(second);
	}

	private static ItemStack[] cloneOffer(ItemStack[] offer) {
		return cloneContents(offer);
	}

	private static ItemStack[] cloneContents(ItemStack[] contents) {
		ItemStack[] copy = new ItemStack[contents.length];
		for (int index = 0; index < contents.length; index++) {
			copy[index] = cloneOrNull(contents[index]);
		}

		return copy;
	}

	private static ItemStack cloneOrNull(ItemStack item) {
		return isEmpty(item) ? null : item.clone();
	}

	private static ItemStack emptyToNull(ItemStack item) {
		return isEmpty(item) ? null : item;
	}

	private static boolean isEmpty(ItemStack item) {
		return item == null || item.getType().isAir() || item.getAmount() <= 0;
	}
}
