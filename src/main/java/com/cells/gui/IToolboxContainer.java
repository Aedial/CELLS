package com.cells.gui;


/**
 * Marks containers that expose the AE2 Network Tool toolbox panel.
 * <p>
 * GUI classes use this to decide whether they need to render the extra panel
 * art and declare the corresponding JEI/HEI exclusion area.
 */
public interface IToolboxContainer {

    boolean hasToolbox();
}