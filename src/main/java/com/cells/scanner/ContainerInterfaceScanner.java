package com.cells.scanner;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import net.minecraftforge.common.DimensionManager;

import appeng.api.networking.IGridNode;
import appeng.api.util.AEPartLocation;
import appeng.container.AEBaseContainer;

import com.cells.ItemRegistry;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.sync.PacketInterfaceScannerSync;


/**
 * Open scanner view with bounded collection and static client results.
 */
public class ContainerInterfaceScanner extends AEBaseContainer {

    private static final int STACKS_PER_PACKET = 16;
    private static final int PACKETS_PER_TICK = 8;

    private final World world;
    private BlockPos rootPos;
    private AEPartLocation rootSide;
    private int rootDimension;
    private final EnumHand hand;
    private final UUID scannerId;
    private final ArrayDeque<InterfaceScanEntry> pending = new ArrayDeque<>();
    private final Map<String, InterfaceScanEntry> entries = new LinkedHashMap<>();

    private InterfaceScanner scanner;
    private boolean includeSubnets;
    private boolean pendingReset;
    private boolean completionSent;
    private boolean complete;
    private boolean failed;
    private boolean canScan;
    private int revision;
    private int contentVersion;

    public ContainerInterfaceScanner(InventoryPlayer inventory, World world, BlockPos pos,
                                      AEPartLocation side, EnumHand hand, boolean fresh) {
        super(inventory, null, null);
        this.world = world;
        this.rootPos = pos;
        this.rootSide = side;
        this.rootDimension = world.provider.getDimension();
        this.hand = hand;

        ItemStack stack = inventory.player.getHeldItem(hand);
        this.scannerId = world.isRemote ? null : InterfaceScannerCache.getId(stack);
        this.includeSubnets = stack.hasTagCompound() && stack.getTagCompound().getBoolean("IncludeSubnets");
        if (world.isRemote) return;

        InterfaceScannerCache.Snapshot previous = InterfaceScannerCache.get(inventory.player, this.scannerId);
        IGridNode root = fresh ? getRootNode() : null;
        if (root != null) {
            restart(root);
        } else if (previous != null) {
            replay(previous);
        } else {
            this.failed = true;
            this.pendingReset = true;
            this.revision++;
        }
    }

    @Override
    public boolean canInteractWith(@Nonnull EntityPlayer player) {
        return !player.isDead && player.getHeldItem(this.hand).getItem() == ItemRegistry.INTERFACE_SCANNER;
    }

    public Collection<InterfaceScanEntry> getEntries() {
        return this.entries.values();
    }

    public int getContentVersion() {
        return this.contentVersion;
    }

    public boolean isIncludeSubnets() {
        return this.includeSubnets;
    }

    public boolean isComplete() {
        return this.complete;
    }

    public boolean hasFailed() {
        return this.failed;
    }

    public boolean canScan() {
        return this.canScan;
    }

    public void setIncludeSubnets(boolean includeSubnets) {
        if (this.world.isRemote || this.includeSubnets == includeSubnets) return;

        IGridNode root = getRootNode();
        if (root == null) {
            this.canScan = false;
            send((EntityPlayerMP) getPlayerInv().player, false, this.completionSent, null);
            return;
        }

        this.includeSubnets = includeSubnets;
        ItemStack stack = getPlayerInv().player.getHeldItem(this.hand);
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        tag.setBoolean("IncludeSubnets", includeSubnets);
        restart(root);
    }

    private IGridNode getRootNode() {
        World rootWorld = DimensionManager.getWorld(this.rootDimension);
        return rootWorld != null ? ScannerTraversal.getNode(rootWorld, this.rootPos, this.rootSide) : null;
    }

    private void restart(IGridNode root) {
        this.revision++;
        this.pending.clear();
        this.entries.clear();
        this.pendingReset = true;
        this.completionSent = false;
        this.complete = false;
        this.failed = false;
        this.canScan = true;
        this.scanner = new InterfaceScanner(root, this.includeSubnets);
    }

    private void replay(InterfaceScannerCache.Snapshot snapshot) {
        this.rootPos = snapshot.rootPos;
        this.rootDimension = snapshot.dimension;
        this.rootSide = snapshot.side;
        this.includeSubnets = snapshot.includeSubnets;
        this.canScan = getRootNode() != null;
        this.pendingReset = true;
        this.revision++;
        for (InterfaceScanEntry entry : snapshot.entries) queueEntry(entry);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (this.world.isRemote || this.completionSent) return;

        EntityPlayerMP player = (EntityPlayerMP) getPlayerInv().player;
        if (this.pendingReset) {
            send(player, true, false, null);
            this.pendingReset = false;
        }

        if (this.scanner != null && this.pending.size() < 64) this.scanner.processBatch(this::queueEntry);
        if (this.scanner != null && this.scanner.isComplete()) {
            InterfaceScannerCache.put(player, this.scannerId, new InterfaceScannerCache.Snapshot(
                this.rootPos, this.rootDimension, this.rootSide, this.includeSubnets, this.entries.values()));
            this.scanner = null;
        }

        int sent = 0;
        while (!this.pending.isEmpty() && sent++ < PACKETS_PER_TICK) {
            send(player, false, false, this.pending.removeFirst());
        }

        if ((this.scanner == null || this.scanner.isComplete()) && this.pending.isEmpty()) {
            send(player, false, true, null);
            this.completionSent = true;
            this.scanner = null;
        }
    }

    private void queueEntry(InterfaceScanEntry entry) {
        this.entries.put(entry.getKey(), entry);
        for (int start = 0; start < entry.missing.size(); start += STACKS_PER_PACKET) {
            this.pending.addLast(entry.slice(start, Math.min(start + STACKS_PER_PACKET, entry.missing.size())));
        }
    }

    private void send(EntityPlayerMP player, boolean reset, boolean finished, InterfaceScanEntry entry) {
        CellsNetworkHandler.INSTANCE.sendTo(new PacketInterfaceScannerSync(this.windowId, this.revision,
            reset, finished, this.failed, this.includeSubnets, this.canScan, entry), player);
    }

    public void receive(int revision, boolean reset, boolean finished, boolean failed,
                         boolean includeSubnets, boolean canScan, InterfaceScanEntry entry) {
        if (revision < this.revision) return;
        if (!reset && revision != this.revision) return;

        if (reset) {
            this.revision = revision;
            this.entries.clear();
        }

        this.includeSubnets = includeSubnets;
        this.complete = finished;
        this.failed = failed;
        this.canScan = canScan;
        if (entry != null) {
            InterfaceScanEntry existing = this.entries.get(entry.getKey());
            if (existing == null) {
                this.entries.put(entry.getKey(), entry);
            } else {
                existing.missing.addAll(entry.missing);
            }
        }

        this.contentVersion++;
    }
}
