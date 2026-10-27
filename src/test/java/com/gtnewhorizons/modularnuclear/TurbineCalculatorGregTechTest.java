package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.TurbineCalculator.FittingMode;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.TurbineCalculator.TurbineMaterial;
import com.gtnewhorizons.modularnuclear.common.nuclear.standalone.TurbineCalculator.TurbineSize;
import com.gtnewhorizons.modularnuclear.data.MaterialRotorDatabaseExporter;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import goodgenerator.items.GGMaterial;
import gregtech.api.enums.Materials;
import gregtech.api.util.TurbineStatCalculator;
import gregtech.common.items.MetaGeneratedTool01;

/** The simulator's rotors match what GregTech's TurbineStatCalculator gives for the same rotor. */
public class TurbineCalculatorGregTechTest {

    private static final Map<TurbineMaterial, Materials> GREGTECH = new LinkedHashMap<>();

    @BeforeAll
    static void init() {
        MaterialRotorDatabaseExporter.initEnvironment();
        GREGTECH.put(TurbineMaterial.HSS_E, Materials.HSSE);
        GREGTECH.put(TurbineMaterial.HSS_S, Materials.HSSS);
        GREGTECH.put(TurbineMaterial.ELVEN_ELEMENTIUM, Materials.ElvenElementium);
        GREGTECH.put(TurbineMaterial.ORINARUKON, Materials.Oriharukon);
        GREGTECH.put(TurbineMaterial.SHADOW_METAL, Materials.Shadow);
        GREGTECH.put(TurbineMaterial.ICHORIUM, Materials.Ichorium);
        GREGTECH.put(TurbineMaterial.DURANIUM, Materials.Duranium);
        GREGTECH.put(TurbineMaterial.GAIA_SPIRIT, Materials.GaiaSpirit);
        GREGTECH.put(TurbineMaterial.ADAMANTIUM, Materials.Adamantium);
        GREGTECH.put(TurbineMaterial.COSMIC_NEUTRONIUM, Materials.CosmicNeutronium);
        GREGTECH.put(TurbineMaterial.INFINITY, Materials.Infinity);
        GREGTECH.put(TurbineMaterial.TUNGSTENSTEEL, Materials.TungstenSteel);
        GREGTECH.put(TurbineMaterial.TITANIUM, Materials.Titanium);
    }

    @Test
    void testRotorsMatchGregTech() {
        for (Map.Entry<TurbineMaterial, Materials> e : GREGTECH.entrySet()) {
            TurbineMaterial ours = e.getKey();
            Materials gt = e.getValue();
            for (int i = 0; i < TurbineSize.values().length; i++) {
                TurbineSize size = TurbineSize.values()[i];
                ItemStack rotor = MetaGeneratedTool01.INSTANCE
                    .getToolWithStats(MaterialRotorDatabaseExporter.ROTOR_TOOL_IDS[i], 1, gt, gt, null);
                TurbineStatCalculator calc = new TurbineStatCalculator(MetaGeneratedTool01.INSTANCE, rotor);
                String what = ours.displayName + " " + size.displayName;
                assertEquals(calc.getSteamEfficiency(), ours.efficiency(size, FittingMode.TIGHT), 1e-6, what);
                assertEquals(calc.getLooseSteamEfficiency(), ours.efficiency(size, FittingMode.LOOSE), 1e-6, what);
                assertEquals(calc.getOptimalSteamFlow(), ours.optimalSteamFlow(size, FittingMode.TIGHT), 1e-3, what);
                assertEquals(
                    calc.getOptimalLooseSteamFlow(),
                    ours.optimalSteamFlow(size, FittingMode.LOOSE),
                    1e-3,
                    what);
            }
        }
    }

    @Test
    void testEveryRotorIsChecked() {
        for (TurbineMaterial m : TurbineMaterial.values()) {
            assertTrue(
                GREGTECH.containsKey(m) || m == TurbineMaterial.EXT_UNST_NAQUADAH,
                m.displayName + " has no GregTech counterpart in this test");
        }
    }

    @Test
    void testExtremelyUnstableNaquadahMatchesGoodGenerator() {
        // A GoodGenerator (Werkstoff) material: its rotor stats are overrides with the default steam multiplier.
        // Werkstoffs record the mod that registers them, so the mocked loader needs an active mod.
        ModContainer goodGenerator = Mockito.mock(ModContainer.class);
        Mockito.when(goodGenerator.getName())
            .thenReturn("GoodGenerator");
        Mockito.when(
            Loader.instance()
                .activeModContainer())
            .thenReturn(goodGenerator);
        assertEquals(
            GGMaterial.extremelyUnstableNaquadah.getStats()
                .getSpeedOverride(),
            TurbineMaterial.EXT_UNST_NAQUADAH.toolSpeed,
            1e-6);
        assertEquals(
            GGMaterial.extremelyUnstableNaquadah.getStats()
                .getQualityOverride(),
            TurbineMaterial.EXT_UNST_NAQUADAH.toolQuality);
        assertEquals(1f, TurbineMaterial.EXT_UNST_NAQUADAH.steamMultiplier);
    }
}
