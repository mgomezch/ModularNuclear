package com.gtnewhorizons.modularnuclear.common.gui;

import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.gtnewhorizons.modularnuclear.common.metatileentity.compact.MTECompactBath;

import gregtech.api.modularui2.GTGuiTextures;

/**
 * A Compact Coolant Bath's GUI: hot ingots in, the ingots being cooled, GregTech's bath arrow for the one nearest
 * done, cooled ingots out; the loop's gas through its coil in the bottom corners. See Note [Compact machine GUIs]
 * and Note [The compact bath]
 */
public class CompactBathGui extends CompactInlineGui<MTECompactBath> {

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
    protected Flow createProcessRow(ModularPanel panel, PanelSyncManager syncManager) {
        return Flow.row()
            .childPadding(SLOT_SIZE / 3)
            .child(
                new ItemSlot().slot(
                    new ModularSlot(machine.inventoryHandler, MTECompactBath.IN).slotGroup(SLOTS)
                        .filter(MTECompactBath::cools))
                    .backgroundOverlay(GTGuiTextures.OVERLAY_SLOT_IN_STANDARD))
            .child(workSlots(syncManager))
            .child(
                arrow(
                    syncManager,
                    "progress",
                    GTGuiTextures.PROGRESSBAR_BATH,
                    ProgressWidget.Direction.CIRCULAR_CW,
                    machine::getGuiProgress,
                    machine::getGuiProgressLines))
            .child(
                new ItemSlot().slot(
                    new ModularSlot(machine.inventoryHandler, MTECompactBath.OUT).slotGroup(SLOTS)
                        .canPut(false))
                    .backgroundOverlay(GTGuiTextures.OVERLAY_SLOT_OUT_STANDARD));
    }

    /** The ingots being cooled, in one row or two: shown, not reachable, each with its temperature. */
    private Flow workSlots(PanelSyncManager syncManager) {
        int n = machine.getWorkSlotCount();
        int columns = n <= 3 ? n : (n + 1) / 2;
        Flow rows = Flow.column()
            .coverChildren();
        Flow row = null;
        for (int i = 0; i < n; i++) {
            if (i % columns == 0) {
                row = Flow.row()
                    .coverChildren();
                rows.child(row);
            }
            final int index = i;
            Supplier<String> text = syncedLines(syncManager, "work" + i, () -> machine.getGuiWorkSlotLines(index));
            row.child(new ItemSlot() {

                @Override
                public void buildTooltip(ItemStack stack, RichTooltip tooltip) {
                    super.buildTooltip(stack, tooltip);
                    addLines(tooltip, "\n" + text.get());
                }
            }.slot(
                new ModularSlot(machine.inventoryHandler, MTECompactBath.FIRST_WORK + i).accessibility(false, false)));
        }
        return rows;
    }

    @Override
    protected Flow createBottomLeftCornerFlow(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createBottomLeftCornerFlow(panel, syncManager).child(inlet(syncManager));
    }

    @Override
    protected Flow createBottomRightCornerFlow(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createBottomRightCornerFlow(panel, syncManager).child(outlet(syncManager));
    }
}
