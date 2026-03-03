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
import com.cells.network.packets.PacketSetConfigurableCellMaxTypes;


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
    private GuiTextField typesField;
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

        // Text field for per-type capacity (78 wide, 10 tall, at 7,40 in GUI)
        this.capacityField = new GuiTextField(0, this.fontRenderer,
            this.guiLeft + 7, this.guiTop + 40, 78, this.fontRenderer.FONT_HEIGHT);
        this.capacityField.setEnableBackgroundDrawing(false);
        this.capacityField.setMaxStringLength(11);  // 8 * 2^31
        this.capacityField.setTextColor(0xFFFFFF);
        this.capacityField.setVisible(true);
        this.capacityField.setFocused(true);

        // Text field for types limit (78 wide at 7,66 in GUI)
        this.typesField = new GuiTextField(1, this.fontRenderer,
            this.guiLeft + 7, this.guiTop + 66, 78, this.fontRenderer.FONT_HEIGHT);
        this.typesField.setEnableBackgroundDrawing(false);
        this.typesField.setMaxStringLength(11);
        this.typesField.setTextColor(0xFFFFFF);
        this.typesField.setVisible(true);
        this.typesField.setFocused(false);

        this.container.setTextField(this.capacityField);
        this.container.setTypesField(this.typesField);
    }

    @Override
    public void drawFG(int offsetX, int offsetY, int mouseX, int mouseY) {
        boolean hasComponent = container.componentPresent == 1;

        // TODO: draw component name if present otherwise "insert component here"
        // Determine localization suffix based on channel type
        String suffix = "none";
        if (hasComponent && container.componentChannelTypeOrdinal >= 0) {
            ChannelType channelType = ChannelType.values()[container.componentChannelTypeOrdinal];
            suffix = channelType.getLocalizationSuffix();
        }

        // Title for the size limit field
        String fieldTitleKey = "gui.cells.configurable_cell.capacity_title." + suffix;
        this.fontRenderer.drawString(I18n.format(fieldTitleKey), 6, 29, 0x404040);

        // Title for the types limit field
        this.fontRenderer.drawString(I18n.format("gui.cells.configurable_cell.types_title"), 5, 55, 0x404040);

        this.fontRenderer.drawString("/", 85, 40, 0x000000);
        this.fontRenderer.drawString("/", 85, 65, 0x000000);

        if (hasComponent) {
            // Max capacity per type (effective capacity) display
            String maxStr = ReadableNumberConverter.INSTANCE.toWideReadableForm(container.physicalMaxPerType);
            this.fontRenderer.drawStringWithShadow(maxStr, 93, 40, 0x39E539);

            // Max types display
            this.fontRenderer.drawStringWithShadow(String.valueOf(container.maxTypesConfig), 93, 65, 0x39E539);
        } else {
            String noComponent = I18n.format("gui.cells.configurable_cell.no_component");
            this.fontRenderer.drawStringWithShadow(noComponent, 93, 40, 0xCC2020);
            this.fontRenderer.drawStringWithShadow(noComponent, 93, 65, 0xCC2020);
        }

        // "Inventory" label above player inventory slots
        this.fontRenderer.drawString(I18n.format("container.inventory"), 8, 91, 0x404040);
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);

        this.capacityField.drawTextBox();
        this.typesField.drawTextBox();
    }

    @Override
    protected void keyTyped(char character, int key) throws IOException {
        if (this.checkHotbarKeys(key)) return;

        boolean isValidKey = key == Keyboard.KEY_DELETE || key == Keyboard.KEY_RIGHT
            || key == Keyboard.KEY_LEFT || key == Keyboard.KEY_BACK
            || Character.isDigit(character);

        // Handle capacity field input - validate on every change
        if (isValidKey && this.capacityField.isFocused() && this.capacityField.textboxKeyTyped(character, key)) {
            handleCapacityFieldChange();
            return;
        }

        // Handle types field input - allow free typing, only validate on Enter
        if (this.typesField.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                // Enter pressed - validate and send
                commitTypesField();
                this.typesField.setFocused(false);
                return;
            }

            if (isValidKey && this.typesField.textboxKeyTyped(character, key)) {
                // Just update the text, don't send packet yet
                stripLeadingZeros(this.typesField);
                return;
            }
        }

        super.keyTyped(character, key);
    }

    /**
     * Strip leading zeros from a text field.
     */
    private void stripLeadingZeros(GuiTextField field) {
        String out = field.getText();
        boolean fixed = false;

        while (out.startsWith("0")) {
            out = out.substring(1);
            fixed = true;
        }

        if (fixed) field.setText(out);
    }

    private void handleCapacityFieldChange() {
        String out = this.capacityField.getText();

        // Remove leading zeros
        boolean fixed = false;
        while (out.startsWith("0")) {
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
    }

    /**
     * Commit the types field value to the server.
     * Called when Enter is pressed, focus is lost, or GUI is closed.
     */
    private void commitTypesField() {
        String out = this.typesField.getText();

        if (out.isEmpty()) {
            // Empty field = no limit (config max)
            CellsNetworkHandler.INSTANCE.sendToServer(new PacketSetConfigurableCellMaxTypes(Integer.MAX_VALUE));
        } else {
            try {
                int parsed = Integer.parseInt(out);
                if (parsed < 1) parsed = 1;

                CellsNetworkHandler.INSTANCE.sendToServer(new PacketSetConfigurableCellMaxTypes(parsed));
            } catch (NumberFormatException e) {
                // Ignore invalid input - server will reject
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        // If types field was focused and is about to lose focus, commit first
        boolean typesWasFocused = this.typesField.isFocused();

        super.mouseClicked(mouseX, mouseY, mouseButton);
        this.capacityField.mouseClicked(mouseX, mouseY, mouseButton);
        this.typesField.mouseClicked(mouseX, mouseY, mouseButton);

        // If types field lost focus, commit the value
        if (typesWasFocused && !this.typesField.isFocused()) commitTypesField();
    }

    @Override
    public void onGuiClosed() {
        // Commit types field value when GUI is closed
        commitTypesField();
        super.onGuiClosed();
    }
}
