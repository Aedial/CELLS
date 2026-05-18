package com.cells.gui.subnetproxy;

import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;

import javax.annotation.Nonnull;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Optional;

import appeng.client.gui.widgets.ITooltip;

import com.cells.Tags;
import com.cells.network.sync.ResourceType;


/**
 * Side button for toggling a single Subnet Proxy channel on/off.
 * <p>
 * Renders using {@code button_background_config.png}, a 64x64 atlas containing
 * a 2x2 grid of 18x18 button sprites:
 * <ul>
 *   <li>(0, 0) = green (enabled), not hovered</li>
 *   <li>(18, 0) = red (disabled), not hovered</li>
 *   <li>(0, 18) = green (enabled), hovered</li>
 *   <li>(18, 18) = red (disabled), hovered</li>
 * </ul>
 * The current enabled state is supplied by a {@link BooleanSupplier} so the
 * button reflects server-synced state every frame without manual refresh.
 * <p>
 * The foreground icon is a small ItemStack indicating the channel
 * (chest=item, water bucket=fluid, gas tank=gas, jar=essentia) matching
 * the icons used by the filter-mode tab button.
 */
public class GuiChannelToggleButton extends GuiButton implements ITooltip {

    public static final int SIZE = 18;

    private static final ResourceLocation TEXTURE =
        new ResourceLocation(Tags.MODID, "textures/guis/button_background_config.png");

    /** Atlas is 64x64 even though only 36x36 is used; matches the source PNG dimensions. */
    private static final int TEX_SHEET = 64;

    private final ResourceType type;
    private final BooleanSupplier enabledSupplier;

    public GuiChannelToggleButton(int buttonId, int x, int y,
                                  ResourceType type, BooleanSupplier enabledSupplier) {
        super(buttonId, x, y, SIZE, SIZE, "");
        this.type = type;
        this.enabledSupplier = enabledSupplier;
    }

    public ResourceType getType() {
        return this.type;
    }

    @Override
    public void drawButton(@Nonnull Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.hovered = mouseX >= this.x && mouseY >= this.y
            && mouseX < this.x + this.width && mouseY < this.y + this.height;

        boolean enabled = this.enabledSupplier.getAsBoolean();

        // Atlas U: column 0 = green (enabled), column 1 = red (disabled)
        // Atlas V: row 0 = idle, row 1 = hovered
        int u = enabled ? 0 : SIZE;
        int v = this.hovered ? SIZE : 0;

        mc.getTextureManager().bindTexture(TEXTURE);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        drawModalRectWithCustomSizedTexture(this.x, this.y, u, v, SIZE, SIZE, TEX_SHEET, TEX_SHEET);

        // Foreground icon (channel-specific item stack rendered at native size)
        ItemStack icon = getIconForType(this.type);
        if (!icon.isEmpty()) renderIcon(mc, icon);
    }

    private void renderIcon(Minecraft mc, ItemStack stack) {
        RenderItem itemRender = mc.getRenderItem();

        RenderHelper.enableGUIStandardItemLighting();
        // 1px inset on both axes so the 16x16 item sits centered in the 18x18 button
        itemRender.renderItemAndEffectIntoGUI(stack, this.x + 1, this.y + 1);
        RenderHelper.disableStandardItemLighting();
    }

    /**
     * Pick a representative icon for each channel. Mirrors the tab-button icons
     * used by the filter-mode button (chest/bucket/tank/jar) so the user gets
     * consistent visual cues across all subnet-proxy controls.
     */
    private static ItemStack getIconForType(ResourceType type) {
        switch (type) {
            case ITEM:
                return new ItemStack(Blocks.CHEST);
            case FLUID:
                return new ItemStack(Items.WATER_BUCKET);
            case GAS:
                if (Loader.isModLoaded("mekanism")) return getGasIcon();
                return new ItemStack(Items.GLASS_BOTTLE);
            case ESSENTIA:
                if (Loader.isModLoaded("thaumcraft")) return getEssentiaIcon();
                return new ItemStack(Items.GLASS_BOTTLE);
            default:
                return ItemStack.EMPTY;
        }
    }

    @Optional.Method(modid = "mekanism")
    private static ItemStack getGasIcon() {
        return new ItemStack(mekanism.common.MekanismBlocks.GasTank);
    }

    @Optional.Method(modid = "thaumcraft")
    private static ItemStack getEssentiaIcon() {
        Block jar = thaumcraft.api.blocks.BlocksTC.jarNormal;
        if (jar != null) return new ItemStack(jar);
        return new ItemStack(Items.GLASS_BOTTLE);
    }

    // ========================= ITooltip =========================

    @Override
    public String getMessage() {
        boolean enabled = this.enabledSupplier.getAsBoolean();
        String typeName = I18n.format("cells.type." + this.type.name().toLowerCase());
        String stateKey = enabled
            ? "gui.cells.subnet_proxy.channel_toggle.enabled"
            : "gui.cells.subnet_proxy.channel_toggle.disabled";
        return I18n.format(stateKey, typeName) + "\n\n"
            + I18n.format("gui.cells.subnet_proxy.channel_toggle.description");
    }

    @Override
    public int xPos() {
        return this.x;
    }

    @Override
    public int yPos() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return SIZE;
    }

    @Override
    public int getHeight() {
        return SIZE;
    }

    @Override
    public boolean isVisible() {
        return this.visible;
    }

    /** Convenience wrapper since AE2's ITooltip expects a List<String>. */
    public List<String> getTooltip() {
        return Arrays.asList(getMessage().split("\n"));
    }
}
