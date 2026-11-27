package com.cells.parts.subnetproxy;

import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.entity.player.EntityPlayer;

import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;


/**
 * Action source for a Subnet Proxy delivery, with the original caller's player, machine, and context.
 */
public final class SubnetProxyActionSource implements IActionSource {

    private final IActionSource source;

    public SubnetProxyActionSource(@Nullable IActionSource source) {
        this.source = source;
    }

    @Override
    @Nonnull
    public Optional<EntityPlayer> player() {
        return this.source == null ? Optional.empty() : this.source.player();
    }

    @Override
    @Nonnull
    public Optional<IActionHost> machine() {
        return this.source == null ? Optional.empty() : this.source.machine();
    }

    @Override
    @Nonnull
    public <T> Optional<T> context(@Nonnull Class<T> key) {
        return this.source == null ? Optional.empty() : this.source.context(key);
    }
}
