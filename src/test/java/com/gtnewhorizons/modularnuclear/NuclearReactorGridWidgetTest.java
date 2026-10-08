package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.gui.NuclearReactorGridWidget;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.opencomputers.NuclearControlEnvironment;
import li.cil.oc.api.machine.Callback;
import com.gtnewhorizons.modularnuclear.common.nuclear.INuclearTile;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.textures.ModularNuclearTextures;
import com.gtnewhorizons.modularui.api.screen.ModularUIContext;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;

import gregtech.api.enums.Textures;
import gregtech.api.render.TextureFactory;
import io.netty.buffer.Unpooled;

public class NuclearReactorGridWidgetTest {

    @BeforeAll
    static void initEnvironment() {
        Thread.currentThread()
            .setName("Server thread");
        try {
            cpw.mods.fml.common.Loader mockLoader = org.mockito.Mockito.mock(cpw.mods.fml.common.Loader.class);
            org.mockito.Mockito.when(mockLoader.getCallableCrashInformation())
                .thenReturn(org.mockito.Mockito.mock(cpw.mods.fml.common.ICrashCallable.class));
            java.lang.reflect.Field f = cpw.mods.fml.common.Loader.class.getDeclaredField("instance");
            f.setAccessible(true);
            if (f.get(null) == null) {
                f.set(null, mockLoader);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }

        try {
            net.minecraft.init.Bootstrap.func_151354_b();
        } catch (Throwable t) {
            t.printStackTrace();
        }

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
    private ModularUIContext context;
    private ModularWindow window;

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

        context = mock(ModularUIContext.class);
        when(context.getPlayer()).thenReturn(player);

        window = mock(ModularWindow.class);
        when(window.getContext()).thenReturn(context);

        com.gtnewhorizons.modularui.api.widget.IWidgetParent parent = mock(
            com.gtnewhorizons.modularui.api.widget.IWidgetParent.class);
        widget.initialize(window, parent, 0);
    }

    @Test
    void testZoomCellSizes() {
        int[] sizes = widget.getZoomCellSizes(5);
        assertTrue(sizes.length >= 3);
        assertEquals(18, sizes[0]);

        int[] sizesLarge = widget.getZoomCellSizes(15);
        assertTrue(sizesLarge[0] <= 18);
        assertTrue(sizesLarge[sizesLarge.length - 1] >= 40);
    }

    @Test
    void testBusLeftClickInsertAndPickup() throws IOException {
        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.bus", 1, new String[0], null);
        reactor.mGrid[1][1] = new MTENuclearReactor.NuclearGridTile(reactor, bus, 1, 1);

        // Player holds 5 apples on cursor
        ItemStack held = new ItemStack(Items.apple, 5);
        player.inventory.setItemStack(held);

        // Send left click on (1, 1)
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        buf.writeInt(1); // gx
        buf.writeInt(1); // gy
        buf.writeInt(0); // button = left
        buf.writeBoolean(false); // isShift = false

        widget.readOnServer(NuclearReactorGridWidget.PACKET_SLOT_CLICK, buf);

        // Bus should receive 1 apple, cursor should have 4
        assertNotNull(bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT]);
        assertEquals(1, bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT].stackSize);
        assertEquals(Items.apple, bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT].getItem());
        assertNotNull(player.inventory.getItemStack());
        assertEquals(4, player.inventory.getItemStack().stackSize);

        // Now clear cursor and click again to pick up
        player.inventory.setItemStack(null);
        PacketBuffer buf2 = new PacketBuffer(Unpooled.buffer());
        buf2.writeInt(1);
        buf2.writeInt(1);
        buf2.writeInt(0);
        buf2.writeBoolean(false);

        widget.readOnServer(NuclearReactorGridWidget.PACKET_SLOT_CLICK, buf2);

        // Bus is empty, player cursor holds the 1 apple
        assertNull(bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT]);
        assertNotNull(player.inventory.getItemStack());
        assertEquals(1, player.inventory.getItemStack().stackSize);
        assertEquals(
            Items.apple,
            player.inventory.getItemStack()
                .getItem());
    }

    @Test
    void testBusShiftClickQuickMoveToInventory() throws IOException {
        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.bus", 1, new String[0], null);
        reactor.mGrid[0][0] = new MTENuclearReactor.NuclearGridTile(reactor, bus, 0, 0);

        bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = new ItemStack(Items.stick, 1);
        player.inventory.setItemStack(null);

        // Shift click with empty cursor
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeInt(0);
        buf.writeBoolean(true); // isShift = true

        widget.readOnServer(NuclearReactorGridWidget.PACKET_SLOT_CLICK, buf);

        // Bus should be empty
        assertNull(bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT]);
        // Player inventory should contain the stick
        boolean foundStick = false;
        for (ItemStack s : player.inventory.mainInventory) {
            if (s != null && s.getItem() == Items.stick) {
                foundStick = true;
                break;
            }
        }
        assertTrue(foundStick);
    }

    @Test
    void testBatchShiftInsert() throws IOException {
        MTEHatchNuclearBus bus0 = new MTEHatchNuclearBus("test.bus0", 1, new String[0], null);
        MTEHatchNuclearBus bus1 = new MTEHatchNuclearBus("test.bus1", 1, new String[0], null);
        MTEHatchNuclearBus bus2 = new MTEHatchNuclearBus("test.bus2", 1, new String[0], null);

        // In 3x3 grid, corners (0,0), (0,2), (2,0), (2,2) are null cells.
        // Visual row 0 (gy = 2): non-corner is gx = 1
        reactor.mGrid[1][2] = new MTENuclearReactor.NuclearGridTile(reactor, bus0, 1, 2);
        // Visual row 1 (gy = 1): non-corners are gx = 0, gx = 1
        reactor.mGrid[0][1] = new MTENuclearReactor.NuclearGridTile(reactor, bus1, 0, 1);
        reactor.mGrid[1][1] = new MTENuclearReactor.NuclearGridTile(reactor, bus2, 1, 1);

        // Cursor holds 2 items
        player.inventory.setItemStack(new ItemStack(Items.iron_ingot, 2));

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        buf.writeInt(0); // button

        widget.readOnServer(NuclearReactorGridWidget.PACKET_BATCH_SHIFT_INSERT, buf);

        // Visual top-left to bottom-right order:
        // First is (gx=1, gy=2), second is (gx=0, gy=1)
        assertNotNull(bus0.mInventory[MTEHatchNuclearBus.SLOT_INPUT]);
        assertEquals(1, bus0.mInventory[MTEHatchNuclearBus.SLOT_INPUT].stackSize);
        assertNotNull(bus1.mInventory[MTEHatchNuclearBus.SLOT_INPUT]);
        assertEquals(1, bus1.mInventory[MTEHatchNuclearBus.SLOT_INPUT].stackSize);

        // bus2 should remain empty because cursor only had 2 items
        assertNull(bus2.mInventory[MTEHatchNuclearBus.SLOT_INPUT]);

        // Cursor should now be empty (null)
        assertNull(player.inventory.getItemStack());
    }

    @Test
    void testDragStep() throws IOException {
        MTEHatchNuclearBus bus = new MTEHatchNuclearBus("test.bus", 1, new String[0], null);
        reactor.mGrid[1][1] = new MTENuclearReactor.NuclearGridTile(reactor, bus, 1, 1);

        player.inventory.setItemStack(new ItemStack(Items.gold_ingot, 4));

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        buf.writeInt(1);
        buf.writeInt(1);
        buf.writeInt(0);

        widget.readOnServer(NuclearReactorGridWidget.PACKET_DRAG_STEP, buf);

        assertNotNull(bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT]);
        assertEquals(1, bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT].stackSize);
        assertEquals(Items.gold_ingot, bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT].getItem());
        assertEquals(3, player.inventory.getItemStack().stackSize);
    }

    @Test
    void testFluidHatchContainerClick() throws IOException {
        MTEHatchNuclearHatch hatch = new MTEHatchNuclearHatch("test.hatch", 1, 16000, new String[0], null);
        reactor.mGrid[1][1] = new MTENuclearReactor.NuclearGridTile(reactor, hatch, 1, 1);

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

        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        buf.writeInt(1);
        buf.writeInt(1);
        buf.writeInt(0);
        buf.writeBoolean(true); // shift-click

        widget.readOnServer(NuclearReactorGridWidget.PACKET_SLOT_CLICK, buf);
        assertEquals(5000, hatch.mInputFluid.amount);
    }

    @Test
    void testControlRodModeClickOnlyHandlesRodItemAndCannotAlterInsertion() throws IOException {
        MTEHatchNuclearControlRod rod = new MTEHatchNuclearControlRod("test.rod", 1, new String[0], null);
        MTENuclearReactor.NuclearGridTile tile = new MTENuclearReactor.NuclearGridTile(reactor, rod, 1, 1);
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
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        buf.writeInt(1); // gx
        buf.writeInt(1); // gy
        buf.writeInt(0); // button left click
        buf.writeBoolean(false); // isShift

        widget.readOnServer(NuclearReactorGridWidget.PACKET_SLOT_CLICK, buf);

        // Rod item was placed into the hatch
        assertNotNull(rod.mInventory[MTEHatchNuclearControlRod.SLOT_ROD]);
        // But insertion percent must REMAIN 0% because GUI clicking never alters insertion!
        assertEquals(0, rod.getInsertionPercent());

        // Now click again to take it out
        buf = new PacketBuffer(Unpooled.buffer());
        buf.writeInt(1);
        buf.writeInt(1);
        buf.writeInt(0);
        buf.writeBoolean(false);

        widget.readOnServer(NuclearReactorGridWidget.PACKET_SLOT_CLICK, buf);

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
                assertTrue(name.startsWith("get"),
                    "All OpenComputers reactor callbacks must be strictly read-only getters! Found forbidden method: " + name);
                assertFalse(name.toLowerCase().contains("set"), "Reactor OpenComputers API must never contain setters: " + name);
                assertFalse(name.toLowerCase().contains("scram"), "Reactor OpenComputers API must never contain SCRAM controls: " + name);
                assertFalse(name.toLowerCase().contains("rod"), "Reactor OpenComputers API must never contain control rod controls: " + name);
                assertFalse(name.toLowerCase().contains("insert"), "Reactor OpenComputers API must never contain insertion controls: " + name);
            }
        }
        assertTrue(callbackCount > 0, "NuclearControlEnvironment must define callbacks");
    }
}
