package com.cells.helpers;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.Optional;

import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;

import com.cells.blocks.combinedinterface.ICombinedInterfaceHost;
import com.cells.blocks.interfacebase.IFilterableInterfaceHost;
import com.cells.blocks.interfacebase.IInterfaceHost;
import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.blocks.interfacebase.fluid.FluidInterfaceLogic;
import com.cells.blocks.interfacebase.item.ItemInterfaceLogic;
import com.cells.blocks.iointerface.IIOInterfaceHost;
import com.cells.gui.QuickAddHelper;
import com.cells.integration.mekanismenergistics.GasInterfaceLogic;
import com.cells.integration.thaumicenergistics.EssentiaInterfaceLogic;
import com.cells.items.ItemRecoveryContainer;
import com.cells.network.sync.ResourceType;


/**
 * Helpers for transferring Recovery Container contents into CELLS interfaces with long quantities.
 */
public final class RecoveryContainerHelper {

    private RecoveryContainerHelper() {}

    /**
     * Transfer the recovery contents into the clicked interface's import logic.
     *
     * @return The accepted amount, or -1 when the target has no matching import logic
     */
    public static long tryTransferToInterface(ItemStack stack, TileEntity tile, Vec3d hit) {
        Object target = tile;
        if (tile instanceof IPartHost) {
            SelectedPart selected = ((IPartHost) tile).selectPart(hit);
            target = selected == null ? null : selected.part;
        }

        ResourceType type = ItemRecoveryContainer.getResourceType(ItemRecoveryContainer.getType(stack));
        IInterfaceLogic logic = getImportLogic(target, type);
        if (logic == null || !type.getName().equals(logic.getTypeName())) return -1;

        long amount = ItemRecoveryContainer.getAmount(stack);
        if (amount <= 0) return 0;

        switch (type) {
            case ITEM:
                if (!(logic instanceof ItemInterfaceLogic)) return -1;

                return ((ItemInterfaceLogic) logic).receiveFilteredLong(
                    ItemRecoveryContainer.getContainedItemIdentity(stack), amount, true);

            case FLUID:
                if (!(logic instanceof FluidInterfaceLogic)) return -1;

                return ((FluidInterfaceLogic) logic).receiveFilteredLong(
                    ItemRecoveryContainer.getFluidStack(stack), amount, true);

            case GAS:
                return type.isAvailable() ? transferGas(stack, logic, amount) : -1;

            case ESSENTIA:
                return type.isAvailable() ? transferEssentia(stack, logic, amount) : -1;

            default:
                return -1;
        }
    }

    @Nullable
    private static IInterfaceLogic getImportLogic(Object target, ResourceType type) {
        if (target instanceof IIOInterfaceHost) return ((IIOInterfaceHost) target).getImportLogic();
        if (!(target instanceof IInterfaceHost) || ((IInterfaceHost) target).isExport()) return null;

        if (target instanceof ICombinedInterfaceHost) {
            return ((ICombinedInterfaceHost) target).getLogicForType(type);
        }

        if (target instanceof IFilterableInterfaceHost) {
            return ((IFilterableInterfaceHost<?, ?>) target).getInterfaceLogic();
        }

        return null;
    }

    @Optional.Method(modid = "mekeng")
    private static long transferGas(ItemStack stack, IInterfaceLogic logic, long amount) {
        if (!(logic instanceof GasInterfaceLogic)) return -1;

        return ((GasInterfaceLogic) logic).receiveFilteredLong(
            QuickAddHelper.getGasFromItemStack(stack), amount, true);
    }

    @Optional.Method(modid = "thaumicenergistics")
    private static long transferEssentia(ItemStack stack, IInterfaceLogic logic, long amount) {
        if (!(logic instanceof EssentiaInterfaceLogic)) return -1;

        thaumicenergistics.api.EssentiaStack essentia = QuickAddHelper.getEssentiaFromItemStack(stack);
        if (essentia == null || essentia.getAspect() == null) return 0;

        return amount - ((EssentiaInterfaceLogic) logic).addToContainer(essentia.getAspect(), amount);
    }
}
