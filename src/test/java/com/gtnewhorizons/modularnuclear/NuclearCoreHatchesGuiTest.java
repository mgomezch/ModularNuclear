package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraft.entity.player.EntityPlayerMP;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cleanroommc.modularui.api.RecipeViewerSettings;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressureInput;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressureOutput;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

public class NuclearCoreHatchesGuiTest {

    @BeforeAll
    static void initEnvironment() {
        TestEnvironmentHelper.init();
    }

    private PosGuiData createMockGuiData(EntityPlayerMP player, gregtech.api.metatileentity.BaseMetaTileEntity te) {
        PosGuiData data = mock(PosGuiData.class);
        when(data.getPlayer()).thenReturn(player);
        when(data.getTileEntity()).thenReturn(te);
        return data;
    }

    private gregtech.api.metatileentity.BaseMetaTileEntity createMockTileEntity() {
        gregtech.api.metatileentity.BaseMetaTileEntity te = mock(gregtech.api.metatileentity.BaseMetaTileEntity.class);
        when(te.getXCoord()).thenReturn(100);
        when(te.getYCoord()).thenReturn((short) 64);
        when(te.getZCoord()).thenReturn(200);
        when(te.canAccessData()).thenReturn(true);
        when(te.isServerSide()).thenReturn(true);
        when(te.getCoverAtSide(org.mockito.Mockito.any())).thenReturn(gregtech.api.covers.CoverRegistry.NO_COVER);
        return te;
    }

    @Test
    void testNuclearBusBuildUI() {
        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.bus", 1, new String[0], null);
        BaseMetaTileEntity te = createMockTileEntity();
        bus.setBaseMetaTileEntity(te);

        assertTrue(bus.hasMui2Gui(), "Nuclear bus must declare MUI2 GUI");

        EntityPlayerMP player = mock(EntityPlayerMP.class);
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        PosGuiData data = createMockGuiData(player, te);

        UISettings uiSettings = new UISettings(RecipeViewerSettings.DUMMY);
        ModularSyncManager syncManager = new ModularSyncManager(false);
        PanelSyncManager panelSyncManager = new PanelSyncManager(syncManager, true);

        ModularPanel panel = bus.buildUI(data, panelSyncManager, uiSettings);
        assertNotNull(panel, "Bus buildUI must return a non-null ModularPanel");
        WidgetTree.collectSyncValues(panelSyncManager, panel);

        com.cleanroommc.modularui.screen.ModularContainer container = new com.cleanroommc.modularui.screen.ModularContainer();
        container.construct(player, syncManager, uiSettings, panel.getName(), data);
        assertNotNull(container);
    }

    @Test
    void testNuclearHatchBuildUI() {
        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch", 1, 16000, new String[0], null);
        BaseMetaTileEntity te = createMockTileEntity();
        hatch.setBaseMetaTileEntity(te);

        assertTrue(hatch.hasMui2Gui(), "Nuclear hatch must declare MUI2 GUI");

        EntityPlayerMP player = mock(EntityPlayerMP.class);
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        PosGuiData data = createMockGuiData(player, te);

        UISettings uiSettings = new UISettings(RecipeViewerSettings.DUMMY);
        ModularSyncManager syncManager = new ModularSyncManager(false);
        PanelSyncManager panelSyncManager = new PanelSyncManager(syncManager, true);

        ModularPanel panel = hatch.buildUI(data, panelSyncManager, uiSettings);
        assertNotNull(panel, "Hatch buildUI must return a non-null ModularPanel");
        WidgetTree.collectSyncValues(panelSyncManager, panel);

        com.cleanroommc.modularui.screen.ModularContainer container = new com.cleanroommc.modularui.screen.ModularContainer();
        container.construct(player, syncManager, uiSettings, panel.getName(), data);
        assertNotNull(container);
    }

    @Test
    void testNuclearControlRodBuildUI() {
        MTEHatchNuclearControlRod rod = new MTEHatchNuclearControlRod("test.rod", 1, new String[0], null);
        BaseMetaTileEntity te = createMockTileEntity();
        rod.setBaseMetaTileEntity(te);

        assertTrue(rod.hasMui2Gui(), "Control rod must declare MUI2 GUI");

        EntityPlayerMP player = mock(EntityPlayerMP.class);
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        PosGuiData data = createMockGuiData(player, te);

        UISettings uiSettings = new UISettings(RecipeViewerSettings.DUMMY);
        ModularSyncManager syncManager = new ModularSyncManager(false);
        PanelSyncManager panelSyncManager = new PanelSyncManager(syncManager, true);

        ModularPanel panel = rod.buildUI(data, panelSyncManager, uiSettings);
        assertNotNull(panel, "Control rod buildUI must return a non-null ModularPanel");
        WidgetTree.collectSyncValues(panelSyncManager, panel);

        com.cleanroommc.modularui.screen.ModularContainer container = new com.cleanroommc.modularui.screen.ModularContainer();
        container.construct(player, syncManager, uiSettings, panel.getName(), data);
        assertNotNull(container);
    }

    @Test
    void testNuclearHighPressureBuildUI() {
        MTEHatchNuclearHighPressure hp = new MTEHatchNuclearHighPressure("test.hp", 1, new String[0], null);
        BaseMetaTileEntity te = createMockTileEntity();
        hp.setBaseMetaTileEntity(te);

        assertTrue(hp.hasMui2Gui(), "High-pressure hatch must declare MUI2 GUI");

        EntityPlayerMP player = mock(EntityPlayerMP.class);
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        PosGuiData data = createMockGuiData(player, te);

        UISettings uiSettings = new UISettings(RecipeViewerSettings.DUMMY);
        ModularSyncManager syncManager = new ModularSyncManager(false);
        PanelSyncManager panelSyncManager = new PanelSyncManager(syncManager, true);

        ModularPanel panel = hp.buildUI(data, panelSyncManager, uiSettings);
        assertNotNull(panel, "High-pressure buildUI must return a non-null ModularPanel");
        WidgetTree.collectSyncValues(panelSyncManager, panel);

        com.cleanroommc.modularui.screen.ModularContainer container = new com.cleanroommc.modularui.screen.ModularContainer();
        container.construct(player, syncManager, uiSettings, panel.getName(), data);
        assertNotNull(container);
    }

    @Test
    void testNuclearHighPressureInputBuildUI() {
        MTEHatchNuclearHighPressureInput hpIn = new MTEHatchNuclearHighPressureInput("test.hp.in", 1, new String[0], null);
        BaseMetaTileEntity te = createMockTileEntity();
        hpIn.setBaseMetaTileEntity(te);

        assertTrue(hpIn.hasMui2Gui(), "High-pressure input hatch must declare MUI2 GUI");

        EntityPlayerMP player = mock(EntityPlayerMP.class);
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        PosGuiData data = createMockGuiData(player, te);

        UISettings uiSettings = new UISettings(RecipeViewerSettings.DUMMY);
        ModularSyncManager syncManager = new ModularSyncManager(false);
        PanelSyncManager panelSyncManager = new PanelSyncManager(syncManager, true);

        ModularPanel panel = hpIn.buildUI(data, panelSyncManager, uiSettings);
        assertNotNull(panel, "High-pressure input buildUI must return a non-null ModularPanel");
        WidgetTree.collectSyncValues(panelSyncManager, panel);

        com.cleanroommc.modularui.screen.ModularContainer container = new com.cleanroommc.modularui.screen.ModularContainer();
        container.construct(player, syncManager, uiSettings, panel.getName(), data);
        assertNotNull(container);
    }

    @Test
    void testNuclearHighPressureOutputBuildUI() {
        MTEHatchNuclearHighPressureOutput hpOut = new MTEHatchNuclearHighPressureOutput("test.hp.out", 1, new String[0], null);
        BaseMetaTileEntity te = createMockTileEntity();
        hpOut.setBaseMetaTileEntity(te);

        assertTrue(hpOut.hasMui2Gui(), "High-pressure output hatch must declare MUI2 GUI");

        EntityPlayerMP player = mock(EntityPlayerMP.class);
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        PosGuiData data = createMockGuiData(player, te);

        UISettings uiSettings = new UISettings(RecipeViewerSettings.DUMMY);
        ModularSyncManager syncManager = new ModularSyncManager(false);
        PanelSyncManager panelSyncManager = new PanelSyncManager(syncManager, true);

        ModularPanel panel = hpOut.buildUI(data, panelSyncManager, uiSettings);
        assertNotNull(panel, "High-pressure output buildUI must return a non-null ModularPanel");
        WidgetTree.collectSyncValues(panelSyncManager, panel);

        com.cleanroommc.modularui.screen.ModularContainer container = new com.cleanroommc.modularui.screen.ModularContainer();
        container.construct(player, syncManager, uiSettings, panel.getName(), data);
        assertNotNull(container);
    }
}
