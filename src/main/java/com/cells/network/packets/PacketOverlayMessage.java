package com.cells.network.packets;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.cells.gui.overlay.MessageType;
import com.cells.gui.overlay.OverlayMessageRenderer;
import com.cells.util.TranslatedTextHelper;


/**
 * Server→Client packet that triggers the overlay message renderer.
 * <p>
 * Sent alongside (or instead of) regular chat messages so the player gets
 * immediate visual feedback via the overlay, in addition to the persistent
 * chat history. The translation key is resolved client-side so it respects
 * the client's locale.
 */
public class PacketOverlayMessage implements IMessage {

    private String translationKey;
    private MessageType type;
    private String[] args;

    public PacketOverlayMessage() {
    }

    /**
     * @param translationKey The localization key
     * @param type           The overlay message type (SUCCESS, ERROR, WARNING)
     * @param args           Optional format arguments (must be pre-localized strings)
     */
    public PacketOverlayMessage(String translationKey, MessageType type, String... args) {
        this.translationKey = translationKey;
        this.type = type;
        this.args = args;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.type = MessageType.values()[buf.readByte()];

        int keyLen = buf.readInt();
        byte[] keyBytes = new byte[keyLen];
        buf.readBytes(keyBytes);
        this.translationKey = new String(keyBytes, StandardCharsets.UTF_8);

        int argCount = buf.readByte();
        this.args = new String[argCount];
        for (int i = 0; i < argCount; i++) {
            int argLen = buf.readInt();
            byte[] argBytes = new byte[argLen];
            buf.readBytes(argBytes);
            this.args[i] = new String(argBytes, StandardCharsets.UTF_8);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeByte(this.type.ordinal());

        byte[] keyBytes = this.translationKey.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(keyBytes.length);
        buf.writeBytes(keyBytes);

        buf.writeByte(this.args.length);
        for (String arg : this.args) {
            byte[] argBytes = arg.getBytes(StandardCharsets.UTF_8);
            buf.writeInt(argBytes.length);
            buf.writeBytes(argBytes);
        }
    }

    /**
     * Client-side handler: resolves translation key and displays via overlay renderer.
     */
    public static class ClientHandler implements IMessageHandler<PacketOverlayMessage, IMessage> {

        @Override
        public IMessage onMessage(PacketOverlayMessage message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                // Resolve args that are themselves translation keys (passed as raw keys
                // by ServerMessageHelper for TextComponentTranslation arguments).
                Object[] translatedArgs = new Object[message.args.length];
                for (int i = 0; i < message.args.length; i++) {
                    String arg = message.args[i];
                    String translated = I18n.format(arg);
                    // If I18n resolved to something different, use the translation;
                    // otherwise keep the original (it was already a plain string)
                    translatedArgs[i] = translated.equals(arg)
                        ? arg
                        : TranslatedTextHelper.normalizeEscapedNewlines(translated);
                }

                String text = TranslatedTextHelper.normalizeEscapedNewlines(
                    I18n.format(message.translationKey, translatedArgs));
                OverlayMessageRenderer.setMessage(text, message.type);
            });

            return null;
        }
    }
}
