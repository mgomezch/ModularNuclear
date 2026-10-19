package com.gtnewhorizons.modularnuclear.mixins;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

@LateMixin
public class ModularNuclearLateMixins implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.modularnuclear.late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        List<String> mixins = new ArrayList<>();
        if (loadedMods.contains("gregtech")) {
            mixins.add("MixinMTELargeTurbineSteam");
            mixins.add("MixinMTEXLTurbineSteam");
            mixins.add("MixinMTECoolantTower");
            mixins.add("MixinGTPostLoad");
        }
        return mixins;
    }
}
