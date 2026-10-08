package com.gtnewhorizons.modularnuclear.common.nuclearcontrol;

import static com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil.formatNumber;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControl;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.MTENuclearReactor;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import shedar.mods.ic2.nuclearcontrol.api.CardState;
import shedar.mods.ic2.nuclearcontrol.api.DisplaySettingHelper;
import shedar.mods.ic2.nuclearcontrol.api.ICardWrapper;
import shedar.mods.ic2.nuclearcontrol.api.IRemoteSensor;
import shedar.mods.ic2.nuclearcontrol.api.NewPanelSetting;
import shedar.mods.ic2.nuclearcontrol.api.PanelSetting;
import shedar.mods.ic2.nuclearcontrol.api.PanelString;
import shedar.mods.ic2.nuclearcontrol.items.ItemCardBase;
import shedar.mods.ic2.nuclearcontrol.panel.CardWrapperImpl;

public class ItemCardModularNuclear extends ItemCardBase implements IRemoteSensor {

    public static final UUID CARD_TYPE = UUID.fromString("6a9c1e7a-3f44-482a-9e45-5d9c72e2db4e");

    public static final int DISPLAY_STATUS = 1;
    public static final int DISPLAY_TEMP = 2;
    public static final int DISPLAY_COOLANT_DUR = 3;
    public static final int DISPLAY_FUEL_DUR = 4;
    public static final int DISPLAY_COOLANT_FILL = 5;
    public static final int DISPLAY_FUEL_FILL = 6;
    public static final int DISPLAY_ITEM_COUNTS = 7;
    public static final int DISPLAY_FLUID_AMOUNTS = 8;
    public static final int DISPLAY_HATCH_COUNTS = 9;
    public static final int DISPLAY_CYCLE_DEPLETION = 10;
    public static final int DISPLAY_CYCLE_COOLANT = 11;
    public static final int DISPLAY_CYCLE_TRANSMUTATION = 12;

    public static final int COLOR_ONLINE = 0x00FF00;
    public static final int COLOR_OFFLINE = 0xFF0000;
    public static final int COLOR_WHITE = 0xFFFFFF;
    public static final int COLOR_CYAN = 0x55FFFF;
    public static final int COLOR_ORANGE = 0xFFAA00;
    public static final int COLOR_YELLOW = 0xFFFF55;
    public static final int COLOR_PURPLE = 0xDDA0DD;
    public static final int COLOR_GRAY = 0xAAAAAA;

    public ItemCardModularNuclear() {
        super("card_modular_nuclear");
        setUnlocalizedName("modularnuclear.card_modular_nuclear");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister iconRegister) {
        this.itemIcon = iconRegister.registerIcon("modularnuclear:card_modular_nuclear");
    }

    @Override
    public UUID getCardType() {
        return CARD_TYPE;
    }

    @Override
    public List<PanelSetting> getSettingsList() {
        List<PanelSetting> list = new ArrayList<>(12);
        list.add(new NewPanelSetting("Reactor Status", DISPLAY_STATUS, CARD_TYPE));
        list.add(new NewPanelSetting("Temperature (Min/Avg/Max)", DISPLAY_TEMP, CARD_TYPE));
        list.add(new NewPanelSetting("Coolant Item Durability", DISPLAY_COOLANT_DUR, CARD_TYPE));
        list.add(new NewPanelSetting("Fuel Item Durability", DISPLAY_FUEL_DUR, CARD_TYPE));
        list.add(new NewPanelSetting("Coolant Hatch Fill %", DISPLAY_COOLANT_FILL, CARD_TYPE));
        list.add(new NewPanelSetting("Fuel Hatch Fill %", DISPLAY_FUEL_FILL, CARD_TYPE));
        list.add(new NewPanelSetting("Fuel/Coolant Item Counts", DISPLAY_ITEM_COUNTS, CARD_TYPE));
        list.add(new NewPanelSetting("Fuel/Coolant Fluid Volumes", DISPLAY_FLUID_AMOUNTS, CARD_TYPE));
        list.add(new NewPanelSetting("Core Hatch Allocation", DISPLAY_HATCH_COUNTS, CARD_TYPE));
        list.add(new NewPanelSetting("Last Cycle: Depleted Items & Fuel", DISPLAY_CYCLE_DEPLETION, CARD_TYPE));
        list.add(new NewPanelSetting("Last Cycle: Coolant In/Out", DISPLAY_CYCLE_COOLANT, CARD_TYPE));
        list.add(new NewPanelSetting("Last Cycle: Transmutation", DISPLAY_CYCLE_TRANSMUTATION, CARD_TYPE));
        return list;
    }

    @Override
    public CardState update(TileEntity panelTe, ICardWrapper cardWrapper, int range) {
        ChunkCoordinates target = cardWrapper.getTarget();
        if (target == null) return CardState.NO_TARGET;

        if (panelTe != null && range > 0) {
            double dx = panelTe.xCoord - target.posX;
            double dy = panelTe.yCoord - target.posY;
            double dz = panelTe.zCoord - target.posZ;
            if (dx * dx + dy * dy + dz * dz > (double) range * range) {
                return CardState.OUT_OF_RANGE;
            }
        }

        World world = panelTe != null ? panelTe.getWorldObj() : null;
        if (world == null) return CardState.NO_TARGET;
        return update(world, cardWrapper, range);
    }

    @Override
    public CardState update(World world, ICardWrapper cardWrapper, int range) {
        ChunkCoordinates target = cardWrapper.getTarget();
        if (target == null) return CardState.NO_TARGET;

        if (!world.blockExists(target.posX, target.posY, target.posZ)) {
            return CardState.OUT_OF_RANGE;
        }

        TileEntity te = world.getTileEntity(target.posX, target.posY, target.posZ);
        MTENuclearReactor reactor = null;
        if (te instanceof IGregTechTileEntity gt) {
            IMetaTileEntity mte = gt.getMetaTileEntity();
            if (mte instanceof MTENuclearReactor r) {
                reactor = r;
            } else if (mte instanceof MTEHatchNuclearHatch hatch && hatch.mReactor != null) {
                reactor = hatch.mReactor;
            } else if (mte instanceof MTEHatchNuclearBus bus && bus.mReactor != null) {
                reactor = bus.mReactor;
            } else if (mte instanceof MTEHatchNuclearControl ctrl && ctrl.mReactor != null) {
                reactor = ctrl.mReactor;
            } else if (mte instanceof MTEHatchNuclearControlRod rod && rod.mReactor != null) {
                reactor = rod.mReactor;
            }
        }

        if (reactor == null || !reactor.mMachine) {
            cardWrapper.setBoolean("isOnline", false);
            return CardState.CUSTOM_ERROR;
        }

        reactor.calculateTelemetry();

        cardWrapper.setBoolean(
            "isOnline",
            reactor.getBaseMetaTileEntity() != null && reactor.getBaseMetaTileEntity()
                .isActive());
        cardWrapper.setDouble("tempMin", reactor.mMinTileTemp);
        cardWrapper.setDouble("tempMax", reactor.mMaxTileTemp);
        cardWrapper.setDouble("tempAvg", reactor.mAvgTileTemp);

        cardWrapper.setDouble("coolItemDurMin", reactor.mMinCoolantItemDur);
        cardWrapper.setDouble("coolItemDurMax", reactor.mMaxCoolantItemDur);
        cardWrapper.setDouble("coolItemDurAvg", reactor.mAvgCoolantItemDur);

        cardWrapper.setDouble("fuelItemDurMin", reactor.mMinFuelItemDur);
        cardWrapper.setDouble("fuelItemDurMax", reactor.mMaxFuelItemDur);
        cardWrapper.setDouble("fuelItemDurAvg", reactor.mAvgFuelItemDur);

        cardWrapper.setDouble("coolHatchFillMin", reactor.mMinCoolantHatchFill);
        cardWrapper.setDouble("coolHatchFillMax", reactor.mMaxCoolantHatchFill);
        cardWrapper.setDouble("coolHatchFillAvg", reactor.mAvgCoolantHatchFill);

        cardWrapper.setDouble("fuelHatchFillMin", reactor.mMinFuelHatchFill);
        cardWrapper.setDouble("fuelHatchFillMax", reactor.mMaxFuelHatchFill);
        cardWrapper.setDouble("fuelHatchFillAvg", reactor.mAvgFuelHatchFill);

        cardWrapper.setInt("totalFuelItems", reactor.mTotalFuelItems);
        cardWrapper.setInt("totalCoolantItems", reactor.mTotalCoolantItems);

        cardWrapper.setLong("totalCoolantFluid", reactor.mTotalCoolantFluid);
        cardWrapper.setLong("totalCoolantCapacity", reactor.mTotalCoolantCapacity);
        cardWrapper.setLong("totalFuelFluid", reactor.mTotalFuelFluid);
        cardWrapper.setLong("totalFuelCapacity", reactor.mTotalFuelCapacity);

        cardWrapper.setInt("coolantHatchCount", reactor.mCoolantHatchCount);
        cardWrapper.setInt("fuelHatchCount", reactor.mFuelHatchCount);

        cardWrapper.setInt("zeroedCoolantLastCycle", reactor.mZeroedCoolantItemsLastCycle);
        cardWrapper.setInt("zeroedFuelLastCycle", reactor.mZeroedFuelItemsLastCycle);
        cardWrapper.setLong("consumedCoolantLastCycle", reactor.mConsumedCoolantLastCycle);
        cardWrapper.setLong("producedHotCoolantLastCycle", reactor.mProducedHotCoolantLastCycle);
        cardWrapper.setLong("transmutationLossLastCycle", reactor.mTransmutationLossLastCycle);
        cardWrapper.setLong("transmutationByproductsLastCycle", reactor.mTransmutationByproductsLastCycle);
        cardWrapper.setLong("depletedLiquidFuelLastCycle", reactor.mDepletedLiquidFuelLastCycle);

        return CardState.OK;
    }

    private static double getDouble(ICardWrapper wrapper, String key) {
        Double val = wrapper.getDouble(key);
        return val != null ? val : 0.0;
    }

    private static int getInt(ICardWrapper wrapper, String key) {
        Integer val = wrapper.getInt(key);
        return val != null ? val : 0;
    }

    private static long getLong(ICardWrapper wrapper, String key) {
        Long val = wrapper.getLong(key);
        return val != null ? val : 0L;
    }

    private PanelString createLine(String left, String right, int colorLeft, int colorRight) {
        PanelString s = new PanelString();
        s.textLeft = left;
        s.textRight = right;
        s.colorLeft = colorLeft;
        s.colorRight = colorRight;
        return s;
    }

    @Override
    public List<PanelString> getStringData(DisplaySettingHelper helper, ICardWrapper cardWrapper, boolean isLeft) {
        List<PanelString> list = new LinkedList<>();
        if (cardWrapper == null) return list;

        CardState state = cardWrapper.getState();
        if (state == CardState.NO_TARGET) {
            list.add(createLine("MPTR Reactor", "NO TARGET", COLOR_WHITE, COLOR_OFFLINE));
            return list;
        } else if (state == CardState.OUT_OF_RANGE) {
            list.add(createLine("MPTR Reactor", "OUT OF RANGE", COLOR_WHITE, COLOR_OFFLINE));
            return list;
        } else if (state == CardState.CUSTOM_ERROR) {
            list.add(createLine("MPTR Reactor", "UNFORMED/OFFLINE", COLOR_WHITE, COLOR_ORANGE));
            return list;
        }

        boolean isOnline = Boolean.TRUE.equals(cardWrapper.getBoolean("isOnline"));

        if (helper.getSetting(DISPLAY_STATUS)) {
            list.add(
                createLine(
                    "MPTR Reactor",
                    isOnline ? "ONLINE" : "OFFLINE",
                    COLOR_WHITE,
                    isOnline ? COLOR_ONLINE : COLOR_OFFLINE));
        }

        if (helper.getSetting(DISPLAY_TEMP)) {
            double minT = getDouble(cardWrapper, "tempMin");
            double avgT = getDouble(cardWrapper, "tempAvg");
            double maxT = getDouble(cardWrapper, "tempMax");
            int color = maxT < 300.0 ? COLOR_ONLINE
                : maxT < 800.0 ? COLOR_YELLOW : maxT < 1200.0 ? COLOR_ORANGE : COLOR_OFFLINE;
            list.add(
                createLine(
                    "Temperature (Min/Avg/Max):",
                    String.format("%.0f / %.0f / %.0f °C", minT, avgT, maxT),
                    COLOR_WHITE,
                    color));
        }

        if (helper.getSetting(DISPLAY_COOLANT_DUR)) {
            int items = getInt(cardWrapper, "totalCoolantItems");
            if (items > 0) {
                double min = getDouble(cardWrapper, "coolItemDurMin");
                double avg = getDouble(cardWrapper, "coolItemDurAvg");
                double max = getDouble(cardWrapper, "coolItemDurMax");
                list.add(
                    createLine(
                        "Coolant Item Dur (Min/Avg/Max):",
                        String.format("%.1f%% / %.1f%% / %.1f%%", min, avg, max),
                        COLOR_WHITE,
                        COLOR_CYAN));
            } else {
                list.add(createLine("Coolant Item Durability:", "NO ITEMS", COLOR_WHITE, COLOR_GRAY));
            }
        }

        if (helper.getSetting(DISPLAY_FUEL_DUR)) {
            int items = getInt(cardWrapper, "totalFuelItems");
            if (items > 0) {
                double min = getDouble(cardWrapper, "fuelItemDurMin");
                double avg = getDouble(cardWrapper, "fuelItemDurAvg");
                double max = getDouble(cardWrapper, "fuelItemDurMax");
                int color = min > 50.0 ? COLOR_ONLINE : min > 20.0 ? COLOR_YELLOW : COLOR_OFFLINE;
                list.add(
                    createLine(
                        "Fuel Item Dur (Min/Avg/Max):",
                        String.format("%.1f%% / %.1f%% / %.1f%%", min, avg, max),
                        COLOR_WHITE,
                        color));
            } else {
                list.add(createLine("Fuel Item Durability:", "NO RODS", COLOR_WHITE, COLOR_GRAY));
            }
        }

        if (helper.getSetting(DISPLAY_COOLANT_FILL)) {
            int count = getInt(cardWrapper, "coolantHatchCount");
            if (count > 0) {
                double min = getDouble(cardWrapper, "coolHatchFillMin");
                double avg = getDouble(cardWrapper, "coolHatchFillAvg");
                double max = getDouble(cardWrapper, "coolHatchFillMax");
                list.add(
                    createLine(
                        "Coolant Fill (Min/Avg/Max):",
                        String.format("%.1f%% / %.1f%% / %.1f%%", min, avg, max),
                        COLOR_WHITE,
                        COLOR_CYAN));
            } else {
                list.add(createLine("Coolant Hatch Fill %:", "NO HATCHES", COLOR_WHITE, COLOR_GRAY));
            }
        }

        if (helper.getSetting(DISPLAY_FUEL_FILL)) {
            int count = getInt(cardWrapper, "fuelHatchCount");
            if (count > 0) {
                double min = getDouble(cardWrapper, "fuelHatchFillMin");
                double avg = getDouble(cardWrapper, "fuelHatchFillAvg");
                double max = getDouble(cardWrapper, "fuelHatchFillMax");
                list.add(
                    createLine(
                        "Fuel Fill (Min/Avg/Max):",
                        String.format("%.1f%% / %.1f%% / %.1f%%", min, avg, max),
                        COLOR_WHITE,
                        COLOR_ORANGE));
            } else {
                list.add(createLine("Fuel Hatch Fill %:", "NO HATCHES", COLOR_WHITE, COLOR_GRAY));
            }
        }

        if (helper.getSetting(DISPLAY_ITEM_COUNTS)) {
            int fuel = getInt(cardWrapper, "totalFuelItems");
            int cool = getInt(cardWrapper, "totalCoolantItems");
            list.add(
                createLine(
                    "Core Items (Fuel / Coolant):",
                    String.format("%d / %d", fuel, cool),
                    COLOR_WHITE,
                    COLOR_YELLOW));
        }

        if (helper.getSetting(DISPLAY_FLUID_AMOUNTS)) {
            long coolAmt = getLong(cardWrapper, "totalCoolantFluid");
            long coolCap = getLong(cardWrapper, "totalCoolantCapacity");
            long fuelAmt = getLong(cardWrapper, "totalFuelFluid");
            long fuelCap = getLong(cardWrapper, "totalFuelCapacity");
            list.add(
                createLine(
                    "Coolant Fluid:",
                    String.format("%s / %s L", formatNumber(coolAmt), formatNumber(coolCap)),
                    COLOR_WHITE,
                    COLOR_CYAN));
            list.add(
                createLine(
                    "Fuel Fluid:",
                    String.format("%s / %s L", formatNumber(fuelAmt), formatNumber(fuelCap)),
                    COLOR_WHITE,
                    COLOR_ORANGE));
        }

        if (helper.getSetting(DISPLAY_HATCH_COUNTS)) {
            int coolH = getInt(cardWrapper, "coolantHatchCount");
            int fuelH = getInt(cardWrapper, "fuelHatchCount");
            list.add(
                createLine(
                    "Core Hatches (Coolant / Fuel):",
                    String.format("%d / %d", coolH, fuelH),
                    COLOR_WHITE,
                    COLOR_WHITE));
        }

        if (helper.getSetting(DISPLAY_CYCLE_DEPLETION)) {
            int zeroCool = getInt(cardWrapper, "zeroedCoolantLastCycle");
            int zeroFuel = getInt(cardWrapper, "zeroedFuelLastCycle");
            long depFuel = getLong(cardWrapper, "depletedLiquidFuelLastCycle");
            list.add(
                createLine(
                    "Cycle Depleted:",
                    String.format("Fuel: %d itm, %s L | Cool: %d itm", zeroFuel, formatNumber(depFuel), zeroCool),
                    COLOR_WHITE,
                    COLOR_YELLOW));
        }

        if (helper.getSetting(DISPLAY_CYCLE_COOLANT)) {
            long inCool = getLong(cardWrapper, "consumedCoolantLastCycle");
            long outCool = getLong(cardWrapper, "producedHotCoolantLastCycle");
            list.add(
                createLine(
                    "Cycle Coolant Turnover:",
                    String.format("-%s L in / +%s L out", formatNumber(inCool), formatNumber(outCool)),
                    COLOR_WHITE,
                    COLOR_CYAN));
        }

        if (helper.getSetting(DISPLAY_CYCLE_TRANSMUTATION)) {
            long loss = getLong(cardWrapper, "transmutationLossLastCycle");
            long byprod = getLong(cardWrapper, "transmutationByproductsLastCycle");
            list.add(
                createLine(
                    "Cycle Transmutation:",
                    String.format("Loss: -%s L | Byprod: +%s L", formatNumber(loss), formatNumber(byprod)),
                    COLOR_WHITE,
                    COLOR_PURPLE));
        }

        return list;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        super.addInformation(stack, player, list, advanced);
        ICardWrapper wrapper = new CardWrapperImpl(stack, -1);
        ChunkCoordinates target = wrapper.getTarget();
        if (target != null) {
            list.add(
                EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
                    "item.modularnuclear.sensorcard.target",
                    target.posX,
                    target.posY,
                    target.posZ));
        } else {
            list.add(
                EnumChatFormatting.RED + StatCollector.translateToLocal("item.modularnuclear.sensorcard.unlinked"));
        }
    }
}
