package com.gtnewhorizons.modularnuclear.common.metatileentity.hatch;

import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_COLORS;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_PIPE_IN;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.oredict.OreDictionary;

import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.gui.MTEHatchNuclearControlRodGui;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.projectred.ProjectRedIntegration;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.SlotWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.objects.ItemData;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.api.util.GTSplit;
import gregtech.api.util.GTUtility;

/**
 * Nuclear Control Rod Hatch.
 * Placed on the top layer of the Nuclear Reactor alongside nuclear buses and hatches.
 * Holds exactly 1 Long Rod of Silver, Boron, Cadmium, Indium, or Hafnium.
 * Incoming redstone signal (0..15) determines how far the control rod is inserted (0% to 100%).
 * Absorbs neutrons and converts them into heat in the reactor core.
 */
public class MTEHatchNuclearControlRod extends MTEHatch {

    public static final int SLOT_ROD = 0;
    public static final int SLOT_MODERATOR = 1;
    public com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor mReactor;

    public int getTicksPerCycle() {
        return mReactor != null ? mReactor.getTicksPerCycle() : NuclearSimulationEngine.ticksPerCycle;
    }

    public enum ControlRodType {

        NONE("None", 0.0, 0.0, 0.02),
        SILVER("Silver", 0.50, 0.20, 0.20),
        BORON("Boron", 0.70, 0.35, 0.08),
        CADMIUM("Cadmium", 0.85, 0.50, 0.12),
        INDIUM("Indium", 0.95, 0.65, 0.14),
        HAFNIUM("Hafnium", 0.99, 0.80, 0.16);

        public final String displayName;
        public final double maxThermalAbsorption;
        public final double maxFastAbsorption;
        public final double heatTransferCoeff;

        ControlRodType(String displayName, double maxThermalAbsorption, double maxFastAbsorption,
            double heatTransferCoeff) {
            this.displayName = displayName;
            this.maxThermalAbsorption = maxThermalAbsorption;
            this.maxFastAbsorption = maxFastAbsorption;
            this.heatTransferCoeff = heatTransferCoeff;
        }
    }

    public double mTemperature = 20.0;
    public double mHeatHu = 0.0;
    public long mCumulativeFluence = 0L;
    public int mFastFlux = 0;
    public int mThermalFlux = 0;
    public int mFastAbsorbed = 0;
    public int mThermalAbsorbed = 0;
    public int mLastFastFlux = 0;
    public int mLastThermalFlux = 0;
    public int mLastFastAbsorbed = 0;
    public int mLastThermalAbsorbed = 0;

    public static final int CHANNEL_AUTO = -1;

    public static final String[] DYE_NAMES = new String[] { "White", "Orange", "Magenta", "Light Blue", "Yellow",
        "Lime", "Pink", "Gray", "Light Gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black" };

    public int mInputChannel = CHANNEL_AUTO;

    public static String getChannelName(int channel) {
        if (channel < 0 || channel >= 16) {
            return "Auto (Strongest)";
        }
        return "Ch " + channel + " (" + DYE_NAMES[channel] + ")";
    }

    public void cycleInputChannel(int dir) {
        int ch = mInputChannel + dir;
        if (ch < -1) ch = 15;
        else if (ch > 15) ch = -1;
        this.mInputChannel = ch;
        markTileDirty();
    }

    public MTEHatchNuclearControlRod(int aID, String aName, String aNameRegional, int aTier) {
        this(aID, aName, aNameRegional, aTier, 1);
    }

    public MTEHatchNuclearControlRod(int aID, String aName, String aNameRegional, int aTier, int aInventorySize) {
        super(aID, aName, aNameRegional, aTier, aInventorySize, (String) null);
    }

    public MTEHatchNuclearControlRod(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        this(aName, aTier, 1, aDescription, aTextures);
    }

    public MTEHatchNuclearControlRod(String aName, int aTier, int aInventorySize, String[] aDescription,
        ITexture[][][] aTextures) {
        super(aName, aTier, aInventorySize, aDescription, aTextures);
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("gt.blockmachines.hatch.nuclearcontrolrod.desc");
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchNuclearControlRod(mName, mTier, mDescriptionArray, mTextures);
    }

    public static ControlRodType getRodType(ItemStack stack) {
        if (stack == null) return ControlRodType.NONE;

        try {
            com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ControlRodData crd = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
                .findControlRod(stack);
            if (crd != null && crd.id != null) {
                if ("rod_silver".equals(crd.id)) return ControlRodType.SILVER;
                if ("rod_boron".equals(crd.id)) return ControlRodType.BORON;
                if ("rod_cadmium".equals(crd.id)) return ControlRodType.CADMIUM;
                if ("rod_indium".equals(crd.id)) return ControlRodType.INDIUM;
                if ("rod_hafnium".equals(crd.id)) return ControlRodType.HAFNIUM;
            }
        } catch (Throwable ignored) {}

        try {
            String cfgType = com.gtnewhorizons.modularnuclear.common.config.ModularNuclearConfig
                .getControlRodType(stack);
            if (cfgType != null) {
                return ControlRodType.valueOf(cfgType.toUpperCase());
            }
        } catch (Throwable ignored) {}

        if (stack.getItem() == null) return ControlRodType.NONE;

        try {
            ItemData itemData = GTOreDictUnificator.getItemData(stack);
            if (itemData != null && itemData.mPrefix == OrePrefixes.stickLong && itemData.mMaterial != null) {
                Materials mat = itemData.mMaterial.mMaterial;
                if (mat == Materials.Silver) return ControlRodType.SILVER;
                if (mat == Materials.Boron) return ControlRodType.BORON;
                if (mat == Materials.Cadmium) return ControlRodType.CADMIUM;
                if (mat == Materials.Indium) return ControlRodType.INDIUM;
                if (mat != null && "Hafnium".equalsIgnoreCase(mat.mName)) return ControlRodType.HAFNIUM;
            }
        } catch (Throwable ignored) {}

        try {
            int[] oreIDs = OreDictionary.getOreIDs(stack);
            for (int id : oreIDs) {
                String name = OreDictionary.getOreName(id);
                if ("stickLongSilver".equals(name)) return ControlRodType.SILVER;
                if ("stickLongBoron".equals(name)) return ControlRodType.BORON;
                if ("stickLongCadmium".equals(name)) return ControlRodType.CADMIUM;
                if ("stickLongIndium".equals(name)) return ControlRodType.INDIUM;
                if ("stickLongHafnium".equals(name)) return ControlRodType.HAFNIUM;
            }
        } catch (Throwable ignored) {}

        try {
            String unloc = stack.getUnlocalizedName();
            if (unloc != null) {
                String clean = unloc.startsWith("item.") ? unloc.substring(5) : unloc;
                if ("stickLongSilver".equalsIgnoreCase(clean)) return ControlRodType.SILVER;
                if ("stickLongBoron".equalsIgnoreCase(clean)) return ControlRodType.BORON;
                if ("stickLongCadmium".equalsIgnoreCase(clean)) return ControlRodType.CADMIUM;
                if ("stickLongIndium".equalsIgnoreCase(clean)) return ControlRodType.INDIUM;
                if ("stickLongHafnium".equalsIgnoreCase(clean)) return ControlRodType.HAFNIUM;
            }
        } catch (Throwable ignored) {}

        return ControlRodType.NONE;
    }

    public static boolean isControlRod(ItemStack stack) {
        if (stack == null) return false;
        if (com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findControlRod(stack) != null)
            return true;
        return getRodType(stack) != ControlRodType.NONE;
    }

    public static boolean isModerator(ItemStack stack) {
        if (stack == null) return false;
        if (isControlRod(stack)) return false;
        if (com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry.findModerator(stack) != null)
            return true;
        return com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification.isItemModerator(stack);
    }

    public boolean hasControlRodItem() {
        return mInventory != null && mInventory.length > SLOT_ROD && isControlRod(mInventory[SLOT_ROD]);
    }

    public ItemStack getModeratorStack() {
        if (mInventory != null && mInventory.length > SLOT_MODERATOR) {
            return mInventory[SLOT_MODERATOR];
        }
        return null;
    }

    public boolean hasModerator() {
        return isModerator(getModeratorStack());
    }

    public double getModeratorInsertionRatio() {
        if (!hasModerator()) return 0.0;
        return Math.max(0.0, Math.min(1.0, 1.0 - getInsertionRatio()));
    }

    public int getModeratorInsertionPercent() {
        return (int) Math.round(getModeratorInsertionRatio() * 100.0);
    }

    public String getModeratorDisplayName() {
        ItemStack modStack = getModeratorStack();
        if (modStack == null) return "None";
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData data = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findModerator(modStack);
        if (data != null && data.name != null && !data.name.isEmpty()) {
            return data.name;
        }
        return modStack.getDisplayName();
    }

    public double getModeratorProbability() {
        if (!hasModerator()) return 0.0;
        double ratio = getModeratorInsertionRatio();
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData data = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findModerator(getModeratorStack());
        double mod = (data != null) ? data.moderation : 0.50;
        return Math.max(0.0, Math.min(1.0, ratio * mod));
    }

    public double getModeratorAbsorptionProbability(NeutronType type) {
        if (!hasModerator()) return 0.0;
        double ratio = getModeratorInsertionRatio();
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData data = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findModerator(getModeratorStack());
        double abs = (data != null)
            ? ((type == NeutronType.FAST) ? data.absorptionFast : data.absorptionThermal)
            : ((type == NeutronType.FAST) ? 0.002 : 0.009);
        return Math.max(0.0, Math.min(1.0, ratio * abs));
    }

    public double getModeratorScatteringProbability(NeutronType type) {
        if (!hasModerator()) return 0.0;
        double ratio = getModeratorInsertionRatio();
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData data = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findModerator(getModeratorStack());
        double scattering = (data != null)
            ? ((type == NeutronType.FAST) ? data.scatteringFast : data.scatteringThermal)
            : ((type == NeutronType.FAST) ? 0.93 : 0.621);
        return Math.max(0.0, Math.min(1.0, ratio * scattering));
    }

    public boolean mScram = false;

    public void setScram(boolean scram) {
        this.mScram = scram;
        markTileDirty();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()) {
            aBaseMetaTileEntity.tryDisableTicking();
        }
    }

    public double getAmbientTemperature() {
        if (getBaseMetaTileEntity() != null && getBaseMetaTileEntity().getWorld() != null) {
            return MTENuclearReactor.calculateAmbientTemperature(
                getBaseMetaTileEntity().getWorld(),
                getBaseMetaTileEntity().getXCoord(),
                getBaseMetaTileEntity().getYCoord(),
                getBaseMetaTileEntity().getZCoord());
        }
        return NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;
    }

    public byte getRedstoneSignal() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return 0;
        return base.getStrongestRedstone();
    }

    public int getFineRedstoneSignal() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) return -1;
        return ProjectRedIntegration
            .getBundledInput(base.getWorld(), base.getXCoord(), base.getYCoord(), base.getZCoord(), mInputChannel);
    }

    public boolean isInterfacingProjectRed() {
        return getFineRedstoneSignal() >= 0;
    }

    public static final int MAX_MINING_PIPES = 5;
    public int mMiningPipeCount = MAX_MINING_PIPES;

    public int getMiningPipeCount() {
        return mMiningPipeCount;
    }

    public void setMiningPipeCount(int count) {
        this.mMiningPipeCount = Math.max(0, Math.min(MAX_MINING_PIPES, count));
    }

    public int getMaxExtractionPercent() {
        return mMiningPipeCount * 20;
    }

    public double getMaxExtractionRatio() {
        return (double) mMiningPipeCount * 0.20;
    }

    public int getMinInsertionPercent() {
        return Math.max(0, 100 - mMiningPipeCount * 20);
    }

    public double getMinInsertionRatio() {
        return Math.max(0.0, 1.0 - (double) mMiningPipeCount * 0.20);
    }

    public double getTargetInsertionRatio() {
        if (mScram) return 1.0;
        int fine = getFineRedstoneSignal();
        if (fine >= 0) {
            return Math.max(0.0, Math.min(1.0, (double) fine / 255.0));
        }
        byte rs = getRedstoneSignal();
        return Math.max(0.0, Math.min(1.0, (double) rs / 15.0));
    }

    public int getTargetInsertionPercent() {
        return (int) Math.round(getTargetInsertionRatio() * 100.0);
    }

    public double getInsertionRatio() {
        double target = getTargetInsertionRatio();
        double minInsertion = getMinInsertionRatio();
        return Math.max(minInsertion, target);
    }

    public int getInsertionPercent() {
        return (int) Math.round(getInsertionRatio() * 100.0);
    }

    public static boolean isMiningPipeBlock(Block block, int meta) {
        if (block == null) return false;

        // 1. Direct IC2 block / item lookup
        try {
            Block ic2Pipe = GameRegistry.findBlock("IC2", "blockMiningPipe");
            if (ic2Pipe != null && block == ic2Pipe) return true;
        } catch (Throwable ignored) {}

        try {
            Block ic2Tip = GameRegistry.findBlock("IC2", "blockMiningTip");
            if (ic2Tip != null && block == ic2Tip) return true;
        } catch (Throwable ignored) {}

        try {
            ItemStack ic2Stack = ic2.api.item.IC2Items.getItem("miningPipe");
            if (ic2Stack != null && Block.getBlockFromItem(ic2Stack.getItem()) == block) return true;
        } catch (Throwable ignored) {}

        // 2. Registry Unique Identifier
        try {
            GameRegistry.UniqueIdentifier uid = GameRegistry.findUniqueIdentifierFor(block);
            if (uid != null
                && ("blockMiningPipe".equalsIgnoreCase(uid.name) || "blockMiningTip".equalsIgnoreCase(uid.name))) {
                return true;
            }
        } catch (Throwable ignored) {}

        // 3. Unlocalized name check
        try {
            String unloc = block.getUnlocalizedName();
            if (unloc != null) {
                String clean = unloc.startsWith("tile.") ? unloc.substring(5) : unloc;
                if ("blockMiningPipe".equalsIgnoreCase(clean) || "miningPipe".equalsIgnoreCase(clean)
                    || "mining_pipe".equalsIgnoreCase(clean)
                    || "blockMiningTip".equalsIgnoreCase(clean)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}

        // 4. OreDictionary check
        try {
            Item item = Item.getItemFromBlock(block);
            if (item != null) {
                ItemStack stack = new ItemStack(item, 1, meta);
                int[] ids = OreDictionary.getOreIDs(stack);
                for (int id : ids) {
                    String oreName = OreDictionary.getOreName(id);
                    if ("miningPipe".equalsIgnoreCase(oreName) || "pipeMining".equalsIgnoreCase(oreName)
                        || "blockMiningPipe".equalsIgnoreCase(oreName)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    public int countMiningPipes() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) {
            return mMiningPipeCount;
        }
        World world = base.getWorld();
        ForgeDirection facing = base.getFrontFacing();
        if (facing == null || facing == ForgeDirection.UNKNOWN) {
            return 0;
        }

        int count = 0;
        int x = base.getXCoord();
        int y = base.getYCoord();
        int z = base.getZCoord();

        for (int i = 1; i <= MAX_MINING_PIPES; i++) {
            int bx = x + facing.offsetX * i;
            int by = y + facing.offsetY * i;
            int bz = z + facing.offsetZ * i;
            Block block = world.getBlock(bx, by, bz);
            int meta = world.getBlockMetadata(bx, by, bz);
            if (isMiningPipeBlock(block, meta)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    public void updateMiningPipeHousing() {
        this.mMiningPipeCount = countMiningPipes();
    }

    public double getAbsorptionProbability(NeutronType type) {
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ControlRodData crData = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findControlRod(mInventory[SLOT_ROD]);
        if (crData != null) {
            double ratio = getInsertionRatio();
            double max = (type == NeutronType.THERMAL) ? crData.maxThermalAbsorption : crData.maxFastAbsorption;
            return Math.max(0.01, ratio * max);
        }
        ControlRodType rod = getRodType(mInventory[SLOT_ROD]);
        if (rod == ControlRodType.NONE) return 0.01;
        double ratio = getInsertionRatio();
        double max = (type == NeutronType.THERMAL) ? rod.maxThermalAbsorption : rod.maxFastAbsorption;
        return Math.max(0.01, ratio * max);
    }

    public double getScatteringProbability(NeutronType type) {
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ControlRodData crData = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findControlRod(mInventory[SLOT_ROD]);
        if (crData != null) {
            double ratio = getInsertionRatio();
            double scattering = (type == NeutronType.THERMAL) ? crData.scatteringThermal : crData.scatteringFast;
            return Math.max(0.01, scattering * (1.0 - ratio));
        }
        ControlRodType rod = getRodType(mInventory[SLOT_ROD]);
        if (rod == ControlRodType.NONE) return 0.02;
        double ratio = getInsertionRatio();
        return Math.max(0.01, 0.05 * (1.0 - ratio));
    }

    public double getHeatTransferCoeff() {
        com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ControlRodData crData = com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry
            .findControlRod(mInventory[SLOT_ROD]);
        if (crData != null) {
            return crData.heatTransferCoeff;
        }
        ControlRodType rod = getRodType(mInventory[SLOT_ROD]);
        if (rod == ControlRodType.NONE) return 0.02;
        return rod.heatTransferCoeff;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public boolean doesFillContainers() {
        return false;
    }

    @Override
    public boolean doesEmptyContainers() {
        return false;
    }

    @Override
    public boolean canTankBeFilled() {
        return false;
    }

    @Override
    public boolean canTankBeEmptied() {
        return false;
    }

    @Override
    public boolean isValidSlot(int aIndex) {
        return aIndex == SLOT_ROD;
    }

    @Override
    public boolean isItemValidForSlot(int aIndex, ItemStack aStack) {
        return aIndex == SLOT_ROD && mInventory[SLOT_ROD] == null && isControlRod(aStack);
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection aSide,
        ItemStack aStack) {
        return aIndex == SLOT_ROD && mInventory[SLOT_ROD] == null && isControlRod(aStack);
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection aSide,
        ItemStack aStack) {
        return aIndex == SLOT_ROD;
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, net.minecraft.entity.player.EntityPlayer aPlayer, float aX,
        float aY, float aZ, ItemStack aTool) {
        cycleInputChannel(1);
        GTUtility.sendChatToPlayer(aPlayer, "Control rod bundled input channel: " + getChannelName(mInputChannel));
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (aBaseMetaTileEntity != null && aBaseMetaTileEntity.isServerSide()) {
            updateMiningPipeHousing();
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setDouble("mTemperature", mTemperature);
        aNBT.setDouble("mHeatHu", mHeatHu);
        aNBT.setLong("mCumulativeFluence", mCumulativeFluence);
        aNBT.setBoolean("mScram", mScram);
        aNBT.setInteger("mInputChannel", mInputChannel);
        aNBT.setInteger("mMiningPipeCount", mMiningPipeCount);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("mTemperature")) {
            mTemperature = aNBT.getDouble("mTemperature");
        } else {
            mTemperature = getAmbientTemperature();
        }
        if (aNBT.hasKey("mHeatHu")) {
            mHeatHu = aNBT.getDouble("mHeatHu");
        }
        if (aNBT.hasKey("mCumulativeFluence")) {
            mCumulativeFluence = aNBT.getLong("mCumulativeFluence");
        }
        mScram = aNBT.getBoolean("mScram");
        if (aNBT.hasKey("mInputChannel")) {
            mInputChannel = aNBT.getInteger("mInputChannel");
        }
        if (aNBT.hasKey("mMiningPipeCount")) {
            mMiningPipeCount = aNBT.getInteger("mMiningPipeCount");
        }
    }

    public void markTileDirty() {
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    @Override
    public boolean isFacingValid(ForgeDirection facing) {
        return true;
    }

    @Override
    public ITexture getCasingTexture() {
        ITexture tex = super.getCasingTexture();
        if (tex != null) return tex;
        tex = Textures.BlockIcons.getCasingTextureForId(BlockNuclearCasing.CASING_TEXTURE_INDEX);
        if (tex != null) return tex;
        return Textures.BlockIcons.MACHINE_CASINGS[mTier][0];
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        byte color = getBaseMetaTileEntity() == null ? -1 : getBaseMetaTileEntity().getColorization();
        ITexture coloredOverlay = TextureFactory.of(OVERLAY_PIPE_COLORS[color + 1]);
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_PIPE_IN), coloredOverlay,
            TextureFactory.of(Textures.BlockIcons.OVERLAY_HATCH_HEAT_SENSOR_GLOW) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        byte color = getBaseMetaTileEntity() == null ? -1 : getBaseMetaTileEntity().getColorization();
        ITexture coloredOverlay = TextureFactory.of(OVERLAY_PIPE_COLORS[color + 1]);
        return new ITexture[] { aBaseTexture, TextureFactory.of(OVERLAY_PIPE_IN), coloredOverlay,
            TextureFactory.of(Textures.BlockIcons.OVERLAY_HATCH_HEAT_SENSOR) };
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity,
        net.minecraft.entity.player.EntityPlayer aPlayer) {
        if (aPlayer != null && aPlayer.getHeldItem() != null && mReactor != null) {
            if (mReactor.handleSensorCardLinking(aPlayer.getHeldItem(), aPlayer, mReactor.getBaseMetaTileEntity())) {
                return true;
            }
        }
        updateMiningPipeHousing();
        openGui(aPlayer);
        return true;
    }

    @Override
    protected boolean useMui2() {
        return true;
    }

    @Override
    public ModularPanel buildUI(com.cleanroommc.modularui.factory.PosGuiData data, PanelSyncManager syncManager,
        UISettings uiSettings) {
        return new MTEHatchNuclearControlRodGui(this).build(data, syncManager, uiSettings);
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(96, 60))
            .widget(
                new TextWidget("Control rod hatch").setDefaultColor(Color.rgb(180, 100, 255))
                    .setPos(10, 20))
            .widget(
                new TextWidget().setStringSupplier(() -> String.format("Temp: %.1f °C", mTemperature))
                    .setDefaultColor(Color.rgb(255, 200, 0))
                    .setPos(10, 31))
            .widget(new TextWidget().setStringSupplier(() -> {
                int fine = getFineRedstoneSignal();
                if (fine >= 0) {
                    return String.format("Insert: %d%% (PR: %d)", getInsertionPercent(), fine);
                }
                return String.format("Insert: %d%% (RS: %d)", getInsertionPercent(), getRedstoneSignal());
            })
                .setDefaultColor(Color.rgb(100, 255, 200))
                .setPos(10, 42))
            .widget(
                new TextWidget().setStringSupplier(() -> "Rod: " + getRodType(mInventory[SLOT_ROD]).displayName)
                    .setDefaultColor(Color.rgb(200, 200, 255))
                    .setPos(10, 53))
            .widget(new TextWidget().setStringSupplier(() -> {
                if (mMiningPipeCount < MAX_MINING_PIPES) {
                    return String.format("Pipes: %d/5 (Min %d%%)", mMiningPipeCount, getMinInsertionPercent());
                }
                return "Pipes: 5/5 (Full)";
            })
                .setDefaultColor(Color.rgb(200, 200, 100))
                .setPos(10, 64))
            .widget(
                new TextWidget("Rod").setDefaultColor(0xFFFFFFFF)
                    .setPos(125, 16))
            .widget(
                new SlotWidget(inventoryHandler, SLOT_ROD).setFilter(MTEHatchNuclearControlRod::isControlRod)
                    .setBackground(getGUITextureSet().getItemSlot(), GTUITextures.OVERLAY_SLOT_IN)
                    .setPos(122, 28));
    }
}
