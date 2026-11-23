package com.gtnewhorizons.modularnuclear.mixins.late;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.gtnewhorizons.modularnuclear.common.nei.NEIItemGroups;

import codechicken.nei.CollapsibleItems;
import codechicken.nei.NEIClientConfig;

// Adds the mod's NEI item groups. See Note [NEI item groups from the mod]
// At integration into GTNH: mgomezch/ModularNuclear#44
@Mixin(value = CollapsibleItems.class, remap = false)
public abstract class MixinCollapsibleItems {

    @Shadow
    private static void parseFile(List<String> itemStrings) {}

    @Inject(method = "load", at = @At(value = "INVOKE", target = "Lcodechicken/nei/CollapsibleItems;loadStates()V"))
    private static void modularnuclear$addGroups(CallbackInfo ci) {
        if (NEIClientConfig.enableCollapsibleItems()) {
            parseFile(NEIItemGroups.lines());
        }
    }
}
