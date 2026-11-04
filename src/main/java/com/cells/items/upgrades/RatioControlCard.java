package com.cells.items.upgrades;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.IItemHandler;

import appeng.util.InventoryAdaptor;

import com.cells.Cells;
import com.cells.blocks.interfacebase.IRatioCardStateContainer;
import com.cells.gui.CellsGuiHandler;
import com.cells.items.upgrades.ratio.RatioCardInventory;
import com.cells.items.upgrades.ratio.RatioCardRecipe;
import com.cells.util.CellDisassemblyHelper;


public class RatioControlCard extends AbstractCustomUpgrade {

    @SideOnly(Side.CLIENT)
    private ItemStack tooltipCard;
    @SideOnly(Side.CLIENT)
    private RatioCardInventory tooltipInventory;
    @SideOnly(Side.CLIENT)
    private List<RatioCardRecipe> tooltipRecipes;
    @SideOnly(Side.CLIENT)
    private WeakReference<World> tooltipWorld;

    public RatioControlCard() {
        super("ratio_card");
        setMaxStackSize(1);
    }

    public static ItemStack find(IItemHandler upgrades) {
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            ItemStack card = upgrades.getStackInSlot(slot);
            if (card.getItem() instanceof RatioControlCard) return card;
        }

        return ItemStack.EMPTY;
    }

    @Override
    @Nonnull
    public ActionResult<ItemStack> onItemRightClick(@Nonnull World world, @Nonnull EntityPlayer player,
                                                    @Nonnull EnumHand hand) {
        if (player.isSneaking()) {
            return CellDisassemblyHelper.handleRightClick(world, player, hand,
                held -> disassemble(held, player));
        }

        if (!world.isRemote) {
            player.openGui(Cells.instance, CellsGuiHandler.GUI_RATIO_CARD, world, hand.ordinal(), 0, 0);
        }

        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }

    private static boolean disassemble(ItemStack card, EntityPlayer player) {
        RatioCardInventory inventory = new RatioCardInventory(card);
        List<ItemStack> patterns = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack pattern = inventory.getStackInSlot(slot);
            if (!pattern.isEmpty()) patterns.add(pattern.copy());
        }

        if (!CellDisassemblyHelper.disassembleUpgrade(card, player)) return false;

        InventoryAdaptor target = InventoryAdaptor.getAdaptor(player);
        for (ItemStack pattern : patterns) CellDisassemblyHelper.returnItem(pattern, target, player);

        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(@Nonnull ItemStack stack, @Nullable World world,
                               @Nonnull List<String> tooltip, @Nonnull ITooltipFlag flag) {
        tooltip.add(I18n.format("tooltip.cells.ratio_card.desc"));
        tooltip.add(I18n.format("tooltip.cells.ratio_card.memory"));
        World currentWorld = world == null ? Minecraft.getMinecraft().world : world;
        if (this.tooltipCard == null || this.tooltipWorld == null || this.tooltipWorld.get() != currentWorld
                || !ItemStack.areItemStackTagsEqual(this.tooltipCard, stack)) {
            this.tooltipCard = stack.copy();
            this.tooltipInventory = new RatioCardInventory(stack);
            this.tooltipRecipes = null;
            this.tooltipWorld = new WeakReference<>(currentWorld);
        }

        RatioCardInventory inventory = this.tooltipInventory;
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) count++;
        }

        tooltip.add(I18n.format("tooltip.cells.ratio_card.count", count, inventory.getSlots()));
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        if (!shift && count > 0) tooltip.add(I18n.format("tooltip.cells.ratio_card.shift"));

        int inactive = 0;
        EntityPlayer player = Minecraft.getMinecraft().player;
        Container container = player == null ? null : player.openContainer;
        if (container instanceof IRatioCardStateContainer) {
            IRatioCardStateContainer state = (IRatioCardStateContainer) container;
            if (state.getInstalledRatioCard() == stack) inactive = state.getInactivePatternMask();
        }

        if (inactive != 0) {
            tooltip.add(TextFormatting.YELLOW + I18n.format("tooltip.cells.ratio_card.inactive",
                Integer.bitCount(inactive)));
        }

        if (shift) {
            if (this.tooltipRecipes == null) {
                this.tooltipRecipes = new ArrayList<>();
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    ItemStack pattern = inventory.getStackInSlot(slot);
                    if (!pattern.isEmpty()) {
                        this.tooltipRecipes.add(RatioCardRecipe.read(slot, pattern, currentWorld));
                    }
                }
            }

            for (RatioCardRecipe recipe : this.tooltipRecipes) {
                ItemStack output = recipe.output.isEmpty() ? recipe.pattern : recipe.output;
                String name = output.getDisplayName();
                if (recipe.inputs.isEmpty()) name = I18n.format("tooltip.cells.ratio_card.invalid");
                TextFormatting color = (inactive & (1 << recipe.slot)) != 0
                    ? TextFormatting.YELLOW : TextFormatting.GRAY;
                tooltip.add(color + I18n.format("tooltip.cells.ratio_card.pattern", recipe.slot + 1, name));
            }
        }

        addCompatibilityTooltip(tooltip, "export_interface");
    }
}
