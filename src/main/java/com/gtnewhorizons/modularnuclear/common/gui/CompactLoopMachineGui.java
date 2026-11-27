package com.gtnewhorizons.modularnuclear.common.gui;

import java.util.List;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.FluidSlotSyncHandler;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import com.cleanroommc.modularui.widgets.ProgressWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.cleanroommc.modularui.widgets.slot.FluidSlot;
import com.gtnewhorizons.modularnuclear.common.nei.NEICompactLoopHandler;

import gregtech.api.metatileentity.implementations.MTETieredMachineBlock;
import gregtech.api.modularui2.GTWidgetThemes;
import gregtech.common.gui.modularui.singleblock.base.MTETieredMachineBlockBaseGui;
import gregtech.common.modularui2.widget.GTProgressWidget;

/*-
Note [Compact machine GUIs] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
A compact loop machine (Note [Compact loop machines]) opens a GregTech one-block machine's GUI on a right click,
laid out as GregTech's own: CompactLoopMachineGui extends GregTech's MTETieredMachineBlockBaseGui, which gives it
the 176 by 166 panel, the power switch in the top right corner and the player's inventory, and the GUI puts in
the middle what goes in and what comes out of the machine's process, with GregTech's progress arrow between them.
docs/refrigeration-loops-design.md, "GUIs", has the survey of GregTech's one-block GUIs this follows.

There is no text on the panel.  The numbers are the portable scanner's (getInfoData) and Waila's, and those a
player needs at the GUI are in the tooltips of its slots and arrow.  The server formats each tooltip's lines and
syncs them as one string (syncedLines), so a machine says one thing in its GUI and to the scanner.

The parts, all made by helpers here:

  stateSlot     a fluid slot showing a fluid where it is in the process (the loop's gas at the machine's inlet or
                outlet, a pump's tanks), filled whatever the amount, with its numbers in its tooltip.  It takes
                no cells.  Its tank is a FluidView: the server's view of the machine's state, which the slot's
                sync handler copies to the client.
  arrow         GregTech's GTProgressWidget with a texture GregTech's own machines use, its progress synced from
                the server and its tooltip the machine's status and readout.  A click on it opens NEI's page of
                compact loop machines, as a click on a GregTech machine's arrow opens its recipes.
  errorIcon     GregTech's error picture at the bottom centre, shown while something keeps the machine from
                working at all (MTECompactLoopMachine.getGuiProblem), with the reason in its tooltip.

What the arrow measures:

  - A machine with a process of its own shows that process's progress: the bath its most advanced ingot's
    cooling (CompactBathGui), the heat exchanger its next litre of liquid (CompactExchangerGui), the pump its
    loop's charge (CompactPumpGui).
  - A machine the loop only flows through (compressor, valve, turbo-expander, radiator: CompactInlineGui) has no
    batches: its arrow runs while the machine works, filling in two seconds at full load and more slowly at
    less (MTECompactLoopMachine.advanceGuiPhase, guiLoad).  Its two slots are the loop's gas at its inlet and
    outlet.

The bath and the heat exchanger have item or fluid slots of their own in the middle, so the loop's gas goes to
the bottom corners, where GregTech's machines keep their fluid input and output: inlet on the left, outlet on the
right.  The pump's charge setting is a button in the bottom left corner, where GregTech's machines keep their
mode buttons.

The GUIs use only what GregTech and ModularUI 2 have in both GTNH 2.9.1 and the dailies; the link check covers
them (Note [Link checks against a pack] in nh-dev's tools/linkcheck/check.py).
*/
/** The base of the compact loop machines' GUIs. See Note [Compact machine GUIs] */
public abstract class CompactLoopMachineGui<T extends MTETieredMachineBlock> extends MTETieredMachineBlockBaseGui<T> {

    protected static final int ARROW_WIDTH = 20;
    /** Room for three rows of slots, as a GregTech machine's recipe area has. */
    protected static final int PROCESS_HEIGHT = 3 * SLOT_SIZE;

    public CompactLoopMachineGui(T machine) {
        super(machine);
    }

    /** The compact machines have no sound to muffle. */
    @Override
    protected boolean supportsMuffler() {
        return false;
    }

    @Override
    protected boolean doesAddGregTechLogo() {
        return false;
    }

    /** The process area, centred in GregTech's content section. */
    protected abstract Flow createProcessRow(ModularPanel panel, PanelSyncManager syncManager);

    @Override
    protected ParentWidget<?> createContentSection(ModularPanel panel, PanelSyncManager syncManager) {
        return super.createContentSection(panel, syncManager).child(
            createProcessRow(panel, syncManager).coverChildrenWidth()
                .height(PROCESS_HEIGHT)
                .horizontalCenter()
                .crossAxisAlignment(Alignment.CrossAxis.CENTER));
    }

    /** The bottom row: GregTech's corners, and the error icon at its centre. */
    @Override
    protected ParentWidget<?> createBottomSection(ModularPanel panel, PanelSyncManager syncManager) {
        ParentWidget<?> section = super.createBottomSection(panel, syncManager);
        Widget<?> error = createErrorIcon(syncManager);
        return error != null ? section.child(error.horizontalCenter()) : section;
    }

    /** The error icon, or null for a machine that has none. */
    protected Widget<?> createErrorIcon(PanelSyncManager syncManager) {
        return null;
    }

    // --- Helpers ---

    /**
     * Syncs lines formatted on the server as one string, under key, and returns the client's copy of them joined
     * by newlines.
     */
    protected static Supplier<String> syncedLines(PanelSyncManager syncManager, String key,
        Supplier<List<String>> lines) {
        StringSyncValue value = new StringSyncValue(() -> String.join("\n", lines.get()));
        syncManager.syncValue(key, value);
        return value::getValue;
    }

    /** Adds lines joined by newlines to a tooltip, the first as its title and the rest grey. */
    protected static void addLines(RichTooltip tooltip, String joined) {
        if (joined == null || joined.isEmpty()) return;
        String[] lines = joined.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;
            tooltip.addLine(IKey.str(i == 0 ? lines[i] : EnumChatFormatting.GRAY + lines[i]));
        }
    }

    /**
     * A fluid slot showing where a fluid is in the process, with its tooltip lines; it takes no cells.
     * See Note [Compact machine GUIs]
     */
    protected FluidSlot stateSlot(PanelSyncManager syncManager, String key, Supplier<FluidStack> fluid,
        Supplier<List<String>> lines) {
        Supplier<String> text = syncedLines(syncManager, key + ".lines", lines);
        FluidSlot slot = new FluidSlot() {

            @Override
            protected void addToolTip(RichTooltip tooltip) {
                FluidStack f = getFluidStack();
                if (f != null) tooltip.addLine(IKey.str(f.getLocalizedName()));
                addLines(tooltip, text.get());
            }

            @Override
            protected boolean displayAmountText() {
                return false;
            }
        };
        FluidView view = new FluidView(baseMetaTileEntity.isServerSide() ? fluid : null);
        return slot.syncHandler(
            new FluidSlotSyncHandler(view).canFillSlot(false)
                .canDrainSlot(false)
                .controlsAmount(false))
            .alwaysShowFull(true);
    }

    /** GregTech's progress arrow, its progress and tooltip lines synced from the server. */
    protected GTProgressWidget arrow(PanelSyncManager syncManager, String key, UITexture texture,
        ProgressWidget.Direction direction, DoubleSupplier progress, Supplier<List<String>> lines) {
        DoubleSyncValue value = new DoubleSyncValue(progress::getAsDouble);
        syncManager.syncValue(key, value);
        Supplier<String> text = syncedLines(syncManager, key + ".lines", lines);
        GTProgressWidget arrow = new GTProgressWidget();
        arrow.neiTransferRect(NEICompactLoopHandler.OVERLAY_ID, null, null);
        arrow.value(value)
            .texture(texture, ARROW_WIDTH)
            .direction(direction)
            .size(ARROW_WIDTH, SLOT_SIZE);
        arrow.tooltipAutoUpdate(true)
            .tooltipBuilder(t -> addLines(t, text.get()));
        return arrow;
    }

    /** GregTech's error picture, shown while problem is not empty, with it as its tooltip. */
    protected Widget<?> errorIcon(PanelSyncManager syncManager, Supplier<String> problem) {
        StringSyncValue value = new StringSyncValue(problem::get);
        syncManager.syncValue("problem", value);
        IDrawable.DrawableWidget icon = new IDrawable.DrawableWidget(IDrawable.EMPTY);
        icon.size(SLOT_SIZE)
            .widgetTheme(GTWidgetThemes.PICTURE_ERROR)
            .setEnabledIf(
                w -> !value.getValue()
                    .isEmpty());
        icon.tooltipAutoUpdate(true)
            .tooltipBuilder(t -> addLines(t, value.getValue()));
        return icon;
    }

    /**
     * A tank showing a machine's state: on the server it reads source, and on the client it holds what the slot's
     * sync handler copies into it. See Note [Compact machine GUIs]
     */
    protected static final class FluidView extends FluidTank {

        private final Supplier<FluidStack> source;

        FluidView(Supplier<FluidStack> source) {
            super(Integer.MAX_VALUE);
            this.source = source;
        }

        @Override
        public FluidStack getFluid() {
            return source != null ? source.get() : super.getFluid();
        }

        @Override
        public int getFluidAmount() {
            FluidStack f = getFluid();
            return f != null ? f.amount : 0;
        }
    }
}
