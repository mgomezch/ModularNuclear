package com.gtnewhorizons.modularnuclear.common.metatileentity.compact;

import net.minecraft.item.ItemStack;

import gregtech.api.metatileentity.MetaTileEntity;

/**
 * The compact loop machines' ids and items: seven kinds, each in the five tiers from MV to LuV. See Note [Compact loop
 * machines]
 */
public final class CompactLoopMachines {

    private CompactLoopMachines() {}

    /** A kind of compact machine: its ids run from firstId (MV) to firstId + 4 (LuV). */
    public enum Kind {

        PUMP(32200, "compact.loop_pump", "Compact Loop Pump"),
        COMPRESSOR(32205, "compact.gas_compressor", "Compact Gas Compressor"),
        VALVE(32210, "compact.expansion_valve", "Compact Expansion Valve"),
        EXPANDER(32215, "compact.turbo_expander", "Compact Turbo-Expander"),
        RADIATOR(32220, "compact.radiator", "Compact Radiator"),
        BATH(32225, "compact.coolant_bath", "Compact Coolant Bath"),
        EXCHANGER(32230, "compact.heat_exchanger", "Compact Heat Exchanger");

        public final int firstId;
        public final String name;
        public final String english;
        private final ItemStack[] items = new ItemStack[TIERS];

        Kind(int firstId, String name, String english) {
            this.firstId = firstId;
            this.name = name;
            this.english = english;
        }

        public int id(int tier) {
            return firstId + tier - CompactTier.FIRST;
        }

        /** The machine's item at a GregTech tier (2 is MV), or null before init. */
        public ItemStack item(int tier) {
            ItemStack stack = items[tier - CompactTier.FIRST];
            return stack != null ? stack.copy() : null;
        }

        /** The kind of the machine with this id, or null. */
        public static Kind ofId(int id) {
            for (Kind k : values()) if (id >= k.firstId && id < k.firstId + TIERS) return k;
            return null;
        }

        private MetaTileEntity create(int tier) {
            int id = id(tier);
            String internal = name + ".tier." + String.format("%02d", tier);
            String regional = english + " ("
                + CompactTier.of(tier)
                    .voltageName()
                + ")";
            switch (this) {
                case PUMP:
                    return new MTECompactLoopPump(id, internal, regional, tier);
                case COMPRESSOR:
                    return new MTECompactCompressor(id, internal, regional, tier);
                case VALVE:
                    return new MTECompactValve(id, internal, regional, tier);
                case EXPANDER:
                    return new MTECompactExpander(id, internal, regional, tier);
                case RADIATOR:
                    return new MTECompactRadiator(id, internal, regional, tier);
                case BATH:
                    return new MTECompactBath(id, internal, regional, tier);
                default:
                    return new MTECompactExchanger(id, internal, regional, tier);
            }
        }
    }

    public static final int TIERS = CompactTier.LAST - CompactTier.FIRST + 1;

    public static void init() {
        for (Kind kind : Kind.values()) {
            for (int tier = CompactTier.FIRST; tier <= CompactTier.LAST; tier++) {
                kind.items[tier - CompactTier.FIRST] = kind.create(tier)
                    .getStackForm(1L);
            }
        }
    }
}
