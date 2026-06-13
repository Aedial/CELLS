package com.cells.cells.configurable;

import javax.annotation.Nonnull;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.IContainerListener;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandlerModifiable;

import appeng.container.AEBaseContainer;
import appeng.container.slot.AppEngSlot;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;

import com.cells.gui.overlay.ServerMessageHelper;


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

    @SideOnly(Side.CLIENT)
    private GuiTextField typesField;

    // long values because they are treated as short, otherwise

    @GuiSync(0)
    public long maxPerType = Long.MAX_VALUE;

    @GuiSync(1)
    public long physicalMaxPerType = 0;

    @GuiSync(2)
    public int componentChannelTypeOrdinal = -1;

    @GuiSync(3)
    public int componentPresent = 0;

    @GuiSync(4)
    public long currentTypes = 0;

    @GuiSync(5)
    public long maxTypesConfig = 0;

    @GuiSync(6)
    public long userMaxTypes = Integer.MAX_VALUE;

    public ContainerConfigurableCell(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, null, null);
        this.hand = hand;
        this.lockedSlotIndex = (hand == EnumHand.MAIN_HAND) ? playerInv.currentItem : -1;

        this.cellStack = playerInv.player.getHeldItem(hand);

        // Component slot handler backed by cell NBT - gets cell dynamically from player's hand
        this.componentSlotHandler = new ComponentSlotHandler(playerInv.player, hand);

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

    @SideOnly(Side.CLIENT)
    public void setTypesField(GuiTextField field) {
        this.typesField = field;
        this.typesField.setText(this.userMaxTypes == Integer.MAX_VALUE ? "" : String.valueOf(this.userMaxTypes));
    }

    public void setMaxPerType(long value) {
        ComponentHelper.setMaxPerType(this.cellStack, value);
        this.maxPerType = value;
    }

    /**
     * Set the user-configured max types for this cell.
     * Validates the new value before applying.
     *
     * @param value The new max types value (Integer.MAX_VALUE for unlimited)
     * @return null if successful, or an error message key if validation failed
     */
    public String setUserMaxTypes(int value) {
        // Treat empty/unlimited as config max
        if (value == Integer.MAX_VALUE || value <= 0) {
            ComponentHelper.setUserMaxTypes(this.cellStack, Integer.MAX_VALUE);
            this.userMaxTypes = Integer.MAX_VALUE;
            return null;
        }

        // Validate the new value
        String error = ComponentHelper.validateNewMaxTypes(this.cellStack, value);
        if (error != null) return error;

        ComponentHelper.setUserMaxTypes(this.cellStack, value);
        this.userMaxTypes = value;
        return null;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) updateSyncValues();
    }

    private void updateSyncValues() {
        this.maxPerType = ComponentHelper.getMaxPerType(cellStack);
        this.userMaxTypes = ComponentHelper.getUserMaxTypes(cellStack);

        ComponentInfo info = ComponentHelper.getComponentInfo(ComponentHelper.getInstalledComponent(cellStack));
        if (info != null) {
            int configMaxTypes = info.getChannelType().getMaxTypes();
            int effectiveMaxTypes = ComponentHelper.getEffectiveMaxTypes(cellStack, info.getChannelType());
            this.physicalMaxPerType = ComponentHelper.calculatePhysicalPerTypeCapacity(info, effectiveMaxTypes);
            this.componentChannelTypeOrdinal = info.getChannelType().ordinal();
            this.componentPresent = 1;
            this.maxTypesConfig = configMaxTypes;

            // Get current stored types from NBT
            long[] summary = ComponentHelper.getStoredContentSummary(cellStack);
            this.currentTypes = (int) Math.min(summary[0], Integer.MAX_VALUE);
        } else {
            this.physicalMaxPerType = 0;
            this.componentChannelTypeOrdinal = -1;
            this.componentPresent = 0;
            this.maxTypesConfig = 0;
            this.currentTypes = 0;
        }
    }

    @Override
    public void onUpdate(String field, Object oldValue, Object newValue) {
        if (field.equals("maxPerType") && this.textField != null) {
            this.textField.setText(this.maxPerType == Long.MAX_VALUE ? "" : String.valueOf(this.maxPerType));
        }

        if (field.equals("userMaxTypes") && this.typesField != null) {
            this.typesField.setText(this.userMaxTypes == Integer.MAX_VALUE ? "" : String.valueOf(this.userMaxTypes));
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
     */
    @Override
    @Nonnull
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, @Nonnull EntityPlayer player) {
        // Prevent interactions with the locked slot (the cell in hand) if the container is open
        if (lockedSlotIndex >= 0 && slotId >= 0 && slotId < this.inventorySlots.size()) {
            Slot slot = this.inventorySlots.get(slotId);
            if (slot != null && slot.inventory instanceof InventoryPlayer) {
                int playerSlot = slot.getSlotIndex();
                if (playerSlot == lockedSlotIndex) return ItemStack.EMPTY;
            }
        }

        // Stacked cells represent one installed component per cell, so pickup
        // interactions must consume or return the full stack size at once.
        if (slotId == 0 && clickTypeIn == ClickType.PICKUP && cellStack.getCount() > 1) {
            return handleStackedComponentClick(player);
        }

        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    /**
     * Handle pickup clicks for stacked configurable cells.
     * The component slot represents one component per cell, so inserting or
     * extracting must always operate on the entire cell stack at once.
     */
    private ItemStack handleStackedComponentClick(EntityPlayer player) {
        ItemStack cursor = player.inventory.getItemStack();
        ItemStack installed = ComponentHelper.getInstalledComponent(cellStack);
        ItemStack displayedInstalled = ItemStack.EMPTY;
        int requiredComponents = cellStack.getCount();

        if (!installed.isEmpty()) {
            displayedInstalled = installed.copy();
            displayedInstalled.setCount(requiredComponents);
        }

        if (cursor.isEmpty()) {
            if (installed.isEmpty()) return ItemStack.EMPTY;
            if (ComponentHelper.hasContent(cellStack)) return displayedInstalled;

            player.inventory.setItemStack(displayedInstalled);
            ComponentHelper.setInstalledComponent(cellStack, ItemStack.EMPTY);
            refreshStackedComponentSlot(player);
            return displayedInstalled;
        }

        ComponentInfo cursorInfo = ComponentHelper.getComponentInfo(cursor);
        if (cursorInfo == null) return displayedInstalled;

        if (installed.isEmpty()) {
            if (cursor.getCount() < requiredComponents) {
                if (!player.world.isRemote && player instanceof EntityPlayerMP) {
                    ServerMessageHelper.warning(
                        (EntityPlayerMP) player, "cells.configurable_cell.split_stack");
                }
                return ItemStack.EMPTY;
            }

            ItemStack toInstall = cursor.copy();
            toInstall.setCount(1);
            ComponentHelper.setInstalledComponent(cellStack, toInstall);
            cursor.shrink(requiredComponents);
            if (cursor.getCount() <= 0) {
                player.inventory.setItemStack(ItemStack.EMPTY);
            }

            refreshStackedComponentSlot(player);
            return ItemStack.EMPTY;
        }

        if (ItemStack.areItemStacksEqual(cursor, installed)
            && ItemStack.areItemStackTagsEqual(cursor, installed)) {
            return displayedInstalled;
        }

        if (cursor.getCount() != requiredComponents) {
            if (!player.world.isRemote && player instanceof EntityPlayerMP) {
                ServerMessageHelper.warning(
                    (EntityPlayerMP) player, "cells.configurable_cell.split_stack");
            }
            return displayedInstalled;
        }

        if (ComponentHelper.hasContent(cellStack)
            && !ComponentHelper.canSwapComponent(cellStack, cursor)) {
            return displayedInstalled;
        }

        ItemStack toInstall = cursor.copy();
        toInstall.setCount(1);
        ComponentHelper.setInstalledComponent(cellStack, toInstall);
        player.inventory.setItemStack(displayedInstalled);
        refreshStackedComponentSlot(player);

        return displayedInstalled;
    }

    /**
     * Force the component slot to resync after a handled stacked-cell click.
     * The custom pickup path bypasses vanilla slot mutations, so swap updates
     * need an explicit refresh to avoid leaving the old component rendered.
     */
    private void refreshStackedComponentSlot(EntityPlayer player) {
        if (player.world.isRemote) {
            this.detectAndSendChanges();
            return;
        }

        syncAllSlots(player);

        if (player instanceof EntityPlayerMP) ((EntityPlayerMP) player).updateHeldItem();
    }

    /**
     * Transfer stack click (shift-click) - handle component slot interactions.
     * For stacked cells: extraction is allowed, and insertion requires
     * one component per cell in the held stack.
     */
    @Override
    @Nonnull
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        boolean isStacked = cellStack.getCount() > 1;

        if (index == 0) {
            // Shift-click component slot: move to player inventory
            ItemStack component = ComponentHelper.getInstalledComponent(cellStack);
            if (component.isEmpty()) return ItemStack.EMPTY;

            // Block extraction if the cell still has stored content
            if (ComponentHelper.hasContent(cellStack)) return ItemStack.EMPTY;

            // For stacked cells, extract one component per cell
            ItemStack toTransfer = component.copy();
            toTransfer.setCount(cellStack.getCount());

            if (!player.inventory.addItemStackToInventory(toTransfer)) return ItemStack.EMPTY;

            ComponentHelper.setInstalledComponent(cellStack, ItemStack.EMPTY);

            // Sync slot 0 and player inventory to client
            syncAllSlots(player);

            return component;
        }

        // Shift-click from player inventory: try to install as component
        if (isStacked) {
            Slot slot = this.inventorySlots.get(index);
            if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

            ItemStack slotStack = slot.getStack();
            if (ComponentHelper.getComponentInfo(slotStack) == null) return ItemStack.EMPTY;
            if (!ComponentHelper.getInstalledComponent(cellStack).isEmpty()) return ItemStack.EMPTY;

            int requiredComponents = cellStack.getCount();
            if (slotStack.getCount() < requiredComponents) {
                if (!player.world.isRemote && player instanceof EntityPlayerMP) {
                    ServerMessageHelper.warning(
                        (EntityPlayerMP) player, "cells.configurable_cell.split_stack");
                }
                return ItemStack.EMPTY;
            }

            ItemStack toInstall = slotStack.splitStack(requiredComponents);
            toInstall.setCount(1);
            ComponentHelper.setInstalledComponent(cellStack, toInstall);
            slot.onSlotChanged();

            syncAllSlots(player);

            return toInstall;
        }

        Slot slot = this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) return ItemStack.EMPTY;

        ItemStack slotStack = slot.getStack();
        if (ComponentHelper.getComponentInfo(slotStack) == null) return ItemStack.EMPTY;
        if (!ComponentHelper.getInstalledComponent(cellStack).isEmpty()) return ItemStack.EMPTY;

        ItemStack toInstall = slotStack.splitStack(1);
        ComponentHelper.setInstalledComponent(cellStack, toInstall);
        slot.onSlotChanged();

        // Sync all affected slots to client
        syncAllSlots(player);

        return toInstall;
    }

    /**
     * Sync all slot contents to the client.
     * Used after shift-click operations that may affect multiple slots.
     */
    private void syncAllSlots(EntityPlayer player) {
        if (player.world.isRemote) return;

        for (IContainerListener listener : this.listeners) {
            for (int i = 0; i < this.inventorySlots.size(); i++) {
                Slot slot = this.inventorySlots.get(i);
                listener.sendSlotContents(this, i, slot.getStack());
            }

            if (listener instanceof EntityPlayerMP) {
                ((EntityPlayerMP) listener).isChangingQuantityOnly = false;
            }
        }

        this.detectAndSendChanges();
    }

    /**
     * Custom IItemHandler for the component slot, backed by cell NBT.
     * <p>
     * Validates:
     * - Insert: must be a recognized component
     * - Swap: only allowed when the replacement component is compatible
     * - Extract: blocked if the cell has stored content
     */
    private static class ComponentSlotHandler implements IItemHandlerModifiable {

        private final EntityPlayer player;
        private final EnumHand hand;

        ComponentSlotHandler(EntityPlayer player, EnumHand hand) {
            this.player = player;
            this.hand = hand;
        }

        /**
         * Get the cell stack dynamically from the player's hand.
         * This ensures we always have the current state, not a stale reference.
         */
        private ItemStack getCellStack() {
            return player.getHeldItem(hand);
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Nonnull
        @Override
        public ItemStack getStackInSlot(int slot) {
            ItemStack cellStack = getCellStack();
            ItemStack component = ComponentHelper.getInstalledComponent(cellStack);
            if (component.isEmpty()) return ItemStack.EMPTY;

            // For stacked cells, show component count matching cell stack count
            // This indicates how many components will be extracted
            if (cellStack.getCount() > 1) {
                ItemStack display = component.copy();
                display.setCount(cellStack.getCount());
                return display;
            }

            return component;
        }

        @Override
        public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
            ItemStack cellStack = getCellStack();

            if (stack.isEmpty()) {
                ComponentHelper.setInstalledComponent(cellStack, ItemStack.EMPTY);
                return;
            }

            ItemStack normalized = stack.copy();
            normalized.setCount(1);
            ComponentHelper.setInstalledComponent(cellStack, normalized);
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            if (stack.isEmpty()) return false;

            ItemStack cellStack = getCellStack();
            int requiredComponents = cellStack.getCount();

            if (requiredComponents > 1 && stack.getCount() < requiredComponents) return false;

            ComponentInfo newInfo = ComponentHelper.getComponentInfo(stack);
            if (newInfo == null) return false;

            ItemStack currentComponent = ComponentHelper.getInstalledComponent(cellStack);
            if (currentComponent.isEmpty()) return true;

            if (ItemStack.areItemStacksEqual(currentComponent, stack)
                && ItemStack.areItemStackTagsEqual(currentComponent, stack)) {
                return true;
            }

            if (requiredComponents > 1 && stack.getCount() != requiredComponents) return false;

            return !ComponentHelper.hasContent(cellStack)
                || ComponentHelper.canSwapComponent(cellStack, stack);
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) return ItemStack.EMPTY;

            ItemStack cellStack = getCellStack();
            int requiredComponents = cellStack.getCount();

            if (requiredComponents > 1 && stack.getCount() < requiredComponents) {
                if (!simulate && !player.world.isRemote && player instanceof EntityPlayerMP) {
                    ServerMessageHelper.warning(
                        (EntityPlayerMP) player, "cells.configurable_cell.split_stack");
                }
                return stack;
            }

            // Must be a recognized component
            ComponentInfo newInfo = ComponentHelper.getComponentInfo(stack);
            if (newInfo == null) return stack;

            // Reject if a component is already installed
            ItemStack currentComponent = ComponentHelper.getInstalledComponent(cellStack);
            if (!currentComponent.isEmpty()) return stack;

            // No existing component - simple insert
            if (!simulate) {
                ItemStack toStore = stack.copy();
                toStore.setCount(1);
                ComponentHelper.setInstalledComponent(cellStack, toStore);
            }

            if (stack.getCount() > requiredComponents) {
                ItemStack remainder = stack.copy();
                remainder.shrink(requiredComponents);

                return remainder;
            }

            return ItemStack.EMPTY;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack cellStack = getCellStack();
            ItemStack component = ComponentHelper.getInstalledComponent(cellStack);
            if (component.isEmpty()) return ItemStack.EMPTY;

            // Block extraction if the cell still has stored content
            if (ComponentHelper.hasContent(cellStack)) return ItemStack.EMPTY;

            // For stacked cells, both left and right click should extract the full
            // component stack because the slot represents one component per cell.
            int extractCount = cellStack.getCount() > 1 ? cellStack.getCount() : Math.min(amount, 1);
            ItemStack result = component.copy();
            result.setCount(extractCount);

            if (!simulate) ComponentHelper.setInstalledComponent(cellStack, ItemStack.EMPTY);

            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            // The slot represents one component per cell in the held stack.
            return getCellStack().getCount();
        }
    }
}
