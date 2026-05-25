package com.cells.network.sync;

import java.util.Map;


/**
 * Container hook for compacting pattern multiplier synchronization.
 */
public interface ICompactingPatternMultiplierSyncContainer {

    void receivePatternMultipliers(Map<Integer, Long> multipliers);
}