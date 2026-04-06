/**
 * Internal manager classes extracted from
 * {@link com.cells.blocks.interfacebase.AbstractResourceInterfaceLogic} to reduce its size.
 * <p>
 * These managers are <strong>internal composition</strong>: the logic class owns them
 * and delegates work, but subclasses still override logic methods directly.
 * The managers operate on the logic's protected arrays/maps via shared references.
 * <p>
 * <ul>
 *   <li>{@link com.cells.blocks.interfacebase.managers.InterfaceUpgradeManager}: upgrade cards, capacity, page navigation</li>
 *   <li>{@link com.cells.blocks.interfacebase.managers.InterfaceInventoryManager}: filter/storage operations, mappings, network I/O, serialization</li>
 *   <li>{@link com.cells.blocks.interfacebase.managers.InterfaceTickScheduler}: polling rate, sleep/wake, dual-timer dispatch</li>
 *   <li>{@link com.cells.blocks.interfacebase.managers.InterfaceAdjacentHandler}: capability cache, auto-pull/push</li>
 * </ul>
 */
package com.cells.blocks.interfacebase.managers;
