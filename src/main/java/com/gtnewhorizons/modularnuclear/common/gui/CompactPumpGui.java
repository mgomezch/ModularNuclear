package com.gtnewhorizons.modularnuclear.common.gui;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactLoopPump;

import gregtech.api.modularui2.GTGuiTextures;

/**
 * A Compact Loop Pump's GUI: the gas in its tanks, the arrow for its loop's charge, and the gas in its loop; a
 * button in the bottom left corner cycles the charge setting, as a screwdriver does. See Note [Compact machine GUIs]
 * and Note [The pump on a gas loop]
 */
public class CompactPumpGui extends CompactLoopMachineGui<MTECompactLoopPump> {

    private IntSyncValue chargeSyncer;

    public CompactPumpGui(MTECompactLoopPump machine) {
        super(machine);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        chargeSyncer = new IntSyncValue(machine::getChargeSetting, machine::setChargeSetting).allowC2S();
        syncManager.syncValue("chargeSetting", chargeSyncer);
    }

    @Override
    protected Flow createProcessRow(ModularPanel panel, PanelSyncManager syncManager) {
        return Flow.row()
            .childPadding(SLOT_SIZE / 2)
            .child(stateSlot(syncManager, "tanks", machine::getGuiReservoirFluid, machine::getGuiReservoirLines))
            .child(
                arrow(
                    syncManager,
                    "progress",
                    GTGuiTextures.PROGRESSBAR_ARROW_STANDARD,
                    ProgressWidget.Direction.RIGHT,
                    machine::getGuiProgress,
                    machine::getGuiProgressLines))
            .child(stateSlot(syncManager, "loop", machine::getGuiLoopFluid, machine::getGuiLoopLines));
    }

    @Override
    protected Flow createBottomLeftCornerFlow(ModularPanel panel, PanelSyncManager syncManager) {
        int settings = machine.getChargeSettingCount();
        // A click sets the next setting, a right click the one before
        CycleButtonWidget button = new CycleButtonWidget().size(SLOT_SIZE)
            .stateCount(settings)
            .value(chargeSyncer)
            .overlay(GTGuiTextures.OVERLAY_BUTTON_SCREWDRIVER);
        String hint = EnumChatFormatting.GRAY
            + StatCollector.translateToLocal("modularnuclear.compact.pump.gui.charge.tooltip");
        for (int i = 0; i < settings; i++) {
            button.addTooltip(i, machine.describeChargeSetting(i));
            button.addTooltip(i, hint);
        }
        return super.createBottomLeftCornerFlow(panel, syncManager).child(button);
    }

    @Override
    protected boolean supportsChargerSlot() {
        return true;
    }

    @Override
    protected Widget<?> createErrorIcon(PanelSyncManager syncManager) {
        return errorIcon(syncManager, machine::getGuiProblem);
    }
}
