package com.gtnewhorizons.modularnuclear.mixins.late;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.IReadyBeforeFirstTick;

import gregtech.api.metatileentity.BaseMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

@Mixin(value = BaseMetaTileEntity.class, remap = false)
public abstract class MixinBaseMetaTileEntity {

    @Shadow
    protected MetaTileEntity mMetaTileEntity;

    // See Note [Hatches that work without ticking]
    @Redirect(
        method = { "fill", "canFill" },
        at = @At(
            value = "FIELD",
            opcode = Opcodes.GETFIELD,
            target = "Lgregtech/api/metatileentity/BaseMetaTileEntity;mTickTimer:J"),
        require = 2)
    private long modularnuclear$readTickTimer(BaseMetaTileEntity self) {
        return mMetaTileEntity instanceof IReadyBeforeFirstTick ? Long.MAX_VALUE : self.mTickTimer;
    }

    // See Note [Hatches that work without ticking]
    // func_145829_t is validate outside the dev environment
    @Redirect(
        method = { "validate", "func_145829_t" },
        at = @At(
            value = "FIELD",
            opcode = Opcodes.PUTFIELD,
            target = "Lgregtech/api/metatileentity/BaseMetaTileEntity;mTickTimer:J"),
        require = 1)
    private void modularnuclear$resetTickTimer(BaseMetaTileEntity self, long value) {
        if (mMetaTileEntity instanceof IReadyBeforeFirstTick && self.isTickDisabled()) return;
        self.mTickTimer = value;
    }
}
