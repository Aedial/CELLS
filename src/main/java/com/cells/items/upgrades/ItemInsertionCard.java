package com.cells.items.upgrades;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;


/**
 * Insertion Card, an upgrade card for the Subnet Proxy.
 * <p>
 * When installed in a Subnet Proxy's upgrade slot, the front part (Grid A side)
 * registers as an {@code ICellContainer} on Grid A, exposing a write-only handler
 * that forwards item insertions to Grid B's storage. This enables the standard
 * AE2 insertion flow: items inserted into Grid A (by the player, import buses, etc.)
 * can be routed to Grid B's storage based on priority and filter settings.
 * <p>
 * Without this card, the proxy is strictly one-way: Grid A can only extract from
 * Grid B. With this card, Grid A can also push items into Grid B.
 * <p>
 * <b>Compatibility:</b> Only works with the Subnet Proxy.
 */
public class ItemInsertionCard extends AbstractCustomUpgrade {

    public ItemInsertionCard() {
        super("insertion_card");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(I18n.format("tooltip.cells.insertion_card.desc"));
        addCompatibilityTooltip(tooltip, "subnet_proxy");
    }
}
