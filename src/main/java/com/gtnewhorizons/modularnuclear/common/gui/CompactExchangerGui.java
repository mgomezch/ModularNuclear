package com.gtnewhorizons.modularnuclear.common.gui;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactExchanger;

import gregtech.api.modularui2.GTGuiTextures;

/**
 * A Compact Heat Exchanger's GUI: the tank of gas it liquefies, the arrow for its next litre, and the tank of
 * liquid it has made, which take and fill fluid cells; its two passes of loop gas in the bottom corners. See Note
 * [Compact machine GUIs] and Note [The compact heat exchanger]
 */
public class CompactExchangerGui extends CompactInlineGui<MTECompactExchanger> {

    public CompactExchangerGui(MTECompactExchanger machine) {
        super(machine);
    }

    @Override
    protected Flow createProcessRow(ModularPanel panel, PanelSyncManager syncManager) {
        return Flow.row()
            .childPadding(SLOT_SIZE / 2)
            .child(
                new FluidSlot()
                    .syncHandler(
                        new FluidSlotSyncHandler(machine.getGasTank())
                            .filter(fluid -> fluid != null && MTECompactExchanger.cryogenOf(fluid.getFluid()) != null)
                            .canDrainSlot(false))
                    .backgroundOverlay(GTGuiTextures.OVERLAY_SLOT_IN_STANDARD))
            .child(
                arrow(
                    syncManager,
                    "progress",
                    GTGuiTextures.PROGRESSBAR_ARROW_STANDARD,
                    ProgressWidget.Direction.RIGHT,
                    machine::getGuiProgress,
                    machine::getGuiProgressLines))
            .child(
                new FluidSlot().syncHandler(new FluidSlotSyncHandler(machine.getLiquidTank()).canFillSlot(false))
                    .backgroundOverlay(GTGuiTextures.OVERLAY_SLOT_OUT_STANDARD));
    }

    @Override
    protected Flow createBottomLeftCornerFlow(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createBottomLeftCornerFlow(panel, syncManager)
            .child(stateSlot(syncManager, "pass1", machine::getGuiLoopFluid, () -> machine.getGuiPassLines(false)));
    }

    @Override
    protected Flow createBottomRightCornerFlow(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createBottomRightCornerFlow(panel, syncManager).child(
            stateSlot(syncManager, "pass2", machine::getGuiSecondPassFluid, () -> machine.getGuiPassLines(true)));
    }
}
