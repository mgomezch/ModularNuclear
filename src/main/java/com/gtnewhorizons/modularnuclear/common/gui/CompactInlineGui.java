package com.gtnewhorizons.modularnuclear.common.gui;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactLoopMachine;

import gregtech.api.util.GTUtility;

/**
 * The GUI of a compact machine the loop flows through: the loop's gas at its inlet, the arrow, and the gas at its
 * outlet. The heat exchanger and the bath build on it. See Note [Compact machine GUIs]
 */
public class CompactInlineGui<T extends MTECompactLoopMachine> extends CompactLoopMachineGui<T> {

    public CompactInlineGui(T machine) {
        super(machine);
    }

    @Override
    protected Flow createProcessRow(ModularPanel panel, PanelSyncManager syncManager) {
        return Flow.row()
            .childPadding(SLOT_SIZE / 2)
            .child(inlet(syncManager))
            .child(processArrow(syncManager))
            .child(outlet(syncManager));
    }

    protected Widget<?> processArrow(PanelSyncManager syncManager) {
        return arrow(
            syncManager,
            "progress",
            machine.getGuiProgressTexture(),
            ProgressWidget.Direction.RIGHT,
            machine::getGuiProgress,
            machine::getGuiProgressLines);
    }

    protected Widget<?> inlet(PanelSyncManager syncManager) {
        return stateSlot(syncManager, "inlet", machine::getGuiLoopFluid, () -> machine.getGuiPortLines(true));
    }

    protected Widget<?> outlet(PanelSyncManager syncManager) {
        return stateSlot(syncManager, "outlet", machine::getGuiLoopFluid, () -> machine.getGuiPortLines(false));
    }

    @Override
    protected boolean supportsChargerSlot() {
        return machine.hasPowerSlot();
    }

    /** GregTech's tooltip, without its line on drawing from batteries for a machine that only charges them. */
    @Override
    protected void createTooltipForChargerSlot(RichTooltip tooltip) {
        if (machine.drawsFromPowerSlot()) {
            super.createTooltipForChargerSlot(tooltip);
            return;
        }
        String tierName = GTUtility.getColoredTierNameFromTier(machine.mTier);
        tooltip.addLine(StatCollector.translateToLocal("GT5U.machines.battery_slot.tooltip"))
            .addLine(StatCollector.translateToLocalFormatted("GT5U.machines.battery_slot.tooltip.2", tierName));
    }

    @Override
    protected Widget<?> createErrorIcon(PanelSyncManager syncManager) {
        return errorIcon(syncManager, machine::getGuiProblem);
    }
}
