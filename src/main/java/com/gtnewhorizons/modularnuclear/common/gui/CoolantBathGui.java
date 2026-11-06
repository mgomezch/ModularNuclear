package com.gtnewhorizons.modularnuclear.common.gui;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTECoolantBath;

/**
 * Controller GUI for the Coolant Bath multiblock.
 * Displays the 2D grid of items exchanging heat with the loop coolant,
 * along with real-time tooltips and click-to-eject interactions.
 */
public class CoolantBathGui extends CoolantLoopMachineGui<MTECoolantBath> {

    public CoolantBathGui(MTECoolantBath multiblock) {
        super(multiblock);
    }

    @Override
    protected int getBasePanelWidth() {
        return multiblock.isFormedForGui() ? 200 : super.getBasePanelWidth();
    }

    @Override
    protected int getBasePanelHeight() {
        return multiblock.isFormedForGui() ? 105 + CoolantBathGridWidget.gridHeight(multiblock)
            : super.getBasePanelHeight();
    }

    @Override
    public Flow createMainColumn(ModularPanel panel, PanelSyncManager syncManager) {
        // Registered on both sides whatever each side thinks of the structure, so a click always finds it. It runs on
        // the server thread, and only for the player who has this GUI open.
        syncManager.registerSyncedAction(
            CoolantBathGridWidget.EJECT_ACTION,
            false,
            true,
            buf -> multiblock.ejectSlot(buf.readInt()));
        if (!multiblock.isFormedForGui()) {
            return super.createMainColumn(panel, syncManager);
        }
        // The slots as they are now for the player opening it (a no-op on the client): the bath otherwise only
        // resends them every ten seconds
        multiblock.syncToClients();
        return Flow.column()
            .padding(4)
            .child(new CoolantBathGridWidget(multiblock, syncManager))
            .child(createPanelGap(panel, syncManager))
            .childIf(multiblock.supportsInventoryRow(), () -> createInventoryRow(panel, syncManager));
    }
}
