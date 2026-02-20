package com.cells.cells.configurable;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.items.IItemHandler;

import appeng.container.AEBaseContainer;
import appeng.container.slot.AppEngSlot;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;

import com.cells.config.CellsConfig;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Container for the Configurable Storage Cell GUI.
 * <p>
 * Provides a component slot (reads/writes to cell NBT) and syncs the
 * per-type capacity value between client and server.
 */
public class ContainerConfigurableCell extends AEBaseContainer {

    /** The hand holding the cell, used to lock the slot */
    private final EnumHand hand;

    /** Index of the held cell in the player's inventory (-1 for offhand) */
    private final int lockedSlotIndex;

    /** The cell ItemStack - cached reference to the held cell */
    private final ItemStack cellStack;

    /** The component slot handler backed by cell NBT */
    private final ComponentSlotHandler componentSlotHandler;

    @SideOnly(Side.CLIENT)
    private GuiTextField textField;

    @GuiSync(0)
    public long maxPerType = Long.MAX_VALUE;

    @GuiSync(1)
    public long physicalMaxPerType = 0;

    @GuiSync(2)
    public int componentIsFluid = 0;

    @GuiSync(3)
    public int componentPresent = 0;

    public ContainerConfigurableCell(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, null, null);
        this.hand = hand;
        this.lockedSlotIndex = (hand == EnumHand.MAIN_HAND) ? playerInv.currentItem : -1;

        // If the held cell is stacked, split one off so NBT modifications
        // only affect a single cell instead of duplicating the component across the stack.
        ItemStack held = playerInv.player.getHeldItem(hand);
        if (held.getCount() > 1) {
            ItemStack single = held.splitStack(1);
            playerInv.player.setHeldItem(hand, single);

            // Return the remaining stack to inventory, or drop if full
            if (!playerInv.addItemStackToInventory(held)) playerInv.player.dropItem(held, false);
        }

        this.cellStack = playerInv.player.getHeldItem(hand);

        // Component slot handler backed by cell NBT
        this.componentSlotHandler = new ComponentSlotHandler(cellStack);

        // Add the component slot at position (6, 6) in the GUI
        addSlotToContainer(new AppEngSlot(componentSlotHandler, 0, 6, 6));

        // Bind player inventory - start at y=102 to leave room for our custom GUI area
        bindPlayerInventory(playerInv, 0, 102);

        // Initialize sync values
        updateSyncValues();
    }

    @SideOnly(Side.CLIENT)
    public void setTextField(GuiTextField field) {
        this.textField = field;
        this.textField.setText(this.maxPerType == Long.MAX_VALUE ? "" : String.valueOf(this.maxPerType));
    }

    public void setMaxPerType(long value) {
        ComponentHelper.setMaxPerType(this.cellStack, value);
        this.maxPerType = value;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) updateSyncValues();
    }

    private void updateSyncValues() {
        this.maxPerType = ComponentHelper.getMaxPerType(cellStack);

        ComponentInfo info = ComponentHelper.getComponentInfo(ComponentHelper.getInstalledComponent(cellStack));
        if (info != null) {
            this.physicalMaxPerType = ComponentHelper.calculatePhysicalPerTypeCapacity(info, CellsConfig.configurableCellMaxTypes);
            this.componentIsFluid = info.isFluid() ? 1 : 0;
            this.componentPresent = 1;
        } else {
            this.physicalMaxPerType = 0;
            this.componentIsFluid = 0;
            this.componentPresent = 0;
        }
    }

    @Override
    public void onUpdate(String field, Object oldValue, Object newValue) {
        if (field.equals("maxPerType") && this.textField != null) {
            this.textField.setText(this.maxPerType == Long.MAX_VALUE ? "" : String.valueOf(this.maxPerType));
        }

        super.onUpdate(field, oldValue, newValue);
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        // Cell must still be in the player's hand
        ItemStack held = playerIn.getHeldItem(hand);

        return !held.isEmpty() && held.getItem() instanceof ItemConfigurableCell;
    }

    /**
     * Prevent moving the held cell via hotbar swap, shift-click, etc.
     * Custom handling for the component slot (slot 0) to support swap.
     */
    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        // Prevent interactions with the locked slot (the cell in hand) if the container is open
        if (lockedSlotIndex >= 0 && slotId >= 0 && slotId < this.inventorySlots.size()) {
            Slot slot = this.inventorySlots.get(slotId);
            if (slot != null && slot.inventory instanceof InventoryPlayer) {
                int playerSlot = slot.getSlotIndex();
                if (playerSlot == lockedSlotIndex) return ItemStack.EMPTY;
            }
        }

        // Custom handling for component slot (slot 0) left/right clicks.
        // SlotItemHandler cannot handle swaps for non-IItemHandlerModifiable handlers,
        // so we manage the component slot interactions directly.
        if (slotId == 0 && clickTypeIn == ClickType.PICKUP) return handleComponentSlotClick(player);

        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    /**
     * Handle left/right click on the component slot (slot 0).
     * Supports: extract, insert, and swap operations.
     * <p>
     * If the cell has content, extraction is blocked. Swapping is only allowed
     * if the new component uses the same storage channel and has enough capacity
     * for the existing content.
     */
    private ItemStack handleComponentSlotClick(EntityPlayer player) {
        ItemStack cursor = player.inventory.getItemStack();
        ItemStack installed = ComponentHelper.getInstalledComponent(cellStack);

        if (cursor.isEmpty()) {
            // Empty cursor + non-empty slot: extract component to cursor
            if (installed.isEmpty()) return ItemStack.EMPTY;

            // Block extraction if the cell still has stored content
            if (ComponentHelper.hasContent(cellStack)) return ItemStack.EMPTY;

            player.inventory.setItemStack(installed.copy());
            ComponentHelper.setInstalledComponent(cellStack, ItemStack.EMPTY);

            return installed;
        }

        // Cursor has item: must be a valid component
        if (ComponentHelper.getComponentInfo(cursor) == null) return ItemStack.EMPTY;

        if (installed.isEmpty()) {
            // Empty slot: install one from cursor
            ItemStack toInstall = cursor.splitStack(1);
            ComponentHelper.setInstalledComponent(cellStack, toInstall);

            if (cursor.isEmpty()) player.inventory.setItemStack(ItemStack.EMPTY);

            return ItemStack.EMPTY;
        }

        // Both cursor and slot have components: swap (only if cursor count is 1)
        if (cursor.getCount() != 1) return ItemStack.EMPTY;

        // If the cell has content, only allow swapping to a compatible component
        // with enough capacity for the existing data
        if (ComponentHelper.hasContent(cellStack)
            && !ComponentHelper.canSwapComponent(cellStack, cursor)) return ItemStack.EMPTY;

        ItemStack oldComponent = installed.copy();
        ComponentHelper.setInstalledComponent(cellStack, cursor.copy());
        player.inventory.setItemStack(oldComponent);

        return ItemStack.EMPTY;
    }

    /**
     * Transfer stack click (shift-click) - handle component slot interactions.
     */
    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index == 0) {
            // Shift-click component slot: move to player inventory
            ItemStack component = ComponentHelper.getInstalledComponent(cellStack);
            if (component.isEmpty()) return ItemStack.EMPTY;

            // Block extraction if the cell still has stored content
            if (ComponentHelper.hasContent(cellStack)) return ItemStack.EMPTY;

            if (!player.inventory.addItemStackToInventory(component.copy())) return ItemStack.EMPTY;

            ComponentHelper.setInstalledComponent(cellStack, ItemStack.EMPTY);

            return component;
        }

        // Shift-click from player inventory: try to install as component
        Slot slot = this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        ItemStack slotStack = slot.getStack();
        if (ComponentHelper.getComponentInfo(slotStack) == null) return ItemStack.EMPTY;
        if (!ComponentHelper.getInstalledComponent(cellStack).isEmpty()) return ItemStack.EMPTY;

        ItemStack toInstall = slotStack.splitStack(1);
        ComponentHelper.setInstalledComponent(cellStack, toInstall);
        slot.onSlotChanged();

        return toInstall;
    }

    /**
     * Custom IItemHandler for the component slot, backed by cell NBT.
     * <p>
     * Validates:
     * - Insert: must be a recognized component
     * - Extract: blocked if cell has content (swap handled by slotClick)
     */
    private static class ComponentSlotHandler implements IItemHandler {

        private final ItemStack cellStack;

        ComponentSlotHandler(ItemStack cellStack) {
            this.cellStack = cellStack;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return ComponentHelper.getInstalledComponent(cellStack);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;

            // Must be a recognized component
            ComponentInfo newInfo = ComponentHelper.getComponentInfo(stack);
            if (newInfo == null) return stack;

            // Reject if a component is already installed (swap handled by slotClick)
            ItemStack currentComponent = ComponentHelper.getInstalledComponent(cellStack);
            if (!currentComponent.isEmpty()) return stack;

            // No existing component - simple insert
            if (!simulate) {
                ItemStack toStore = stack.copy();
                toStore.setCount(1);
                ComponentHelper.setInstalledComponent(cellStack, toStore);
            }

            if (stack.getCount() > 1) {
                ItemStack remainder = stack.copy();
                remainder.shrink(1);

                return remainder;
            }

            return ItemStack.EMPTY;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack component = ComponentHelper.getInstalledComponent(cellStack);
            if (component.isEmpty()) return ItemStack.EMPTY;

            // Block extraction if the cell still has stored content
            if (ComponentHelper.hasContent(cellStack)) return ItemStack.EMPTY;

            if (!simulate) ComponentHelper.setInstalledComponent(cellStack, ItemStack.EMPTY);

            return component;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    }
}
