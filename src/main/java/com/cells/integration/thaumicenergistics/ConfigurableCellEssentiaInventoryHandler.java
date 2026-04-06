package com.cells.integration.thaumicenergistics;

import net.minecraft.item.ItemStack;

import appeng.api.storage.IMEInventory;
import appeng.api.storage.IStorageChannel;

import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.item.ItemDummyAspect;

import com.cells.cells.common.AbstractCellInventoryHandler;


/**
 * Inventory handler for essentia cells, with custom config item conversion.
 * <p>
 * The essentia storage channel's {@code createStack(Object)} only accepts
 * {@code Aspect}, {@code EssentiaStack}, or {@code AEEssentiaStack}, never
 * {@code ItemStack}. This means the default conversion in
 * {@link AbstractCellInventoryHandler} (which calls {@code channel.createStack(itemStack)})
 * always returns null for essentia config items, silently producing an empty
 * partition list.
 * <p>
 * This handler overrides {@link #convertConfigItem} to extract the {@link Aspect}
 * from {@link ItemDummyAspect} stacks and pass it to {@code channel.createStack(aspect)},
 * bridging the gap between the ItemStack-based config inventory and the
 * Aspect-based essentia storage channel.
 */
public class ConfigurableCellEssentiaInventoryHandler extends AbstractCellInventoryHandler<IAEEssentiaStack> {

    public ConfigurableCellEssentiaInventoryHandler(IMEInventory<IAEEssentiaStack> inventory,
                                                     IStorageChannel<IAEEssentiaStack> channel) {
        super(inventory, channel);
    }

    /**
     * Convert a config slot ItemStack to an IAEEssentiaStack.
     * <p>
     * Extracts the Aspect from ItemDummyAspect and passes it to the channel's
     * createStack, which accepts Aspect objects directly.
     */
    @Override
    protected IAEEssentiaStack convertConfigItem(ItemStack is, IStorageChannel<IAEEssentiaStack> channel) {
        if (is.getItem() instanceof ItemDummyAspect) {
            Aspect aspect = ((ItemDummyAspect) is.getItem()).getAspect(is);
            if (aspect != null) return channel.createStack(aspect);
        }

        // Fall back to default (will return null for non-dummy items, which is correct)
        return channel.createStack(is);
    }
}
