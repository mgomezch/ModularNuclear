package com.gtnewhorizons.modularnuclear.common.gui;

import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.CycleButtonWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactLoopPump;

/**
 * A Compact Loop Pump's GUI: its loop's charge setting, on a button that cycles it as a screwdriver does.
 * See Note [Compact machine GUIs] and Note [The pump on a gas loop]
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
    protected IWidget createMachineRow(ModularPanel panel, PanelSyncManager syncManager) {
        int settings = machine.getChargeSettingCount();
        // A cycle button: a click sets the next setting, a right click the one before
        CycleButtonWidget button = new CycleButtonWidget().size(SLOT_SIZE)
            .stateCount(settings)
            .value(chargeSyncer)
            .overlay(IKey.str("%"));
        String tip = StatCollector.translateToLocal("modularnuclear.compact.pump.gui.charge.tooltip");
        for (int i = 0; i < settings; i++) button.addTooltip(i, tip);
        return Flow.row()
            .fullWidth()
            .coverChildrenHeight()
            .childPadding(4)
            .paddingRight(POWER_SWITCH_ROOM)
            .crossAxisAlignment(Alignment.CrossAxis.CENTER)
            .child(button)
            .child(
                IKey.dynamic(() -> machine.describeChargeSetting(chargeSyncer.getIntValue()))
                    .asWidget()
                    .scale(TEXT_SCALE)
                    .textAlign(Alignment.CenterLeft)
                    .expanded());
    }
}
