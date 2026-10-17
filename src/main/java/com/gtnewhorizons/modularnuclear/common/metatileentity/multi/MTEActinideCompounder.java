package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureUtility;
import com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.block.ModBlocks;
import com.gtnewhorizons.modularnuclear.common.item.ModItems;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;
import com.gtnewhorizons.modularnuclear.common.textures.ModularNuclearTextures;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchMaintenance;
import gregtech.api.metatileentity.implementations.MTEHatchOutput;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.objects.ItemData;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTStructureUtility;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * Actinide Fuel Compounder Multiblock.
 * 3x3x3 Footprint with hollow core.
 * <p>
 * Compounds custom nuclear fuel mixes from actinide dusts:
 * - Requires exactly 10 dusts of total actinide material (regular = 1, small = 1/4, tiny = 1/9).
 * - Requires 100 L of Tritium and 4 empty fuel rods.
 * - Produces 1 Quad Custom Fuel Rod with unique NBT physics properties.
 */
public class MTEActinideCompounder extends MTEEnhancedMultiBlockBase<MTEActinideCompounder>
    implements ISurvivalConstructable {

    private static final String STRUCTURE_PIECE_MAIN = "compounder_3x3x3";
    private static IStructureDefinition<MTEActinideCompounder> STRUCTURE_DEFINITION = null;

    public MTEActinideCompounder(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public MTEActinideCompounder(String aName) {
        super(aName);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEActinideCompounder(mName);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("Actinide Fuel Compounder")
            .addInfo("Compounds custom nuclear fuel mixes from actinide dusts")
            .addInfo("Circuit 1: 1 Single Fuel Rod (1 empty rod, 25 L Tritium, 2.5 dusts / 90 units, 7.5s)")
            .addInfo("Circuit 2: 1 Dual Fuel Rod (2 empty rods, 50 L Tritium, 5.0 dusts / 180 units, 15s)")
            .addInfo("Circuit 4 (or none): 1 Quad Fuel Rod (4 empty rods, 100 L Tritium, 10.0 dusts / 360 units, 30s)")
            .addInfo("  (Regular dust = 36 units [1.0], Small dust = 9 units [0.25], Tiny dust = 4 units [0.11])")
            .addSeparator()
            .beginStructureBlock(3, 3, 3, true)
            .addController("Front center")
            .addCasingInfoMin("Nuclear Casing", 14, false)
            .addInputBus("Any casing", 1)
            .addOutputBus("Any casing", 1)
            .addInputHatch("Any casing", 1)
            .addEnergyHatch("Any casing", 1)
            .addMaintenanceHatch("Any casing", 1)
            .toolTipFinisher("Modular Nuclear");
        return tt;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == aFacing) {
            if (aActive) {
                return new ITexture[] {
                    Textures.BlockIcons.getCasingTextureForId(BlockNuclearCasing.CASING_TEXTURE_INDEX),
                    TextureFactory.of(ModularNuclearTextures.OVERLAY_FRONT_FISSION_REACTOR_ACTIVE) };
            }
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(BlockNuclearCasing.CASING_TEXTURE_INDEX),
                TextureFactory.of(ModularNuclearTextures.OVERLAY_FRONT_FISSION_REACTOR) };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(BlockNuclearCasing.CASING_TEXTURE_INDEX) };
    }

    public boolean addHatch(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
        if (aTileEntity == null) return false;
        IMetaTileEntity aMetaTileEntity = aTileEntity.getMetaTileEntity();
        if (aMetaTileEntity == null) return false;
        if (aMetaTileEntity instanceof MTEHatchInput) {
            return addInputToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (aMetaTileEntity instanceof MTEHatchInputBus) {
            return addInputToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (aMetaTileEntity instanceof MTEHatchOutput) {
            return addOutputToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (aMetaTileEntity instanceof MTEHatchOutputBus) {
            return addOutputToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (aMetaTileEntity instanceof MTEHatchEnergy) {
            return addEnergyInputToMachineList(aTileEntity, aBaseCasingIndex);
        }
        if (aMetaTileEntity instanceof MTEHatchMaintenance) {
            return addMaintenanceToMachineList(aTileEntity, aBaseCasingIndex);
        }
        return false;
    }

    @Override
    public IStructureDefinition<MTEActinideCompounder> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<MTEActinideCompounder>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,
                    StructureUtility.transpose(
                        new String[][] { { "CCC", "C~C", "CCC" }, { "CCC", "C C", "CCC" }, { "CCC", "CCC", "CCC" } }))
                .addElement(
                    'C',
                    StructureUtility.ofChain(
                        GTStructureUtility
                            .ofHatchAdder(MTEActinideCompounder::addHatch, BlockNuclearCasing.CASING_TEXTURE_INDEX, 1),
                        StructureUtility.ofBlock(ModBlocks.nuclearCasing, 0)))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mEnergyHatches.clear();
        mInputBusses.clear();
        mOutputBusses.clear();
        mInputHatches.clear();
        mOutputHatches.clear();
        mMaintenanceHatches.clear();

        if (!checkPiece(STRUCTURE_PIECE_MAIN, 1, 1, 0)) {
            return false;
        }

        if (mEnergyHatches.isEmpty() || mInputBusses.isEmpty()
            || mOutputBusses.isEmpty()
            || mInputHatches.isEmpty()
            || mMaintenanceHatches.size() != 1) {
            return false;
        }

        checkMaintenance();
        if (aBaseMetaTileEntity != null && !aBaseMetaTileEntity.isAllowedToWork()) {
            aBaseMetaTileEntity.enableWorking();
        }

        return true;
    }

    public static class DustInfo {

        public final CustomFuelMix.ActinideIsotope isotope;
        public final int unitsPerItem;

        public DustInfo(CustomFuelMix.ActinideIsotope isotope, int unitsPerItem) {
            this.isotope = isotope;
            this.unitsPerItem = unitsPerItem;
        }
    }

    public static DustInfo identifyActinideDust(ItemStack stack) {
        if (stack == null) return null;

        int units = 0;
        CustomFuelMix.ActinideIsotope isotope = null;

        ItemData data = GTOreDictUnificator.getAssociation(stack);
        if (data != null && data.mPrefix != null && data.mMaterial != null) {
            if (data.mPrefix == OrePrefixes.dust) {
                units = 36;
            } else if (data.mPrefix == OrePrefixes.dustSmall) {
                units = 9;
            } else if (data.mPrefix == OrePrefixes.dustTiny) {
                units = 4;
            }
            if (units > 0) {
                isotope = CustomFuelMix.ActinideIsotope.fromMaterial(data.mMaterial.mMaterial);
            }
        }

        if (isotope == null || units == 0) {
            for (int id : OreDictionary.getOreIDs(stack)) {
                String ore = OreDictionary.getOreName(id);
                if (ore.startsWith("dustTiny")) {
                    units = 4;
                    isotope = CustomFuelMix.ActinideIsotope.fromName(ore.substring(8));
                } else if (ore.startsWith("dustSmall")) {
                    units = 9;
                    isotope = CustomFuelMix.ActinideIsotope.fromName(ore.substring(9));
                } else if (ore.startsWith("dust")) {
                    units = 36;
                    isotope = CustomFuelMix.ActinideIsotope.fromName(ore.substring(4));
                }
                if (isotope != null && units > 0) break;
            }
        }

        if (isotope != null && units > 0) {
            return new DustInfo(isotope, units);
        }
        return null;
    }

    public static boolean isEmptyFuelRod(ItemStack stack) {
        if (stack == null) return false;
        try {
            if (ItemList.IC2_Fuel_Rod_Empty != null && ItemList.IC2_Fuel_Rod_Empty.isStackEqual(stack, false, true)) {
                return true;
            }
        } catch (Throwable ignored) {}
        String name = stack.getUnlocalizedName();
        if (name != null) {
            String lower = name.toLowerCase();
            if (lower.contains("fuelrod") && lower.contains("empty")) return true;
            if (lower.contains("fuel_rod") && lower.contains("empty")) return true;
            if (lower.contains("itemfuelrodempty")) return true;
        }
        for (int id : OreDictionary.getOreIDs(stack)) {
            String ore = OreDictionary.getOreName(id);
            if ("fuelRodEmpty".equals(ore) || "rodEmpty".equals(ore)) return true;
        }
        return false;
    }

    public static boolean isTritium(FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return false;
        if (Materials.Tritium != null
            && (fluid.getFluid() == Materials.Tritium.mGas || fluid.getFluid() == Materials.Tritium.mFluid
                || Materials.FLUID_MAP.get(fluid.getFluid()) == Materials.Tritium)) {
            return true;
        }
        String name = fluid.getFluid()
            .getName()
            .toLowerCase();
        return name.contains("tritium");
    }

    public static Integer getCircuitConfiguration(ItemStack stack) {
        if (stack == null) return null;
        if (stack.getItem() instanceof gregtech.common.items.ItemIntegratedCircuit) {
            return stack.getItemDamage();
        }
        try {
            if (ItemList.Circuit_Integrated != null && ItemList.Circuit_Integrated.isStackEqual(stack, false, true)) {
                return stack.getItemDamage();
            }
        } catch (Throwable ignored) {}
        for (int id : OreDictionary.getOreIDs(stack)) {
            String ore = OreDictionary.getOreName(id);
            if ("circuitIntegrated".equals(ore)) {
                return stack.getItemDamage();
            }
        }
        return null;
    }

    public static boolean isProgrammedCircuit(ItemStack stack) {
        return getCircuitConfiguration(stack) != null;
    }

    @Override
    public boolean checkRecipe(ItemStack aStack) {
        checkMaintenance();

        // 0. Determine target rod size from Programmed Circuit (default to 4 if absent or 0)
        int targetRodSize = 4;
        for (ItemStack stack : getStoredInputs()) {
            Integer cfg = getCircuitConfiguration(stack);
            if (cfg != null) {
                if (cfg == 1) {
                    targetRodSize = 1;
                } else if (cfg == 2) {
                    targetRodSize = 2;
                } else if (cfg == 4 || cfg == 0) {
                    targetRodSize = 4;
                } else {
                    return false; // Unsupported circuit configuration
                }
                break;
            }
        }

        int requiredEmptyRods = targetRodSize;
        int requiredTritium = 25 * targetRodSize;
        int requiredUnits = CustomFuelMix.getRequiredUnits(targetRodSize);
        int recipeTime = 150 * targetRodSize;

        // 1. Check for required Tritium in stored input fluids
        FluidStack foundTritium = null;
        for (FluidStack fluid : getStoredFluids()) {
            if (fluid != null && isTritium(fluid) && fluid.amount >= requiredTritium) {
                foundTritium = fluid;
                break;
            }
        }
        if (foundTritium == null) {
            return false;
        }

        // 2. Check for at least required empty fuel rods
        int emptyRodCount = 0;
        for (ItemStack stack : getStoredInputs()) {
            if (isEmptyFuelRod(stack)) {
                emptyRodCount += stack.stackSize;
            }
        }
        if (emptyRodCount < requiredEmptyRods) {
            return false;
        }

        // 3. Scan actinide dusts and calculate total units
        Map<CustomFuelMix.ActinideIsotope, Integer> unitsFound = new LinkedHashMap<>();
        List<ItemStack> actinideStacksToConsume = new ArrayList<>();
        int totalUnits = 0;

        for (ItemStack stack : getStoredInputs()) {
            if (isEmptyFuelRod(stack) || isProgrammedCircuit(stack)) continue;
            DustInfo info = identifyActinideDust(stack);
            if (info != null && info.unitsPerItem > 0) {
                int stackUnits = info.unitsPerItem * stack.stackSize;
                int remainingNeeded = requiredUnits - totalUnits;
                if (remainingNeeded <= 0) break;

                int countToTake = Math
                    .min(stack.stackSize, (int) Math.ceil((double) remainingNeeded / info.unitsPerItem));
                // Clamp to exact units if possible
                int takenUnits = countToTake * info.unitsPerItem;
                if (totalUnits + takenUnits > requiredUnits) {
                    countToTake = remainingNeeded / info.unitsPerItem;
                    takenUnits = countToTake * info.unitsPerItem;
                }

                if (countToTake > 0) {
                    totalUnits += takenUnits;
                    unitsFound.put(info.isotope, unitsFound.getOrDefault(info.isotope, 0) + takenUnits);
                    ItemStack consumeStack = stack.copy();
                    consumeStack.stackSize = countToTake;
                    actinideStacksToConsume.add(consumeStack);
                }
            }
        }

        if (totalUnits != requiredUnits) {
            return false;
        }

        // 4. Deplete Inputs
        // A. Deplete Tritium
        FluidStack depleteFluid = new FluidStack(foundTritium.getFluid(), requiredTritium);
        for (MTEHatchInput hatch : mInputHatches) {
            if (hatch != null && hatch.getFluid() != null
                && hatch.getFluid()
                    .isFluidEqual(depleteFluid)) {
                FluidStack drained = hatch.drain(depleteFluid.amount, true);
                if (drained != null) {
                    depleteFluid.amount -= drained.amount;
                    if (depleteFluid.amount <= 0) break;
                }
            }
        }

        // B. Deplete Empty Fuel Rods
        int emptyNeeded = requiredEmptyRods;
        for (MTEHatchInputBus bus : mInputBusses) {
            if (bus == null) continue;
            for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                ItemStack item = bus.getStackInSlot(slot);
                if (isEmptyFuelRod(item)) {
                    int take = Math.min(item.stackSize, emptyNeeded);
                    item.stackSize -= take;
                    emptyNeeded -= take;
                    if (item.stackSize <= 0) {
                        bus.setInventorySlotContents(slot, null);
                    }
                    if (emptyNeeded <= 0) break;
                }
            }
            if (emptyNeeded <= 0) break;
        }

        // C. Deplete Actinide Dusts
        for (ItemStack toConsume : actinideStacksToConsume) {
            int needed = toConsume.stackSize;
            for (MTEHatchInputBus bus : mInputBusses) {
                if (bus == null) continue;
                for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                    ItemStack item = bus.getStackInSlot(slot);
                    if (item != null && item.isItemEqual(toConsume)
                        && ItemStack.areItemStackTagsEqual(item, toConsume)) {
                        int take = Math.min(item.stackSize, needed);
                        item.stackSize -= take;
                        needed -= take;
                        if (item.stackSize <= 0) {
                            bus.setInventorySlotContents(slot, null);
                        }
                        if (needed <= 0) break;
                    }
                }
                if (needed <= 0) break;
            }
        }

        // 5. Build Custom Fuel Mix Output
        CustomFuelMix mix = new CustomFuelMix(unitsFound);
        com.gtnewhorizons.modularnuclear.common.item.ItemCustomFuelRod outputItem = switch (targetRodSize) {
            case 1 -> ModItems.rodCustomFuel1;
            case 2 -> ModItems.rodCustomFuel2;
            default -> ModItems.rodCustomFuel4;
        };
        ItemStack output = new ItemStack(outputItem, 1, 0);
        NBTTagCompound nbt = new NBTTagCompound();
        mix.writeToNBT(nbt);
        output.setTagCompound(nbt);

        this.mOutputItems = new ItemStack[] { output };
        this.mMaxProgresstime = recipeTime;
        this.mEUt = -512; // EV tier
        this.mEfficiency = 10000;
        this.mEfficiencyIncrease = 10000;

        return true;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, 1, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivialBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, 1, 1, 0, elementBudget, env, false, true);
    }
}
