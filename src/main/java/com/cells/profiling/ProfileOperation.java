package com.cells.profiling;


public enum ProfileOperation {

    INSERT("insert", false),
    SIMULATE_INSERT("simulate insert", true),
    EXTRACT("extract", false),
    SIMULATE_EXTRACT("simulate extract", true),
    ENUMERATE("enumerate", false),
    QUERY("query", false),
    SAVE("deferred save", false),
    NOTIFY("deferred notification", false),
    EMC_SYNC("buffered EMC sync", false),
    TICK("other interface work", false),
    NETWORK_IO("network I/O", false),
    ADJACENT_IO("adjacent I/O", false);

    public final String label;
    public final boolean simulation;

    private ProfileOperation(String label, boolean simulation) {
        this.label = label;
        this.simulation = simulation;
    }
}
