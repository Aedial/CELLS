package com.cells.gui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.input.Mouse;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiCustomSlot;


/**
 * AE2 GUI base that makes filled fake slots expose their content to hover.
 * <p>
 * The wrapped slot is only exposed when the fake slot actually has a hover ingredient,
 * which keeps empty filter widgets out of vanilla's normal slot workflows.
 */
public abstract class AbstractFakeSlotAwareGui extends AEBaseGui {

    protected AbstractFakeSlotAwareGui(Container container) {
        super(container);
    }

    @Override
    public Slot getSlotUnderMouse() {
        Slot realSlot = this.getRealSlotUnderMouse();
        if (realSlot != null || !this.shouldExposeFakeSlotUnderMouse()) return realSlot;

        GuiCustomSlot fakeSlot = this.getFakeSlotUnderMouse();
        if (fakeSlot == null) return null;

        return new HoveredGuiCustomSlot(fakeSlot);
    }

    @Nullable
    protected final Slot getRealSlotUnderMouse() {
        return super.getSlotUnderMouse();
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        if (this.getRealSlotUnderMouse() == null && this.getFakeSlotUnderMouse(mouseX, mouseY) != null) return;

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected boolean checkHotbarKeys(int keyCode) {
        if (this.getRealSlotUnderMouse() == null && this.getFakeSlotUnderMouse() != null) return false;

        return super.checkHotbarKeys(keyCode);
    }

    protected boolean shouldExposeFakeSlotUnderMouse() {
        return true;
    }

    @Nullable
    protected GuiCustomSlot getFakeSlotUnderMouse() {
        if (!this.shouldExposeFakeSlotUnderMouse()) return null;

        int mouseX = Mouse.getX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getY() * this.height / this.mc.displayHeight - 1;
        return this.getFakeSlotUnderMouse(mouseX, mouseY);
    }

    @Nullable
    protected GuiCustomSlot getFakeSlotUnderMouse(int mouseX, int mouseY) {
        if (!this.shouldExposeFakeSlotUnderMouse()) return null;

        int relativeX = mouseX - this.guiLeft;
        int relativeY = mouseY - this.guiTop;

        for (GuiCustomSlot slot : this.guiSlots) {
            if (!slot.isVisible() || !slot.isSlotEnabled()) continue;
            if (!hasHoverIngredient(getHoverIngredient(slot))) continue;
            if (isPointInSlot(relativeX, relativeY, slot)) return slot;
        }

        return null;
    }

    @Nullable
    private static Object getHoverIngredient(GuiCustomSlot slot) {
        if (!(slot instanceof IHoverIngredientSlot)) return null;

        Object ingredient = ((IHoverIngredientSlot) slot).getIngredient();
        if (ingredient instanceof ItemStack && ((ItemStack) ingredient).isEmpty()) return null;

        return ingredient;
    }

    private static boolean isPointInSlot(int mouseX, int mouseY, GuiCustomSlot slot) {
        return mouseX >= slot.xPos() - 1
            && mouseX < slot.xPos() + slot.getWidth() + 1
            && mouseY >= slot.yPos() - 1
            && mouseY < slot.yPos() + slot.getHeight() + 1;
    }

    private static boolean hasHoverIngredient(@Nullable Object ingredient) {
        if (ingredient instanceof ItemStack) return !((ItemStack) ingredient).isEmpty();

        return ingredient != null;
    }

    private static final class HoveredGuiCustomSlot extends Slot implements IHoverIngredientSlot {

        private static final InventoryBasic DUMMY_INVENTORY = new InventoryBasic("cells.fake_slot_hover", false, 1);

        private final GuiCustomSlot slot;

        private HoveredGuiCustomSlot(GuiCustomSlot slot) {
            super(DUMMY_INVENTORY, slot.getId(), slot.xPos(), slot.yPos());
            this.slot = slot;
            this.slotNumber = -1;
        }

        @Override
        public boolean canTakeStack(@Nonnull EntityPlayer player) {
            return false;
        }

        @Override
        public boolean isItemValid(@Nonnull ItemStack stack) {
            return false;
        }

        @Override
        public void putStack(@Nonnull ItemStack stack) {
        }

        @Override
        @Nonnull
        public ItemStack decrStackSize(int amount) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotStackLimit() {
            return 0;
        }

        @Override
        public boolean isEnabled() {
            return this.slot.isVisible() && this.slot.isSlotEnabled();
        }

        @Override
        @Nonnull
        public ItemStack getStack() {
            Object ingredient = this.getIngredient();
            if (!(ingredient instanceof ItemStack)) return ItemStack.EMPTY;

            ItemStack stack = (ItemStack) ingredient;
            return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }

        @Override
        @Nullable
        public Object getIngredient() {
            return getHoverIngredient(this.slot);
        }
    }
}