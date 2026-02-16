# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html


## [0.3.7] - 2026-02-16
### Fixed
- Fix items with the same ID and metadata but different NBT, incorrectly merging in hyper-density cells (e.g., Thaumcraft vis crystals).


## [0.3.6] - 2026-02-15
### Fixed
- Fix Import Interface's Polling Rate and Max Slot Size using short int instead of int, causing overflow and negative values when set above 32767.


## [0.3.5] - 2026-02-14
### Changed
- Improve the tooltips to clarify some behaviors.
- Tweak the Import Interface top and bottom.


## [0.3.4] - 2026-02-14
### Added
- Add configurable polling rate to Import Interface, allowing users to set a fixed interval for importing items instead of relying on AE2's adaptive rates. This way, you can stock 100k items to send to the network every hour, for example.

### Fixed
- Fix some stale filter on world load for the Import Interface, which caused it to not import items until the filter was updated.


## [0.3.3] - 2026-02-13
### Added
- Add upgrades support for Import Interface: Trash Unselected (voids items that don't match any filter) and Overflow (voids excess items that don't fit in the slot).

### Fixed
- Fix Cells accepting any upgrade, instead of just the custom + standard AE2 ones.

### Changed
- Apply stricter checks for valid filters in the Import Interface. Filters cannot be duplicated or orphaned (having items in the storage slot without a filter).


## [0.3.2] - 2026-02-12
### Fixed
- Fix Cell disassembly overwriting the whole stack to empty, instead of just one cell.


## [0.3.1] - 2026-02-11
### Added
- Add Import Interface texture and fix the recipe.


## [0.3.0] - 2026-02-11
### Added
- Add Import Interface, a block that act as a filtered interface for importing items into the ME network. It needs to be configured to allow specific items, and can be used to export items into the network from machines that don't necessarily have a filtered export capability (Woot, Ultimate Mob Farm, etc). It does not have any exporting or crafting capabilities, and only works as an import interface.

### Fixed
- Fix Hyper-Density Cells being both "full" and "empty" at the same time, due to NBT desync.


## [0.2.5] - 2026-02-10
### Added
- Further optimize all cells by 2-3x, specifically in very high load scenarios (multiple operations in the same tick). /!\ This optimization assumes the network is not doing hacky things and lacks some proper synchronization, so it might cause issues with mods that do that.


## [0.2.4] - 2026-02-09
### Added
- Improve the performance of compacting cells by a factor 4x.


## [0.2.3] - 2026-02-09
### Fixed
- Fix compacting cells not properly computing the chain when paritioned with the lowest tier, causing the compression ratio to be 1 instead of 4 or 9.


## [0.2.2] - 2026-02-08
### Added
- Add Equal Distribution Card without type limit (unbounded card), meaning it inherits the max types from the cell itself. This is useful if you want equal distribution with higher than 63 types (via config).
- Add textures for Hyper-Density Cells (courtesy of archezechiel)

### Fixed
- Fix type limit config not being applied to Hyper-Density Cells.
- Fix Compacting Cell sometimes not properly reporting a change to the network, when used across subnets.


## [0.2.1] - 2026-02-05
### Added
- Add recipes for all processors, cell components, and upgrades
- Add textures for Hyper-Density Compacting/Fluid Cell Components (courtesy of archezekiel)
- Add overflow protection to Compacting Cells
- Add 3x, 6x, 9x, 12x, 15x Compression/Decompression cards for Compacting Cells. The partitioned item determines the compression chain, and the card determines how many tiers can be compressed/decompressed in that chain. It only goes in one direction at a time (compressing or decompressing), depending on the card used.
  - 3x card allows compressing/decompressing up to 3 tiers (e.g., nugget → ingot → block → double block)
  - 6x card allows compressing/decompressing up to 6 tiers
  - 9x card allows compressing/decompressing up to 9 tiers
  - 12x card allows compressing/decompressing up to 12 tiers
  - 15x card allows compressing/decompressing up to 15 tiers

### Fixed
- Fix Compacting Chain not replacing the previous one when partitioned via API
- Fix (?) some issues with Compacting Cells not properly reporting changes in virtual items to the network, until refreshed (e.g., by reinserting the cell)

### Changed
- Remove normal cells, as they didn't bring much value. Other mods already provide larger normal storage cells, and even 1k Hyper-Density Cells are better than 2G normal cells. It has been decided to not use the normal cell components in the crafting recipes for Hyper-Density Cells.


## [0.2.0] - 2026-02-04
### Added
- Add Fluid Hyper-Density Storage Cells: 1k to 1G (multiplying base size by ~2.1B)
- Add Fluid Normal Storage Cells: 64M, 256M, 1G, 2G
- Add fluid support to the `/fillcell` command
- Add textures for 1k to 1G Hyper-Density Storage Components (courtesy of archezekiel)

### Fixed
- Wire all cell idle drain values from the config file (previously hardcoded)
- Fix Overflow Card being too eager and voiding anything that couldn't be inserted, instead of only voiding excess of already stored types
- Fix some overflow issues with Hyper-Density Cells when nearing maximum capacity


## [0.1.0] - 2026-02-01
### Added
- Add Storage Cells for larger capacities:
  - Normal Storage Cells: 64M, 256M, 1G, 2G
  - Hyper-Density Storage Cells: 1k to 1G (multiplying base size by 2.1B)
- Add Compacting Storage Cells that expose compressed/decompressed item forms to the ME network
  - Available in 1k to 16MG sizes
  - Partition an item to set up the compression chain (e.g., Iron Ingot → Iron Block / Iron Nugget)
  - As partition dictates the compression chain, it cannot be changed while items are stored. This also means that items cannot be inserted before partitioning. If partitioned with the Cell Workbench, inserting the partitioned item is required to initialize the chain. If the partition is changed to something else while the cell is not empty, the new partition is reverted back to the previous one.
  - If partitioned from the Cell Terminal (from the Cell Terminal mod), the chain is automatically initialized. Inserting the partitioned item is not required.
  - Virtual conversion: Insert any tier and extract any other tier (e.g., insert nuggets → extract blocks)
  - Storage capacity counts only the main (partitioned) item tier
- Add Compacting Storage Components for crafting Compacting Cells
- Add server-side configuration file with in-game GUI editor
  - Configure max types per cell
  - Configure idle power drain for each cell type
  - Enable/disable individual cell types (Compacting, HD, HD Compacting, Normal)
- Add Void Overflow Card upgrade for storage Cells: voids excess items when the cell is full. Only works with Compacting and Hyper-Density Cells from this mod.
- Add Equal Distribution Card upgrade (7 variants: 1x, 2x, 4x, 8x, 16x, 32x, 63x)
  - Limits the number of types a cell can hold to the card's value
  - Divides total capacity equally among all types
  - Works with Hyper-Density Storage Cells (NOT compatible with Compacting Cells)
- Add `IItemCompactingCell` interface for Compacting Cells' chain initialization