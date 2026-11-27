package com.gtnewhorizons.modularnuclear.common.gui;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactExchanger;

import gregtech.api.modularui2.GTGuiTextures;

/**
 * A Compact Heat Exchanger's GUI: the tank of gas it liquefies and the tank of liquid it has made. A fluid cell on
 * the gas tank empties into it, and one on the liquid tank fills from it. See Note [Compact machine GUIs] and Note
 * [The compact heat exchanger]
 */
public class CompactExchangerGui extends CompactLoopMachineGui<MTECompactExchanger> {

    public CompactExchangerGui(MTECompactExchanger machine) {
        super(machine);
    }

    @Override
    protected IWidget createMachineRow(ModularPanel panel, PanelSyncManager syncManager) {
        return Flow.row()
            .fullWidth()
            .coverChildrenHeight()
            .childPadding(4)
            .mainAxisAlignment(Alignment.MainAxis.CENTER)
            .child(
                new FluidSlot()
                    .syncHandler(
                        new FluidSlotSyncHandler(machine.getGasTank())
                            .filter(fluid -> fluid != null && MTECompactExchanger.cryogenOf(fluid.getFluid()) != null)
                            .canDrainSlot(false))
                    .background(GTGuiTextures.SLOT_FLUID_TANK))
            .child(
                IKey.str("→")
                    .asWidget()
                    .size(10, SLOT_SIZE))
            .child(
                new FluidSlot().syncHandler(new FluidSlotSyncHandler(machine.getLiquidTank()).canFillSlot(false))
                    .background(GTGuiTextures.SLOT_FLUID_TANK));
    }
}
