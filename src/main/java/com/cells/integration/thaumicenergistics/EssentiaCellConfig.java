package com.cells.integration.thaumicenergistics;

import javax.annotation.Nonnull;

import net.minecraft.item.ItemStack;

import appeng.items.contents.CellConfig;

import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumicenergistics.api.ThEApi;
import thaumicenergistics.item.ItemDummyAspect;
import thaumicenergistics.util.ThEUtil;


/**
 * Config inventory for essentia cells, extending CellConfig for NBT compatibility.
 * <p>
 * Unlike ThaumicEnergistics' own EssentiaCellConfig (which extends ItemStackHandler
 * and uses NBT key "filter"), this extends CellConfig (NBT key "list") to remain
 * compatible with the configurable cell's existing NBT format.
 * <p>
 * Converts essentia containers (phials, jars, etc.) to ItemDummyAspect stacks
 * on insertion, ensuring the partition list can be built correctly.
 */
public class EssentiaCellConfig extends CellConfig {

    public EssentiaCellConfig(ItemStack is) {
        super(is);
    }

    /**
     * Creates a dummy stack representing the first aspect of the given essentia container stack.
     * @param stack The essentia container stack.
     * @return A dummy stack with the first aspect, or null if the stack is not a valid essentia container.
     */
    private ItemStack createDummyStack(@Nonnull ItemStack stack) {
        // Not an essentia container, reject
        if (!(stack.getItem() instanceof IEssentiaContainerItem)) return null;

        // Extract the first aspect from the container
        AspectList list = ((IEssentiaContainerItem) stack.getItem()).getAspects(stack);
        if (list == null || list.size() < 1 || !ThEApi.instance().items().dummyAspect().maybeStack(1).isPresent()) {
            return null;
        }

        ItemStack dummyStack = ThEUtil.setAspect(
            ThEApi.instance().items().dummyAspect().maybeStack(1).get(),
            list.getAspects()[0]
        );

        return dummyStack;
    }

    @Override
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        // Already a dummy aspect or empty, pass through directly
        if (stack.isEmpty() || stack.getItem() instanceof ItemDummyAspect) {
            return super.insertItem(slot, stack, simulate);
        }

        ItemStack dummyStack = createDummyStack(stack);
        if (dummyStack == null) return stack;

        super.insertItem(slot, dummyStack, simulate);
        return stack;
    }

    @Override
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        // Already a dummy aspect or empty, pass through directly
        if (stack.isEmpty() || stack.getItem() instanceof ItemDummyAspect) {
            super.setStackInSlot(slot, stack);
            return;
        }

        ItemStack dummyStack = createDummyStack(stack);
        if (dummyStack == null) return;

        super.setStackInSlot(slot, dummyStack);
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (stack.isEmpty() || stack.getItem() instanceof ItemDummyAspect) {
            return super.isItemValid(slot, stack);
        }

        ItemStack dummyStack = createDummyStack(stack);
        if (dummyStack == null) return false;

        return super.isItemValid(slot, dummyStack);
    }
}
