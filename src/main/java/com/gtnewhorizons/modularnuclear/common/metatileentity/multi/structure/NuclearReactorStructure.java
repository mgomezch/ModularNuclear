package com.gtnewhorizons.modularnuclear.common.metatileentity.multi.structure;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.enums.HatchElement.Maintenance;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;
import static gregtech.api.util.GTStructureUtility.chainItemPipeCasings;

import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.StructureLibAPI;
import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureElement.BlocksToPlace;
import com.gtnewhorizon.structurelib.structure.IStructureElement.PlaceResult;
import com.gtnewhorizon.structurelib.structure.IStructureElementNoPlacement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizon.structurelib.util.ItemStackPredicate;
import com.gtnewhorizons.modularnuclear.common.block.ModBlocks;
import com.gtnewhorizons.modularnuclear.common.metatileentity.ModMetaTileEntities;
import com.gtnewhorizons.modularnuclear.common.metatileentity.NuclearStructureChannels;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHighPressure;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.interfaces.tileentity.ITurnable;
import gregtech.common.blocks.ItemMachines;

public class NuclearReactorStructure {

    public static final String STRUCTURE_TIER_1 = "tier_1";
    public static final String STRUCTURE_TIER_2 = "tier_2";
    public static final String STRUCTURE_TIER_3 = "tier_3";

    public static final String[][] SHAPE_TIER_1 = new String[][] {
        // Slice 0 (Top - Nuclear Hatches & Casings)
        { " ccccc ", "ccgggcc", "cgggggc", "cgggggc", "cgggggc", "ccgggcc", " ccccc " },
        // Slice 1 (Second from top - Item Pipe Casings & Casings)
        { " ccccc ", "ccpppcc", "cpppppc", "cpppppc", "cpppppc", "ccpppcc", " ccccc " },
        // Slice 2 (Third from top - Nuclear Casing Walls, Hollow Interior)
        { " ccccc ", "cc---cc", "c-----c", "c-----c", "c-----c", "cc---cc", " ccccc " },
        // Slice 3 (Fourth from top / Controller layer - Front Center)
        { " cc~cc ", "cc---cc", "c-----c", "c-----c", "c-----c", "cc---cc", " ccccc " },
        // Slice 4 (Bottom - Nuclear Core Bottom Casings / HP Hatches & Perimeter Casings)
        { " ccccc ", "ccbbbcc", "cbbbbbc", "cbbbbbc", "cbbbbbc", "ccbbbcc", " ccccc " } };

    public static final String[][] SHAPE_TIER_2 = new String[][] {
        // Slice 0 (Top - Nuclear Hatches & Casings)
        { "  ccccccc  ", " ccgggggcc ", "ccgggggggcc", "cgggggggggc", "cgggggggggc", "cgggggggggc", "cgggggggggc",
            "cgggggggggc", "ccgggggggcc", " ccgggggcc ", "  ccccccc  " },
        // Slice 1 (Second from top - Item Pipe Casings & Casings)
        { "  ccccccc  ", " ccpppppcc ", "ccpppppppcc", "cpppppppppc", "cpppppppppc", "cpppppppppc", "cpppppppppc",
            "cpppppppppc", "ccpppppppcc", " ccpppppcc ", "  ccccccc  " },
        // Slice 2 (Third from top - Nuclear Casing Walls, Hollow Interior)
        { "  ccccccc  ", " cc-----cc ", "cc-------cc", "c---------c", "c---------c", "c---------c", "c---------c",
            "c---------c", "cc-------cc", " cc-----cc ", "  ccccccc  " },
        // Slice 3 (Fourth from top / Controller layer - Front Center)
        { "  ccc~ccc  ", " cc-----cc ", "cc-------cc", "c---------c", "c---------c", "c---------c", "c---------c",
            "c---------c", "cc-------cc", " cc-----cc ", "  ccccccc  " },
        // Slice 4 (Bottom - Nuclear Core Bottom Casings / HP Hatches & Perimeter Casings)
        { "  ccccccc  ", " ccbbbbbcc ", "ccbbbbbbbcc", "cbbbbbbbbbc", "cbbbbbbbbbc", "cbbbbbbbbbc", "cbbbbbbbbbc",
            "cbbbbbbbbbc", "ccbbbbbbbcc", " ccbbbbbcc ", "  ccccccc  " } };

    public static final String[][] SHAPE_TIER_3 = new String[][] {
        // Slice 0 (Top - Nuclear Hatches & Casings)
        { "   ccccccccc   ", "  ccgggggggcc  ", " ccgggggggggcc ", "ccgggggggggggcc", "cgggggggggggggc",
            "cgggggggggggggc", "cgggggggggggggc", "cgggggggggggggc", "cgggggggggggggc", "cgggggggggggggc",
            "cgggggggggggggc", "ccgggggggggggcc", " ccgggggggggcc ", "  ccgggggggcc  ", "   ccccccccc   " },
        // Slice 1 (Second from top - Item Pipe Casings & Casings)
        { "   ccccccccc   ", "  ccpppppppcc  ", " ccpppppppppcc ", "ccpppppppppppcc", "cpppppppppppppc",
            "cpppppppppppppc", "cpppppppppppppc", "cpppppppppppppc", "cpppppppppppppc", "cpppppppppppppc",
            "cpppppppppppppc", "ccpppppppppppcc", " ccpppppppppcc ", "  ccpppppppcc  ", "   ccccccccc   " },
        // Slice 2 (Third from top - Nuclear Casing Walls, Hollow Interior)
        { "   ccccccccc   ", "  cc-------cc  ", " cc---------cc ", "cc-----------cc", "c-------------c",
            "c-------------c", "c-------------c", "c-------------c", "c-------------c", "c-------------c",
            "c-------------c", "cc-----------cc", " cc---------cc ", "  cc-------cc  ", "   ccccccccc   " },
        // Slice 3 (Fourth from top / Controller layer - Front Center)
        { "   cccc~cccc   ", "  cc-------cc  ", " cc---------cc ", "cc-----------cc", "c-------------c",
            "c-------------c", "c-------------c", "c-------------c", "c-------------c", "c-------------c",
            "c-------------c", "cc-----------cc", " cc---------cc ", "  cc-------cc  ", "   ccccccccc   " },
        // Slice 4 (Bottom - Nuclear Core Bottom Casings / HP Hatches & Perimeter Casings)
        { "   ccccccccc   ", "  ccbbbbbbbcc  ", " ccbbbbbbbbbcc ", "ccbbbbbbbbbbbcc", "cbbbbbbbbbbbbbc",
            "cbbbbbbbbbbbbbc", "cbbbbbbbbbbbbbc", "cbbbbbbbbbbbbbc", "cbbbbbbbbbbbbbc", "cbbbbbbbbbbbbbc",
            "cbbbbbbbbbbbbbc", "ccbbbbbbbbbbbcc", " ccbbbbbbbbbcc ", "  ccbbbbbbbcc  ", "   ccccccccc   " } };

    private static IStructureDefinition<MTENuclearReactor> STRUCTURE_DEFINITION = null;

    public static IStructureDefinition<MTENuclearReactor> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTENuclearReactor>builder()
                // Tier 1: 7x7 Footprint, 5x5 Octagonal Chamber (21 cells), Height 5
                .addShape(STRUCTURE_TIER_1, transpose(SHAPE_TIER_1))
                // Tier 2: 11x11 Footprint, 9x9 Octagonal Chamber (69 cells), Height 5
                .addShape(STRUCTURE_TIER_2, transpose(SHAPE_TIER_2))
                // Tier 3: 15x15 Footprint, 13x13 Octagonal Chamber (145 cells), Height 5
                .addShape(STRUCTURE_TIER_3, transpose(SHAPE_TIER_3))
                .addElement(
                    'c',
                    buildHatchAdder(MTENuclearReactor.class).atLeast(Maintenance)
                        .adder(
                            (t, te, index) -> t.addMaintenanceToMachineList(te, index)
                                || t.addOutputToMachineList(te, index)
                                || t.addDynamoToMachineList(te, index)
                                || t.addExoticDynamoToMachineList(te, index)
                                || t.addNuclearControlHatchToMachineList(te, index))
                        .casingIndex(MTENuclearReactor.CASING_INDEX)
                        .hint(1)
                        .buildAndChain(
                            StructureUtility
                                .onElementPass(t -> t.mCasing++, StructureUtility.ofBlock(ModBlocks.nuclearCasing, 0))))
                .addElement('p', chainItemPipeCasings(-1, (t, casingTier) -> {
                    if (casingTier < 3) {
                        t.mPipeTier = -1;
                    } else {
                        t.mPipeTier = casingTier - 3;
                    }
                }, t -> t.mPipeTier == -1 ? -1 : t.mPipeTier + 3))
                .addElement('g', NuclearStructureChannels.NUCLEAR_HATCH.use(new NuclearHatchElement()))
                .addElement('b', new BottomCoreElement())
                .addElement('-', new HollowChamberElement())
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    public static ItemStack getNuclearHatchStack(int tier) {
        if (tier == 0 && ModMetaTileEntities.nuclearBus != null) {
            return ModMetaTileEntities.nuclearBus.copy();
        }
        if (tier >= 1 && tier <= 9 && ModMetaTileEntities.nuclearHatches[tier - 1] != null) {
            return ModMetaTileEntities.nuclearHatches[tier - 1].copy();
        }
        if (tier == 10 && ModMetaTileEntities.nuclearHighPressureHatch != null) {
            return ModMetaTileEntities.nuclearHighPressureHatch.copy();
        }
        return null;
    }

    public static boolean isNuclearCoreHatch(IMetaTileEntity mte) {
        return mte instanceof MTEHatchNuclearHatch || mte instanceof MTEHatchNuclearBus
            || mte instanceof MTEHatchNuclearHighPressure;
    }

    public static class NuclearHatchElement implements IStructureElement<MTENuclearReactor> {

        public NuclearHatchElement() {}

        @Override
        public boolean check(MTENuclearReactor t, World world, int x, int y, int z) {
            if (world.getTileEntity(x, y, z) instanceof IGregTechTileEntity te) {
                ForgeDirection requiredFacing = t != null ? t.getNuclearHatchFacing() : ForgeDirection.UP;
                if (te.getFrontFacing() != requiredFacing) {
                    return false;
                }
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearHatch hatch) {
                    hatch.mReactor = t;
                    int tier = hatch.mTier;
                    if (t.mHatchTier == -1 || tier < t.mHatchTier) {
                        t.mHatchTier = tier;
                    }
                    t.mNuclearTiles.add(te);
                    return true;
                } else if (mte instanceof MTEHatchNuclearBus bus) {
                    bus.mReactor = t;
                    t.mNuclearTiles.add(te);
                    return true;
                } else if (mte instanceof MTEHatchNuclearHighPressure hp) {
                    hp.mReactor = t;
                    hp.setIsInlet(true);
                    t.mNuclearTiles.add(te);
                    t.mTopHighPressureHatches.add(hp);
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean couldBeValid(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            if (world.getTileEntity(x, y, z) instanceof IGregTechTileEntity te) {
                IMetaTileEntity mte = te.getMetaTileEntity();
                return mte instanceof MTEHatchNuclearHatch || mte instanceof MTEHatchNuclearBus
                    || mte instanceof MTEHatchNuclearHighPressure;
            }
            return world.getBlock(x, y, z) == GregTechAPI.sBlockMachines;
        }

        @Override
        public List<String> getDescription(MTENuclearReactor t) {
            return Collections.singletonList("Top-layer hatches must face opposite to pipe casings");
        }

        @Override
        public boolean spawnHint(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            StructureLibAPI.hintParticle(world, x, y, z, GregTechAPI.sBlockMachines, 0);
            return true;
        }

        @Override
        public boolean placeBlock(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            ForgeDirection targetFacing = t != null ? t.getNuclearHatchFacing() : ForgeDirection.UP;
            TileEntity existingTe = world.getTileEntity(x, y, z);
            if (existingTe instanceof IGregTechTileEntity gte && isNuclearCoreHatch(gte.getMetaTileEntity())) {
                gte.setFrontFacing(targetFacing);
                return true;
            }

            int tier = NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger, 0, 10);
            ItemStack stack = getNuclearHatchStack(tier);
            if (stack == null) return false;
            if (stack.getItem() instanceof ItemMachines itemMachines) {
                boolean success = itemMachines
                    .placeBlockAt(stack, null, world, x, y, z, targetFacing.ordinal(), 0.5f, 0.5f, 0.5f, 0);
                if (success && world.getTileEntity(x, y, z) instanceof ITurnable turnable) {
                    turnable.setFrontFacing(targetFacing);
                }
                return success;
            }
            return false;
        }

        @Override
        public PlaceResult survivalPlaceBlock(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            ForgeDirection targetFacing = t != null ? t.getNuclearHatchFacing() : ForgeDirection.UP;
            TileEntity existingTe = world.getTileEntity(x, y, z);
            if (existingTe instanceof IGregTechTileEntity gte && isNuclearCoreHatch(gte.getMetaTileEntity())) {
                if (gte.getFrontFacing() != targetFacing) {
                    gte.setFrontFacing(targetFacing);
                    return PlaceResult.ACCEPT;
                }
                return PlaceResult.SKIP;
            }

            if (check(t, world, x, y, z)) return PlaceResult.SKIP;
            if (!StructureLibAPI.isBlockTriviallyReplaceable(world, x, y, z, env.getActor())) {
                return PlaceResult.REJECT;
            }
            int tier = NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger, 0, 10);
            ItemStack stack = getNuclearHatchStack(tier);
            if (stack == null) return PlaceResult.REJECT;

            PlaceResult result = StructureUtility.survivalPlaceBlock(
                stack,
                ItemStackPredicate.NBTMode.EXACT,
                null,
                false,
                world,
                x,
                y,
                z,
                env.getSource(),
                env.getActor(),
                env.getChatter());
            if (result == PlaceResult.ACCEPT && world.getTileEntity(x, y, z) instanceof ITurnable turnable) {
                turnable.setFrontFacing(targetFacing);
            }
            return result;
        }

        @Override
        public BlocksToPlace getBlocksToPlace(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            int tier = NuclearStructureChannels.NUCLEAR_HATCH.getValueClamped(trigger, 0, 10);
            ItemStack stack = getNuclearHatchStack(tier);
            return stack != null ? BlocksToPlace.create(stack) : BlocksToPlace.createEmpty();
        }
    }

    public static class BottomCoreElement implements IStructureElement<MTENuclearReactor> {

        @Override
        public boolean check(MTENuclearReactor t, World world, int x, int y, int z) {
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);
            if (block == ModBlocks.nuclearCasing && meta == 0) {
                if (t != null) {
                    t.mCasing++;
                }
                return true;
            }
            if (world.getTileEntity(x, y, z) instanceof IGregTechTileEntity te) {
                ForgeDirection requiredFacing = (t != null ? t.getNuclearHatchFacing() : ForgeDirection.UP)
                    .getOpposite();
                if (te.getFrontFacing() != requiredFacing) {
                    return false;
                }
                IMetaTileEntity mte = te.getMetaTileEntity();
                if (mte instanceof MTEHatchNuclearHighPressure hpHatch) {
                    if (t != null) {
                        hpHatch.mReactor = t;
                        hpHatch.setIsInlet(false);
                        t.mBottomHighPressureHatches.add(hpHatch);
                    }
                    return true;
                } else if (mte instanceof MTEHatchNuclearControlRod rod) {
                    if (t != null) {
                        rod.mReactor = t;
                        t.mBottomControlRodHatches.add(rod);
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean couldBeValid(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            Block block = world.getBlock(x, y, z);
            int meta = world.getBlockMetadata(x, y, z);
            if (block == ModBlocks.nuclearCasing && meta == 0) {
                return true;
            }
            if (world.getTileEntity(x, y, z) instanceof IGregTechTileEntity te) {
                return te.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure
                    || te.getMetaTileEntity() instanceof MTEHatchNuclearControlRod;
            }
            return block == GregTechAPI.sBlockMachines;
        }

        @Override
        public List<String> getDescription(MTENuclearReactor t) {
            return Collections.singletonList(
                "Opposite core face: nuclear casing, nuclear core high-pressure hatch, or nuclear core control rod");
        }

        @Override
        public boolean spawnHint(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            StructureLibAPI.hintParticle(world, x, y, z, ModBlocks.nuclearCasing, 0);
            return true;
        }

        @Override
        public boolean placeBlock(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            ForgeDirection targetFacing = (t != null ? t.getNuclearHatchFacing() : ForgeDirection.UP).getOpposite();
            TileEntity existingTe = world.getTileEntity(x, y, z);
            if (existingTe instanceof IGregTechTileEntity gte
                && (gte.getMetaTileEntity() instanceof MTEHatchNuclearHighPressure
                    || gte.getMetaTileEntity() instanceof MTEHatchNuclearControlRod)) {
                gte.setFrontFacing(targetFacing);
                return true;
            }
            return world.setBlock(x, y, z, ModBlocks.nuclearCasing, 0, 3);
        }

        @Override
        public PlaceResult survivalPlaceBlock(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            if (check(t, world, x, y, z)) return PlaceResult.SKIP;
            if (!StructureLibAPI.isBlockTriviallyReplaceable(world, x, y, z, env.getActor())) {
                return PlaceResult.REJECT;
            }
            ItemStack casingStack = new ItemStack(ModBlocks.nuclearCasing, 1, 0);
            return StructureUtility.survivalPlaceBlock(
                casingStack,
                ItemStackPredicate.NBTMode.EXACT,
                null,
                false,
                world,
                x,
                y,
                z,
                env.getSource(),
                env.getActor(),
                env.getChatter());
        }

        @Override
        public BlocksToPlace getBlocksToPlace(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger,
            AutoPlaceEnvironment env) {
            return BlocksToPlace.create(new ItemStack(ModBlocks.nuclearCasing, 1, 0));
        }
    }

    public static class HollowChamberElement implements IStructureElementNoPlacement<MTENuclearReactor> {

        @Override
        public boolean check(MTENuclearReactor t, World world, int x, int y, int z) {
            // Hollow internal cavity: accepts air, water, or any other block/fluid
            return true;
        }

        @Override
        public boolean couldBeValid(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            return true;
        }

        @Override
        public boolean spawnHint(MTENuclearReactor t, World world, int x, int y, int z, ItemStack trigger) {
            return true;
        }

        @Override
        public List<String> getDescription(MTENuclearReactor context) {
            return Collections.singletonList("GT5U.structure.empty");
        }
    }
}
