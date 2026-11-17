package com.cells.mixin.thaumicenergistics;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import thaumcraft.api.aspects.IAspectContainer;

import thaumicenergistics.api.storage.IAEEssentiaStack;
import thaumicenergistics.integration.appeng.grid.EssentiaContainerAdapter;

import com.cells.integration.thaumicenergistics.EssentiaTransferScope;


/**
 * Storage Bus simulation scope for CELLS essentia containers.
 * The bus typically SIMULATEs transfers before actually performing them
 * (seriously...)
 * This detects when a transfer is being simulated versus actually performed,
 * allowing CELLS to properly manage simulation, considering interfaces are 1-way.
 */
@Mixin(value = EssentiaContainerAdapter.class, remap = false)
public class MixinEssentiaContainerAdapter {

    @Shadow
    private IAspectContainer container;

    @Unique
    private boolean cells$enteringTransfer;

    @Inject(method = "injectItems(Lthaumicenergistics/api/storage/IAEEssentiaStack;"
        + "Lappeng/api/config/Actionable;Lappeng/api/networking/security/IActionSource;)"
        + "Lthaumicenergistics/api/storage/IAEEssentiaStack;",
        at = @At("HEAD"), cancellable = true, require = 0)
    private void cells$prepareTransfer(IAEEssentiaStack input, Actionable type, IActionSource source,
                                      CallbackInfoReturnable<IAEEssentiaStack> callback) {
        if (this.cells$enteringTransfer) {
            this.cells$enteringTransfer = false;
            return;
        }

        EssentiaTransferScope scope = EssentiaTransferScope.prepare(this.container, type == Actionable.SIMULATE);
        if (scope == null) return;

        // Re-enter the original method so finally restores the scope after exceptions too
        this.cells$enteringTransfer = true;
        try {
            callback.setReturnValue(((EssentiaContainerAdapter) (Object) this).injectItems(input, type, source));
        } finally {
            this.cells$enteringTransfer = false;
            scope.close();
        }
    }
}
