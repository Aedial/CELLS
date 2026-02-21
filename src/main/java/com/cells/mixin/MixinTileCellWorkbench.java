package com.cells.mixin;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.misc.TileCellWorkbench;
import appeng.util.inv.filter.IAEItemFilter;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


/**
 * Limits the Cell Workbench's cell slot to a stack size of 1.
 * <p>
 * AE2's TileCellWorkbench uses {@code new AppEngInternalInventory(this, 1)} for its cell slot,
 * which defaults to maxStack=64. AE2's own cells have {@code maxStackSize=1} on the Item,
 * so this was never an issue. Our cells are stackable to 64, which allows inserting a full
 * stack into the cell slot. Since all items in a stack share the same NBT, the workbench
 * writes config data to the entire stack, enabling item duplication when the stack is split.
 * <p>
 * The ME Drive avoids this by using {@code AppEngCellInventory} which sets maxStack=1.
 * <p>
 * We use both setMaxStackSize AND a filter to ensure all insertion paths respect the limit:
 * - setMaxStackSize limits getSlotLimit() for GUI slot handling
 * - Filter prevents insertItem() from accepting more than 1 item total
 */
@Mixin(value = TileCellWorkbench.class, remap = false)
public class MixinTileCellWorkbench {

    @Shadow
    @Final
    private AppEngInternalInventory cell;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void cells$limitCellSlotStackSize(CallbackInfo ci) {
        this.cell.setMaxStackSize(0, 1);
    }
}
