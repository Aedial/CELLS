package com.cells.gui.overlay;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import com.cells.network.CellsNetworkHandler;
import com.cells.network.packets.PacketOverlayMessage;
import com.cells.util.TranslatedTextHelper;


/**
 * Server-side utility for sending messages to both chat and the client-side overlay.
 * <p>
 * Unlike {@link MessageHelper} which is client-side only, this class sends a chat message
 * via the server and a {@link PacketOverlayMessage} to trigger the overlay on the client.
 * <p>
 * Use this for all server-side feedback messages (containers, packet handlers, etc.)
 * where the player should see both a chat log entry and an immediate visual overlay.
 * FIXME: Consider resolving the chat message on the client side instead of server side,
 *        so that the overlay and chat are consistent in the player's locale.
 */
public final class ServerMessageHelper {

    private ServerMessageHelper() {}

    /**
     * Sends a success message (green) to the player.
     *
     * @param player         The server-side player
     * @param translationKey The localization key
     * @param args           Optional format arguments
     */
    public static void success(EntityPlayerMP player, String translationKey, Object... args) {
        send(player, translationKey, MessageType.SUCCESS, TextFormatting.GREEN, args);
    }

    /**
     * Sends an error message (red) to the player.
     *
     * @param player         The server-side player
     * @param translationKey The localization key
     * @param args           Optional format arguments
     */
    public static void error(EntityPlayerMP player, String translationKey, Object... args) {
        send(player, translationKey, MessageType.ERROR, TextFormatting.RED, args);
    }

    /**
     * Sends a warning message (yellow) to the player.
     *
     * @param player         The server-side player
     * @param translationKey The localization key
     * @param args           Optional format arguments
     */
    public static void warning(EntityPlayerMP player, String translationKey, Object... args) {
        send(player, translationKey, MessageType.WARNING, TextFormatting.YELLOW, args);
    }

    private static void send(EntityPlayerMP player, String translationKey, MessageType type, TextFormatting chatColor, Object... args) {
        TextComponentTranslation translatedMessage = new TextComponentTranslation(translationKey, args);
        String resolvedText = TranslatedTextHelper.resolveComponentText(translatedMessage);

        TextComponentString chatMessage = new TextComponentString(resolvedText);
        chatMessage.setStyle(new Style().setColor(chatColor));
        player.sendMessage(chatMessage);

        // Send overlay packet so the client also displays it visually.
        // Convert args to strings for transmission; TextComponentTranslation args
        // are resolved server-side here since the overlay packet uses I18n on client.
        String[] stringArgs = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof TextComponentTranslation) {
                // Pass the translation key so client resolves in its own locale
                stringArgs[i] = ((TextComponentTranslation) args[i]).getKey();
            } else {
                stringArgs[i] = String.valueOf(args[i]);
            }
        }

        CellsNetworkHandler.INSTANCE.sendTo(new PacketOverlayMessage(translationKey, type, stringArgs), player);
    }
}
