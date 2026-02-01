# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog and this project adheres to Semantic Versioning.

- Keep a Changelog: https://keepachangelog.com/en/1.1.0/
- Semantic Versioning: https://semver.org/spec/v2.0.0.html


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