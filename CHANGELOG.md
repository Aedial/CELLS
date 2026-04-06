# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html


## [0.5.12-beta2] - 2026-04-04
### Added
- Add in-interface GUI for Pull/Push Cards. This way, you can configure the card without removing it and inserting it back.


## [0.5.12-beta] - 2026-04-03
### Fixed
- Fix Fluid (Hyper-Density/Configurable), Gas (Configurable), and Essentia (Configurable) partitions using Item config, which allowed items to be encoded even if they were not valid for this specific cell type. Items are now correctly validated at encoding time. Re-encoding should not be required for cells that already have a valid partition, but it's always good to do it in order to remove invalid items.

### Added
- Optimize some Push/Pull Card logic.
- Optimize getAvailableItems (protoStack caching and NBT bypass) for all cell types.
- Mention Push/Pull Cards in the controls help widget and the README.
- Add the /inspectSlots command, to show details about the slots of the block the player is looking at.

### Changed
- Move the Controls Help from bottom of the GUI to centered vertically.

### Technical
- Adaptive Mode is set to 1s (20 ticks) when a Push/Pull Card is present, to prevent excessive ticking and network I/O. The card can tick faster (e.g., every tick), but network I/O is throttled to a set minimum to prevent lag. Considering normal Adaptive mode can 
- External Capacilities' slots for Keep Quantity are now queried once instead of once per item in filter.
- IItemRepository is now queried instead of IItemHandler when the TE supports it.
- Slots' content and space is now cached between IItemHandler calls (in the same tick), to avoid redundant queries.


## [0.5.12-alpha2] - 2026-04-02
### Changed
- Clean tooltips to better convey intents and behaviors.
- Change the recipe of Equal Distribution and Compression/Decompression cards, as it's more a configuration than balance.


## [0.5.12-alpha] - 2026-03-31
### Added
- Implement Auto-Pull and Auto-Push Card functionality for all resource interfaces (Item, Fluid, Gas, Essentia). Cards can be configured with transfer quantity, interval, and keep-quantity to automatically move resources between adjacent inventories and the ME network buffer.
- Optimize the I/O both from and to the Interfaces.


## [0.5.11-beta2] - 2026-03-30
### Fixed
- Fix Essentia Storage Bus having caching issues with our interfaces (this is a bug from Thaumic Energistics). This fix makes the Essentia Interface itself handle all the work for the Bus and force changes onto it. This will not fix *any* other block you put the Bus on, either fix the Storage Bus or do the work yourself.
- Fix Interface tiles not showing on Network Tool.
- Fix migration of Export Fluid Interfaces from 0.5.9-rc.

### Changed
- Slight changes in Max Slot Size's typing behavior.


## [0.5.11-beta] - 2026-03-28
### Fixed
- Fix some Upgrades not showing anymore in JEI/Creative Tab.

### Added
- Increase the max slot size limit from 2.1B to 9.2 quintillion (Long.MAX_VALUE), to allow for really high throughput I/O or long waiting times.
- Make max slot size GUI more readable.
- Add Items to the Recovery Orb for quantities above Max Int (cannot hold more than max int in an ItemStack).
- Add a config option to limit the max slot size that players are allowed to set, as a way to balance the potential "bottomless storage" aspect of the interfaces.
- Add a config option for the minimum polling rate, to allow reducing the strain on servers.
- Add recipes for Gas/Essentia Import/Export Interfaces.
- Add Auto-pull/Auto-push cards for Import/Export Interfaces, which automatically pull/push items from/to adjacent inventories. The pull/push interval is set in the card's GUI and can be different for each card. The logic is not yet implemented and will come in a future update.


## [0.5.10-beta3] - 2026-03-27
### Fixed
- Fix incorrect use of server-side I18n on several places.
- Minor tooltip fix.


## [0.5.10-beta2] - 2026-03-26
### Added
- Make Adaptive Mode more efficient by only forcing the wake-up when we are sleeping (not just slower). This means a somewhat constant stream of item will not ask the network to wake up at every insertion, relying instead on AE2's adaptive rates. "Fixed Polling" is not affected by this change (as it never "sleeps").
- Add AE2 ToolNetworkTool support for Import/Export Interfaces (Upgrade Cards holder).

### Fixed
- Fix Import Interfaces not waking up when resources are inserted in via GUI after switching to Adaptive mode.
- Fix client-side refresh of interface slots not sending NBT (losing the NBT of potions, for example), causing the client to display incorrect information.
- Fix Content Recovery Orb not handling fluid NBT.
- Fix crash when JEI is not present (missing some checks).
- Fix crash when Storage Drawers is present (double IItemRepository initialization).


## [0.5.10-beta] - 2026-03-26
### Added
- Optimize the Storage Bus on Item Interface interaction (only ITEM) to be at the same performance level as a drawer wall (without the drawer wall sync overhead).
- Add Creative Fluid Cell, the fluid counterpart of the Creative Cell, with the same behavior but for fluids.
- Add quick-add keybind handling for the Creative Cell, like is done for Import/Export interfaces.
- Add Gas Creative Cell.
- Add Gas Import/Export interface.
- Add Essentia Creative Cell.
- Add Essentia Import/Export interface. It can be used directly on Thaumatorium, Infusion, and probably also with Essentia hatches.
- Add Recovery Orb item, an item that is dropped when a non-item interface is broken/shrunk and cannot send its contents back to the network.

### Fixed
- Fix Import/Export Fluid Interfaces not accepting the same fluid with different NBT (e.g. potions).
- Fix some blocks using the wrong texture.
- Fix inconsistencies with the Memory Card (now tested on all Full-block vs Part).

### Changed
- Make the Creative Cell accept and void content that they produce, acting as both a producer and a sink, to avoid issues with items not being able to return to the network because we extracted them from the cell but they can't be inserted back.
- Reject Cells as valid targets for Creative Cells.
- Retouch component textures to be easier to work with (more unified). The wave animation has been removed to be reworked later (was nearly imperceptible).

### Technical
- Unify Import/Export Interfaces (tiles and parts) even more, to allow for less tedious addition of Gas/Essentia types.
- Unify Creative Cells for the same purpose.
- Unify all slot behaviors (Sneak-click, left-click, right-click, quick-add, JEI add, rendering, etc.)
- Allow Item Interface to provide IItemRepository instead of only IItemHandler, for less capabilities overhead.
- Replace backed textures (~150) by overlaid layers with tinting. Any new cell type would not add new textures, only requiring tinting support in the code. This shaves ~300kB from the (compressed) JAR.


## [0.5.9-rc] - 2026-03-17
### Fixed
- Fix bug where the Import/Export Interface may never wake up in Adaptive mode (polling rate = 0) when there was nothing to import/export and the grid was cut (power loss or not enough channels) then reconnected.
- Fix placing disassembled Import/Export Interfaces resetting them to default settings and filters.
- Fix right-click then left-click on an Export Interface slot putting (Slot size - 32) items in the player's hand, instead of a stack/half a stack.
- Unify the logic of Import/Export Interfaces, squashing a few minor bugs and inconsistencies.
- Mitigate base AE2-UEL crash when handling oversized stack counts in slotClick for some specific mods configurations.

### Changed
- Change disassembly behavior to keep upgrades for Import/Export Interfaces, so that filters are not lost due to capacity shrinkage. The upgrades are still dropped on breaking and the inventory is dropped on both actions.


## [0.5.8] - 2026-03-16
### Added
- Optimize oredict matching for the Ore Dictionary Card, from O(n*m) to O(m) where n is the number of ore dict entries in the cell and m is the number of ore dict entries for the input item. This should mainly affect cases with a big compacting chain (e.g., using Compression/Decompression Cards). m is usually small (most items have 0-2 ore dict entries), so this should be a significant improvement in those cases, while not causing much overhead in the general case.
- Add whitelist and blacklist files for the Oredict matching of the Ore Dictionary Card, to allow modpacks to control which ore dictionary entries are valid for the card.
- Extend the list of default Thaumcraft and Mekanism components for the Configurable Cell.

### Fixed
- Fix the Ore Dictionary Card being too lenient in its matching, allowing every oredict <-> oredict match without restriction, which could be exploited in some cases (e.g., any dye -> lapis or any log -> any other log). The card now matches only via whitelist/blacklist in the config file (defaults to bundled if not present). This matches what the Storage Drawers's Conversion Upgrade does, and should be less exploitable. The whitelist/blacklist can be configured by modpacks to allow/deny specific entries if needed.

### Changed
- Move the default configurable_components.cfg file to config/cells. The config/ path is still valid, but the new location takes precedence if both exist.


## [0.5.7] - 2026-03-13
### Added
- Add a config to control the number of upgrade slots for each cell type.
- Increase the default number of upgrade slots for all cell types from 2 to 4.


## [0.5.6] - 2026-03-13
### Added
- Add shift + right-click to set a Creative Cell filter from inventory.
- Optimize the Creative Cell. Shouldn't make a huge difference in most cases, but who knows.

### Fixed
- Fixed Creative Cell not notifying the grid the items are not consumed. It's not that big of a deal as it has a ton of items, but could cause problem in some fringe cases.


## [0.5.5] - 2026-03-13
### Added
Add Creative Cell, a Cell that can only be configured in Creative mode, and exposes 4.6 quintillion of each set item.


## [0.5.4] - 2026-03-08
### Fixed
- Fix Configurable Cells' weird behavior with max types (no max showing -1)


## [0.5.3] - 2026-03-07
### Fixed
- (Really) fix the mod crashing on some versions of Cleanroom due to blocks registry not being mapped yet.


## [0.5.2] - 2026-03-06
### Added
- Add NAE2 JEI Cell View feature as a built-in feature. It does not depend on NAE2 because hooking into it requires either:
  - Heavy use of mixins to NAE2.
  - Tagging our cells as IStorageCell<?>, which then overrides our custom handlers and break the cells. Compacting loses compacting power, Hyper-Density loses the extra space, etc.


## [0.5.1] - 2026-03-05
### Added
- Add Oredict card support for Compacting Cells. The cell will convert the items into the partitioned item based on the Oredict entry.

### Fixed
- Fix item filters not being able to be set on page 2+ for Import Interfaces placed before 0.5.0

### Changed
- Remove NBT size for Compacting Cells, as they shouldn't have any issue with large NBT sizes, storing only 1 "real" item.
- Lower the warning threshold for NBT size to 100kB, as 1MB is probably already too late for most cases, and it's better to be safe and warn earlier. That's still only 20 cells away from the 2MB packet size limit, but will see how it goes in practice.


## [0.5.0] - 2026-03-04
### Added
- Add Export Interfaces: same as Import Interfaces but for exposing items/fluids from the ME network to outside piping. They have the same filtering and upgrade capabilities as Import Interfaces, but work in reverse.
- Add clear button to Import/Export interfaces. Clears the current filters (if possible).
- Add Capacity Card support to Import/Export Interfaces. Each capacity card adds a page of 36 additional filter/storage slots (up to 4 cards for 5 pages total = 180 slots). Removing a capacity card returns items from deleted pages to the network and clears those filters.


## [0.4.11] - 2026-03-02
### Added
- Add additional Memory Card keybind to include filters when saving a card.
- Add keybind to quickly add the hovered item to the first free filter slot in the Import Interfaces.
- Add better explanations for how to best use the Import Interface in the buttons tooltip and the controls help widget.
- Add a tooltip for components that are compatible with the Configurable Cell, to make it more clear which ones can be used in it.
- Add NBT size and a warning for cells with large NBT sizes, to make it more clear when a cell might come close to the packet size limit and cause issues with the network.
- Split the Configurable Cell's types limit (config) between the different data types (item, fluid, essentia, gas), to allow more flexibility in configuring it for different use cases.
- Allow configurable cells to have the types limit set in the GUI, to trade-off between max types and max capacity per type without needing to change the config and reload the world.
- Allow disabling the NBT size calculation in config.


## [0.4.10] - 2026-03-01
### Fixed
- Fix gradle not including the API by default.


## [0.4.9] - 2026-02-29
### Changed
- Make MixinBooter optional. The storage cells will only stack to 1 if mixins are not enabled, preventing duplication exploits in ME Chest/Workbench.


## [0.4.8] - 2026-02-28
### Fixed
- Fix a weird crash with some versions of Cleanroom (Material.IRON not existing)


## [0.4.7] - 2026-02-28
### Fixed
- Fix Import Interface's slots not being aligned with filtered slots (slots with no filter are still not exposed)


## [0.4.6] - 2026-02-27
### Added
- Add Essentia and Gas support for Configurable Cells (with textures for all the supported sizes: 1k, 4k, ..., 1g, 2g).
- Add support for Essentia and Gas in /fillCell.
- Add recipes for combining the Configurable Cell and the component directly.
- Add some warnings for custom components in the configurable_components.cfg.

### Changed
- Harmonize Configurable Cells.

### Technical
- Refactor the codebase to share the common logic between the various cell types.


## [0.4.5] - 2026-02-25
### Added
- Add support for downloading and uploading Import Interface settings to memory cards and dismantled blocks.
- Add part variants to both Import Interfaces.


## [0.4.4] - 2026-02-24
### Changed
- Finish updating placeholder textures to (less placeholder) assets. Proper assets will come (probably, maybe, who knows, lol).


## [0.4.3] - 2026-02-23
### Added
- Add stricter checks for upgrades being applied (instead of all custom cards).

### Changed
- Update textures from placeholders to proper (still somewhat placeholder) assets.


## [0.4.2] - 2026-02-22
### Fixed
- Fix empty Configurable Cell not displaying correctly in ME Chest/ME Drive, due to reporting the wrong status.
- Fix Configurable Cell sometimes voiding invalid items if spammed madly on the Component slot.
- Fix Configurable Cell using the wrong calculation for fluids, allowing 1000x more than expected, and reporting 250x the bytes.
- Fix Import Interface's max slot size not working properly with some piping methods. It now always exposes an empty dummy slot at index 0.

### Changed
- Make Compression/Decompression cards cheaper, requiring an Overclocked Processor instead of a Singularity one.


## [0.4.1] - 2026-02-21
### Added
- Add Fluid Import Interface: a filtered interface that imports fluids into the ME network. Accepts fluid containers (buckets, etc.) as filter ghost items and routes matching fluids to the network. Supports overflow and trash-unselected upgrade cards.
- Add chat warnings explaining why a filter cannot be added or removed for both Import Interface and Fluid Import Interface.

### Fixed
- Fix polling rate over-ticking for each polling rate change (until world reload), due to the ticking requests not being properly unregistered on AE2's side.
- Fix Compacting Cell not properly updating the compacting chain when adding/removing a compression/decompression card after partitioning the cell.
- Fix ME Chest and Cell Workbench accepting stacked cells, which causes duplication when the NBT is written to the stack. Mixins are used to set the relevant slots' max stack size to 1, like is the case for ME Drives.
- Fix Configurable Cell using only 1 component for the whole stack, instead of 1 component per cell, resulting in component duplication/voiding.


## [0.4.0] - 2026-02-19
### Added
- Add Configurable ME Storage Cell: a universal cell that accepts an item/fluid ME Storage Component (AE2, NAE2, CrazyAE) to define its capacity and storage type. Features built-in equal distribution, configurable per-type capacity limits via GUI, and component hot-swapping.
- Add player message when trying to disassemble a cell with content, explaining the need to empty it first.

### Fixed
- Fix HD Item and HD Fluid cells having a fixed bytes-per-type overhead that breaks when max types exceeds 128. Overhead is now dynamically computed as 50% of total bytes, supporting both config and Equal Distribution Card limits.


## [0.3.8] - 2026-02-18
### Fixed
- Fix Import Interface not allowing items with the same ID and metadata but different NBT in filters, due NBT collision when checking for duplicates.


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