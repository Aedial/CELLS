package com.cells.items.pullpush;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.api.parts.IPart;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;
import appeng.tile.inventory.AppEngInternalInventory;

import com.cells.ItemRegistry;
import com.cells.blocks.interfacebase.IFilterableInterfaceHost;
import com.cells.items.ItemAutoPullCard;
import com.cells.items.ItemAutoPushCard;


/**
 * Container for the Pull/Push Card configuration GUI.
 * Handles syncing the interval between client and server.
 */
public class ContainerPullPushCard extends AEBaseContainer {

    /** Minimum interval in ticks */
    public static final int MINIMUM_INTERVAL = 1;

    /** Default interval in ticks */
    public static final int DEFAULT_INTERVAL = 20;

    /** Minimum quantity to be transferred per operation */
    public static final int MINIMUM_QUANTITY = 1;

    /** Minimum quantity to keep in the adjacent inventory */
    public static final int MINIMUM_KEEP_QUANTITY = 0;

    /** The hand holding the card (null in interface mode) */
    private final EnumHand hand;

    /** The card ItemStack - cached reference */
    private final ItemStack cardStack;

    /** Whether this is a pull card (true) or push card (false) */
    private final boolean isPullCard;

    /**
     * Reference to the interface host when the card is being edited from within
     * an interface GUI. Null when the card is hand-held. Used to call
     * {@link IFilterableInterfaceHost#refreshUpgrades()} after changes so the
     * interface picks up the new settings without re-inserting the card.
     */
    @SuppressWarnings("rawtypes")
    private final IFilterableInterfaceHost interfaceHost;

    @SideOnly(Side.CLIENT)
    private IValuesListener listener;

    /** Interval in ticks, synced between client and server */
    @GuiSync(0)
    public long interval;

    /** Quantity to be transfered per operation */
    @GuiSync(1)
    public long quantity;

    /** Quantity to keep in the adjacent inventory */
    @GuiSync(2)
    public long keepsQuantity;

    private void initVars() {
        // Load initial interval from NBT
        if (this.isPullCard) {
            this.interval = ItemAutoPullCard.getInterval(cardStack);
            this.quantity = ItemAutoPullCard.getQuantity(cardStack);
            this.keepsQuantity = ItemAutoPullCard.getKeepQuantity(cardStack);
        } else {
            this.interval = ItemAutoPushCard.getInterval(cardStack);
            this.quantity = ItemAutoPushCard.getQuantity(cardStack);
            this.keepsQuantity = ItemAutoPushCard.getKeepQuantity(cardStack);
        }
    }

    /**
     * Hand-held mode: the player is holding the card and right-clicked to open the GUI.
     */
    public ContainerPullPushCard(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, null, null);
        this.hand = hand;
        this.interfaceHost = null;
        this.cardStack = playerInv.player.getHeldItem(hand);

        // Determine card type
        this.isPullCard = this.cardStack.getItem() == ItemRegistry.PULL_CARD;

        this.initVars();
    }

    /**
     * Interface mode: the card is in the interface's upgrade inventory and is
     * being edited from the interface GUI. Changes will trigger
     * {@link IFilterableInterfaceHost#refreshUpgrades()} so the interface
     * immediately picks up the new settings.
     */
    @SuppressWarnings("rawtypes")
    public ContainerPullPushCard(InventoryPlayer playerInv, IFilterableInterfaceHost host) {
        super(playerInv,
            host instanceof TileEntity ? (TileEntity) host : null,
            host instanceof IPart ? (IPart) host : null);

        this.hand = null;
        this.interfaceHost = host;

        // Find the pull/push card in the upgrade inventory
        AppEngInternalInventory upgradeInv = host.getUpgradeInventory();
        ItemStack found = ItemStack.EMPTY;

        for (int i = 0; i < upgradeInv.getSlots(); i++) {
            ItemStack stack = upgradeInv.getStackInSlot(i);
            if (stack.getItem() instanceof ItemAutoPullCard || stack.getItem() instanceof ItemAutoPushCard) {
                found = stack;
                break;
            }
        }

        this.cardStack = found;
        this.isPullCard = found.getItem() instanceof ItemAutoPullCard;

        this.initVars();
    }

    @SideOnly(Side.CLIENT)
    public void setListener(final IValuesListener listener) {
        this.listener = listener;
        this.listener.onIntervalChanged((int) Math.min(this.interval, Integer.MAX_VALUE));
        this.listener.onQuantityChanged((int) Math.min(this.quantity, Integer.MAX_VALUE));
        this.listener.onKeepsQuantityChanged((int) Math.min(this.keepsQuantity, Integer.MAX_VALUE));
    }

    /**
     * Set the interval for this card.
     *
     * @param newValue The new interval in ticks
     */
    public void setInterval(final int newValue) {
        int clamped = Math.max(MINIMUM_INTERVAL, newValue);

        // Update NBT
        if (this.isPullCard) {
            ItemAutoPullCard.setInterval(cardStack, clamped);
        } else {
            ItemAutoPushCard.setInterval(cardStack, clamped);
        }

        this.interval = clamped;

        // In interface mode, refresh upgrades so the interface picks up the
        // new interval without needing to remove and re-insert the card.
        if (this.interfaceHost != null) this.interfaceHost.refreshUpgrades();
    }

    /**
     * Set the quantity for this card.
     *
     * @param newValue The new quantity
     */
    public void setQuantity(final int newValue) {
        int clamped = Math.max(MINIMUM_QUANTITY, newValue);

        // Update NBT
        if (this.isPullCard) {
            ItemAutoPullCard.setQuantity(cardStack, clamped);
        } else {
            ItemAutoPushCard.setQuantity(cardStack, clamped);
        }

        this.quantity = clamped;

        if (this.interfaceHost != null) this.interfaceHost.refreshUpgrades();
    }

    /**
     * Set the keep quantity for this card.
     *
     * @param newValue The new keep quantity
     */
    public void setKeepQuantity(final int newValue) {
        int clamped = Math.max(MINIMUM_KEEP_QUANTITY, newValue);

        // Update NBT
        if (this.isPullCard) {
            ItemAutoPullCard.setKeepQuantity(cardStack, clamped);
        } else {
            ItemAutoPushCard.setKeepQuantity(cardStack, clamped);
        }

        this.keepsQuantity = clamped;

        if (this.interfaceHost != null) this.interfaceHost.refreshUpgrades();
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        // Read current interval from NBT
        if (Platform.isServer()) this.initVars();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("interval") && this.listener != null) {
            this.listener.onIntervalChanged((int) Math.min(this.interval, Integer.MAX_VALUE));
        } else if (field.equals("quantity") && this.listener != null) {
            this.listener.onQuantityChanged((int) Math.min(this.quantity, Integer.MAX_VALUE));
        } else if (field.equals("keepsQuantity") && this.listener != null) {
            this.listener.onKeepsQuantityChanged((int) Math.min(this.keepsQuantity, Integer.MAX_VALUE));
        }

        super.onUpdate(field, oldValue, newValue);
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        // Interface mode: delegate to AEBaseContainer's distance check
        if (this.hand == null) return super.canInteractWith(playerIn);

        // Hand mode: the player must still be holding the card
        ItemStack held = playerIn.getHeldItem(this.hand);
        if (held.isEmpty()) return false;

        return held.getItem() == ItemRegistry.PULL_CARD || held.getItem() == ItemRegistry.PUSH_CARD;
    }

    /**
     * @return true if this container is in interface mode (card is in an upgrade slot),
     *         false if the card is hand-held.
     */
    public boolean isInterfaceMode() {
        return this.interfaceHost != null;
    }

    /**
     * @return The interface host when in interface mode, or null in hand mode.
     */
    @SuppressWarnings("rawtypes")
    public IFilterableInterfaceHost getInterfaceHost() {
        return this.interfaceHost;
    }

    public boolean isPullCard() {
        return this.isPullCard;
    }

    public ItemStack getCardStack() {
        return this.cardStack;
    }

    /**
     * Listener interface for values changes (used by GUI to update display).
     */
    @SideOnly(Side.CLIENT)
    public interface IValuesListener {
        void onIntervalChanged(int interval);
        void onQuantityChanged(int quantity);
        void onKeepsQuantityChanged(int keepsQuantity);
    }
}
