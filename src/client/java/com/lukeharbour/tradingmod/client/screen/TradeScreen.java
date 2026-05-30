package com.lukeharbour.tradingmod.client.screen;

import com.lukeharbour.tradingmod.network.ConfirmTradePayload;
import com.lukeharbour.tradingmod.trade.TradeMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class TradeScreen extends AbstractContainerScreen<TradeMenu> {
	private static final int PANEL = 0xF014161B;
	private static final int PANEL_BORDER = 0xFF59616D;
	private static final int REMOTE_PANEL = 0xFF20252F;
	private static final int LOCAL_PANEL = 0xFF1E2A27;
	private static final int SLOT = 0xFF0D0F14;
	private static final int SLOT_BORDER = 0xFF3A414B;
	private static final int READY = 0xFF43D17D;
	private static final int WAITING = 0xFF8B95A3;
	private static final int TEXT = 0xFFE8EEF5;
	private static final int SUBTLE_TEXT = 0xFFAEB7C3;

	private Button confirmButton;

	public TradeScreen(TradeMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title, TradeMenu.IMAGE_WIDTH, TradeMenu.IMAGE_HEIGHT);
		titleLabelX = 12;
		titleLabelY = 10;
		inventoryLabelX = TradeMenu.PLAYER_INV_X;
		inventoryLabelY = TradeMenu.PLAYER_INV_Y - 12;
	}

	@Override
	protected void init() {
		super.init();
		confirmButton = Button.builder(confirmMessage(), button -> ClientPlayNetworking.send(
						new ConfirmTradePayload(menu.getSessionId(), !menu.isLocalConfirmed())))
				.bounds(leftPos + (imageWidth - 98) / 2, topPos + 96, 98, 20)
				.build();
		addRenderableWidget(confirmButton);
		updateButton();
	}

	@Override
	protected void containerTick() {
		updateButton();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		updateButton();
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);

		int x = leftPos;
		int y = topPos;
		graphics.fill(x, y, x + imageWidth, y + imageHeight, PANEL);
		graphics.outline(x, y, imageWidth, imageHeight, PANEL_BORDER);

		drawOfferPanel(graphics, x + 12, y + 25, 92, 90, menu.isRemoteConfirmed(), REMOTE_PANEL);
		drawOfferPanel(graphics, x + 122, y + 25, 92, 90, menu.isLocalConfirmed(), LOCAL_PANEL);
		drawSlotGrid(graphics, x + TradeMenu.REMOTE_OFFER_X, y + TradeMenu.OFFER_Y, TradeMenu.OFFER_COLUMNS, TradeMenu.OFFER_ROWS);
		drawSlotGrid(graphics, x + TradeMenu.LOCAL_OFFER_X, y + TradeMenu.OFFER_Y, TradeMenu.OFFER_COLUMNS, TradeMenu.OFFER_ROWS);
		drawSlotGrid(graphics, x + TradeMenu.PLAYER_INV_X, y + TradeMenu.PLAYER_INV_Y, 9, 3);
		drawSlotGrid(graphics, x + TradeMenu.PLAYER_INV_X, y + TradeMenu.PLAYER_INV_Y + 58, 9, 1);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.text(font, title, titleLabelX, titleLabelY, TEXT);
		graphics.text(font, Component.literal("Their Offer"), TradeMenu.REMOTE_OFFER_X, 27, TEXT);
		graphics.text(font, Component.literal("Your Offer"), TradeMenu.LOCAL_OFFER_X, 27, TEXT);
		graphics.text(font, Component.literal(menu.getPartnerName()), TradeMenu.REMOTE_OFFER_X, 100, SUBTLE_TEXT);
		graphics.text(font, statusText(menu.isRemoteConfirmed()), TradeMenu.REMOTE_OFFER_X, 111, statusColor(menu.isRemoteConfirmed()));
		graphics.text(font, statusText(menu.isLocalConfirmed()), TradeMenu.LOCAL_OFFER_X, 111, statusColor(menu.isLocalConfirmed()));
		graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, SUBTLE_TEXT);
	}

	private void drawOfferPanel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, boolean confirmed, int color) {
		graphics.fill(x, y, x + width, y + height, color);
		graphics.outline(x, y, width, height, confirmed ? READY : PANEL_BORDER);
	}

	private void drawSlotGrid(GuiGraphicsExtractor graphics, int x, int y, int columns, int rows) {
		for (int row = 0; row < rows; row++) {
			for (int column = 0; column < columns; column++) {
				int slotX = x + column * 18 - 1;
				int slotY = y + row * 18 - 1;
				graphics.fill(slotX, slotY, slotX + 18, slotY + 18, SLOT);
				graphics.outline(slotX, slotY, 18, 18, SLOT_BORDER);
			}
		}
	}

	private void updateButton() {
		if (confirmButton != null) {
			confirmButton.active = !menu.isComplete();
			confirmButton.setMessage(confirmMessage());
		}
	}

	private Component confirmMessage() {
		return Component.literal(menu.isLocalConfirmed() ? "Confirmed" : "Confirm");
	}

	private Component statusText(boolean confirmed) {
		return Component.literal(confirmed ? "Confirmed" : "Waiting");
	}

	private int statusColor(boolean confirmed) {
		return confirmed ? READY : WAITING;
	}
}
