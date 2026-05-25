package com.cells.network.sync;

import java.util.Map;

import com.cells.blocks.compactingpatternexposer.TileCompactingPatternExposer;


/**
 * Container hook for compacting pattern preview synchronization.
 */
public interface ICompactingPatternPreviewSyncContainer {

    void receivePatternPreviews(Map<Integer, TileCompactingPatternExposer.PatternPreview> upwardPreviews,
                                Map<Integer, TileCompactingPatternExposer.PatternPreview> downwardPreviews);
}
