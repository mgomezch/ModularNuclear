package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactBath;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactCompressor;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactExpander;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactLoopPump;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactValve;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** The compact machines' power slot. See Note [The compact machines' power slot] */
public class CompactPowerSlotTest {

    private static final long CAPACITY = 9000L;

    @BeforeAll
    static void init() {
        TestEnvironmentHelper.init();
    }

    /** The machine on a tile that holds stored EU of CAPACITY. */
    private static <T extends IMetaTileEntity> T holding(T machine, long stored) {
        IGregTechTileEntity base = Mockito.mock(IGregTechTileEntity.class);
        Mockito.when(base.getStoredEU())
            .thenReturn(stored);
        Mockito.when(base.getEUCapacity())
            .thenReturn(CAPACITY);
        machine.setBaseMetaTileEntity(base);
        return machine;
    }

    @Test
    public void aPoweredMachineChargesWhenFullAndDrawsWhenLow() {
        // Above two thirds it charges, below one third it draws, between them it does neither
        MTECompactCompressor full = holding(new MTECompactCompressor("t.c", 2, null, null), 7000L);
        assertEquals(1, full.rechargerSlotCount());
        assertEquals(0, full.dechargerSlotCount());
        MTECompactCompressor middle = holding(new MTECompactCompressor("t.c", 2, null, null), 4500L);
        assertEquals(0, middle.rechargerSlotCount());
        assertEquals(0, middle.dechargerSlotCount());
        MTECompactCompressor low = holding(new MTECompactCompressor("t.c", 2, null, null), 1000L);
        assertEquals(0, low.rechargerSlotCount());
        assertEquals(1, low.dechargerSlotCount());

        MTECompactLoopPump pump = holding(new MTECompactLoopPump("t.p", 2, null, null), 1000L);
        assertEquals(1, pump.dechargerSlotCount());
        assertEquals(pump.getPowerSlot(), pump.dechargerSlotStartIndex());
    }

    @Test
    public void thePowerSlotIsTheLastSoSavedSlotsKeepTheirIndices() {
        MTECompactBath bath = new MTECompactBath("t.b", 2, null, null);
        assertEquals(bath.getSizeInventory() - 1, bath.getPowerSlot());
        assertTrue(bath.getPowerSlot() >= MTECompactBath.FIRST_WORK + bath.getWorkSlotCount());
        MTECompactCompressor compressor = new MTECompactCompressor("t.c", 2, null, null);
        assertEquals(0, compressor.getPowerSlot());
        assertEquals(compressor.getPowerSlot(), compressor.rechargerSlotStartIndex());
    }

    @Test
    public void theExpanderOnlyCharges() {
        MTECompactExpander expander = holding(new MTECompactExpander("t.e", 2, null, null), 10L);
        assertTrue(expander.hasPowerSlot());
        assertFalse(expander.drawsFromPowerSlot());
        assertEquals(1, expander.rechargerSlotCount());
        assertEquals(0, expander.dechargerSlotCount());
    }

    @Test
    public void unpoweredMachinesHaveNoPowerSlot() {
        MTECompactValve valve = holding(new MTECompactValve("t.v", 2, null, null), 0L);
        assertFalse(valve.hasPowerSlot());
        assertEquals(0, valve.rechargerSlotCount());
        assertEquals(0, valve.dechargerSlotCount());
        assertFalse(valve.isValidSlot(valve.getPowerSlot()));
    }
}
