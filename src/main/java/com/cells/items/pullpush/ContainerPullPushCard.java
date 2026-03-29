package com.cells.items.pullpush;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.util.Platform;

import com.cells.ItemRegistry;
import com.cells.items.ItemAutoPullCard;
import com.cells.items.ItemAutoPushCard;


/**
 * Container for the Pull/Push Card configuration GUI.
 * Handles syncing the interval between client and server.
 */
public class ContainerPullPushCard extends AEBaseContainer {

    /** Minimum interval in ticks */
    public static final int MINIMUM_INTERVAL = 20;

    /** Minimum quantity to be transferred per operation */
    public static final long MINIMUM_QUANTITY = 1;

    /** The hand holding the card */
    private final EnumHand hand;

    /** The card ItemStack - cached reference */
    private final ItemStack cardStack;

    /** Whether this is a pull card (true) or push card (false) */
    private final boolean isPullCard;

    @SideOnly(Side.CLIENT)
    private IValuesListener listener;

    /** Interval in ticks, synced between client and server */
    @GuiSync(0)
    public long interval = MINIMUM_INTERVAL;

    /** Quantity to be transfered per operation */
    @GuiSync(1)
    public long quantity = MINIMUM_QUANTITY;

    public ContainerPullPushCard(InventoryPlayer playerInv, EnumHand hand) {
        super(playerInv, null, null);
        this.hand = hand;
        this.cardStack = playerInv.player.getHeldItem(hand);

        // Determine card type
        this.isPullCard = cardStack.getItem() == ItemRegistry.PULL_CARD;

        // Load initial interval from NBT
        if (this.isPullCard) {
            this.interval = ItemAutoPullCard.getInterval(cardStack);
            this.quantity = ItemAutoPullCard.getQuantity(cardStack);
        } else {
            this.interval = ItemAutoPushCard.getInterval(cardStack);
            this.quantity = ItemAutoPushCard.getQuantity(cardStack);
        }
    }

    @SideOnly(Side.CLIENT)
    public void setListener(final IValuesListener listener) {
        this.listener = listener;
        this.listener.onIntervalChanged((int) Math.min(this.interval, Integer.MAX_VALUE));
        this.listener.onQuantityChanged((int) Math.min(this.quantity, Integer.MAX_VALUE));
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
    }

    /**
     * Set the quantity for this card.
     *
     * @param newValue The new quantity
     */
    public void setQuantity(final int newValue) {
        long clamped = Math.max(MINIMUM_QUANTITY, newValue);

        // Update NBT
        if (this.isPullCard) {
            ItemAutoPullCard.setQuantity(cardStack, clamped);
        } else {
            ItemAutoPushCard.setQuantity(cardStack, clamped);
        }

        this.quantity = clamped;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();

        if (Platform.isServer()) {
            // Read current interval from NBT
            if (this.isPullCard) {
                this.interval = ItemAutoPullCard.getInterval(cardStack);
                this.quantity = ItemAutoPullCard.getQuantity(cardStack);
            } else {
                this.interval = ItemAutoPushCard.getInterval(cardStack);
                this.quantity = ItemAutoPushCard.getQuantity(cardStack);
            }
        }
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("interval") && this.listener != null) {
            this.listener.onIntervalChanged((int) Math.min(this.interval, Integer.MAX_VALUE));
        } else if (field.equals("quantity") && this.listener != null) {
            this.listener.onQuantityChanged((int) Math.min(this.quantity, Integer.MAX_VALUE));
        }

        super.onUpdate(field, oldValue, newValue);
    }

    @Override
    public boolean canInteractWith(EntityPlayer playerIn) {
        // The player must still be holding the card
        ItemStack held = playerIn.getHeldItem(this.hand);
        if (held.isEmpty()) return false;

        return held.getItem() == ItemRegistry.PULL_CARD || held.getItem() == ItemRegistry.PUSH_CARD;
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
    }
}
