package com.cells.integration.thaumicenergistics;

import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;

import io.netty.buffer.ByteBuf;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.AEApi;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventory;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;

import thaumicenergistics.api.EssentiaStack;
import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.api.storage.IEssentiaStorageChannel;
import thaumicenergistics.integration.appeng.AEEssentiaStack;

import com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic;
import com.cells.items.ItemRecoveryContainer;


/**
 * Essentia-specific implementation of the resource interface logic.
 * Handles essentia import/export interfaces for both tiles and parts.
 * <p>
 * Extends {@link AbstractResourceInterfaceLogic} with EssentiaStack as the resource type,
 * IAEEssentiaStack as the AE2 stack type, and EssentiaStackKey as the key type.
 * <p>
 * Unlike fluid/gas interfaces, essentia uses {@link IAspectContainer} directly
 * on the tile entity rather than a Forge capability. The external handler is
 * exposed through the tile implementing {@link IAspectContainer}.
 */
public class EssentiaInterfaceLogic extends AbstractResourceInterfaceLogic<EssentiaStack, IAEEssentiaStack, EssentiaStackKey> {

    /**
     * Host interface for essentia interfaces.
     */
    public interface Host extends AbstractResourceInterfaceLogic.Host {
    }

    /**
     * Default max slot size for essentia (256 essentia per slot).
     * A standard jar holds 250 essentia, so 256 is a reasonable default.
     */
    public static final int DEFAULT_MAX_SLOT_SIZE = 256;

    /**
     * Default suction for import interface (sink - high suction, wants essentia).
     * The import interface is a SINK that accepts essentia FROM the tube network
     * TO store in the ME network. High suction means tubes will push essentia to us.
     */
    public static final int IMPORT_SUCTION = 128;

    /**
     * Default suction for export interface (source - low suction, provides essentia).
     * The export interface is a SOURCE that provides essentia TO the tube network
     * FROM the ME network. Low suction means tubes will pull essentia from us.
     */
    public static final int EXPORT_SUCTION = 0;

    public EssentiaInterfaceLogic(Host host) {
        super(host, EssentiaStack.class);
        // Override parent's default maxSlotSize for essentia-appropriate values
        this.maxSlotSize = DEFAULT_MAX_SLOT_SIZE;
    }

    @Override
    public String getTypeName() {
        return "essentia";
    }

    /**
     * Get essentia in a specific slot.
     */
    @Nullable
    public EssentiaStack getEssentiaInSlot(int slot) {
        return getResourceInSlot(slot);
    }

    /**
     * Set essentia in a specific slot.
     * Used by GUI essentia pouring in import mode.
     */
    public void setEssentiaInSlot(int slot, @Nullable EssentiaStack essentia) {
        if (slot < 0 || slot >= STORAGE_SLOTS) return;

        setResourceInSlot(slot, essentia);
        this.host.markDirtyAndSave();
    }

    /**
     * Insert essentia into a specific slot.
     * @return Amount actually inserted
     */
    public int insertEssentiaIntoSlot(int slot, EssentiaStack essentia) {
        return insertIntoSlot(slot, essentia);
    }

    /**
     * Drain essentia from a specific slot.
     */
    @Nullable
    public EssentiaStack drainEssentiaFromSlot(int slot, int maxDrain, boolean doDrain) {
        return drainFromSlot(slot, maxDrain, doDrain);
    }

    /**
     * Insert essentia into the ME network.
     */
    public int insertEssentiaIntoNetwork(EssentiaStack essentia) {
        return insertIntoNetwork(essentia);
    }

    // ============================== IAspectContainer Support ==============================

    /**
     * Get the AspectList representation of current storage.
     * Used by IAspectContainer.getAspects().
     */
    public AspectList getAspects() {
        AspectList list = new AspectList();

        for (int i = 0; i < STORAGE_SLOTS; i++) {
            EssentiaStack stored = this.storage[i];
            if (stored == null || stored.getAmount() <= 0) continue;

            Aspect aspect = stored.getAspect();
            if (aspect != null) list.add(aspect, stored.getAmount());
        }

        return list;
    }

    /**
     * Check if the container accepts a specific aspect.
     * Returns true if there's a filter for this aspect.
     */
    public boolean doesContainerAccept(Aspect aspect) {
        if (aspect == null) return false;

        EssentiaStackKey key = EssentiaStackKey.of(aspect);
        return key != null && isInFilter(key);
    }

    /**
     * Check if the container contains at least the specified amount of an aspect.
     */
    public boolean doesContainerContainAmount(Aspect aspect, int amount) {
        if (aspect == null || amount <= 0) return false;

        int slot = findSlotForAspect(aspect);
        if (slot < 0) return false;

        EssentiaStack stored = this.storage[slot];
        return stored != null && stored.getAmount() >= amount;
    }

    /**
     * Get the total amount of a specific aspect in the container.
     * This is what IAspectContainer.containerContains(Aspect) should return.
     */
    public int getEssentiaCount(Aspect aspect) {
        if (aspect == null) return 0;

        long total = 0;
        for (EssentiaStack stored : this.storage) {
            if (stored != null && stored.getAspect() == aspect) {
                total += stored.getAmount();
            }
        }
        // Clamp to avoid overflow
        return (int) Math.min(total, Integer.MAX_VALUE);
    }

    /**
     * Check if the container contains any amount of an aspect.
     * Convenience method for checking presence (amount > 0).
     */
    public boolean containerContainsAny(Aspect aspect) {
        return getEssentiaCount(aspect) > 0;
    }

    /**
     * Add essentia to the container.
     * Used by IEssentiaTransport when tubes push essentia to us.
     * <p>
     * Only IMPORT interfaces accept essentia from tubes - they are SINKS
     * that accept essentia from external sources to store in the ME network.
     *
     * @param aspect The aspect to add
     * @param amount The amount to add
     * @return The amount actually added
     */
    public int addToContainer(Aspect aspect, int amount) {
        if (aspect == null || amount <= 0) return 0;

        // Only import interfaces accept essentia from tubes (they are sinks)
        // Export interfaces PROVIDE essentia, they don't accept it
        if (this.host.isExport()) return 0;

        EssentiaStack toInsert = new EssentiaStack(aspect, amount);
        return receiveFiltered(toInsert, true);
    }

    /**
     * Take essentia from the container (internal method returning amount).
     * Used by IEssentiaTransport when tubes pull essentia from us.
     * <p>
     * Only EXPORT interfaces provide essentia to tubes - they are SOURCES
     * that pull essentia from the ME network and provide it externally.
     * <p>
     * Note: For IAspectContainer.takeFromContainer() which returns boolean,
     * tiles should call this and return (result >= amount).
     *
     * @param aspect The aspect to take
     * @param amount The amount to take
     * @return The amount actually taken
     */
    public int takeEssentiaAmount(Aspect aspect, int amount) {
        if (aspect == null || amount <= 0) return 0;

        // Only export interfaces provide essentia to tubes (they are sources)
        // Import interfaces ACCEPT essentia, they don't provide it
        if (!this.host.isExport()) return 0;

        int slot = findSlotForAspect(aspect);
        if (slot < 0) return 0;

        EssentiaStack drained = drainFromSlot(slot, amount, true);
        return drained != null ? drained.getAmount() : 0;
    }

    /**
     * Find the storage slot for a given aspect.
     */
    public int findSlotForAspect(Aspect aspect) {
        if (aspect == null) return -1;

        EssentiaStackKey key = EssentiaStackKey.of(aspect);
        return key != null ? findSlotByKey(key) : -1;
    }

    /**
     * Get the suction amount for tube connectivity.
     * Import = HIGH suction (sink, accepts essentia), Export = LOW suction (source, provides essentia).
     */
    public int getSuctionAmount() {
        return this.host.isExport() ? EXPORT_SUCTION : IMPORT_SUCTION;
    }

    /**
     * Get the suction type (aspect) for a specific slot or null for any.
     * Returns the first filter aspect if any filters are set.
     *
     * FIXME: Might need to be smarter if we want to support multiple filter slots with different aspects.
     *        Currently, tubes will only push the first aspect type they find in the filters.
     */
    @Nullable
    public Aspect getSuctionType() {
        // For import interfaces (sinks), advertise what we want to accept
        if (!this.host.isExport()) {
            for (int i = 0; i < FILTER_SLOTS; i++) {
                EssentiaStack filter = this.filters[i];
                if (filter != null && filter.getAspect() != null) {
                    return filter.getAspect();
                }
            }
        }
        // For export interfaces (sources), return null = any type we have
        return null;
    }

    /**
     * Get the first stored essentia type.
     * Used by export interfaces to report what essentia type they have available.
     * FIXME: Might need to be a bit smarter, if we want to export multiple types at once.
     *        In the current state, tubes will only pull the first aspect type they find in storage.
     *
     * @return The first stored aspect, or null if empty
     */
    @Nullable
    public Aspect getStoredEssentiaType() {
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            EssentiaStack stored = this.storage[i];
            if (stored != null && stored.getAmount() > 0 && stored.getAspect() != null) {
                return stored.getAspect();
            }
        }
        return null;
    }

    // ============================== Abstract method implementations ==============================

    @Override
    @Nullable
    protected EssentiaStackKey createKey(EssentiaStack resource) {
        return EssentiaStackKey.of(resource);
    }

    @Override
    protected int getAmount(EssentiaStack resource) {
        return resource.getAmount();
    }

    @Override
    protected void setAmount(EssentiaStack resource, int amount) {
        resource.setAmount(amount);
    }

    @Override
    protected EssentiaStack copyWithAmount(EssentiaStack resource, int amount) {
        Aspect aspect = resource.getAspect();
        if (aspect == null) return null;
        return new EssentiaStack(aspect, amount);
    }

    @Override
    protected EssentiaStack copy(EssentiaStack resource) {
        return resource.copy();
    }

    @Override
    protected String getLocalizedName(EssentiaStack resource) {
        Aspect aspect = resource.getAspect();
        return aspect != null ? aspect.getName() : "Unknown";
    }

    @Override
    protected IAEEssentiaStack toAEStack(EssentiaStack resource) {
        return AEEssentiaStack.fromEssentiaStack(resource);
    }

    @Override
    protected EssentiaStack fromAEStack(IAEEssentiaStack aeStack) {
        return aeStack.getStack();
    }

    @Override
    protected long getAEStackSize(IAEEssentiaStack aeStack) {
        return aeStack.getStackSize();
    }

    @Override
    protected void writeResourceToNBT(EssentiaStack resource, NBTTagCompound tag) {
        Aspect aspect = resource.getAspect();
        if (aspect != null) {
            tag.setString("Aspect", aspect.getTag());
            tag.setInteger("Amount", resource.getAmount());
        }
    }

    @Override
    @Nullable
    protected EssentiaStack readResourceFromNBT(NBTTagCompound tag) {
        if (!tag.hasKey("Aspect")) return null;

        String aspectTag = tag.getString("Aspect");
        Aspect aspect = Aspect.getAspect(aspectTag);
        if (aspect == null) return null;

        int amount = tag.hasKey("Amount") ? tag.getInteger("Amount") : 1;
        return new EssentiaStack(aspect, amount);
    }

    @Override
    protected String getResourceName(EssentiaStack resource) {
        Aspect aspect = resource.getAspect();
        return aspect != null ? aspect.getTag() : "";
    }

    @Override
    @Nullable
    protected EssentiaStack getResourceByName(String name, int amount) {
        Aspect aspect = Aspect.getAspect(name);
        if (aspect == null) return null;
        return new EssentiaStack(aspect, amount);
    }

    // ============================== Stream Serialization ==============================

    @Override
    public boolean readStorageFromStream(ByteBuf data) {
        boolean changed = false;

        // Clear all storage first
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (this.storage[i] != null) {
                this.storage[i] = null;
                changed = true;
            }
        }

        int count = data.readShort();
        for (int idx = 0; idx < count; idx++) {
            int slot = data.readShort();
            int tagLen = data.readByte() & 0xFF;
            byte[] tagBytes = new byte[tagLen];
            data.readBytes(tagBytes);
            String tag = new String(tagBytes, StandardCharsets.UTF_8);
            int amount = data.readInt();

            if (slot < 0 || slot >= STORAGE_SLOTS) continue;

            Aspect aspect = Aspect.getAspect(tag);
            if (aspect != null) {
                this.storage[slot] = new EssentiaStack(aspect, amount);
                changed = true;
            }
        }

        return changed;
    }

    @Override
    public void writeStorageToStream(ByteBuf data) {
        // Count non-empty storage slots first
        int count = 0;
        for (int i = 0; i < STORAGE_SLOTS; i++) {
            if (this.storage[i] != null && this.storage[i].getAmount() > 0) count++;
        }

        data.writeShort(count);

        for (int i = 0; i < STORAGE_SLOTS; i++) {
            EssentiaStack essentia = this.storage[i];
            if (essentia == null || essentia.getAmount() <= 0) continue;

            Aspect aspect = essentia.getAspect();
            if (aspect == null) continue;

            String tag = aspect.getTag();
            byte[] tagBytes = tag.getBytes(StandardCharsets.UTF_8);

            data.writeShort(i);
            data.writeByte(tagBytes.length);
            data.writeBytes(tagBytes);
            data.writeInt(essentia.getAmount());
        }
    }

    @Override
    public boolean readFiltersFromStream(ByteBuf data) {
        boolean changed = false;

        // Clear all filters first
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (this.filters[i] != null) {
                this.filters[i] = null;
                changed = true;
            }
        }

        int count = data.readShort();
        for (int idx = 0; idx < count; idx++) {
            int slot = data.readShort();
            int tagLen = data.readByte() & 0xFF;
            byte[] tagBytes = new byte[tagLen];
            data.readBytes(tagBytes);
            String tag = new String(tagBytes, StandardCharsets.UTF_8);

            if (slot < 0 || slot >= FILTER_SLOTS) continue;

            Aspect aspect = Aspect.getAspect(tag);
            if (aspect != null) {
                // Filters have amount 1 (type only)
                this.filters[slot] = new EssentiaStack(aspect, 1);
                changed = true;
            }
        }

        if (changed) this.refreshFilterMap();

        return changed;
    }

    @Override
    public void writeFiltersToStream(ByteBuf data) {
        // Count non-empty filters first
        int count = 0;
        for (int i = 0; i < FILTER_SLOTS; i++) {
            if (this.filters[i] != null) count++;
        }

        data.writeShort(count);

        for (int i = 0; i < FILTER_SLOTS; i++) {
            EssentiaStack essentia = this.filters[i];
            if (essentia == null) continue;

            Aspect aspect = essentia.getAspect();
            if (aspect == null) continue;

            String tag = aspect.getTag();
            byte[] tagBytes = tag.getBytes(StandardCharsets.UTF_8);

            data.writeShort(i);
            data.writeByte(tagBytes.length);
            data.writeBytes(tagBytes);
        }
    }

    @Override
    protected IMEInventory<IAEEssentiaStack> getMEInventory(IStorageGrid storage) {
        return storage.getInventory(
            AEApi.instance().storage().getStorageChannel(IEssentiaStorageChannel.class)
        );
    }

    @Override
    protected ItemStack createRecoveryItem(EssentiaStack resource) {
        if (resource == null || resource.getAspect() == null || resource.getAmount() <= 0) {
            return ItemStack.EMPTY;
        }

        // Use the recovery container to store the essentia for later recovery
        return ItemRecoveryContainer.createForEssentia(
            resource.getAspect().getTag(),
            resource.getAmount()
        );
    }
}
