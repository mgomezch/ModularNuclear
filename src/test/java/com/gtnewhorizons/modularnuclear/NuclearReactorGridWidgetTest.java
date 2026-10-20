package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cleanroommc.modularui.utils.item.PlayerInvWrapper;
import com.cleanroommc.modularui.widgets.slot.ModularSlot;
import com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.gui.NuclearReactorContainer;
import com.gtnewhorizons.modularnuclear.common.gui.NuclearReactorGridWidget;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.NuclearGridTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.opencomputers.NuclearControlEnvironment;
import com.gtnewhorizons.modularnuclear.common.textures.ModularNuclearTextures;

import gregtech.api.enums.Textures;
import gregtech.api.render.TextureFactory;
import li.cil.oc.api.machine.Callback;

public class NuclearReactorGridWidgetTest {

    @BeforeAll
    static void initEnvironment() {
        TestEnvironmentHelper.init();

        try {
            ModularNuclearTextures.init();
            Textures.BlockIcons.setCasingTexture(
                (byte) BlockNuclearCasing.CASING_PAGE,
                (byte) (BlockNuclearCasing.CASING_ID + 112),
                TextureFactory.of(ModularNuclearTextures.MACHINE_CASING_NUCLEAR));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private MTENuclearReactor reactor;
    private NuclearReactorGridWidget widget;
    private EntityPlayerMP player;

    @BeforeEach
    void setUp() {
        Thread.currentThread()
            .setName("Server thread");
        NuclearSimulationEngine.resetDefaultParameters();

        reactor = new MTENuclearReactor("test.widget.reactor");
        reactor.gridSize = 3;
        reactor.mGrid = new INuclearTile[3][3];

        widget = new NuclearReactorGridWidget(reactor);

        player = mock(EntityPlayerMP.class);
        player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
        player.openContainer = new net.minecraft.inventory.Container() {

            @Override
            public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                return true;
            }
        };
    }

    @Test
    void testZoomControls() {
        widget.resetZoom();
        assertEquals(100, widget.getZoomPercent());
        widget.zoomIn();
        assertTrue(widget.getZoomPercent() > 100);
        widget.zoomOut();
        assertEquals(100, widget.getZoomPercent());
    }

    private MTEHatchNuclearBus createTestBus(String name) {
        return new MTEHatchNuclearBus(name, 1, new String[0], null);
    }

    private static class DummyFuelRodItem extends net.minecraft.item.Item {

        public DummyFuelRodItem() {
            this.maxStackSize = 64;
            setUnlocalizedName("gregtech:gt.reactorUraniumSimple");
        }

        @Override
        public String getUnlocalizedName() {
            return "gregtech:gt.reactorUraniumSimple";
        }

        @Override
        public String getUnlocalizedName(ItemStack stack) {
            return "gregtech:gt.reactorUraniumSimple";
        }

        @Override
        public int getItemStackLimit() {
            return 64;
        }

        @Override
        public int getItemStackLimit(ItemStack stack) {
            return 64;
        }
    }

    private static class DummyEmptyCellItem extends net.minecraft.item.Item {

        public DummyEmptyCellItem() {
            this.maxStackSize = 64;
            setUnlocalizedName("ic2.itemCellEmpty");
        }

        @Override
        public String getUnlocalizedName() {
            return "ic2.itemCellEmpty";
        }

        @Override
        public String getUnlocalizedName(ItemStack stack) {
            return "ic2.itemCellEmpty";
        }

        @Override
        public int getItemStackLimit() {
            return 64;
        }

        @Override
        public int getItemStackLimit(ItemStack stack) {
            return 64;
        }
    }

    private static final DummyFuelRodItem DUMMY_FUEL_ROD = new DummyFuelRodItem();
    private static final DummyEmptyCellItem DUMMY_EMPTY_CELL = new DummyEmptyCellItem();

    private ItemStack createValidFuelRod(int count) {
        return new ItemStack(DUMMY_FUEL_ROD, count, 0);
    }

    private ItemStack createEmptyCell(int count) {
        return new ItemStack(DUMMY_EMPTY_CELL, count, 0);
    }

    @Test
    void testBusLeftClickInsertAndPickup() {
        MTEHatchNuclearBus bus = createTestBus("test.bus");
        reactor.mGrid[1][1] = new NuclearGridTile(reactor, bus, 1, 1);

        // Player holds 5 valid fuel rods on cursor
        ItemStack held = createValidFuelRod(5);
        player.inventory.setItemStack(held);

        // Send left click on (1, 1)
        NuclearReactorGridWidget.handleSlotClickServer(reactor, player, 1, 1, 0, false);

        // Bus should receive 1 fuel rod, cursor should have 4
        assertNotNull(bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT));
        assertEquals(1, bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT).stackSize);
        assertNotNull(player.inventory.getItemStack());
        assertEquals(4, player.inventory.getItemStack().stackSize);

        // Now clear cursor and click again to pick up
        player.inventory.setItemStack(null);
        NuclearReactorGridWidget.handleSlotClickServer(reactor, player, 1, 1, 0, false);

        // Bus is empty, player cursor holds the 1 fuel rod
        assertNull(bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT));
        assertNotNull(player.inventory.getItemStack());
        assertEquals(1, player.inventory.getItemStack().stackSize);
    }

    @Test
    void testBusRejectsDisallowedItems() {
        MTEHatchNuclearBus bus = createTestBus("test.bus");
        reactor.mGrid[1][1] = new NuclearGridTile(reactor, bus, 1, 1);

        // 1. Stone via GUI click
        player.inventory.setItemStack(new ItemStack(net.minecraft.init.Blocks.stone, 5));
        NuclearReactorGridWidget.handleSlotClickServer(reactor, player, 1, 1, 0, false);

        assertNull(bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT));
        assertNotNull(player.inventory.getItemStack());
        assertEquals(5, player.inventory.getItemStack().stackSize);

        // Direct setInventorySlotContents rejection with stone
        bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, new ItemStack(net.minecraft.init.Blocks.stone, 5));
        assertNull(
            bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT),
            "setInventorySlotContents must reject non-nuclear blocks");

        // IItemHandlerModifiable insertItem rejection with stone
        ItemStack remainingStone = bus.getInventoryHandler()
            .insertItem(MTEHatchNuclearBus.SLOT_INPUT, new ItemStack(net.minecraft.init.Blocks.stone, 5), false);
        assertNotNull(remainingStone);
        assertEquals(5, remainingStone.stackSize);
        assertNull(bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT));

        // 2. Empty fluid cell via GUI click
        ItemStack cells = createEmptyCell(4);
        player.inventory.setItemStack(cells);
        NuclearReactorGridWidget.handleSlotClickServer(reactor, player, 1, 1, 0, false);

        assertNull(
            bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT),
            "Core bus must strictly reject empty fluid cells");
        assertNotNull(player.inventory.getItemStack());
        assertEquals(4, player.inventory.getItemStack().stackSize);

        // Direct setInventorySlotContents rejection with fluid cells
        bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, createEmptyCell(4));
        assertNull(
            bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT),
            "setInventorySlotContents must reject fluid cells");

        // IItemHandlerModifiable insertItem rejection with fluid cells
        ItemStack remainingCells = bus.getInventoryHandler()
            .insertItem(MTEHatchNuclearBus.SLOT_INPUT, createEmptyCell(4), false);
        assertNotNull(remainingCells);
        assertEquals(4, remainingCells.stackSize);
        assertNull(bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT));
    }

    @Test
    void testBusStackSizeEnforcement() {
        MTEHatchNuclearBus bus = createTestBus("test.bus");
        reactor.mGrid[1][1] = new NuclearGridTile(reactor, bus, 1, 1);

        assertEquals(1, bus.getInventoryStackLimit());
        assertEquals(1, bus.getSlotLimit(0));
        assertEquals(1, bus.getStackSizeLimit(0, createValidFuelRod(1)));
        assertEquals(0, bus.getStackSizeLimit(0, new ItemStack(net.minecraft.init.Blocks.stone, 1)));
        assertEquals(0, bus.getStackSizeLimit(0, createEmptyCell(1)));

        // Direct setInventorySlotContents with stack > 1 must clamp to 1
        bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, createValidFuelRod(4));
        assertNotNull(bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT));
        assertEquals(
            1,
            bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT).stackSize,
            "Bus slot must never hold > 1 item");

        // Clear slot
        bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, null);

        // insertItem with stack of 4 must insert only 1 and return 3
        ItemStack remainder = bus.getInventoryHandler()
            .insertItem(MTEHatchNuclearBus.SLOT_INPUT, createValidFuelRod(4), false);
        assertNotNull(remainder);
        assertEquals(3, remainder.stackSize);
        assertEquals(1, bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT).stackSize);

        // Trying to insert another fuel rod into filled bus must reject completely
        ItemStack secondAttempt = bus.getInventoryHandler()
            .insertItem(MTEHatchNuclearBus.SLOT_INPUT, createValidFuelRod(1), false);
        assertNotNull(secondAttempt);
        assertEquals(1, secondAttempt.stackSize);
        assertEquals(1, bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT).stackSize);
    }

    @Test
    void testBusShiftClickQuickMoveToInventory() {
        MTEHatchNuclearBus bus = createTestBus("test.bus");
        reactor.mGrid[0][0] = new NuclearGridTile(reactor, bus, 0, 0);

        ItemStack fuelRod = createValidFuelRod(1);
        bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, fuelRod);
        player.inventory.setItemStack(null);

        // Shift click with empty cursor
        NuclearReactorGridWidget.handleSlotClickServer(reactor, player, 0, 0, 0, true);

        // Bus should be empty
        assertNull(bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT));
        // Player inventory should contain the fuel rod
        boolean foundRod = false;
        for (ItemStack s : player.inventory.mainInventory) {
            if (s != null && s.getItem() == fuelRod.getItem()) {
                foundRod = true;
                break;
            }
        }
        assertTrue(foundRod);
    }

    @Test
    void testBatchShiftInsert() {
        MTEHatchNuclearBus bus0 = createTestBus("test.bus0");
        MTEHatchNuclearBus bus1 = createTestBus("test.bus1");
        MTEHatchNuclearBus bus2 = createTestBus("test.bus2");

        // In 3x3 grid, corners (0,0), (0,2), (2,0), (2,2) are null cells.
        // Visual row 0 (gy = 2): non-corner is gx = 1
        reactor.mGrid[1][2] = new NuclearGridTile(reactor, bus0, 1, 2);
        // Visual row 1 (gy = 1): non-corners are gx = 0, gx = 1
        reactor.mGrid[0][1] = new NuclearGridTile(reactor, bus1, 0, 1);
        reactor.mGrid[1][1] = new NuclearGridTile(reactor, bus2, 1, 1);

        // Cursor holds 2 valid fuel rods
        player.inventory.setItemStack(createValidFuelRod(2));

        NuclearReactorGridWidget.handleBatchShiftInsertServer(reactor, player, 0);

        // Visual top-left to bottom-right order:
        // First is (gx=1, gy=2), second is (gx=0, gy=1)
        assertNotNull(bus0.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT));
        assertEquals(1, bus0.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT).stackSize);
        assertNotNull(bus1.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT));
        assertEquals(1, bus1.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT).stackSize);

        // bus2 should remain empty because cursor only had 2 items
        assertNull(bus2.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT));

        // Cursor should now be empty (null)
        assertNull(player.inventory.getItemStack());

        // Clear buses
        bus0.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, null);
        bus1.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, null);
        bus2.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, null);

        // Try batch shift insert with disallowed items (stone)
        player.inventory.setItemStack(new ItemStack(net.minecraft.init.Blocks.stone, 10));
        NuclearReactorGridWidget.handleBatchShiftInsertServer(reactor, player, 0);

        assertNull(bus0.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT), "Batch insert must reject stone");
        assertNull(bus1.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT), "Batch insert must reject stone");
        assertNull(bus2.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT), "Batch insert must reject stone");
        assertNotNull(player.inventory.getItemStack());
        assertEquals(10, player.inventory.getItemStack().stackSize);

        // Try batch shift insert with empty fluid cells
        player.inventory.setItemStack(createEmptyCell(10));
        NuclearReactorGridWidget.handleBatchShiftInsertServer(reactor, player, 0);

        assertNull(bus0.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT), "Batch insert must reject fluid cells");
        assertNull(bus1.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT), "Batch insert must reject fluid cells");
        assertNull(bus2.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT), "Batch insert must reject fluid cells");
        assertNotNull(player.inventory.getItemStack());
        assertEquals(10, player.inventory.getItemStack().stackSize);
    }

    @Test
    void testDragStep() {
        MTEHatchNuclearBus bus = createTestBus("test.bus");
        reactor.mGrid[1][1] = new NuclearGridTile(reactor, bus, 1, 1);

        player.inventory.setItemStack(createValidFuelRod(4));

        NuclearReactorGridWidget.handleDragStepServer(reactor, player, 1, 1, 0);

        assertNotNull(bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT));
        assertEquals(1, bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT).stackSize);
        assertEquals(3, player.inventory.getItemStack().stackSize);

        // Now test drag with disallowed item (stone)
        bus.setInventorySlotContents(MTEHatchNuclearBus.SLOT_INPUT, null);
        player.inventory.setItemStack(new ItemStack(net.minecraft.init.Blocks.stone, 4));

        NuclearReactorGridWidget.handleDragStepServer(reactor, player, 1, 1, 0);

        assertNull(bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT), "Bus must not accept stone via drag");
        assertEquals(4, player.inventory.getItemStack().stackSize);

        // Now test drag with fluid cells
        player.inventory.setItemStack(createEmptyCell(4));
        NuclearReactorGridWidget.handleDragStepServer(reactor, player, 1, 1, 0);

        assertNull(bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT), "Bus must not accept fluid cells via drag");
        assertEquals(4, player.inventory.getItemStack().stackSize);
    }

    @Test
    void testFluidHatchContainerClick() {
        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch", 1, 16000, new String[0], null);
        reactor.mGrid[1][1] = new NuclearGridTile(reactor, hatch, 1, 1);

        Fluid water = FluidRegistry.WATER;
        if (water == null) {
            water = new Fluid("water");
            FluidRegistry.registerFluid(water);
        }
        Fluid coolant = FluidRegistry.getFluid("ic2coolant");
        if (coolant == null) {
            coolant = new Fluid("ic2coolant");
            FluidRegistry.registerFluid(coolant);
        }

        hatch.mInputFluid = new FluidStack(coolant, 5000);
        assertEquals(5000, hatch.mInputFluid.amount);

        NuclearReactorGridWidget.handleSlotClickServer(reactor, player, 1, 1, 0, true);
        assertEquals(5000, hatch.mInputFluid.amount);
    }

    @Test
    void testControlRodModeClickOnlyHandlesRodItemAndCannotAlterInsertion() {
        MTEHatchNuclearControlRod rod = new MTEHatchNuclearControlRod("test.rod", 1, new String[0], null);
        NuclearGridTile tile = new NuclearGridTile(reactor, rod, 1, 1);
        reactor.mGrid[1][1] = tile;
        reactor.mCurrentGuiMode = MTENuclearReactor.GUI_MODE_CONTROL_RODS;

        // Initially no redstone signal, 0% insertion
        assertEquals(0, rod.getInsertionPercent());
        assertNull(rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD]);

        // Create a long rod item for Boron
        net.minecraft.item.Item testRodItem = new net.minecraft.item.Item().setUnlocalizedName("stickLongBoron");
        ItemStack boronRod = new ItemStack(testRodItem);
        player.inventory.setItemStack(boronRod);

        // Click on the control rod cell
        NuclearReactorGridWidget.handleSlotClickServer(reactor, player, 1, 1, 0, false);

        // Rod item was placed into the hatch
        assertNotNull(rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD]);
        // But insertion percent must REMAIN 0% because GUI clicking never alters insertion!
        assertEquals(0, rod.getInsertionPercent());

        // Now click again to take it out
        NuclearReactorGridWidget.handleSlotClickServer(reactor, player, 1, 1, 0, false);

        assertNull(rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD]);
        assertEquals(0, rod.getInsertionPercent());
    }

    @Test
    void testScramIsSingleGlobalButtonOverridingIndividualRedstone() {
        MTEHatchNuclearControlRod rodA = new MTEHatchNuclearControlRod("rodA", 1, new String[0], null) {

            @Override
            public byte getRedstoneSignal() {
                return 3; // 3/15 = 20%
            }
        };
        MTEHatchNuclearControlRod rodB = new MTEHatchNuclearControlRod("rodB", 1, new String[0], null) {

            @Override
            public byte getRedstoneSignal() {
                return 8; // 8/15 = 53%
            }
        };

        reactor.mBottomControlRodHatches.add(rodA);
        reactor.mBottomControlRodHatches.add(rodB);

        assertEquals(20, rodA.getInsertionPercent());
        assertEquals(53, rodB.getInsertionPercent());

        // Engage emergency SCRAM (single button on controller UI)
        reactor.setScram(true);
        assertTrue(reactor.mScram);

        // Both rods must be forced to 100% insertion
        assertEquals(100, rodA.getInsertionPercent());
        assertEquals(100, rodB.getInsertionPercent());
        assertEquals(1.0, rodA.getInsertionRatio());
        assertEquals(1.0, rodB.getInsertionRatio());

        // Disengage SCRAM
        reactor.setScram(false);
        assertFalse(reactor.mScram);

        // Both rods revert to their individual redstone signals
        assertEquals(20, rodA.getInsertionPercent());
        assertEquals(53, rodB.getInsertionPercent());
    }

    @Test
    void testOpenComputersApiIsStrictlyReadOnly() {
        // Enforce the architectural rule that all OpenComputers callbacks for the reactor are strictly read-only
        Method[] methods = NuclearControlEnvironment.class.getDeclaredMethods();
        int callbackCount = 0;
        for (Method m : methods) {
            Callback cb = m.getAnnotation(Callback.class);
            if (cb != null) {
                callbackCount++;
                String name = m.getName();
                assertTrue(
                    name.startsWith("get"),
                    "All OpenComputers reactor callbacks must be strictly read-only getters! Found forbidden method: "
                        + name);
                assertFalse(
                    name.toLowerCase()
                        .contains("set"),
                    "Reactor OpenComputers API must never contain setters: " + name);
                assertFalse(
                    name.toLowerCase()
                        .contains("scram"),
                    "Reactor OpenComputers API must never contain SCRAM controls: " + name);
                assertFalse(
                    name.toLowerCase()
                        .contains("rod"),
                    "Reactor OpenComputers API must never contain control rod controls: " + name);
                assertFalse(
                    name.toLowerCase()
                        .contains("insert"),
                    "Reactor OpenComputers API must never contain insertion controls: " + name);
            }
        }
        assertTrue(callbackCount > 0, "NuclearControlEnvironment must define callbacks");
    }

    @Test
    void testMixedNuclearHatchTiersAllowed() {
        MTENuclearReactor r = new MTENuclearReactor("test.nuclear.reactor");
        com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.NuclearReactorStructure.NuclearHatchElement element = new com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure.NuclearReactorStructure.NuclearHatchElement();

        MTEHatchNuclearHatch hatchLV = new MTEHatchNuclearHatch("hatch.lv", 1, 16000, new String[0], null);
        MTEHatchNuclearHatch hatchHV = new MTEHatchNuclearHatch("hatch.hv", 3, 64000, new String[0], null);
        MTEHatchNuclearHatch hatchIV = new MTEHatchNuclearHatch("hatch.iv", 5, 256000, new String[0], null);

        gregtech.api.metatileentity.BaseMetaTileEntity mockTeLV = mock(
            gregtech.api.metatileentity.BaseMetaTileEntity.class);
        when(mockTeLV.getFrontFacing()).thenReturn(net.minecraftforge.common.util.ForgeDirection.UP);
        when(mockTeLV.getMetaTileEntity()).thenReturn(hatchLV);

        gregtech.api.metatileentity.BaseMetaTileEntity mockTeHV = mock(
            gregtech.api.metatileentity.BaseMetaTileEntity.class);
        when(mockTeHV.getFrontFacing()).thenReturn(net.minecraftforge.common.util.ForgeDirection.UP);
        when(mockTeHV.getMetaTileEntity()).thenReturn(hatchHV);

        gregtech.api.metatileentity.BaseMetaTileEntity mockTeIV = mock(
            gregtech.api.metatileentity.BaseMetaTileEntity.class);
        when(mockTeIV.getFrontFacing()).thenReturn(net.minecraftforge.common.util.ForgeDirection.UP);
        when(mockTeIV.getMetaTileEntity()).thenReturn(hatchIV);

        net.minecraft.world.World mockWorld = mock(net.minecraft.world.World.class);
        when(mockWorld.getTileEntity(0, 0, 0)).thenReturn(mockTeLV);
        when(mockWorld.getTileEntity(1, 0, 0)).thenReturn(mockTeHV);
        when(mockWorld.getTileEntity(2, 0, 0)).thenReturn(mockTeIV);

        assertTrue(element.check(r, mockWorld, 0, 0, 0));
        assertTrue(element.check(r, mockWorld, 1, 0, 0));
        assertTrue(element.check(r, mockWorld, 2, 0, 0));

        assertFalse(
            r.mHatchTierInconsistent,
            "Mixed hatch tiers must be allowed without setting mHatchTierInconsistent");
        assertEquals(3, r.mNuclearTiles.size());
        assertEquals(1, r.mHatchTier, "mHatchTier should track lowest hatch tier encountered");
    }

    @Test
    void testContainerTransferStackInSlot() {
        NuclearReactorContainer container = new NuclearReactorContainer(reactor);
        MTEHatchNuclearBus bus = createTestBus("test.bus");
        reactor.mGrid[1][1] = new NuclearGridTile(reactor, bus, 1, 1);

        // 1. Shift-click valid fuel rods from player inventory slot
        ItemStack fuelRods = createValidFuelRod(4);
        player.inventory.mainInventory[0] = fuelRods;
        PlayerInvWrapper invWrapper = new PlayerInvWrapper(player.inventory);
        ModularSlot playerSlot = new ModularSlot(invWrapper, 0);
        container.inventorySlots.add(playerSlot);
        container.inventoryItemStacks.add(null);

        container.transferStackInSlot(player, 0);

        // Core bus must receive exactly 1 fuel rod
        assertNotNull(bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT));
        assertEquals(1, bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT).stackSize);
        // Player inventory slot has 3 left
        assertNotNull(player.inventory.mainInventory[0]);
        assertEquals(3, player.inventory.mainInventory[0].stackSize);

        // 2. Shift-click disallowed item (stone)
        player.inventory.mainInventory[1] = new ItemStack(net.minecraft.init.Blocks.stone, 10);
        ModularSlot stoneSlot = new ModularSlot(invWrapper, 1);
        container.inventorySlots.add(stoneSlot);
        container.inventoryItemStacks.add(null);

        container.transferStackInSlot(player, 1);
        // Stone must NOT have been moved to reactor
        assertEquals(10, player.inventory.mainInventory[1].stackSize);
        assertEquals(1, bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT).stackSize);

        // 3. Shift-click disallowed fluid cells
        player.inventory.mainInventory[2] = createEmptyCell(10);
        ModularSlot cellSlot = new ModularSlot(invWrapper, 2);
        container.inventorySlots.add(cellSlot);
        container.inventoryItemStacks.add(null);

        container.transferStackInSlot(player, 2);
        // Fluid cells must NOT have been moved to reactor
        assertEquals(10, player.inventory.mainInventory[2].stackSize);
        assertEquals(1, bus.getStackInSlot(MTEHatchNuclearBus.SLOT_INPUT).stackSize);
    }
}
