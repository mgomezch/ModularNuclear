package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraft.world.World;

import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.coolantloop.LoopDevices;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchPressurizedFluid;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTEPressurizedDegasser;
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
    void testOnlyTheOwningDeviceClaimsAHatch() {
        World world = mock(World.class);
        MTEHatchPressurizedFluid pheInlet = mock(MTEHatchPressurizedFluid.class);
        MTEHatchPressurizedFluid heaterInput = mock(MTEHatchPressurizedFluid.class);
        MTEHatchPressurizedFluid stranger = mock(MTEHatchPressurizedFluid.class); // e.g. a degasser's hatch

        MTEPressurizedHeatExchanger phe = mock(MTEPressurizedHeatExchanger.class);
        when(phe.getLoopInlet()).thenReturn(pheInlet);
        when(phe.getLoopOutlet()).thenReturn(mock(MTEHatchPressurizedFluid.class));
        when(phe.ownsHatch(any())).thenCallRealMethod();
        MTEPressurizedFluidHeater heater = mock(MTEPressurizedFluidHeater.class);
        when(heater.getLoopInlet()).thenReturn(heaterInput);
        when(heater.getLoopOutlet()).thenReturn(mock(MTEHatchPressurizedFluid.class));
        when(heater.ownsHatch(any())).thenCallRealMethod();
        // A degasser right next to a hatch it does not own
        MTEPressurizedDegasser degasser = mock(MTEPressurizedDegasser.class);
        when(degasser.ownsHatch(any())).thenCallRealMethod();

        at(world, 0, 64, 0, phe);
        at(world, 1, 64, 0, pheInlet);
        at(world, 4, 64, 0, heater);
        at(world, 4, 65, 0, heaterInput);
        at(world, 2, 64, 0, stranger);
        at(world, 2, 64, 1, degasser);

        assertSame(phe, LoopDevices.at(world, 1, 64, 0));
        assertSame(heater, LoopDevices.at(world, 4, 65, 0));
        assertSame(stranger, LoopDevices.at(world, 2, 64, 0), "a hatch nearby devices do not own is its own device");
    }

    @Test
    void testAClaimedHatchNamesItsOwnerWithoutASearch() {
        World world = mock(World.class);
        MTEHatchPressurizedFluid inlet = new MTEHatchPressurizedFluid("inlet", 4, new String[0], null);
        MTEPressurizedDegasser degasser = mock(MTEPressurizedDegasser.class);
        when(degasser.getLoopInlet()).thenReturn(inlet);
        when(degasser.ownsHatch(any())).thenCallRealMethod();
        when(degasser.getBaseMetaTileEntity()).thenReturn(mock(BaseMetaTileEntity.class));
        inlet.setLoopOwner(degasser);
        at(world, 0, 64, 0, inlet);

        assertSame(degasser, LoopDevices.at(world, 0, 64, 0));
        verify(world, times(1)).getTileEntity(anyInt(), anyInt(), anyInt());
    }
}
