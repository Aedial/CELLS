package com.cells.cells.configurable;

import java.io.IOException;

import org.lwjgl.input.Keyboard;

import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

import appeng.client.gui.AEBaseGui;
import appeng.util.ReadableNumberConverter;

import com.cells.Tags;
import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketSetConfigurableCellCapacity;


/**
 * GUI screen for the Configurable Storage Cell.
 * <p>
 * Layout (176x183):
 * - Component slot: 16x16 at (6, 6)
 * - Text field title: at (6, 29)
 * - Text field: 164x10 at (6, 39)
 * - Effective capacity title: at (6, 55)
 * - Effective capacity display: at (6, 65)
 * - Player inventory label: at (8, 91)
 * - Player inventory slots: standard layout starting at (8, 103)
 */
public class GuiConfigurableCell extends AEBaseGui {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Tags.MODID, "textures/guis/configurable_cell.png");

    private GuiTextField capacityField;
    private final ContainerConfigurableCell container;

    public GuiConfigurableCell(InventoryPlayer playerInv, EnumHand hand) {
        super(new ContainerConfigurableCell(playerInv, hand));
        this.container = (ContainerConfigurableCell) this.inventorySlots;
        this.xSize = 176;
        this.ySize = 183;
    }

    @Override
    public void initGui() {
        super.initGui();

        // Text field for per-type capacity (78 wide, 10 tall, at 6,39 in GUI)
        this.capacityField = new GuiTextField(0, this.fontRenderer,
            this.guiLeft + 7, this.guiTop + 40, 78, this.fontRenderer.FONT_HEIGHT);
        this.capacityField.setEnableBackgroundDrawing(false);
        this.capacityField.setMaxStringLength(11);  // 8 * 2^31
        this.capacityField.setTextColor(0xFFFFFF);
        this.capacityField.setVisible(true);
        this.capacityField.setFocused(true);

        this.container.setTextField(this.capacityField);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        boolean isFluid = container.componentIsFluid == 1;
        boolean hasComponent = container.componentPresent == 1;

        // TODO: draw component name if present otherwise "insert component here"

        // Title for the text field
        String suffix = "none";
        if (hasComponent) suffix = isFluid ? "fluid" : "item";

        String fieldTitleKey = "gui.cells.configurable_cell.capacity_title." + suffix;
        this.fontRenderer.drawString(I18n.format(fieldTitleKey), 6, 29, 0x404040);

        this.fontRenderer.drawString("/", 85, 40, 0x000000);

        // Max capacity per type (effective capacity) display
        if (hasComponent) {
            String maxStr = ReadableNumberConverter.INSTANCE.toWideReadableForm(container.physicalMaxPerType);
            this.fontRenderer.drawStringWithShadow(maxStr, 93, 40, 0x39E539);
        } else {
            String noComponent = I18n.format("gui.cells.configurable_cell.no_component");
            this.fontRenderer.drawStringWithShadow(noComponent, 93, 40, 0xCC2020);
        }

        // "Inventory" label above player inventory slots
        this.fontRenderer.drawString(I18n.format("container.inventory"), 8, 91, 0x404040);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        this.capacityField.drawTextBox();
    }

    @Override
    protected void keyTyped(char character, int key) throws IOException {
        if (!this.checkHotbarKeys(key)) {
            if ((key == Keyboard.KEY_DELETE || key == Keyboard.KEY_RIGHT
                || key == Keyboard.KEY_LEFT || key == Keyboard.KEY_BACK
                || Character.isDigit(character)) && this.capacityField.textboxKeyTyped(character, key)) {

                String out = this.capacityField.getText();

                // Remove leading zeros
                boolean fixed = false;
                while (out.startsWith("0") && out.length() > 1) {
                    out = out.substring(1);
                    fixed = true;
                }

                if (fixed) this.capacityField.setText(out);

                if (out.isEmpty()) {
                    // Empty field = no limit (physical max)
                    CellsNetworkHandler.INSTANCE.sendToServer(new PacketSetConfigurableCellCapacity(Long.MAX_VALUE));
                } else {
                    try {
                        long parsed = Long.parseLong(out);
                        if (parsed < 0) parsed = 0;

                        CellsNetworkHandler.INSTANCE.sendToServer(new PacketSetConfigurableCellCapacity(parsed));
                    } catch (NumberFormatException e) {
                        // Ignore invalid input
                    }
                }
            } else {
                super.keyTyped(character, key);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.capacityField.mouseClicked(mouseX, mouseY, mouseButton);
    }
}
