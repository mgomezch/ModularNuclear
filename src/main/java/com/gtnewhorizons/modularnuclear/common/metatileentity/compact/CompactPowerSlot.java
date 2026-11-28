package com.gtnewhorizons.modularnuclear.common.metatileentity.compact;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/*-
Note [The compact machines' power slot] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
The compact machines that take or give EU have a power slot, as GregTech's one-block machines do: the
compressor, radiator and pump, which take power, and the turbo-expander, which gives it.  The valve, bath and
heat exchanger take no power and show no power slot.  The slot is the last of the machine's inventory
(MTECompactLoopMachine.getPowerSlot, MTECompactLoopPump.getPowerSlot), so adding it kept the indices of the
slots a saved machine already had.

The machines do none of the charging themselves.  GregTech's BaseMetaTileEntity charges and drains the items
in the slots a machine names through rechargerSlotStartIndex, rechargerSlotCount, dechargerSlotStartIndex and
dechargerSlotCount, every tick, up to the machine's tier: it charges batteries and tools, it drains batteries,
and it burns an item IC2 gives an energy value, such as redstone dust, one at a time.  The machines name the
power slot for both, and switch each on and off as GregTech's MTEBasicMachine does (charges, draws):

  - it charges the item while the machine's buffer is more than two thirds full, as it is when cables feed it;
  - it draws from the item while the buffer is less than a third full, as it is without cables.

Between the two thirds, it does neither, so a machine on cables doesn't drain a battery only to charge it back.

The turbo-expander only charges (MTECompactExpander.drawsFromPowerSlot): it makes power, so it charges whenever
it holds some, as a battery buffer would.

The GUIs show the slot with GregTech's own widget and tooltip (MTETieredMachineBlockBaseGui.createChargerSlot)
under the error icon, where GregTech's one-block machines have it.  See Note [Compact machine GUIs]
*/
/** When the power slot charges and draws. See Note [The compact machines' power slot] */
public final class CompactPowerSlot {

    private CompactPowerSlot() {}

    /** Whether the power slot's item takes charge from the machine: its buffer is more than two thirds full. */
    public static boolean charges(IGregTechTileEntity base) {
        return base != null && base.getStoredEU() / 2 > base.getEUCapacity() / 3;
    }

    /** Whether the machine draws from the power slot's item: its buffer is less than a third full. */
    public static boolean draws(IGregTechTileEntity base) {
        return base != null && base.getStoredEU() < base.getEUCapacity() / 3;
    }
}
