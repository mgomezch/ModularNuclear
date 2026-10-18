package com.gtnewhorizons.modularnuclear.common.gui;

import static gregtech.api.metatileentity.BaseTileEntity.TOOLTIP_DELAY;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.IPanelHandler;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.drawable.ItemDrawable;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.utils.Alignment;
import com.cleanroommc.modularui.utils.Color;
import com.cleanroommc.modularui.value.sync.BooleanSyncValue;
import com.cleanroommc.modularui.value.sync.DoubleSyncValue;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.LongSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ButtonWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

import gregtech.api.enums.ItemList;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * ModularUI 2 GUI for the Multi-Parameter Thermonuclear Reactor (MPTR).
 * Extends MTEMultiBlockBaseGui to integrate native GTNH multiblock styling, terminal telemetry,
 * emergency SCRAM controls, and the Core Hatches floating grid window.
 */
public class NuclearReactorGui extends MTEMultiBlockBaseGui<MTENuclearReactor> {

    public NuclearReactorGui(MTENuclearReactor multiblock) {
        super(multiblock);
    }

    @Override
    protected void registerSyncValues(PanelSyncManager syncManager) {
        super.registerSyncValues(syncManager);

        syncManager
            .syncValue("coreTemp", new DoubleSyncValue(() -> multiblock.mCoreTemp, v -> multiblock.mCoreTemp = v));
        syncManager.syncValue("pipeTier", new IntSyncValue(() -> multiblock.mPipeTier, v -> multiblock.mPipeTier = v));
        syncManager.syncValue(
            "reactivity",
            new DoubleSyncValue(() -> multiblock.mReactivity, v -> multiblock.mReactivity = v));
        syncManager.syncValue(
            "flux",
            new LongSyncValue(() -> (long) multiblock.mNeutronsProduced, v -> multiblock.mNeutronsProduced = (int) v));
        syncManager.syncValue(
            "fluence",
            new DoubleSyncValue(
                () -> (double) multiblock.mCumulativeNeutrons,
                v -> multiblock.mCumulativeNeutrons = (long) v));
        syncManager.syncValue(
            "fastAbsorbed",
            new LongSyncValue(() -> (long) multiblock.mFastAbsorbed, v -> multiblock.mFastAbsorbed = (int) v));
        syncManager.syncValue(
            "thermalAbsorbed",
            new LongSyncValue(() -> (long) multiblock.mThermalAbsorbed, v -> multiblock.mThermalAbsorbed = (int) v));
        syncManager.syncValue(
            "escapedNeutrons",
            new LongSyncValue(() -> (long) multiblock.mEscapedNeutrons, v -> multiblock.mEscapedNeutrons = (int) v));
        syncManager.syncValue(
            "damage",
            new DoubleSyncValue(() -> multiblock.mReactorDamage, v -> multiblock.mReactorDamage = v));
        syncManager.syncValue("scram", new BooleanSyncValue(() -> multiblock.mScram, multiblock::setScram));

        syncManager.registerSyncedAction("toggleScram", false, true, buf -> multiblock.setScram(!multiblock.mScram));
    }

    @Override
    protected ListWidget<IWidget, ?> createTerminalTextWidget(PanelSyncManager syncManager, ModularPanel parent) {
        ListWidget<IWidget, ?> list = super.createTerminalTextWidget(syncManager, parent);

        DoubleSyncValue coreTempSyncer = syncManager.findSyncHandler("coreTemp", DoubleSyncValue.class);
        IntSyncValue pipeTierSyncer = syncManager.findSyncHandler("pipeTier", IntSyncValue.class);
        DoubleSyncValue reactivitySyncer = syncManager.findSyncHandler("reactivity", DoubleSyncValue.class);
        LongSyncValue fluxSyncer = syncManager.findSyncHandler("flux", LongSyncValue.class);
        DoubleSyncValue fluenceSyncer = syncManager.findSyncHandler("fluence", DoubleSyncValue.class);
        LongSyncValue fastAbsSyncer = syncManager.findSyncHandler("fastAbsorbed", LongSyncValue.class);
        LongSyncValue thermAbsSyncer = syncManager.findSyncHandler("thermalAbsorbed", LongSyncValue.class);
        LongSyncValue escSyncer = syncManager.findSyncHandler("escapedNeutrons", LongSyncValue.class);
        DoubleSyncValue damageSyncer = syncManager.findSyncHandler("damage", DoubleSyncValue.class);
        BooleanSyncValue scramSyncer = syncManager.findSyncHandler("scram", BooleanSyncValue.class);

        // Machine Title Header
        list.child(
            IKey.dynamic(() -> EnumChatFormatting.BOLD + multiblock.getLocalName())
                .color(Color.WHITE.main)
                .asWidget()
                .textAlign(Alignment.CenterLeft)
                .marginBottom(2));

        // Core Temp
        list.child(
            IKey.dynamic(
                () -> String.format(
                    Locale.US,
                    "Core Temp: %.1f / %.0f °C",
                    coreTempSyncer != null ? coreTempSyncer.getDoubleValue() : multiblock.mCoreTemp,
                    NuclearSimulationEngine.getMaxOperatingTemperature(
                        pipeTierSyncer != null ? pipeTierSyncer.getIntValue() : multiblock.mPipeTier)))
                .color(Color.rgb(255, 200, 0))
                .asWidget()
                .textAlign(Alignment.CenterLeft));

        // Avg Reactivity
        list.child(
            IKey.dynamic(
                () -> String.format(
                    Locale.US,
                    "Avg Reactivity: %.1f%%",
                    (reactivitySyncer != null ? reactivitySyncer.getDoubleValue() : multiblock.mReactivity) * 100.0))
                .color(Color.rgb(100, 200, 255))
                .asWidget()
                .textAlign(Alignment.CenterLeft));

        // Flux
        list.child(
            IKey.dynamic(
                () -> "Flux: " + NuclearSimulationEngine.formatNeutronFlux(
                    fluxSyncer != null ? (int) fluxSyncer.getLongValue() : multiblock.mNeutronsProduced))
                .color(Color.rgb(100, 220, 255))
                .asWidget()
                .textAlign(Alignment.CenterLeft));

        // Fluence
        list.child(
            IKey.dynamic(
                () -> "Fluence: " + NuclearSimulationEngine.formatNeutronFluence(
                    fluenceSyncer != null ? (long) fluenceSyncer.getDoubleValue() : multiblock.mCumulativeNeutrons))
                .color(Color.rgb(180, 200, 255))
                .asWidget()
                .textAlign(Alignment.CenterLeft));

        // Neutrons
        list.child(
            IKey.dynamic(
                () -> String.format(
                    Locale.US,
                    "Neutrons: %d fast, %d therm, %d esc",
                    fastAbsSyncer != null ? fastAbsSyncer.getLongValue() : multiblock.mFastAbsorbed,
                    thermAbsSyncer != null ? thermAbsSyncer.getLongValue() : multiblock.mThermalAbsorbed,
                    escSyncer != null ? escSyncer.getLongValue() : multiblock.mEscapedNeutrons))
                .color(Color.rgb(200, 200, 200))
                .asWidget()
                .textAlign(Alignment.CenterLeft));

        // Damage (only shown when damage > 0)
        list.child(
            IKey.dynamic(
                () -> String.format(
                    Locale.US,
                    "Damage: %.1f%%",
                    damageSyncer != null ? damageSyncer.getDoubleValue() : multiblock.mReactorDamage))
                .color(Color.rgb(255, 80, 80))
                .asWidget()
                .textAlign(Alignment.CenterLeft)
                .setEnabledIf(
                    w -> (damageSyncer != null ? damageSyncer.getDoubleValue() : multiblock.mReactorDamage) > 0.0));

        // SCRAM Alert (if active)
        list.child(
            IKey.str(EnumChatFormatting.RED + "" + EnumChatFormatting.BOLD + "EMERGENCY SCRAM ACTIVE")
                .asWidget()
                .textAlign(Alignment.CenterLeft)
                .setEnabledIf(w -> scramSyncer != null ? scramSyncer.getBoolValue() : multiblock.mScram));

        return list;
    }

    @Override
    protected Flow createRightPanelGapRow(ModularPanel parent, PanelSyncManager syncManager) {
        Flow row = super.createRightPanelGapRow(parent, syncManager);

        // Core Hatches grid floating window button
        row.child(createReactorGridButton(syncManager, parent));

        // SCRAM toggle button
        row.child(createScramButton(syncManager));

        return row;
    }

    protected ButtonWidget<?> createReactorGridButton(PanelSyncManager syncManager, ModularPanel parent) {
        IPanelHandler gridPanel = syncManager.syncedPanel(
            ReactorGridPanel.PANEL_NAME,
            true,
            (panelSyncManager, _) -> ReactorGridPanel.createPanel(panelSyncManager, parent, multiblock));

        return new ButtonWidget<>().size(16, 16)
            .marginLeft(4)
            .overlay(new ItemDrawable(ItemList.RodUranium.get(1L)))
            .onMousePressed(d -> {
                if (!gridPanel.isPanelOpen()) {
                    gridPanel.openPanel();
                } else {
                    gridPanel.closePanel();
                }
                return true;
            })
            .tooltipBuilder(t -> {
                t.addLine(StatCollector.translateToLocal("GT5U.gui.button.reactor_hatches"));
                t.addLine(EnumChatFormatting.GRAY + "Open reactor core hatches view");
            })
            .tooltipShowUpTimer(TOOLTIP_DELAY);
    }

    public static class ScramButton extends ButtonWidget<ScramButton> {

        private final BooleanSyncValue scramSyncer;
        private final MTENuclearReactor multiblock;

        public ScramButton(BooleanSyncValue scramSyncer, MTENuclearReactor multiblock) {
            this.scramSyncer = scramSyncer;
            this.multiblock = multiblock;
        }

        @Override
        public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            int w = getArea() != null ? getArea().w() : 64;
            int h = getArea() != null ? getArea().h() : 16;
            // Outer dark red/black border
            GuiDraw.drawRect(0, 0, w, h, 0xFF330000);
            // Background red
            boolean active = scramSyncer != null ? scramSyncer.getBoolValue() : multiblock.mScram;
            boolean hovering = context != null && context.isHovered(this);
            int bgColor = hovering ? (active ? 0xFFFF3333 : 0xFFDD1111) : (active ? 0xFFCC0000 : 0xFFAA0000);
            GuiDraw.drawRect(1, 1, w - 2, h - 2, bgColor);
            // Highlight line at top
            GuiDraw.drawRect(1, 1, w - 2, 1, 0x55FFFFFF);
            // Text: SCRAM or SCRAMMED in bold white letters
            String text = active ? (EnumChatFormatting.BOLD + "SCRAMMED") : (EnumChatFormatting.BOLD + "SCRAM");
            int strW = Minecraft.getMinecraft().fontRenderer.getStringWidth(text);
            int strH = 8;
            Minecraft.getMinecraft().fontRenderer
                .drawStringWithShadow(text, (w - strW) / 2, (h - strH) / 2, 0xFFFFFFFF);
        }
    }

    protected ButtonWidget<?> createScramButton(PanelSyncManager syncManager) {
        BooleanSyncValue scramSyncer = syncManager.findSyncHandler("scram", BooleanSyncValue.class);
        ScramButton scramButton = new ScramButton(scramSyncer, multiblock);

        scramButton.size(64, 16)
            .marginLeft(4)
            .onMousePressed(d -> {
                syncManager.callSyncedAction("toggleScram", b -> {});
                return true;
            })
            .tooltipBuilder(t -> {
                boolean active = scramSyncer != null ? scramSyncer.getBoolValue() : multiblock.mScram;
                t.addLine(EnumChatFormatting.RED + "" + EnumChatFormatting.BOLD + "Emergency SCRAM");
                t.addLine(EnumChatFormatting.GRAY + "Immediately inserts all control rods to 100%.");
                if (active) {
                    t.addLine(EnumChatFormatting.YELLOW + "Status: active (click to reset/disengage)");
                } else {
                    t.addLine(EnumChatFormatting.GREEN + "Status: disengaged (normal redstone control)");
                }
            })
            .tooltipAutoUpdate(true)
            .tooltipShowUpTimer(0);

        return scramButton;
    }
}
