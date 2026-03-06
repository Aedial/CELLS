# Compacting/Extra Large Lattice Storage (C.E.L.L.S.)

An AE2-UEL addon providing additional storage cells with extended capacities and special features.

## FAQ
### My Compacting Cells are not refreshing in the ME Chest's GUI until I reopen it
This is a limitation of the ME Chest's implementation, which doesn't listen for changes on the network. It only listens to what the player manually inserts, which doesn't work well with the virtual items of the Compacting Cells. This issue is purely visual, and resolves itself when leaving the GUI and reopening it.

### I have 9.2+ Quintillion items but the terminal shows way less than that
It may be that your version of AE2 does not handle Long overflows (so far, AE2-UEL 0.56.7 doesn't), causing the count to wrap around and show incorrect values. This is purely a display issue, and your items are safe in the cells. Cell Terminal should show the correct values as it does not accumulate the content of multiple cells, but rather shows each cell separately.

### What mods does C.E.L.L.S. support?
The mod requires AE2-UEL. It also has support for:
- MixinBooter: Allows C.E.L.L.S.'s Storage Cells to stack to 64. Without it, allowing them to stack enables a duplication exploit with the ME Chest
- JEI: Import Interface drag-and-drop and quick-add features
- Thaumic Energistics: Configurable Cell components
- Mekanism Energistics: Configurable Cell components

### Why such heavy warnings on increasing number of types?
Because of limitations in Minecraft and AE2 :
- Let's take the default packet size of 2MB. It's what you are fixed to unless you use a mod to increase it. An enchanted piece of armor can take 100s-1000s of bytes of NBT data, a normal drive can store 10 cells, so 2MB / 1000B / 10 cells = a measly 200 different items before you hit the limit. That's really not a lot!
- If a chunk contains more than that limit *across all machines/containers*, the chunk will fail to load and kick all players trying to load it. This is a vanilla Minecraft limitation. That's why you should isolate and separate your NBT-heavy content into different chunks and networks, to avoid chunk-banning yourself. Getting stuck in that chunk will result in a crash loop until you manually move your player with an NBT editor to a different chunk, or remove the offending machines. There are ways to avoid this issue, but they come at a performance cost and do not solvee the issue below, which is inherent to AE2's design.
- AE2 will kick you from the network if the total NBT size of the network's storage exceeds this limit. This means the total combined NBT size of all your cells on a single network. Of course, making a separate network can alleviate this issue, but not solve the root of it. Hyper-Density Cells are not much bigger than regular cells in terms of NBT size, as the extra capacity is negligible compared to the NBT size of the items themselves.
- TL;DR: The item count itself doesn't matter, what you store does.


## Features

### Import Interface/Fluid Import Interface
A block that acts as a filtered interface for importing items/fluids into the ME network. It needs to be configured to allow specific content, and can be used to import items/fluids into the network from machines that don't necessarily have a filtered export capability (Woot, Ultimate Mob Farm, etc). It does not have any exporting/stocking or crafting capabilities, and only works as an import interface. The top part of each slot is used for the filter, while the bottom part is used for the actual import. The size of the slots can be configured in the GUI, allowing higher/lower amount of each item/fluid to be kept in the interface if the export targets are full. The polling rate can also be configured, allowing content to be imported at a fixed interval instead of AE2's adaptive rates.

They hold 36 slots, expandable up to 5x with capacity cards.

### Export Interface/Fluid Export Interface
The counterpart to the Import Interface, allowing items/fluids to be exported from the ME network to any outside piping. Slot size and polling rate apply all the same as the Import Interface.


### Compacting Storage Cells
Storage cells that automatically expose compressed and decompressed forms of items to the ME network, similar to Storage Drawers' Compacting Drawer.

#### How It Works
1. **Partition Required**: Compacting cells require a partition to be set before they can accept items.
2. **Compression Chain**: When partitioned with an item (e.g., Iron Ingot), the cell automatically detects the compression chain:
    - Higher tier: Iron Block (compressed form)
    - Main tier: Iron Ingot (the partitioned item)
    - Lower tier: Iron Nugget (decompressed form)
3. **Virtual Conversion**: Items are stored in a unified pool and can be extracted in any compression tier:
    - Insert 81 Iron Nuggets → Extract 81 Nuggets, 9 Iron Ingots, or 1 Iron Block
    - Insert 1 Iron Block → Extract 9 Iron Ingots, 81 Iron Nuggets, or 1 Iron Block
    - All conversions are lossless and instant
    - Due to size limitations, the maximum capacity is ~9.2 Quintillion items of the lowest tier. This is mainly an issue with high compression chains (using compression/decompression cards)
4. **Single Item Type**: Each compacting cell stores only one item type (with its compression variants).
5. **Storage Counting**: Storage capacity is measured in main tier (partitioned item) units, so no need to worry about conversion factors when checking capacity.

#### Available Tiers
- **1k - 2G Compacting Storage Cells** (normal sizes)
- **1k - 1G Hyper-Density Compacting Storage Cells** (with ~2.1B multiplier per byte)

#### Partition Protection
- If a compacting cell contains items, the partition cannot be changed.
- Empty the cell first before changing what item type it stores.


### Hyper-Density Storage Cells
Storage cells with an internal multiplier of ~2.1 billion per displayed byte:
- **1k - 1G Hyper-Density Storage Cells** (each "byte" holds ~17.2B items)

### Hyper-Density Fluid Storage Cells
Fluid storage cells with the same massive multiplier:
- **1k - 1G Hyper-Density Fluid Storage Cells** (each "byte" holds ~17.2B buckets)

### Hyper-Density Compacting Cells
Combining hyper-density storage with compacting functionality:
- **1k - 1G Hyper-Density Compacting Storage Cells** (each "byte" holds ~17.2B items, with compression capabilities)


### Configurable Storage Cell
A universal storage cell that accepts a ME Storage Component (AE2) to define its capacity and storage type:
- Insert a component to configure the cell's capacity and storage type (item or fluid).
- **Built-in equal distribution**: capacity is divided equally among all types (configurable max types, default 63). The bytes overhead is taken into account proactively, meaning the cell only has 50% of its total bytes available for storage, reserving the other 50% for overhead.
- **Per-type capacity limit**: configure a maximum items/mB per type via the GUI text field
- Shift-right-click to disassemble (returns cell, component, and upgrades)
- The cell can be crafted with the component to directly insert it, and will show such things in JEI.
- Components cannot be removed while the cell has content. Swapping to another component of the same type (item↔item, fluid↔fluid) is allowed if the new component has enough capacity for the existing data.

#### Component Whitelist
The list of accepted storage components is defined in [configurable\_components.cfg](https://github.com/Aedial/CELLS/blob/main/src/main/resources/assets/cells/configurable_components.cfg). To add or remove supported components, place a copy of this file in your **Forge config directory** (`config/configurable_components.cfg`). The config override takes priority over the bundled file.

**This means you can define custom components past the default ones provided by the mods in your modlist.**

Each entry has the format:
```
registry_name@metadata = bytes,channel,tier_name
```
- `registry_name@metadata`: The item's registry name and damage value (e.g. `appliedenergistics2:material@35`)
- `bytes`: Total byte capacity of the component (e.g. `1024` for 1K)
- `channel`: `item` or `fluid`
- `tier_name`: Used for texture selection (e.g. `1k`, `64k`, `1g`)


### Upgrades

#### Void Overflow Card
Install in a cell's upgrade slots to void excess items when full. Useful for automated systems where overflow should be destroyed.

**Compatible with**: Compacting Cells, Hyper-Density Cells, Hyper-Density Compacting Cells, Configurable Storage Cells, Import Interfaces


#### Trash Unselected Card
Install in an Import Interface to void items that don't match any filter. This is useful to prevent clogging the machine with leftover items, especially when used with machines that export items without filtering capabilities.

**Compatible with**: Import Interfaces


#### Equal Distribution Card
Limits the number of types a cell can hold and divides capacity equally among them. Available in 7 variants:
- **1x**: 1 type (all capacity to one item)
- **2x**: 2 types (half capacity each)
- **4x**: 4 types (quarter capacity each)
- **8x**: 8 types
- **16x**: 16 types
- **32x**: 32 types
- **63x**: 63 types (default max)
- **unbounded**: inherits max types from the cell (see config)

Use cases:
- Force a cell to hold exactly one item type with maximum capacity (1x)
- Prevent one item from dominating cell storage
- Ensure fair storage distribution across multiple stored items

**Compatible with**: Hyper-Density Storage Cells


#### Oredict Card
Install in a Compacting Cell's upgrade slots to enable oredict support for compression/decompression. When enabled, the cell will consider items in the same oredict group as equivalent. For example, if you have both Copper Ingots from mod A and mod B in the same oredict group, the cell accepts both and converts them into the partitioned item, unifying the storage. This is equivalent to the "Convertion Upgrade" from Storage Drawers, but for Compacting Cells.

**Compatible with**: Compacting Cells


#### Compression/Decompression Cards
Install in a Compacting Cell's upgrade slots to increase the number of compression tiers that are available for compression/decompression. Do note it only goes in one direction at a time (compressing or decompressing), depending on the card used.
- **3x Card**: Allows compressing/decompressing up to 3 tiers (e.g., nugget → ingot → block → double block)
- **6x Card**: Allows compressing/decompressing up to 6 tiers
- **9x Card**: Allows compressing/decompressing up to 9 tiers
- **12x Card**: Allows compressing/decompressing up to 12 tiers
- **15x Card**: Allows compressing/decompressing up to 15 tiers

**Compatible with**: Compacting Cells


## Configuration

The mod includes a server-side configuration file with an in-game GUI editor:

### Max Types
Configure the maximum number of types allowed per cell:
- Hyper-Density Storage Cells
- Hyper-Density Fluid Storage Cells
- Configurable Storage Cells (each content type has its own config)

### Idle Power Drain
Configure power drain per tick for each cell type:
- Compacting Cells
- Hyper-Density Cells
- Hyper-Density Compacting Cells
- Hyper-Density Fluid Cells
- Configurable Cells

### Cell Toggles
Enable or disable entire cell categories:
- Compacting Cells
- Hyper-Density Cells
- Hyper-Density Compacting Cells
- Hyper-Density Fluid Cells
- Configurable Cells (single categories can be disabled by removing all components of that category from the whitelist)

### NBT Debug
Enable or disable the NBT Size tooltip for all cells. This tooltip shows an upper bound estimation of the NBT size of the cell's content, to help with realizing when to move NBT-heavy content somewhere else, to avoid chunkbanning or AE2's network kick.
This setting may take some performance away if your cells have items with large NBT data flickering in and out of them, but this should be rare in practice and is generally not a problem for most use cases.


## Commands

### /fillcell
Fill a storage cell with a specified quantity of items or fluids, for testing purposes.
- Usage: `/fillCell <item id>|<fluid id> <count>`
- Supports suffixes for count: k (thousand), m (million), b (billion), t (trillion), q (quadrillion), qq (quintillion).
- The storage cell must be held in the main hand.
- Example: `/fillCell minecraft:iron_ingot 10k` fills the held cell with 10,000 Iron Ingots.


## Credits
- Chinese translation: @ZHAY10086
- Hyper-Density Item/Fluid Cells'/Cell Components' textures: ArchEzekiel