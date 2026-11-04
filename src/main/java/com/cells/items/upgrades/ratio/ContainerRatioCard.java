package com.cells.items.upgrades.ratio;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotRestrictedInput;
import appeng.util.Platform;

import com.cells.integration.nae2.pmt.PMTManager;
import com.cells.integration.nae2.pmt.PMTSlot;
import com.cells.items.upgrades.RatioControlCard;


public class ContainerRatioCard extends AEBaseContainer {

    public static final int PMT_X = -90;
    public static final int PMT_Y = 0;

    private static final int SLOT_SPACING = 18;
    private static final int PATTERNS_X = 8;
    private static final int PATTERNS_Y = 18;
    private static final int PMT_SLOT_MARGIN = 8;
    private static final int PLAYER_INVENTORY_Y = 85;
    private static final int PATTERN_ICON = 8 * 16 + 15;

    private final EnumHand hand;
    private final ItemStack card;
    private final RatioCardInventory patterns;
    private final PMTManager pmt;
    private final int playerSlots;

    public ContainerRatioCard(InventoryPlayer player, EnumHand hand) {
        super(player, null, null);
        this.hand = hand;
        this.card = player.player.getHeldItem(hand);
        this.patterns = new RatioCardInventory(this.card);
        this.pmt = PMTManager.findPMT(player.player);

        for (int slot = 0; slot < this.patterns.getSlots(); slot++) {
            SlotRestrictedInput patternSlot = new SlotRestrictedInput(
                SlotRestrictedInput.PlacableItemType.ENCODED_PATTERN, this.patterns, slot,
                PATTERNS_X + slot % RatioCardInventory.COLUMNS * SLOT_SPACING,
                PATTERNS_Y + slot / RatioCardInventory.COLUMNS * SLOT_SPACING, player);
            patternSlot.setStackLimit(RatioCardInventory.SLOT_LIMIT);
            patternSlot.setIIcon(PATTERN_ICON);
            addSlotToContainer(patternSlot);
        }

        if (this.pmt != null) {
            for (int row = 0; row < PMTManager.ROWS; row++) {
                for (int column = 0; column < PMTManager.COLUMNS; column++) {
                    int slot = column * PMTManager.ROWS + row;
                    addSlotToContainer(new PMTSlot(
                        this.pmt.getPatternInventory(), this.pmt, slot,
                        PMT_X + PMT_SLOT_MARGIN + column * SLOT_SPACING,
                        PMT_Y + 1 + PMT_SLOT_MARGIN + row * SLOT_SPACING,
                        column, player));
                }
            }
        }

        for (int slot = 0; slot < player.getSizeInventory(); slot++) {
            ItemStack stack = player.getStackInSlot(slot);
            if (stack == this.card || this.pmt != null && stack == this.pmt.getPMTStack()) {
                lockPlayerInventorySlot(slot);
            }
        }

        this.playerSlots = this.inventorySlots.size();
        bindPlayerInventory(player, 0, PLAYER_INVENTORY_Y);
    }

    public PMTManager getPMT() {
        return this.pmt;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (!(player.getHeldItem(this.hand).getItem() instanceof RatioControlCard)) return false;

        return Platform.isClient() || player.getHeldItem(this.hand) == this.card
            && (this.pmt == null || this.pmt.isPresent(player));
    }

    @Override
    public ItemStack slotClick(int slotId, int button, ClickType action, EntityPlayer player) {
        if (!canInteractWith(player)) return ItemStack.EMPTY;

        if (action == ClickType.SWAP && button >= 0 && button < player.inventory.getSizeInventory()) {
            ItemStack swapping = player.inventory.getStackInSlot(button);
            if (swapping == player.getHeldItem(this.hand)
                    || this.pmt != null && PMTManager.isPMTItem(swapping)) {
                return ItemStack.EMPTY;
            }
        }

        if (slotId >= 0 && slotId < this.inventorySlots.size()) {
            Slot slot = this.inventorySlots.get(slotId);
            if (slot instanceof PMTSlot && !((PMTSlot) slot).isSlotEnabled()) return ItemStack.EMPTY;
        }

        return super.slotClick(slotId, button, action, player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (Platform.isClient() || !canInteractWith(player)) return ItemStack.EMPTY;
        if (index < 0 || index >= this.inventorySlots.size()) return ItemStack.EMPTY;

        Slot slot = this.inventorySlots.get(index);
        if (!slot.getHasStack() || !slot.canTakeStack(player)) return ItemStack.EMPTY;
        if (slot instanceof PMTSlot && !((PMTSlot) slot).isSlotEnabled()) return ItemStack.EMPTY;

        ItemStack stack = slot.getStack();
        if (stack == this.card || this.pmt != null && stack == this.pmt.getPMTStack()) return ItemStack.EMPTY;

        ItemStack original = stack.copy();
        if (index < this.patterns.getSlots()) {
            if (!moveToPMT(stack)) {
                mergeItemStack(stack, this.playerSlots, this.inventorySlots.size(), true);
            }
        } else if (index < this.playerSlots) {
            if (!mergeItemStack(stack, 0, this.patterns.getSlots(), false)) {
                mergeItemStack(stack, this.playerSlots, this.inventorySlots.size(), true);
            }
        } else if (!mergeItemStack(stack, 0, this.patterns.getSlots(), false)) {
            moveToPMT(stack);
        }

        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;

        if (stack.isEmpty()) slot.putStack(ItemStack.EMPTY);
        else slot.onSlotChanged();
        slot.onTake(player, stack);
        player.inventory.markDirty();
        detectAndSendChanges();
        return ItemStack.EMPTY;
    }

    private boolean moveToPMT(ItemStack stack) {
        boolean moved = false;
        for (int pass = 0; pass < 2; pass++) {
            for (int index = this.patterns.getSlots(); index < this.playerSlots; index++) {
                PMTSlot slot = (PMTSlot) this.inventorySlots.get(index);
                if (!slot.isSlotEnabled() || slot.getHasStack() != (pass == 0)) continue;

                moved |= mergeItemStack(stack, index, index + 1, false);
                if (stack.isEmpty()) return moved;
            }
        }

        return moved;
    }

    @Override
    public void detectAndSendChanges() {
        if (Platform.isServer()) {
            boolean changed = this.patterns.consumeDirty();
            if (this.pmt != null) changed |= this.pmt.consumeDirty();
            if (changed) getInventoryPlayer().markDirty();
        }

        super.detectAndSendChanges();
    }
}
