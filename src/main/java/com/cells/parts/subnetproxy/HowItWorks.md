# Design decisions
To simplify the management of grids, it has been decided to treat each grid as independent and handle the changes explicitly, instead of relying on AE2's enumeration mechanisms.
As such, *if there is no passthrough on the grid*, we *should* control every change in a deterministic manner (via lifecycle scheduling). AE2 may eat some updates internally (in very specific cases), but there is nothing we can do about it, beside waiting for a grid reset.

## AE2 Concepts
- The Tasks Queue : AE2 maintains a queue of deltas to be processed, representing physical changes in providers' content that has not yet been applied to the cache. It being non-empty indicates that the state between World and the AE2 cache is not fully synchronized.
- Grid Reset : Some events or device may trigger a complete reset of the grid, causing AE2 to discard its cached state and reinitialize the grid from physical providers.
- AE2 Cache : AE2 maintains an internal cache of the grid's state, which may not always be in sync with the physical providers. This cache is used to keep many things cheap to access, such as the available items in the grid, but it may get out of sync if the updates are not fully processed (or handled improperly).
- Subnet Node : Owner of all Subnet Proxies within the grid, responsible for managing their state and interactions with the AE2 cache.

## Caching Strategy
We aggressively cache everything to ensure efficient management :
- Local providers (weak references) : updated on local pathing/connection/power/channel changes
- Edges (merged filters) : parallel Subnet Proxies going in the same direction are combined into a single edge, combining their filters and resource channels. Updated whenever the underlying proxies, their filters, or their channels change.

## Lifecycle Scheduling
After a grid reset and enough time has passed without further changes, a Subnet Node reset is scheduled at tick END and executed at next tick START (after verifying there is no remaining pending changes in the grid), to make sure that all pending changes and updates are processed by AE2 before we capture the local baseline. This is done to accomodate AE2's internal processing and ensure (to the best of our ability) that we work with a consistent view of the grid. The delay is there to ensure we are not in a chronic reset state (from some misbehaving component continuously triggering grid resets).

Pending topology changes (proxies, connections, providers) are then processed before scheduling the Subnet Node reset, to ensure the grid is up-to-date before capturing the local baseline and advertising it to neighboring grids.

An unstable grid is qualified as having experienced one of the following in the last X ticks (see config) :
- Grid changes (addition or removal of nodes, providers, or connections), but not inventory changes
- Grid reset

If a grid stays unstable for a prolonged period, it may never refresh its local baseline, leading to potential inconsistencies and missed updates. This is a known limitation of this debounce-based approach.

The metadata of the previous tick is similarly cleaned up at next tick START, ensuring that we do not retain stale information about the next tick.

## Insertion/Extraction ?
Insertion and extraction operations are handled out of the Proxies mesh, considering the Proxies as another black-box inventory. Of course, the rules of Subnet Proxies still apply :
- Extraction : only within the 2-hop neighborhood of the grid. This ensures that items are only pulled from grids we have the deltas of, avoiding inconsistencies due to missing updates from more distant grids.
- Insertion : only with Insertion Card and no nested insertion. It is meant purely for inserting content back into a subnet we extracted from. This allows more hands-off storage on subnets.

# In Practice

## How grid initialization/reset works

Once the grid is stable, we :
1. Capture the local baseline of the local grid's inventory. This is currently done by iterating over all local providers (excluding passthroughs) and collecting their available contents.
It is planned to retrieve the cache directly when there is no passthrough, bypassing the need to query each local provider individually (which is much more efficient and reliable), as passthroughs are the only thing that may link 2 grids together, beside us.
2. Advertise the baseline to all other grids within the 2-hop neighborhood. This tells neighboring grids about the current state of our inventory, updating their remote contribution and AE2 caches accordingly. If our local inventory didn't change, there is close to no impact on the neighbors, as they see no difference in the contributions from our grid.
3. Import any remote contributions from neighboring grids, updating the AE2 caches with the received items.
4. Enable enumeration (for things like terminals and CPUs). Enumeration is the deduplicated view of all remote contributions, as each proxy advertising its own baseline would otherwise lead to duplicate entries for parallel edges (diamond-shaped connections).

## What is on the hot-path

### Deltas forwarding
- Filters : deltas are propagated to neighbors whose filters match the changed contents.

### Grid reset
- Caches : Local providers and edges are checked for consistency and updated if necessary, ensuring that the cached state stays accurate.
- Baseline refresh : The local baseline is fetched, which may involve asking ALL local providers for their current inventory state.
- Remote contributions : Any remote contributions from neighboring grids are imported, updating the AE2 caches with the received items.
- Local contribution : The local contribution is sent to all neighboring grids.