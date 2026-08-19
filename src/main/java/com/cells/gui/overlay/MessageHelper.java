package com.cells.gui.overlay;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import com.cells.util.TranslatedTextHelper;


/**
 * Utility for sending messages to both the chat and the GUI overlay.
 * <p>
 * Messages are:
 * 1. Sent to chat (for history and when GUI is closed)
 * 2. Displayed in the overlay (for immediate visual feedback)
 * <p>
 * This ensures players get both persistent feedback (chat) and
 * immediate visual feedback (overlay) with appropriate colors.
 */
public final class MessageHelper {

    private MessageHelper() {}

    /**
     * Sends a success message (green).
     *
     * @param translationKey The localization key
     * @param args           Optional format arguments
     */
    public static void success(String translationKey, Object... args) {
        send(translationKey, MessageType.SUCCESS, TextFormatting.GREEN, args);
    }

    /**
     * Sends an error message (red).
     *
     * @param translationKey The localization key
     * @param args           Optional format arguments
     */
    public static void error(String translationKey, Object... args) {
        send(translationKey, MessageType.ERROR, TextFormatting.RED, args);
    }

    /**
     * Sends a warning message (yellow).
     *
     * @param translationKey The localization key
     * @param args           Optional format arguments
     */
    public static void warning(String translationKey, Object... args) {
        send(translationKey, MessageType.WARNING, TextFormatting.YELLOW, args);
    }

    /**
     * Sends a raw string message (not localized) as an error.
     *
     * @param message The message text
     */
    public static void errorRaw(String message) {
        sendRaw(message, MessageType.ERROR, TextFormatting.RED);
    }

    /**
     * Sends a raw string message (not localized) as a success.
     *
     * @param message The message text
     */
    public static void successRaw(String message) {
        sendRaw(message, MessageType.SUCCESS, TextFormatting.GREEN);
    }

    /**
     * Sends a raw string message (not localized) as a warning.
     *
     * @param message The message text
     */
    public static void warningRaw(String message) {
        sendRaw(message, MessageType.WARNING, TextFormatting.YELLOW);
    }

    /**
     * Sends a message using an existing ITextComponent (for compatibility).
     * The component is sent to chat, and its unformatted text is sent to overlay.
     *
     * @param component The text component to send
     * @param type      The message type for overlay coloring
     */
    public static void sendComponent(ITextComponent component, MessageType type) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

        // Send to chat
        player.sendMessage(component);

        // Send to overlay using unformatted text
        OverlayMessageRenderer.setMessage(component.getUnformattedText(), type);
    }

    private static void send(String translationKey, MessageType type, TextFormatting chatColor, Object... args) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

        TextComponentTranslation translatedMessage = new TextComponentTranslation(translationKey, args);
        String resolvedText = TranslatedTextHelper.resolveComponentText(translatedMessage);

        TextComponentString chatMessage = new TextComponentString(resolvedText);
        chatMessage.setStyle(new Style().setColor(chatColor));
        player.sendMessage(chatMessage);

        OverlayMessageRenderer.setMessage(resolvedText, type);
    }

    private static void sendRaw(String message, MessageType type, TextFormatting chatColor) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (player == null) return;

        // Create colored chat message
        TextComponentString chatMessage = new TextComponentString(message);
        chatMessage.setStyle(new Style().setColor(chatColor));
        player.sendMessage(chatMessage);

        // Send to overlay
        OverlayMessageRenderer.setMessage(message, type);
    }
}
