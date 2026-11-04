package com.cells.items.upgrades.ratio;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.AEBaseGui;

import com.cells.integration.nae2.pmt.PMTManager;
import com.cells.integration.nae2.pmt.PMTRenderer;
import com.cells.integration.nae2.pmt.PMTSlot;


public class GuiRatioCard extends AEBaseGui {

    private static final ResourceLocation CHEST = new ResourceLocation("textures/gui/container/generic_54.png");

    private static final int CHEST_TOP_HEIGHT = 71;
    private static final int INVENTORY_TEXTURE_Y = 126;
    private static final int INVENTORY_HEIGHT = 96;
    private static final int LABEL_X = 8;
    private static final int TITLE_Y = 6;
    private static final int INVENTORY_LABEL_Y = 74;
    private static final int TEXT_COLOR = 0x404040;
    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = CHEST_TOP_HEIGHT + INVENTORY_HEIGHT + 1;

    private final ContainerRatioCard container;

    public GuiRatioCard(InventoryPlayer player, EnumHand hand) {
        super(new ContainerRatioCard(player, hand));
        this.container = (ContainerRatioCard) this.inventorySlots;
        this.xSize = GUI_WIDTH;
        this.ySize = GUI_HEIGHT;
    }

    @Override
    public void drawBG(int x, int y, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(CHEST);
        drawTexturedModalRect(x, y, 0, 0, this.xSize, CHEST_TOP_HEIGHT);
        drawTexturedModalRect(x, y + CHEST_TOP_HEIGHT, 0, INVENTORY_TEXTURE_Y, this.xSize, INVENTORY_HEIGHT);

        PMTManager pmt = this.container.getPMT();
        PMTRenderer.drawBackground(this, pmt, x, y, ContainerRatioCard.PMT_X, ContainerRatioCard.PMT_Y);
        PMTRenderer.drawSlotBackgrounds(this, pmt, x, y);
    }

    @Override
    public void drawFG(int x, int y, int mouseX, int mouseY) {
        this.fontRenderer.drawString(I18n.format("item.cells.ratio_card.name"), LABEL_X, TITLE_Y, TEXT_COLOR);
        this.fontRenderer.drawString(I18n.format("container.inventory"), LABEL_X, INVENTORY_LABEL_Y, TEXT_COLOR);
    }

    @Override
    protected void renderHoveredToolTip(int mouseX, int mouseY) {
        Slot slot = getSlotUnderMouse();
        if (slot instanceof PMTSlot) {
            PMTRenderer.drawSlotTooltip(this, (PMTSlot) slot, mouseX, mouseY, this.width, this.height);
            return;
        }

        super.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected boolean hasClickedOutside(int mouseX, int mouseY, int left, int top) {
        if (this.container.getPMT() != null && PMTRenderer.isMouseInsidePanel(
                mouseX, mouseY, left, top, ContainerRatioCard.PMT_X, ContainerRatioCard.PMT_Y)) return false;

        return super.hasClickedOutside(mouseX, mouseY, left, top);
    }

    @Override
    public List<Rectangle> getJEIExclusionArea() {
        List<Rectangle> areas = new ArrayList<>(super.getJEIExclusionArea());
        if (this.container.getPMT() != null) {
            areas.add(new Rectangle(this.guiLeft + ContainerRatioCard.PMT_X,
                this.guiTop + ContainerRatioCard.PMT_Y, PMTRenderer.PMT_WIDTH, PMTRenderer.PMT_HEIGHT));
        }

        return areas;
    }
}
