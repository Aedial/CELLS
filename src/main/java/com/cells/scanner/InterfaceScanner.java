package com.cells.scanner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.AEPartLocation;

import com.cells.blocks.combinedinterface.ICombinedInterfaceHost;
import com.cells.blocks.interfacebase.IInterfaceHost;
import com.cells.blocks.interfacebase.IInterfaceLogic;
import com.cells.blocks.interfacebase.IFilterableInterfaceHost;
import com.cells.blocks.interfacebase.IResourceInterfaceLogic;
import com.cells.blocks.iointerface.IIOInterfaceHost;
import com.cells.network.sync.ResourceType;


/**
 * Bounded traversal and export buffer snapshots for the standalone interface scanner.
 * It handles the interfaces collection and traversal across subnets.
 */
public final class InterfaceScanner {

    private static final int NODES_PER_TICK = 32;

    private final ArrayDeque<IGridNode> pending = new ArrayDeque<>();
    private final ArrayDeque<IGrid> grids = new ArrayDeque<>();
    private final Set<IGrid> visited = new HashSet<>();
    private final Set<Object> scannedMachines = new HashSet<>();
    private final boolean includeSubnets;

    public InterfaceScanner(IGridNode root, boolean includeSubnets) {
        this.includeSubnets = includeSubnets;
        enqueueGrid(root.getGrid());
    }

    public boolean isComplete() {
        return this.pending.isEmpty() && this.grids.isEmpty();
    }

    public void processBatch(Consumer<InterfaceScanEntry> result) {
        int processed = 0;
        while (!isComplete() && processed++ < NODES_PER_TICK) {
            if (this.pending.isEmpty()) {
                collectGrid(this.grids.removeFirst());
                if (this.pending.isEmpty()) continue;
            }

            IGridNode node = this.pending.removeFirst();
            Object machine = node.getMachine();
            if (this.includeSubnets && ScannerTraversal.isSubnetMachine(machine.getClass())) {
                ScannerTraversal.collectSubnetNodes(node, remote -> enqueueGrid(remote.getGrid()));
            }

            if (!this.scannedMachines.add(machine) || !(machine instanceof IInterfaceHost)) continue;

            IInterfaceHost host = (IInterfaceHost) machine;
            World world = host.getHostWorld();
            if (world == null || !world.isBlockLoaded(host.getHostPos())) continue;

            if (machine instanceof ICombinedInterfaceHost) {
                for (IInterfaceLogic logic : ((ICombinedInterfaceHost) machine).getAllLogics()) {
                    collect(host, logic, host.isExport(), false, true, result);
                }
            } else if (machine instanceof IIOInterfaceHost) {
                IIOInterfaceHost io = (IIOInterfaceHost) machine;
                collect(host, io.getExportLogic(), true, true, false, result);
            } else if (machine instanceof IFilterableInterfaceHost) {
                collect(host, ((IFilterableInterfaceHost<?, ?>) machine).getInterfaceLogic(),
                    host.isExport(), false, false, result);
            }
        }
    }

    private void enqueueGrid(IGrid grid) {
        if (grid != null && this.visited.add(grid)) this.grids.addLast(grid);
    }

    private void collectGrid(IGrid grid) {
        for (Class<? extends IGridHost> type : grid.getMachinesClasses()) {
            boolean interfaces = IInterfaceHost.class.isAssignableFrom(type);
            if (!interfaces && !(this.includeSubnets && ScannerTraversal.isSubnetMachine(type))) continue;

            for (IGridNode node : grid.getMachines(type)) this.pending.addLast(node);
        }
    }

    private void collect(IInterfaceHost host, IInterfaceLogic logic, boolean export,
                         boolean directionLabel, boolean typeLabel, Consumer<InterfaceScanEntry> result) {
        if (!export || !(logic instanceof IResourceInterfaceLogic)) return;

        IResourceInterfaceLogic<?, ?> resourceLogic = (IResourceInterfaceLogic<?, ?>) logic;
        List<ItemStack> missing = new ArrayList<>();
        for (int slot = 0; slot < logic.getEffectiveFilterSlots(); slot++) {
            Object filter = resourceLogic.getFilter(slot);
            if (!(filter instanceof IAEStack) || !resourceLogic.isStorageEmpty(slot)) continue;

            ItemStack display = ((IAEStack<?>) filter).asItemStackRepresentation().copy();
            if (display.isEmpty()) continue;

            display.setCount(1);
            missing.add(display);
        }

        if (missing.isEmpty()) return;

        ResourceType type = ResourceType.valueOf(logic.getTypeName().toUpperCase(java.util.Locale.ROOT));
        AEPartLocation side = host.getPartSide();
        int facing = side != null ? side.ordinal() : AEPartLocation.INTERNAL.ordinal();
        result.accept(new InterfaceScanEntry(host.getHostPos(), host.getHostWorld().provider.getDimension(),
            facing, type, export, directionLabel, typeLabel, host.getBackButtonStack(), missing));
    }
}
