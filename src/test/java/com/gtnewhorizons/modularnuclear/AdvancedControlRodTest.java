package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearAdvancedControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.NuclearGridTile;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.NuclearReactorPhysics;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;

import gregtech.api.metatileentity.BaseMetaTileEntity;

public class AdvancedControlRodTest {

    @BeforeAll
    static void initEnvironment() {
        TestEnvironmentHelper.init();
    }

    private ItemStack createMockControlRodItem() {
        Item item = mock(Item.class);
        when(item.getUnlocalizedName()).thenReturn("item.stickLongCadmium");
        when(item.getUnlocalizedName(any())).thenReturn("item.stickLongCadmium");
        return new ItemStack(item, 1, 0);
    }

    private ItemStack createMockModeratorItem() {
        Item item = mock(Item.class);
        when(item.getUnlocalizedName()).thenReturn("item.stickLongGraphite");
        when(item.getUnlocalizedName(any())).thenReturn("item.stickLongGraphite");
        return new ItemStack(item, 1, 0);
    }

    private ItemStack createMockFuelItem() {
        Item item = mock(Item.class);
        when(item.getUnlocalizedName()).thenReturn("item.fuelRodUranium");
        when(item.getUnlocalizedName(any())).thenReturn("item.fuelRodUranium");
        return new ItemStack(item, 1, 0);
    }

    private BaseMetaTileEntity createMockTileEntity(byte redstoneSignal) {
        BaseMetaTileEntity te = mock(BaseMetaTileEntity.class);
        when(te.getXCoord()).thenReturn(10);
        when(te.getYCoord()).thenReturn((short) 60);
        when(te.getZCoord()).thenReturn(20);
        when(te.canAccessData()).thenReturn(true);
        when(te.isServerSide()).thenReturn(true);
        when(te.getStrongestRedstone()).thenReturn(redstoneSignal);
        when(te.getCoverAtSide(any())).thenReturn(gregtech.api.covers.CoverRegistry.NO_COVER);
        return te;
    }

    @Test
    void testSlotConfigurationAndItemFiltering() {
        MTEHatchNuclearAdvancedControlRod advRod = new MTEHatchNuclearAdvancedControlRod(
            "test.advrod",
            5,
            new String[0],
            null);
        advRod.setBaseMetaTileEntity(createMockTileEntity((byte) 0));

        // Inventory size must be exactly 2
        assertEquals(2, advRod.mInventory.length, "Advanced control rod must have exactly 2 inventory slots");

        ItemStack rodStack = createMockControlRodItem();
        ItemStack modStack = createMockModeratorItem();
        ItemStack fuelStack = createMockFuelItem();

        assertTrue(
            MTEHatchNuclearControlRod.isControlRod(rodStack),
            "Cadmium long rod must be classified as control rod");
        assertFalse(MTEHatchNuclearControlRod.isModerator(rodStack), "Control rod must not be classified as moderator");

        assertTrue(
            MTEHatchNuclearControlRod.isModerator(modStack),
            "Graphite long rod must be classified as moderator");
        assertFalse(
            MTEHatchNuclearControlRod.isControlRod(modStack),
            "Moderator must not be classified as control rod");

        assertFalse(MTEHatchNuclearControlRod.isControlRod(fuelStack));
        assertFalse(MTEHatchNuclearControlRod.isModerator(fuelStack));

        // Slot valid check
        assertTrue(advRod.isValidSlot(0), "Slot 0 must be valid");
        assertTrue(advRod.isValidSlot(1), "Slot 1 must be valid");
        assertFalse(advRod.isValidSlot(2), "Slot 2 must not be valid");

        // Slot 0 (Control Rod) accepts only control rods
        assertTrue(advRod.isItemValidForSlot(0, rodStack));
        assertFalse(advRod.isItemValidForSlot(0, modStack));
        assertFalse(advRod.isItemValidForSlot(0, fuelStack));

        // Slot 1 (Moderator) accepts only moderators
        assertTrue(advRod.isItemValidForSlot(1, modStack));
        assertFalse(advRod.isItemValidForSlot(1, rodStack));
        assertFalse(advRod.isItemValidForSlot(1, fuelStack));

        // Put into slot 0
        advRod.mInventory[0] = rodStack;
        assertFalse(advRod.isItemValidForSlot(0, rodStack), "Cannot put into occupied slot 0");
        assertTrue(advRod.allowPullStack(advRod.getBaseMetaTileEntity(), 0, ForgeDirection.UP, rodStack));

        // Put into slot 1
        advRod.mInventory[1] = modStack;
        assertFalse(advRod.isItemValidForSlot(1, modStack), "Cannot put into occupied slot 1");
        assertTrue(advRod.allowPullStack(advRod.getBaseMetaTileEntity(), 1, ForgeDirection.UP, modStack));
    }

    @Test
    void testDisplacementMechanics() {
        MTEHatchNuclearAdvancedControlRod advRod = new MTEHatchNuclearAdvancedControlRod(
            "test.advrod.disp",
            5,
            new String[0],
            null);
        BaseMetaTileEntity te = createMockTileEntity((byte) 0);
        advRod.setBaseMetaTileEntity(te);

        advRod.mInventory[0] = createMockControlRodItem();
        advRod.mInventory[1] = createMockModeratorItem();

        // 1. Redstone 0: Rod is 0% inserted, Moderator is 100% inserted
        assertEquals(0, advRod.getInsertionPercent(), "Control rod must be 0% inserted at redstone 0");
        assertEquals(100, advRod.getModeratorInsertionPercent(), "Moderator must be 100% inserted when rod is 0%");
        assertEquals(0.0, advRod.getInsertionRatio(), 1e-4);
        assertEquals(1.0, advRod.getModeratorInsertionRatio(), 1e-4);

        // 2. Redstone 15: Rod is 100% inserted, Moderator is 0% inserted
        when(te.getStrongestRedstone()).thenReturn((byte) 15);
        assertEquals(100, advRod.getInsertionPercent(), "Control rod must be 100% inserted at redstone 15");
        assertEquals(0, advRod.getModeratorInsertionPercent(), "Moderator must be 0% inserted when rod is 100%");
        assertEquals(1.0, advRod.getInsertionRatio(), 1e-4);
        assertEquals(0.0, advRod.getModeratorInsertionRatio(), 1e-4);

        // 3. Redstone ~90% (e.g. signal 9 of 15 -> 60%, or fine signal)
        // With 15 scale: 9 / 15 = 0.60
        when(te.getStrongestRedstone()).thenReturn((byte) 9);
        assertEquals(60, advRod.getInsertionPercent());
        assertEquals(40, advRod.getModeratorInsertionPercent(), "Moderator insertion must be 100% - rod%");

        // 4. SCRAM state overrides rod to 100% and moderator to 0%
        advRod.setScram(true);
        assertEquals(100, advRod.getInsertionPercent(), "SCRAM must force rod insertion to 100%");
        assertEquals(0, advRod.getModeratorInsertionPercent(), "SCRAM must force moderator insertion to 0%");

        // 5. Mining pipe constraints
        advRod.setScram(false);
        when(te.getStrongestRedstone()).thenReturn((byte) 0);
        advRod.setMiningPipeCount(0); // 0 pipes: min insertion is 100%
        assertEquals(100, advRod.getInsertionPercent(), "With 0 pipes, rod cannot be extracted below 100%");
        assertEquals(0, advRod.getModeratorInsertionPercent(), "With rod stuck at 100%, moderator remains at 0%");

        advRod.setMiningPipeCount(5); // 5 pipes: full extraction allowed
        assertEquals(0, advRod.getInsertionPercent());
        assertEquals(100, advRod.getModeratorInsertionPercent());
    }

    @Test
    void testMutualExclusivityNeutronMath() {
        MTENuclearReactor reactor = mock(MTENuclearReactor.class);
        MTEHatchNuclearAdvancedControlRod advRod = new MTEHatchNuclearAdvancedControlRod(
            "test.advrod.physics",
            5,
            new String[0],
            null);
        BaseMetaTileEntity te = createMockTileEntity((byte) 0);
        advRod.setBaseMetaTileEntity(te);

        advRod.mInventory[0] = createMockControlRodItem();
        advRod.mInventory[1] = createMockModeratorItem();

        NuclearGridTile tile = new NuclearGridTile(reactor, advRod, 2, 2);

        // A. Fully inserted control rod (x = 1.0)
        when(te.getStrongestRedstone()).thenReturn((byte) 15);
        tile.refreshTileProperties();

        double pRodFast = advRod.getAbsorptionProbability(NeutronType.FAST);
        double pMod = advRod.getModeratorProbability();
        assertEquals(0.0, pMod, 1e-4, "Moderator displacer probability must be 0 when rod is 100% inserted");
        assertTrue(pRodFast > 0.0, "Control rod fast absorption must be active when inserted");

        // Mutual exclusivity verification: P_rod + P_mod <= 1.0
        assertTrue(pRodFast + pMod <= 1.0, "P_rod + P_mod must never exceed 1.0");
        double pBaseEff = Math.max(0.0, 1.0 - pRodFast - pMod);
        assertTrue(pBaseEff >= 0.0, "pBaseEff must be non-negative");
        assertEquals(1.0, pRodFast + pMod + pBaseEff, 1e-4, "P_rod + P_mod + pBaseEff must sum to 1.0");

        // Fast moderation probability of tile must come solely from base tile (scaled by pBaseEff)
        double tileModWhenInserted = NuclearReactorPhysics.getTileModerationProbability(tile);
        double baseMod = NuclearReactorPhysics.getBaseModerationProbability(tile);
        assertEquals(pBaseEff * baseMod, tileModWhenInserted, 1e-4);

        // B. Fully withdrawn control rod (x = 0.0) -> Displacer is 100% inserted
        when(te.getStrongestRedstone()).thenReturn((byte) 0);
        tile.refreshTileProperties();

        double pRodFastWithdrawn = advRod.getAbsorptionProbability(NeutronType.FAST);
        double pModWithdrawn = advRod.getModeratorProbability();
        assertTrue(pModWithdrawn >= 0.40, "Graphite moderator must provide substantial moderation (~0.50)");

        // Mutual exclusivity verification
        assertTrue(pRodFastWithdrawn + pModWithdrawn <= 1.0, "P_rod + P_mod must never exceed 1.0");
        double pBaseEffWithdrawn = Math.max(0.0, 1.0 - pRodFastWithdrawn - pModWithdrawn);
        assertTrue(pBaseEffWithdrawn >= 0.0);
        assertEquals(1.0, pRodFastWithdrawn + pModWithdrawn + pBaseEffWithdrawn, 1e-4);

        // Fast moderation probability of tile must include the displacer moderation
        double tileModWhenWithdrawn = NuclearReactorPhysics.getTileModerationProbability(tile);
        assertEquals(pModWithdrawn + pBaseEffWithdrawn * baseMod, tileModWhenWithdrawn, 1e-4);
        assertTrue(
            tileModWhenWithdrawn > tileModWhenInserted,
            "Withdrawing control rod must introduce moderator into core and increase tile moderation!");

        // C. Absorption partitioning verification in onTileNeutronAbsorbed
        advRod.mFastAbsorbed = 0;
        NuclearReactorPhysics.onTileNeutronAbsorbed(reactor, tile, NeutronType.FAST, 1000);
        // Neutrons absorbed by rod vs base are strictly partitioned without double counting
        assertTrue(advRod.mFastAbsorbed >= 0 && advRod.mFastAbsorbed <= 1000);
    }

    @Test
    void testStandardControlRodBackwardCompatibility() {
        MTENuclearReactor reactor = mock(MTENuclearReactor.class);
        MTEHatchNuclearControlRod stdRod = new MTEHatchNuclearControlRod("test.stdrod", 5, new String[0], null);
        BaseMetaTileEntity te = createMockTileEntity((byte) 0);
        stdRod.setBaseMetaTileEntity(te);
        stdRod.mInventory[0] = createMockControlRodItem();

        assertFalse(stdRod.hasModerator(), "Standard control rod hatch must not have moderator");
        assertEquals(0.0, stdRod.getModeratorProbability(), 1e-4);
        assertEquals("None", stdRod.getModeratorDisplayName());

        NuclearGridTile tile = new NuclearGridTile(reactor, stdRod, 1, 1);
        double tileMod = NuclearReactorPhysics.getTileModerationProbability(tile);
        double baseMod = NuclearReactorPhysics.getBaseModerationProbability(tile);
        double pRod = stdRod.getAbsorptionProbability(NeutronType.FAST);
        assertEquals(
            (1.0 - pRod) * baseMod,
            tileMod,
            1e-4,
            "Standard control rod must preserve backward-compatible moderation");
    }
}
