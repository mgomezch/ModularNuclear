package com.gtnewhorizons.modularnuclear.common.metatileentity.hatch;

import java.util.Arrays;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.modularnuclear.common.block.BlockNuclearCasing;
import com.gtnewhorizons.modularnuclear.common.projectred.ProjectRedIntegration;
import com.gtnewhorizons.modularui.api.drawable.IDrawable;
import com.gtnewhorizons.modularui.api.drawable.Text;
import com.gtnewhorizons.modularui.api.math.Alignment;
import com.gtnewhorizons.modularui.api.math.Color;
import com.gtnewhorizons.modularui.api.screen.ModularWindow;
import com.gtnewhorizons.modularui.api.screen.UIBuildContext;
import com.gtnewhorizons.modularui.common.widget.ButtonWidget;
import com.gtnewhorizons.modularui.common.widget.DrawableWidget;
import com.gtnewhorizons.modularui.common.widget.FakeSyncWidget;
import com.gtnewhorizons.modularui.common.widget.TextWidget;

import cpw.mods.fml.common.Optional;
import gregtech.api.enums.Textures;
import gregtech.api.gui.modularui.GTUITextures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTSplit;
import gregtech.api.util.GTUtility;
import mrtjp.projectred.api.IBundledEmitter;

@Optional.Interface(iface = "mrtjp.projectred.api.IBundledEmitter", modid = "ProjRed|Transmission")
public class MTEHatchNuclearControl extends MTEHatch implements IBundledEmitter {

    public static final int METRIC_TEMPERATURE = 0;
    public static final int METRIC_COOLANT_ITEM_DURABILITY = 1;
    public static final int METRIC_FUEL_ITEM_DURABILITY = 2;
    public static final int METRIC_COOLANT_HATCH_FILL = 3;
    public static final int METRIC_FUEL_HATCH_FILL = 4;
    public static final int METRIC_REACTOR_DAMAGE = 5;
    public static final int METRIC_COUNT = 6;

    public static final int STAT_MIN = 0;
    public static final int STAT_MAX = 1;
    public static final int STAT_AVG = 2;
    public static final int STAT_COUNT = 3;

    public static final int MODE_TEMP_MIN = 0;
    public static final int MODE_TEMP_MAX = 1;
    public static final int MODE_TEMP_AVG = 2;
    public static final int MODE_COOLANT_ITEM_DUR_MIN = 3;
    public static final int MODE_COOLANT_ITEM_DUR_MAX = 4;
    public static final int MODE_COOLANT_ITEM_DUR_AVG = 5;
    public static final int MODE_FUEL_ITEM_DUR_MIN = 6;
    public static final int MODE_FUEL_ITEM_DUR_MAX = 7;
    public static final int MODE_FUEL_ITEM_DUR_AVG = 8;
    public static final int MODE_COOLANT_HATCH_FILL_MIN = 9;
    public static final int MODE_COOLANT_HATCH_FILL_MAX = 10;
    public static final int MODE_COOLANT_HATCH_FILL_AVG = 11;
    public static final int MODE_FUEL_HATCH_FILL_MIN = 12;
    public static final int MODE_FUEL_HATCH_FILL_MAX = 13;
    public static final int MODE_FUEL_HATCH_FILL_AVG = 14;
    public static final int MODE_DAMAGE_MIN = 15;
    public static final int MODE_DAMAGE_MAX = 16;
    public static final int MODE_DAMAGE_AVG = 17;
    public static final int MODE_COUNT = 18;

    public static final int BUNDLED_MODE_MULTI = 0;
    public static final int BUNDLED_MODE_BROADCAST = 1;
    public static final int BUNDLED_MODE_COUNT = 18;

    public static final String[] DYE_NAMES = new String[] { "White", "Orange", "Magenta", "Light Blue", "Yellow",
        "Lime", "Pink", "Gray", "Light Gray", "Cyan", "Purple", "Blue", "Brown", "Green", "Red", "Black" };

    public com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor mReactor;
    private int mMetric = 0;
    private int mStatistic = 0;
    private byte mOutputStrength = 0;
    private int mFineOutputStrength = 0;
    private int mBundledChannelMode = BUNDLED_MODE_MULTI;
    private final byte[] mBundledSignal = new byte[16];

    public MTEHatchNuclearControl(int aID, String aName, String aNameRegional, int aTier) {
        super(aID, aName, aNameRegional, aTier, 0, (String) null);
    }

    public MTEHatchNuclearControl(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        super(aName, aTier, 0, aDescription, aTextures);
    }

    @Override
    public String[] getDescription() {
        return GTSplit.splitLocalized("gt.blockmachines.hatch.nuclearcontrol.desc");
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchNuclearControl(mName, mTier, mDescriptionArray, mTextures);
    }

    public static String getMetricName(int metric) {
        return switch (metric) {
            case METRIC_TEMPERATURE -> "Temperature";
            case METRIC_COOLANT_ITEM_DURABILITY -> "Coolant item durability";
            case METRIC_FUEL_ITEM_DURABILITY -> "Fuel item durability";
            case METRIC_COOLANT_HATCH_FILL -> "Coolant hatch fill %";
            case METRIC_FUEL_HATCH_FILL -> "Fuel hatch fill %";
            case METRIC_REACTOR_DAMAGE -> "Reactor damage %";
            default -> "Unknown";
        };
    }

    public static String getStatisticName(int stat) {
        return switch (stat) {
            case STAT_MIN -> "min";
            case STAT_MAX -> "max";
            case STAT_AVG -> "avg";
            default -> "unknown";
        };
    }

    public static String getStatisticDisplayName(int stat) {
        return switch (stat) {
            case STAT_MIN -> "Minimum";
            case STAT_MAX -> "Maximum";
            case STAT_AVG -> "Average";
            default -> "Unknown";
        };
    }

    public static String getModeName(int mode) {
        int metric = (mode / STAT_COUNT) % METRIC_COUNT;
        int stat = mode % STAT_COUNT;
        return getMetricName(metric) + " (" + getStatisticName(stat) + ")";
    }

    public int getMetric() {
        return mMetric;
    }

    public void setMetric(int metric) {
        if (metric < 0) {
            metric = (metric % METRIC_COUNT + METRIC_COUNT) % METRIC_COUNT;
        } else {
            metric = metric % METRIC_COUNT;
        }
        this.mMetric = metric;
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    public void cycleMetric(int dir) {
        setMetric(mMetric + dir);
    }

    public int getStatistic() {
        return mStatistic;
    }

    public void setStatistic(int stat) {
        if (stat < 0) {
            stat = (stat % STAT_COUNT + STAT_COUNT) % STAT_COUNT;
        } else {
            stat = stat % STAT_COUNT;
        }
        this.mStatistic = stat;
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    public void cycleStatistic(int dir) {
        setStatistic(mStatistic + dir);
    }

    public int getMode() {
        return mMetric * STAT_COUNT + mStatistic;
    }

    public void setMode(int mode) {
        if (mode < 0) {
            mode = (mode % MODE_COUNT + MODE_COUNT) % MODE_COUNT;
        } else {
            mode = mode % MODE_COUNT;
        }
        this.mMetric = mode / STAT_COUNT;
        this.mStatistic = mode % STAT_COUNT;
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    public byte getOutputStrength() {
        return mOutputStrength;
    }

    public int getFineOutputStrength() {
        return mFineOutputStrength;
    }

    public void setFineOutputStrengthDirect(int fine) {
        this.mFineOutputStrength = fine;
    }

    public int getBundledChannelMode() {
        return mBundledChannelMode;
    }

    public void setBundledChannelMode(int mode) {
        if (mode < 0) {
            mode = (mode % BUNDLED_MODE_COUNT + BUNDLED_MODE_COUNT) % BUNDLED_MODE_COUNT;
        } else {
            mode = mode % BUNDLED_MODE_COUNT;
        }
        this.mBundledChannelMode = mode;
        if (getBaseMetaTileEntity() != null) {
            getBaseMetaTileEntity().markDirty();
        }
    }

    public void cycleBundledMode(int dir) {
        setBundledChannelMode(mBundledChannelMode + dir);
    }

    public static String getBundledModeName(int mode) {
        if (mode == BUNDLED_MODE_MULTI) return "Multi-Channel (All)";
        if (mode == BUNDLED_MODE_BROADCAST) return "Broadcast (All Colors)";
        int ch = mode - 2;
        if (ch >= 0 && ch < DYE_NAMES.length) {
            return "Ch " + ch + " (" + DYE_NAMES[ch] + ")";
        }
        return "Unknown";
    }

    public byte[] getBundledSignal() {
        return mBundledSignal;
    }

    @Optional.Method(modid = "ProjRed|Transmission")
    @Override
    public byte[] getBundledSignal(int side) {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null) {
            int front = te.getFrontFacing()
                .ordinal();
            int opp = te.getFrontFacing()
                .getOpposite()
                .ordinal();
            if (side == front || side == opp) {
                return mBundledSignal;
            }
        }
        return null;
    }

    public boolean isInterfacingProjectRed() {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te == null || te.getWorld() == null) return false;
        return ProjectRedIntegration
            .isInterfacing(te.getWorld(), te.getXCoord(), te.getYCoord(), te.getZCoord(), te.getFrontFacing());
    }

    public void setOutputs(byte vanillaSignal, int fineSignal, byte[] allSignals) {
        byte cappedVanilla = (byte) Math.max(0, Math.min(15, vanillaSignal));
        int cappedFine = Math.max(0, Math.min(255, fineSignal));

        boolean changed = (mOutputStrength != cappedVanilla) || (mFineOutputStrength != cappedFine);
        this.mOutputStrength = cappedVanilla;
        this.mFineOutputStrength = cappedFine;

        byte fineByte = (byte) (cappedFine & 0xFF);
        byte[] prevBundled = mBundledSignal.clone();

        if (mBundledChannelMode == BUNDLED_MODE_MULTI) {
            for (int i = 0; i < 15; i++) {
                mBundledSignal[i] = (allSignals != null && i < allSignals.length) ? allSignals[i] : 0;
            }
            mBundledSignal[15] = fineByte;
        } else if (mBundledChannelMode == BUNDLED_MODE_BROADCAST) {
            Arrays.fill(mBundledSignal, fineByte);
        } else {
            int targetCh = mBundledChannelMode - 2;
            Arrays.fill(mBundledSignal, (byte) 0);
            if (targetCh >= 0 && targetCh < 16) {
                mBundledSignal[targetCh] = fineByte;
            }
        }

        if (!Arrays.equals(prevBundled, mBundledSignal)) {
            changed = true;
        }

        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te != null) {
            ForgeDirection facing = te.getFrontFacing();
            for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
                te.setOutputRedstoneSignal(side, side == facing ? mOutputStrength : (byte) 0);
            }
            if (changed) {
                te.issueBlockUpdate();
            }
        }
    }

    public void setOutputRedstone(byte signal) {
        setOutputs(signal, (int) Math.round(((double) (signal & 0xFF) / 15.0) * 255.0), null);
    }

    public void setOutputStrengthDirect(byte signal) {
        this.mOutputStrength = signal;
    }

    @Override
    public boolean doesEmptyContainers() {
        return false;
    }

    @Override
    public boolean doesFillContainers() {
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
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection aSide,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection aSide,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowGeneralRedstoneOutput() {
        return true;
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
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        ForgeDirection facing = aBaseMetaTileEntity.getFrontFacing();
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            aBaseMetaTileEntity.setOutputRedstoneSignal(side, side == facing ? mOutputStrength : (byte) 0);
        }
    }

    @Override
    public void onScrewdriverRightClick(ForgeDirection side, EntityPlayer aPlayer, float aX, float aY, float aZ,
        ItemStack aTool) {
        if (aPlayer != null && aPlayer.isSneaking()) {
            cycleBundledMode(1);
            GTUtility
                .sendChatToPlayer(aPlayer, "Control hatch bundled mode: " + getBundledModeName(mBundledChannelMode));
        } else {
            cycleMetric(1);
            GTUtility.sendChatToPlayer(aPlayer, "Control hatch metric: " + getMetricName(mMetric));
        }
    }

    @Override
    public boolean onSolderingToolRightClick(ForgeDirection side, ForgeDirection wrenchingSide, EntityPlayer aPlayer,
        float aX, float aY, float aZ, ItemStack aTool) {
        cycleStatistic(1);
        GTUtility.sendChatToPlayer(aPlayer, "Control hatch statistic: " + getStatisticDisplayName(mStatistic));
        return true;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (aPlayer != null && aPlayer.getHeldItem() != null && mReactor != null) {
            if (mReactor.handleSensorCardLinking(aPlayer.getHeldItem(), aPlayer, mReactor.getBaseMetaTileEntity())) {
                return true;
            }
        }
        openGui(aPlayer);
        return true;
    }

    @Override
    protected boolean useMui2() {
        return false;
    }

    @Override
    public void addUIWidgets(ModularWindow.Builder builder, UIBuildContext buildContext) {
        builder.widget(
            new DrawableWidget().setDrawable(GTUITextures.PICTURE_SCREEN_BLACK)
                .setPos(7, 16)
                .setSize(162, 75))
            .widget(
                new TextWidget("Nuclear control hatch").setDefaultColor(Color.rgb(0, 255, 128))
                    .setPos(12, 20))
            // Row 1: Metric
            .widget(
                new ButtonWidget().setOnClick((clickData, widget) -> cycleMetric(-1))
                    .setBackground(() -> new IDrawable[] { GTUITextures.BUTTON_STANDARD })
                    .addTooltip("Previous metric")
                    .setPos(12, 32)
                    .setSize(12, 12))
            .widget(
                new TextWidget(Text.localised("<")).setTextAlignment(Alignment.Center)
                    .setPos(12, 34)
                    .setSize(12, 10))
            .widget(
                new TextWidget().setStringSupplier(() -> "Metric: " + getMetricName(mMetric))
                    .setDefaultColor(Color.rgb(100, 200, 255))
                    .setPos(28, 34))
            .widget(
                new ButtonWidget().setOnClick((clickData, widget) -> cycleMetric(1))
                    .setBackground(() -> new IDrawable[] { GTUITextures.BUTTON_STANDARD })
                    .addTooltip("Next metric")
                    .setPos(153, 32)
                    .setSize(12, 12))
            .widget(
                new TextWidget(Text.localised(">")).setTextAlignment(Alignment.Center)
                    .setPos(153, 34)
                    .setSize(12, 10))
            // Row 2: Statistic
            .widget(
                new ButtonWidget().setOnClick((clickData, widget) -> cycleStatistic(-1))
                    .setBackground(() -> new IDrawable[] { GTUITextures.BUTTON_STANDARD })
                    .addTooltip("Previous statistic")
                    .setPos(12, 47)
                    .setSize(12, 12))
            .widget(
                new TextWidget(Text.localised("<")).setTextAlignment(Alignment.Center)
                    .setPos(12, 49)
                    .setSize(12, 10))
            .widget(
                new TextWidget().setStringSupplier(() -> "Stat: " + getStatisticDisplayName(mStatistic))
                    .setDefaultColor(Color.rgb(255, 220, 100))
                    .setPos(28, 49))
            .widget(
                new ButtonWidget().setOnClick((clickData, widget) -> cycleStatistic(1))
                    .setBackground(() -> new IDrawable[] { GTUITextures.BUTTON_STANDARD })
                    .addTooltip("Next statistic")
                    .setPos(153, 47)
                    .setSize(12, 12))
            .widget(
                new TextWidget(Text.localised(">")).setTextAlignment(Alignment.Center)
                    .setPos(153, 49)
                    .setSize(12, 10))
            // Row 3: Output Signal
            .widget(new TextWidget().setStringSupplier(() -> {
                if (isInterfacingProjectRed()) {
                    return String.format("Output: %d / 15 (PR: %d / 255)", mOutputStrength, mFineOutputStrength);
                }
                return String.format("Output: %d / 15 (Fine: %d / 255)", mOutputStrength, mFineOutputStrength);
            })
                .setDefaultColor(Color.rgb(255, 80, 80))
                .setPos(12, 62))
            // Row 4: ProjectRed Bundled Cable Mode
            .widget(
                new ButtonWidget().setOnClick((clickData, widget) -> cycleBundledMode(-1))
                    .setBackground(() -> new IDrawable[] { GTUITextures.BUTTON_STANDARD })
                    .addTooltip("Previous bundled mode")
                    .setPos(12, 74)
                    .setSize(12, 12))
            .widget(
                new TextWidget(Text.localised("<")).setTextAlignment(Alignment.Center)
                    .setPos(12, 76)
                    .setSize(12, 10))
            .widget(
                new TextWidget().setStringSupplier(() -> "PR: " + getBundledModeName(mBundledChannelMode))
                    .setDefaultColor(Color.rgb(200, 160, 255))
                    .setPos(28, 76))
            .widget(
                new ButtonWidget().setOnClick((clickData, widget) -> cycleBundledMode(1))
                    .setBackground(() -> new IDrawable[] { GTUITextures.BUTTON_STANDARD })
                    .addTooltip("Next bundled mode")
                    .setPos(153, 74)
                    .setSize(12, 12))
            .widget(
                new TextWidget(Text.localised(">")).setTextAlignment(Alignment.Center)
                    .setPos(153, 76)
                    .setSize(12, 10))
            // Syncers
            .widget(new FakeSyncWidget.IntegerSyncer(this::getMetric, this::setMetric))
            .widget(new FakeSyncWidget.IntegerSyncer(this::getStatistic, this::setStatistic))
            .widget(new FakeSyncWidget.ByteSyncer(this::getOutputStrength, this::setOutputStrengthDirect))
            .widget(new FakeSyncWidget.IntegerSyncer(this::getFineOutputStrength, this::setFineOutputStrengthDirect))
            .widget(new FakeSyncWidget.IntegerSyncer(this::getBundledChannelMode, this::setBundledChannelMode));
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setInteger("mMetric", mMetric);
        aNBT.setInteger("mStatistic", mStatistic);
        aNBT.setInteger("mMode", getMode());
        aNBT.setByte("mOutputStrength", mOutputStrength);
        aNBT.setInteger("mFineOutputStrength", mFineOutputStrength);
        aNBT.setInteger("mBundledChannelMode", mBundledChannelMode);
        aNBT.setByteArray("mBundledSignal", mBundledSignal);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("mMetric")) {
            mMetric = aNBT.getInteger("mMetric");
            mStatistic = aNBT.getInteger("mStatistic");
        } else if (aNBT.hasKey("mMode")) {
            setMode(aNBT.getInteger("mMode"));
        }
        mOutputStrength = aNBT.getByte("mOutputStrength");
        if (aNBT.hasKey("mFineOutputStrength")) {
            mFineOutputStrength = aNBT.getInteger("mFineOutputStrength");
        }
        if (aNBT.hasKey("mBundledChannelMode")) {
            mBundledChannelMode = aNBT.getInteger("mBundledChannelMode");
        }
        if (aNBT.hasKey("mBundledSignal")) {
            byte[] arr = aNBT.getByteArray("mBundledSignal");
            if (arr != null && arr.length == 16) {
                System.arraycopy(arr, 0, mBundledSignal, 0, 16);
            }
        }
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_HATCH_SPLITTER_REDSTONE),
            TextureFactory.builder()
                .addIcon(Textures.BlockIcons.OVERLAY_HATCH_SPLITTER_REDSTONE_GLOW)
                .glow()
                .build() };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_HATCH_SPLITTER_REDSTONE) };
    }
}
