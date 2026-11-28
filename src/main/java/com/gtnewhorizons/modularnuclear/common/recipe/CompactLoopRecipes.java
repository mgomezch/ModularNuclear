package com.gtnewhorizons.modularnuclear.common.recipe;

import static gregtech.api.recipe.RecipeMaps.assemblerRecipes;
import static gregtech.api.util.GTRecipeBuilder.SECONDS;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.CompactLoopMachines;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.CompactLoopMachines.Kind;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.CompactTier;

import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;

/*-
Note [Compact loop machine recipes] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Each compact loop machine (Note [Compact loop machines]) is assembled at its own tier from that tier's hull and
components, as GregTech's own one-block machines are: an MV Compact Gas Compressor takes an MV hull, MV pistons
and motor, bronze rotors and MV circuits, in an MV assembler.  So a whole MV loop is within reach of a player
who has just made aluminium, with nothing from a later tier: no cleanroom, no high-pressure hatches.

Besides the hull, components and circuits, a recipe takes its tier's rotor, pipe and plate (TIERS), the materials
GregTech's machines of that tier use: bronze rotors, steel pipe and aluminium plate at MV, up to HSS-S,
niobium-titanium and HSS-S at LuV.  What each kind takes says what it does:

    Loop Pump       2 pumps, a motor, 2 pipes, 2 circuits
    Gas Compressor  2 pistons, a motor, 2 rotors, 2 circuits
    Expansion Valve a pump, 2 pipes, 4 plates, a circuit
    Turbo-Expander  a motor, 2 rotors, 2 pipes, a circuit
    Radiator        2 motors, 2 rotors, 4 plates, 2 pipes
    Coolant Bath    a pump, a conveyor, 2 pipes, 2 circuits     (as a Chemical Bath: pump, conveyors, circuits)
    Heat Exchanger  2 pumps, 4 pipes, 4 plates, a circuit

The pump's tanks are Iron Tanks the player makes separately; none goes into the pump.
*/
/** The compact loop machines' assembler recipes. See Note [Compact loop machine recipes] */
public final class CompactLoopRecipes {

    private CompactLoopRecipes() {}

    /** A tier's parts. */
    private static final class Parts {

        final int tier;
        final ItemList hull, motor, pump, piston, conveyor;
        final Materials circuit, rotor, pipe, plate;

        Parts(int tier, ItemList hull, ItemList motor, ItemList pump, ItemList piston, ItemList conveyor,
            Materials circuit, Materials rotor, Materials pipe, Materials plate) {
            this.tier = tier;
            this.hull = hull;
            this.motor = motor;
            this.pump = pump;
            this.piston = piston;
            this.conveyor = conveyor;
            this.circuit = circuit;
            this.rotor = rotor;
            this.pipe = pipe;
            this.plate = plate;
        }

        ItemStack circuits(int n) {
            return GTOreDictUnificator.get(OrePrefixes.circuit, circuit, n);
        }

        ItemStack rotors(int n) {
            return GTOreDictUnificator.get(OrePrefixes.rotor, rotor, n);
        }

        ItemStack pipes(int n) {
            return GTOreDictUnificator.get(OrePrefixes.pipeMedium, pipe, n);
        }

        ItemStack plates(int n) {
            return GTOreDictUnificator.get(OrePrefixes.plate, plate, n);
        }
    }

    private static Parts[] tiers() {
        return new Parts[] {
            new Parts(
                2,
                ItemList.Hull_MV,
                ItemList.Electric_Motor_MV,
                ItemList.Electric_Pump_MV,
                ItemList.Electric_Piston_MV,
                ItemList.Conveyor_Module_MV,
                Materials.MV,
                Materials.Bronze,
                Materials.Steel,
                Materials.Aluminium),
            new Parts(
                3,
                ItemList.Hull_HV,
                ItemList.Electric_Motor_HV,
                ItemList.Electric_Pump_HV,
                ItemList.Electric_Piston_HV,
                ItemList.Conveyor_Module_HV,
                Materials.HV,
                Materials.Steel,
                Materials.StainlessSteel,
                Materials.StainlessSteel),
            new Parts(
                4,
                ItemList.Hull_EV,
                ItemList.Electric_Motor_EV,
                ItemList.Electric_Pump_EV,
                ItemList.Electric_Piston_EV,
                ItemList.Conveyor_Module_EV,
                Materials.EV,
                Materials.StainlessSteel,
                Materials.Titanium,
                Materials.Titanium),
            new Parts(
                5,
                ItemList.Hull_IV,
                ItemList.Electric_Motor_IV,
                ItemList.Electric_Pump_IV,
                ItemList.Electric_Piston_IV,
                ItemList.Conveyor_Module_IV,
                Materials.IV,
                Materials.TungstenSteel,
                Materials.TungstenSteel,
                Materials.TungstenSteel),
            new Parts(
                6,
                ItemList.Hull_LuV,
                ItemList.Electric_Motor_LuV,
                ItemList.Electric_Pump_LuV,
                ItemList.Electric_Piston_LuV,
                ItemList.Conveyor_Module_LuV,
                Materials.LuV,
                Materials.HSSS,
                Materials.NiobiumTitanium,
                Materials.HSSS) };
    }

    public static void init() {
        if (GTValues.RA == null || assemblerRecipes == null) return;
        for (Parts p : tiers()) {
            add(p, Kind.PUMP, 30, p.pump.get(2L), p.motor.get(1L), p.pipes(2), p.circuits(2));
            add(p, Kind.COMPRESSOR, 30, p.piston.get(2L), p.motor.get(1L), p.rotors(2), p.circuits(2));
            add(p, Kind.VALVE, 20, p.pump.get(1L), p.pipes(2), p.plates(4), p.circuits(1));
            add(p, Kind.EXPANDER, 30, p.motor.get(1L), p.rotors(2), p.pipes(2), p.circuits(1));
            add(p, Kind.RADIATOR, 20, p.motor.get(2L), p.rotors(2), p.plates(4), p.pipes(2));
            add(p, Kind.BATH, 20, p.pump.get(1L), p.conveyor.get(1L), p.pipes(2), p.circuits(2));
            add(p, Kind.EXCHANGER, 30, p.pump.get(2L), p.pipes(4), p.plates(4), p.circuits(1));
        }
    }

    private static void add(Parts p, Kind kind, int seconds, ItemStack... parts) {
        ItemStack machine = kind.item(p.tier);
        if (machine == null) return;
        ItemStack[] inputs = new ItemStack[parts.length + 1];
        inputs[0] = p.hull.get(1L);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] == null) return;
            inputs[i + 1] = parts[i];
        }
        GTValues.RA.stdBuilder()
            .itemInputs((Object[]) inputs)
            .itemOutputs(machine)
            .duration(seconds * SECONDS)
            .eut(CompactTier.of(p.tier).euPerTick)
            .addTo(assemblerRecipes);
    }

    /** How many recipes init adds when every part exists. */
    public static int expectedCount() {
        return CompactLoopMachines.TIERS * Kind.values().length;
    }
}
