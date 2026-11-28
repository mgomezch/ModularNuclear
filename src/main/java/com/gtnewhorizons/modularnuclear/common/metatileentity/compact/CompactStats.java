package com.gtnewhorizons.modularnuclear.common.metatileentity.compact;

import java.util.ArrayList;
import java.util.List;

import com.gtnewhorizons.modularnuclear.common.coolantloop.gas.GasStageModels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.CompactLoopMachines.Kind;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.gas.GasMachineParts;

import gregtech.api.enums.GTValues;

/**
 * What a compact loop machine of a kind and tier can do, as rows of a label's language key and a value: what NEI
 * shows for it (NEICompactLoopHandler). The keys are under modularnuclear.nei.compact.row. See Note [Compact loop
 * machines]
 */
public final class CompactStats {

    private CompactStats() {}

    /** A loop of each tier against the Vacuum Freezer, in EU per ingot: CompactLoopBalanceTest's figures. */
    private static final String[] AGAINST_FREEZER = { "1.0", "1.7", "2.3", "2.8", "2.8" };

    public static List<String[]> rows(Kind kind, int tier) {
        CompactTier t = CompactTier.of(tier);
        List<String[]> rows = new ArrayList<>();
        switch (kind) {
            case PUMP:
                row(rows, "loop_volume", "%,.0f L", t.maxLoopVolumeLiters);
                row(rows, "fill_rate", "%.0f%%", t.fillRateFactor * 100.0);
                row(rows, "charging_power", "%d EU/t", Math.max(1L, t.euPerTick / 4L));
                row(rows, "tanks", "%s", "Iron Tanks");
                row(rows, "against_freezer", "%s x", AGAINST_FREEZER[tier - CompactTier.FIRST]);
                break;
            case COMPRESSOR:
                row(rows, "power", "%d EU/t (%s)", t.euPerTick, GTValues.VN[tier]);
                row(rows, "drive", "%.0f%%", t.driveEfficiency * 100.0);
                row(rows, "shaft", "%sW", GasMachineParts.si(t.euPerTick * 20.0 * t.shaftJoulesPerEu()));
                row(rows, "isentropic", "%.0f%%", t.compressorEfficiency * 100.0);
                row(
                    rows,
                    "ratio",
                    "%.1f to %.1f",
                    t.builtInRatio,
                    t.builtInRatio * (1.0 + GasStageModels.Compressor.UNLOADING_BAND));
                row(rows, "displacement", "%.0f m³/s", t.displacement);
                break;
            case VALVE:
                row(rows, "orifice", "%.0f cm²", t.valveArea * 1e4);
                row(rows, "superheat", "%.0f K", new GasStageModels.Thermostat().setPointK);
                row(rows, "power", "%s", "-");
                break;
            case EXPANDER:
                row(rows, "nozzle", "%.0f cm²", t.expanderNozzle * 1e4);
                row(rows, "isentropic", "%.0f%%", t.expanderEfficiency * 100.0);
                row(rows, "output", "%d EU/t (%s)", t.euPerTick, GTValues.VN[tier]);
                break;
            case RADIATOR:
                row(rows, "conductance", "%sW/K", GasMachineParts.si(t.radiatorConductance));
                row(rows, "fan", "%d EU/t", t.radiatorFanEu);
                row(rows, "full_fan", "+%.0f K", CompactTier.RADIATOR_FULL_FAN_K);
                break;
            case BATH:
                row(rows, "slots", "%d", t.bathSlots);
                row(rows, "pace", "%.1f x", MTECompactBath.COIL_PACE);
                row(rows, "power", "%s", "-");
                break;
            default:
                row(rows, "conductance", "%sW/K", GasMachineParts.si(t.exchangerConductance));
                row(rows, "tank", "%,d L", MTECompactExchanger.capacity(tier));
                row(rows, "power", "%s", "-");
        }
        row(rows, "pressure", "%.0f bar", t.maxPressureBar);
        row(rows, "volume", "%,.0f L", t.volumeM3 * 1000.0);
        return rows;
    }

    private static void row(List<String[]> rows, String key, String format, Object... args) {
        rows.add(new String[] { "modularnuclear.nei.compact.row." + key, String.format(format, args) });
    }
}
