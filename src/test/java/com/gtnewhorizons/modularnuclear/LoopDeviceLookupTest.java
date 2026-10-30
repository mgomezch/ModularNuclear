package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopGraphCrawler;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedFluidHeater;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedHeatExchanger;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** A loop crawl attributes a high-pressure hatch only to the device that owns it, never to a nearby one. */
public class LoopDeviceLookupTest {

    private static BaseMetaTileEntity at(World world, int x, int y, int z, IMetaTileEntity mte) {
        BaseMetaTileEntity te = mock(BaseMetaTileEntity.class);
        when(te.getMetaTileEntity()).thenReturn(mte);
        when(world.getTileEntity(x, y, z)).thenReturn(te);
        return te;
    }

    @Test
    void testOnlyTheOwningPheOrHeaterClaimsAHatch() {
        World world = mock(World.class);
        MTEHatchPressurizedFluid pheInlet = mock(MTEHatchPressurizedFluid.class);
        MTEHatchPressurizedFluid heaterInput = mock(MTEHatchPressurizedFluid.class);
        MTEHatchPressurizedFluid stranger = mock(MTEHatchPressurizedFluid.class); // e.g. a degasser's hatch

        MTEPressurizedHeatExchanger phe = mock(MTEPressurizedHeatExchanger.class);
        when(phe.getPrimaryInlet()).thenReturn(pheInlet);
        when(phe.getPrimaryOutlet()).thenReturn(mock(MTEHatchPressurizedFluid.class));
        MTEPressurizedFluidHeater heater = mock(MTEPressurizedFluidHeater.class);
        when(heater.getPressurizedInputHatch()).thenReturn(heaterInput);
        when(heater.getPressurizedOutputHatch()).thenReturn(mock(MTEHatchPressurizedFluid.class));

        at(world, 0, 64, 0, phe);
        at(world, 1, 64, 0, pheInlet);
        at(world, 4, 64, 0, heater);
        at(world, 4, 65, 0, heaterInput);
        at(world, 2, 64, 0, stranger);

        assertSame(phe, LoopGraphCrawler.findAdjacentPHE(world, 1, 64, 0));
        assertSame(heater, LoopGraphCrawler.findAdjacentFluidHeater(world, 4, 65, 0));
        assertNull(LoopGraphCrawler.findAdjacentPHE(world, 2, 64, 0), "a nearby PHE must not claim another hatch");
        assertNull(LoopGraphCrawler.findAdjacentFluidHeater(world, 2, 64, 0), "nor a nearby heater");
        assertNull(LoopGraphCrawler.findAdjacentFluidHeater(world, 1, 64, 0), "a heater never claims a PHE's hatch");
    }
}
