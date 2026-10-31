package com.cells.blocks.interfacebase;


public interface IRedirectStatusContainer {

    public static final int NONE = 0;
    public static final int UNBOUND = 1;
    public static final int NOT_ADJACENT = 2;
    public static final int NO_TARGET = 3;
    public static final int READY = 4;

    public int getRedirectStatus();
}
