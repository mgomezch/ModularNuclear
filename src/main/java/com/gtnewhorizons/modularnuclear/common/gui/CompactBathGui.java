package com.gtnewhorizons.modularnuclear.common.gui;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactBath;

import gregtech.api.modularui2.GTGuiTextures;

/**
 * A Compact Coolant Bath's GUI: hot ingots in on the left, the ingots being cooled in the middle (shown, not
 * reachable), cooled ingots out on the right. See Note [Compact machine GUIs] and Note [The compact bath]
 */
public class CompactBathGui extends CompactLoopMachineGui<MTECompactBath> {

    private static final String SLOTS = "bath_inv";

    public CompactBathGui(MTECompactBath machine) {
        super(machine);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        syncManager.registerSlotGroup(SLOTS, 1);
    }

    @Override
    protected IWidget createMachineRow(ModularPanel panel, PanelSyncManager syncManager) {
        Flow row = Flow.row()
            .fullWidth()
            .coverChildrenHeight()
            .childPadding(2)
            .mainAxisAlignment(Alignment.MainAxis.CENTER);
        row.child(
            new ItemSlot().slot(
                new ModularSlot(machine.inventoryHandler, MTECompactBath.IN).slotGroup(SLOTS)
                    .filter(MTECompactBath::cools))
                .backgroundOverlay(GTGuiTextures.OVERLAY_SLOT_IN_STANDARD));
        row.child(
            IKey.str("→")
                .asWidget()
                .size(10, SLOT_SIZE));
        for (int i = 0; i < machine.getWorkSlotCount(); i++) {
            row.child(
                new ItemSlot().slot(
                    new ModularSlot(machine.inventoryHandler, MTECompactBath.FIRST_WORK + i)
                        .accessibility(false, false)));
        }
        row.child(
            IKey.str("→")
                .asWidget()
                .size(10, SLOT_SIZE));
        row.child(
            new ItemSlot().slot(
                new ModularSlot(machine.inventoryHandler, MTECompactBath.OUT).slotGroup(SLOTS)
                    .canPut(false))
                .backgroundOverlay(GTGuiTextures.OVERLAY_SLOT_OUT_STANDARD));
        return row;
    }
}
