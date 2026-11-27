package com.cells.parts.subnetproxy;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;

import appeng.api.AEApi;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartModel;
import appeng.api.parts.PartItemStack;
import appeng.api.util.AECableType;
import appeng.api.util.AEPartLocation;
import appeng.items.parts.PartModels;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;

import com.cells.api.ISubnetProxy;
import com.cells.parts.CellsPartType;
import com.cells.parts.ItemCellsPart;
import com.cells.Tags;
import com.cells.util.PowerStateHelper;


/**
 * Source-side half of the Subnet Proxy, attached to its cable through the AEBasePart proxy.
 * <p>
 * GUI interactions delegate to the attached Front. Power and channel state supply the pairing LED
 * and captured endpoint availability. The shared grid owner receives source-membership events;
 * this part queues counterpart capture after physical connection or availability changes.
 * <p>
 * The Front contributes physical read and insertion adapters to the destination grid. Grid-owned
 * enumeration and forwarded deltas retain the imported quantities while both grids keep
 * their own services.
 */
public class PartSubnetProxyBack extends AEBasePart implements IPowerChannelState, ISubnetProxy {

    // LED state flags (mirroring PartBasicState constants)
    protected static final int POWERED_FLAG = 1;
    protected static final int CHANNEL_FLAG = 2;
    protected static final int BOTH_PARTS_FLAG = 4;

    @PartModels
    public static final ResourceLocation MODEL_BASE = new ResourceLocation(Tags.MODID, "part/subnet_proxy_back/base");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_OFF = new ResourceLocation(Tags.MODID, "part/subnet_proxy_back/status_off");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_ON = new ResourceLocation(Tags.MODID, "part/subnet_proxy_back/status_on");
    @PartModels
    public static final ResourceLocation MODEL_STATUS_HAS_CHANNEL = new ResourceLocation(Tags.MODID, "part/subnet_proxy_back/status_active");

    public static final IPartModel MODELS_OFF = new PartModel(MODEL_BASE, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(MODEL_BASE, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(MODEL_BASE, MODEL_STATUS_HAS_CHANNEL);

    /** Client-side LED state. Written by server in writeToStream, read by client in readFromStream. */
    private int clientFlags = 0;

    /**
     * Cached server-side flag: whether the front counterpart exists in the adjacent block.
     * Updated on neighbor changes and placement, used in writeToStream to avoid
     * a tile entity lookup every tick.
     */
    private boolean cachedHasFront = false;

    /**
     * World tick when this part was placed. Used to suppress spurious
     * activation caused by off-hand fall-through on the same tick as
     * placement (AE2's client-side PartPlacement returns PASS, so
     * Minecraft tries the off-hand which triggers onPartActivate).
     */
    private long placedTick = -1;

    public PartSubnetProxyBack(final ItemStack is) {
        super(is);

        // The part proxy connects to Grid A on this cable bus
        // Grid A's shared owner receives cell-array events and schedules suitable state capture
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(1.0);
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        this.cachedHasFront = this.findFrontPart() != null;
        this.connectionChanged();
    }

    @Override
    public void removeFromWorld() {
        PartSubnetProxyFront front = this.findFrontPart();
        if (front != null) front.invalidateSnapshots();

        super.removeFromWorld();
        this.cachedHasFront = false;

        if (front != null) SubnetProxyManager.makeDirty(front);
    }

    private void connectionChanged() {
        PartSubnetProxyFront front = this.findFrontPart();
        if (front == null) return;

        front.invalidateSnapshots();
        SubnetProxyManager.makeDirty(front);
    }

    @Override
    public void onPlacement(EntityPlayer player, EnumHand hand, ItemStack held, AEPartLocation side) {
        super.onPlacement(player, hand, held, side);
        TileEntity te = this.getHost() != null ? this.getHost().getTile() : null;
        if (te != null) this.placedTick = te.getWorld().getTotalWorldTime();

    }

    private void markHostForUpdate() {
        IPartHost host = this.getHost();
        if (host == null) return;

        host.markForUpdate();
    }

    @Override
    public void readFromNBT(final NBTTagCompound extra) {
        super.readFromNBT(extra);
    }

    @Nullable
    public PartSubnetProxyFront findFrontPart() {
        TileEntity selfTile = this.getHost() != null ? this.getHost().getTile() : null;
        if (selfTile == null) return null;

        AEPartLocation side = this.getSide();
        if (side == null) return null;

        // The front part is in the adjacent block in our facing direction,
        // on the opposite side (facing back toward us).
        EnumFacing facing = side.getFacing();
        BlockPos adjacentPos = selfTile.getPos().offset(facing);
        if (!selfTile.getWorld().isBlockLoaded(adjacentPos)) return null;

        TileEntity adjacentTile = selfTile.getWorld().getTileEntity(adjacentPos);
        if (!(adjacentTile instanceof IPartHost)) return null;

        IPart candidate = ((IPartHost) adjacentTile).getPart(AEPartLocation.fromFacing(facing.getOpposite()));
        if (candidate instanceof PartSubnetProxyFront) return (PartSubnetProxyFront) candidate;

        return null;
    }

    @Override
    public void onNeighborChanged(IBlockAccess w, BlockPos pos, BlockPos neighbor) {
        this.connectionChanged();

        // TODO: could exit early if neighbor != adjacentPos
        boolean hasFront = findFrontPart() != null;
        if (hasFront != this.cachedHasFront) {
            this.cachedHasFront = hasFront;
            this.markHostForUpdate();
        }
    }

    private int computeStateFlags() {
        int flags = 0;
        if (PowerStateHelper.isPowered(this.getProxy())) flags |= POWERED_FLAG;
        if (PowerStateHelper.hasChannel(this.getProxy())) flags |= CHANNEL_FLAG;
        if (this.cachedHasFront) flags |= BOTH_PARTS_FLAG;

        return flags;
    }

    private int getStateFlags() {
        TileEntity hostTile = this.getHost() != null ? this.getHost().getTile() : null;
        if (hostTile != null && !hostTile.getWorld().isRemote) return this.computeStateFlags();

        return this.clientFlags;
    }

    @Override
    public void writeToStream(final ByteBuf data) {
        int flags = this.computeStateFlags();
        this.clientFlags = flags;

        data.writeByte((byte) flags);
    }

    @Override
    public boolean readFromStream(final ByteBuf data) {
        final int old = this.clientFlags;
        this.clientFlags = data.readByte();
        return old != this.clientFlags;
    }

    @Override
    public boolean isPowered() {
        return (this.getStateFlags() & POWERED_FLAG) == POWERED_FLAG;
    }

    @Override
    public boolean isActive() {
        int flags = this.getStateFlags();
        return (flags & CHANNEL_FLAG) == CHANNEL_FLAG
            && (flags & BOTH_PARTS_FLAG) == BOTH_PARTS_FLAG;
    }

    @Override
    public void getBoxes(final IPartCollisionHelper bch) {
        // 5 units deep from the face (z=11..16 in part-local coords)
        bch.addBox(0, 0, 11, 16, 16, 16);
    }

    @Override
    @Nonnull
    public AECableType getCableConnectionType(final AEPartLocation dir) {
        return AECableType.SMART;
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 2;
    }

    @Override
    public int getFilterSlots() {
        PartSubnetProxyFront front = findFrontPart();
        return front != null ? front.getFilterSlots() : 0;
    }

    @Override
    @Nonnull
    public ItemStack getFilter(int slot) {
        PartSubnetProxyFront front = findFrontPart();
        return front != null ? front.getFilter(slot) : ItemStack.EMPTY;
    }

    @Override
    public void setFilter(int slot, @Nonnull ItemStack stack) {
        PartSubnetProxyFront front = findFrontPart();
        if (front == null) return;

        front.setFilter(slot, stack);
    }

    @Override
    public void clearFilters() {
        PartSubnetProxyFront front = findFrontPart();
        if (front == null) return;

        front.clearFilters();
    }

    @Override
    public boolean isOutboundConnection() {
        return false;
    }

    @Override
    @Nonnull
    public EnumFacing getPrimaryFacing() {
        AEPartLocation side = this.getSide();
        return side != null ? side.getFacing() : EnumFacing.NORTH;
    }

    @Override
    @Nullable
    public IGrid getTargetGrid() {
        PartSubnetProxyFront front = findFrontPart();
        if (front == null) return null;

        IGridNode node = front.getProxy().getNode();
        return node != null ? node.getGrid() : null;
    }

    @Override
    @Nonnull
    public ItemStack getRemoteDisplayStack() {
        PartSubnetProxyFront front = findFrontPart();
        return front != null ? front.getItemStack(PartItemStack.PICK) : ItemStack.EMPTY;
    }

    @Override
    public boolean useStandardMemoryCard() {
        return false;
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        // If the player is holding the complementary (front) part, attempt to place it
        // on the adjacent block's opposite face instead of activating this part.
        if (isHoldingComplementaryPart(player, hand, CellsPartType.SUBNET_PROXY_FRONT)) {
            return tryPlaceComplementaryPart(player, hand);
        }

        // Suppress activation on the same tick the part was placed.
        // AE2's client-side PartPlacement returns PASS, causing Minecraft to
        // try the off-hand, which triggers onPartActivate on the just-placed part.
        TileEntity te = this.getHost() != null ? this.getHost().getTile() : null;
        if (te != null && te.getWorld().getTotalWorldTime() == this.placedTick) return false;

        // Delegate to the front part in the adjacent block
        PartSubnetProxyFront front = findFrontPart();
        if (front != null) return front.onPartActivate(player, hand, pos);

        // No front part, show error
        if (!player.world.isRemote) {
            player.sendMessage(new TextComponentTranslation("chat.cells.subnet_proxy.need_front"));
        }

        return true;
    }

    @Override
    public boolean onPartShiftActivate(final EntityPlayer player, final EnumHand hand, final Vec3d pos) {
        TileEntity te = this.getHost() != null ? this.getHost().getTile() : null;
        if (te != null && te.getWorld().getTotalWorldTime() == this.placedTick) return false;

        PartSubnetProxyFront front = findFrontPart();
        if (front != null) return front.onPartShiftActivate(player, hand, pos);

        if (!player.world.isRemote) {
            player.sendMessage(new TextComponentTranslation("chat.cells.subnet_proxy.need_front"));
        }

        return true;
    }

    private boolean isHoldingComplementaryPart(EntityPlayer player, EnumHand hand, CellsPartType expectedType) {
        ItemStack held = player.getHeldItem(hand);
        if (held.isEmpty()) return false;
        if (!(held.getItem() instanceof ItemCellsPart)) return false;

        CellsPartType type = CellsPartType.getById(held.getItemDamage());
        return type == expectedType;
    }

    private boolean tryPlaceComplementaryPart(EntityPlayer player, EnumHand hand) {
        if (player.world.isRemote) return true;

        IPartHost host = this.getHost();
        AEPartLocation side = this.getSide();
        if (host == null || side == null) return true;

        TileEntity selfTile = host.getTile();
        if (selfTile == null) return true;

        EnumFacing facing = side.getFacing();
        BlockPos adjacentPos = selfTile.getPos().offset(facing);

        // Delegate to AE2's part placement helper
        AEApi.instance().partHelper().placeBus(
            player.getHeldItem(hand), adjacentPos,
            facing.getOpposite(), player, hand, player.world);

        return true;
    }

    @Nonnull
    @Override
    public IPartModel getStaticModels() {
        if (this.isActive() && this.isPowered()) return MODELS_HAS_CHANNEL;
        if (this.isPowered()) return MODELS_ON;
        return MODELS_OFF;
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkChannelsChanged event) {
        this.connectionChanged();
        this.markHostForUpdate();
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkPowerStatusChange event) {
        this.connectionChanged();
        this.markHostForUpdate();
    }
}
