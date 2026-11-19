package com.cells.commands;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;

import com.cells.profiling.CellsProfiler;
import com.cells.profiling.ProfileSession;


/**
 * Operator command for bounded CELLS profiling captures across loaded dimensions.
 */
public class ProfileCommand extends CommandBase {

    private static final int DEFAULT_SECONDS = 20;
    private static final int MAX_SECONDS = 3600;

    @Override
    @Nonnull
    public String getName() {
        return "cells";
    }

    @Override
    @Nonnull
    public String getUsage(@Nonnull ICommandSender sender) {
        return "commands.cells.profile.usage";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                        @Nonnull String[] args) throws CommandException {
        if (args.length < 2 || args.length > 3 || !"profile".equals(args[0])) {
            throw new WrongUsageException(this.getUsage(sender));
        }

        if ("stop".equals(args[1])) {
            if (args.length != 2) throw new WrongUsageException(this.getUsage(sender));
            if (!CellsProfiler.requestStop()) throw new CommandException("commands.cells.profile.inactive");

            sender.sendMessage(new TextComponentTranslation("commands.cells.profile.stopping"));
            return;
        }

        ProfileSession.Mode mode;
        try {
            mode = ProfileSession.Mode.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new WrongUsageException(this.getUsage(sender));
        }

        int seconds = args.length == 3 ? parseInt(args[2], 1, MAX_SECONDS) : DEFAULT_SECONDS;
        UUID playerId = sender instanceof EntityPlayerMP ? ((EntityPlayerMP) sender).getUniqueID() : null;
        if (!CellsProfiler.start(server, mode, seconds, playerId)) {
            throw new CommandException("commands.cells.profile.running");
        }

        sender.sendMessage(new TextComponentTranslation("commands.cells.profile.started",
            mode.name().toLowerCase(Locale.ROOT), seconds));
    }

    @Override
    @Nonnull
    public List<String> getTabCompletions(@Nonnull MinecraftServer server, @Nonnull ICommandSender sender,
                                         @Nonnull String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "profile");
        if (!"profile".equals(args[0])) return Collections.emptyList();

        if (args.length == 2) {
            return getListOfStringsMatchingLastWord(args, "adaptive", "devices", "sources", "stop");
        }

        if (args.length == 3 && !"stop".equals(args[1])) {
            return getListOfStringsMatchingLastWord(args, "5", "10", "20", "60");
        }

        return Collections.emptyList();
    }
}
