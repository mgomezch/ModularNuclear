package com.gtnewhorizons.modularnuclear.common.gui;

import java.util.function.Supplier;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;

import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.modularui2.GTGuiTextures;
import gregtech.common.gui.modularui.singleblock.base.MTETieredMachineBlockBaseGui;

/*-
Note [Compact machine GUIs] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A compact loop machine (Note [Compact loop machines]) opens a GregTech one-block machine's GUI on a right click:
CompactLoopMachineGui on GregTech's MTETieredMachineBlockBaseGui, which gives it the panel, the power switch and,
for a machine with slots, the player's inventory.

Every machine's GUI is a screen of its readout lines (IReadoutLines.getReadoutLines), the same lines the scanner
and Waila show: they are formatted on the server and synced as one string (registerSyncValues), as
CoolantLoopMachineGui does for the multiblock gas machines, so a machine says one thing everywhere.  The screen
scrolls, and its text is small (TEXT_SCALE) and wraps: the lines are written for a scanner's width.

Above the screen is a row (createMachineRow): a line saying what the machine is for (getGuiHeadline, the first
line of its tooltip), which also keeps the screen clear of the power switch in the panel's corner.  Three machines
put their own widgets there instead:

  CompactBathGui       the slot hot ingots go in, the ingots being cooled (shown, not reachable), and the slot the
                       cooled ones come out of
  CompactExchangerGui  its gas tank and its liquid tank, which take and fill fluid cells
  CompactPumpGui       a button for the loop's charge setting, as a screwdriver cycles it

A machine without slots binds no player inventory (doesBindPlayerInventory), and its panel is the shorter for it.
The GUIs use only what GregTech's base class and ModularUI 2 have in both GTNH 2.9.1 and the dailies; the link
check covers them (Note [Link checks against a pack] in nh-dev's tools/linkcheck/check.py).
*/
/** The GUI of a compact loop machine. See Note [Compact machine GUIs] */
public class CompactLoopMachineGui<T extends MTETieredMachineBlock & IReadoutLines>
    extends MTETieredMachineBlockBaseGui<T> {

    /** Most readout lines a machine shows. */
    protected static final int READOUT_LINES = 12;
    protected static final float TEXT_SCALE = 0.75f;
    protected static final String LINES_SYNC = "readoutLines";

    private StringSyncValue linesSyncer;

    public CompactLoopMachineGui(T machine) {
        super(machine);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);
        linesSyncer = new StringSyncValue(() -> String.join("\n", machine.getReadoutLines()));
        syncManager.syncValue(LINES_SYNC, linesSyncer);
    }

    @Override
    protected int getBasePanelWidth() {
        return 216;
    }

    /** Taller than GregTech's 166 with the player's inventory, for the screen under a machine's own row. */
    @Override
    protected int getBasePanelHeight() {
        return machine.supportsInventoryRow() ? 196 : 116;
    }

    /** The compact machines have no sound to muffle. */
    @Override
    protected boolean supportsMuffler() {
        return false;
    }

    /** Room the power switch in the panel's top right corner takes from a row beside it. */
    protected static final int POWER_SWITCH_ROOM = 16;

    /**
     * The row above the screen: a machine's own widgets, or by default a line saying what the machine is for. It
     * keeps clear of the power switch in the corner, which would otherwise sit on the screen.
     */
    protected IWidget createMachineRow(ModularPanel panel, PanelSyncManager syncManager) {
        return Flow.row()
            .fullWidth()
            .height(SLOT_SIZE)
            .paddingRight(POWER_SWITCH_ROOM)
            .child(
                IKey.str(machine.getGuiHeadline())
                    .asWidget()
                    .scale(TEXT_SCALE)
                    .textAlign(Alignment.CenterLeft)
                    .expanded());
    }

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        Flow column = Flow.column()
            .expanded()
            .fullWidth()
            .childPadding(2);
        IWidget row = createMachineRow(panel, syncManager);
        if (row != null) column.child(row);
        return column.child(createScreen());
    }

    /** The screen: the machine's readout lines, scrolling. */
    protected ParentWidget<?> createScreen() {
        ListWidget<IWidget, ?> list = new ListWidget<>().full()
            .crossAxisAlignment(Alignment.CrossAxis.START);
        for (int i = 0; i < READOUT_LINES; i++) {
            final int line = i;
            Supplier<String> text = () -> line(line);
            list.child(
                IKey.dynamic(text)
                    .color(ColorUtils.loopFlow::getColor)
                    .asWidget()
                    .scale(TEXT_SCALE)
                    .textAlign(Alignment.CenterLeft)
                    .fullWidth()
                    .marginBottom(1)
                    .setEnabledIf(
                        w -> !text.get()
                            .isEmpty()));
        }
        return new ParentWidget<>().fullWidth()
            .expanded()
            .background(GTGuiTextures.BACKGROUND_TEXT_FIELD)
            .padding(4, 3)
            .child(list.collapseDisabledChild());
    }

    private String line(int index) {
        String all = linesSyncer != null ? linesSyncer.getValue() : null;
        if (all == null || all.isEmpty()) return "";
        String[] parts = all.split("\n", -1);
        return index < parts.length ? parts[index] : "";
    }
}
