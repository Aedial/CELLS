# Compacting/Extra Large Lattice Storage (C.E.L.L.S.)

An AE2-UEL addon providing additional storage cells with extended capacities and special features.

## FAQ
### My Compacting Cells are not refreshing in the ME Chest until I reopen it
This is a limitation of the ME Chest's implementation, which doesn't listen for changes on the network. It handles everything by itself, which doesn't work well with the virtual items of the Compacting Cells. This issue is purely visual, and the cell is working correctly.

## Features

### Import Interface
A block that acts as a filtered interface for importing items into the ME network. It needs to be configured to allow specific items, and can be used to import items into the network from machines that don't necessarily have a filtered export capability (Woot, Ultimate Mob Farm, etc). It does not have any exporting/stocking or crafting capabilities, and only works as an import interface. The top part of each slot is used for the filter, while the bottom part is used for the actual import. The size of the slots can be configured in the GUI, allowing more or less items to be kept in the interface if the export targets are full.

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
- **1k - 64k Compacting Storage Cells** (Standard AE2 sizes)
- **256k - 16M Compacting Storage Cells** (NAE2-equivalent sizes)
- **64M - 2G Compacting Storage Cells** (Extended sizes)
- **1k - 16M Hyper-Density Compacting Storage Cells** (with ~2.1B multiplier per byte)

#### Partition Protection
- If a compacting cell contains items, the partition cannot be changed.
- Empty the cell first before changing what item type it stores.


### Hyper-Density Storage Cells
Storage cells with an internal multiplier of ~2.1 billion per displayed byte:
- **1k - 1G Hyper-Density Storage Cells** (each "byte" holds ~17.2B items)

### Hyper-Density Fluid Storage Cells
Fluid storage cells with the same massive multiplier:
- **1k - 1G Hyper-Density Fluid Storage Cells** (each "byte" holds ~17.2M buckets)

### Hyper-Density Compacting Cells
Combining hyper-density storage with compacting functionality:
- **1k - 1G Hyper-Density Compacting Storage Cells** (each "byte" holds ~17.2B items, with compression capabilities)


### Upgrades

#### Void Overflow Card
Install in a cell's upgrade slots to void excess items when full.
Useful for automated systems where overflow should be destroyed.

**Compatible with**: Compacting Cells, Hyper-Density Cells, Hyper-Density Compacting Cells, Import Interface

### Trash Unselected Card
Install in an Import Interface to void items that don't match any filter. This is useful to prevent clogging the interface with unwanted items, especially when used with machines that export items without filtering capabilities.

**Compatible with**: Import Interface

#### Equal Distribution Card
Limits the number of types a cell can hold and divides capacity equally among them. Available in 7 variants:
- **1x**: 1 type (all capacity to one item)
- **2x**: 2 types (half capacity each)
- **4x**: 4 types (quarter capacity each)
- **8x**: 8 types
- **16x**: 16 types
- **32x**: 32 types
- **63x**: 63 types (default max)
- **unbounded**: inherits max types from the cell

Use cases:
- Force a cell to hold exactly one item type with maximum capacity (1x)
- Prevent one item from dominating cell storage
- Ensure fair distribution across multiple stored items

**Compatible with**: Hyper-Density Storage Cells


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

### General Settings
- **Max Types**: Maximum types per cell (default: 63)

### Idle Power Drain
Configure power drain per tick for each cell type:
- Compacting Cells
- Hyper-Density Cells
- Hyper-Density Compacting Cells
- Fluid Hyper-Density Cells

### Cell Toggles
Enable or disable entire cell categories:
- Compacting Cells
- Hyper-Density Cells
- Hyper-Density Compacting Cells
- Fluid Hyper-Density Cells

## API

This mod exposes an API for computing compression chains:

```java
// Initialize the compacting cell chain for a given partition. Should be called when setting the partition item.
void initializeCompactingCellChain(@Nonnull ItemStack cellStack, @Nonnull ItemStack partitionItem, @Nonnull World world);
```

## Commands

### /fillcell
Fill a storage cell with a specified quantity of items or fluids, for testing purposes.
- Usage: `/fillCell <item id>|<fluid id> <count>`
- Supports suffixes for count: k (thousand), m (million), b (billion), t (trillion), q (quadrillion), qq (quintillion).
- The storage cell must be held in the main hand.
- Example: `/fillCell minecraft:iron_ingot 10k` fills the held cell with 10,000 Iron Ingots.


## Building
```
./gradlew -q build
```
Resulting jar will be under `build/libs/`.

## License
This project is licensed under the MIT License - see the LICENSE file for details.
