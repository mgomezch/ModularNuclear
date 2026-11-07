package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import net.minecraft.nbt.NBTTagCompound;

/** The reactor's saved state: its flags, temperatures, damage, structure size, and last cycle's readings. */
final class NuclearReactorNbt {

    private NuclearReactorNbt() {}

    static void save(MTENuclearReactor r, NBTTagCompound aNBT) {
        aNBT.setBoolean("mWorldSaved", true);
        aNBT.setBoolean("mScram", r.mScram);
        aNBT.setBoolean("mMoltenDown", r.mMoltenDown);
        aNBT.setDouble("mCoreTemp", r.mCoreTemp);
        aNBT.setDouble("mAvgTemp", r.mAvgTemp);
        aNBT.setDouble("mReactorDamage", r.mReactorDamage);
        aNBT.setInteger("gridSize", r.gridSize);
        aNBT.setInteger("coreDimension", r.coreDimension);
        aNBT.setInteger("mPipeTier", r.mPipeTier);
        aNBT.setLong("mDirectPowerEUt", r.mDirectPowerEUt);
        aNBT.setLong("mWallNeutronAccumulator", r.mWallNeutronAccumulator);
        aNBT.setInteger("mWallMaintenanceTimer", r.mWallMaintenanceTimer);
        aNBT.setInteger("mOutputCoolantRate", r.mOutputCoolantRate);
        aNBT.setString("mOutputCoolantName", r.mOutputCoolantName != null ? r.mOutputCoolantName : "");

        aNBT.setDouble("mMinTileTemp", r.mMinTileTemp);
        aNBT.setDouble("mMaxTileTemp", r.mMaxTileTemp);
        aNBT.setDouble("mAvgTileTemp", r.mAvgTileTemp);
        aNBT.setDouble("mMinCoolantItemDur", r.mMinCoolantItemDur);
        aNBT.setDouble("mMaxCoolantItemDur", r.mMaxCoolantItemDur);
        aNBT.setDouble("mAvgCoolantItemDur", r.mAvgCoolantItemDur);
        aNBT.setDouble("mMinFuelItemDur", r.mMinFuelItemDur);
        aNBT.setDouble("mMaxFuelItemDur", r.mMaxFuelItemDur);
        aNBT.setDouble("mAvgFuelItemDur", r.mAvgFuelItemDur);
        aNBT.setDouble("mMinCoolantHatchFill", r.mMinCoolantHatchFill);
        aNBT.setDouble("mMaxCoolantHatchFill", r.mMaxCoolantHatchFill);
        aNBT.setDouble("mAvgCoolantHatchFill", r.mAvgCoolantHatchFill);
        aNBT.setDouble("mMinFuelHatchFill", r.mMinFuelHatchFill);
        aNBT.setDouble("mMaxFuelHatchFill", r.mMaxFuelHatchFill);
        aNBT.setDouble("mAvgFuelHatchFill", r.mAvgFuelHatchFill);

        aNBT.setInteger("mTotalFuelItems", r.mTotalFuelItems);
        aNBT.setInteger("mTotalCoolantItems", r.mTotalCoolantItems);
        aNBT.setLong("mTotalCoolantFluid", r.mTotalCoolantFluid);
        aNBT.setLong("mTotalCoolantCapacity", r.mTotalCoolantCapacity);
        aNBT.setLong("mTotalFuelFluid", r.mTotalFuelFluid);
        aNBT.setLong("mTotalFuelCapacity", r.mTotalFuelCapacity);
        aNBT.setInteger("mCoolantHatchCount", r.mCoolantHatchCount);
        aNBT.setInteger("mFuelHatchCount", r.mFuelHatchCount);

        aNBT.setInteger("mZeroedCoolantItemsLastCycle", r.mZeroedCoolantItemsLastCycle);
        aNBT.setInteger("mZeroedFuelItemsLastCycle", r.mZeroedFuelItemsLastCycle);
        aNBT.setLong("mConsumedCoolantLastCycle", r.mConsumedCoolantLastCycle);
        aNBT.setLong("mProducedHotCoolantLastCycle", r.mProducedHotCoolantLastCycle);
        aNBT.setLong("mTransmutationLossLastCycle", r.mTransmutationLossLastCycle);
        aNBT.setLong("mTransmutationByproductsLastCycle", r.mTransmutationByproductsLastCycle);
        aNBT.setLong("mDepletedLiquidFuelLastCycle", r.mDepletedLiquidFuelLastCycle);
    }

    static void load(MTENuclearReactor r, NBTTagCompound aNBT) {
        if (aNBT.hasKey("display", 10)) {
            NBTTagCompound display = aNBT.getCompoundTag("display");
            if (display.hasKey("Name", 8)) {
                String name = display.getString("Name")
                    .trim();
                r.mCustomName = !name.isEmpty() ? name : null;
            } else {
                r.mCustomName = null;
            }
        } else {
            r.mCustomName = null;
        }
        r.mWorldSaved = aNBT.getBoolean("mWorldSaved");
        if (!r.mWorldSaved) {
            r.mWrench = false;
            r.mScrewdriver = false;
            r.mSoftMallet = false;
            r.mHardHammer = false;
            r.mSolderingTool = false;
            r.mCrowbar = false;
        }
        r.mScram = aNBT.getBoolean("mScram");
        if (aNBT.hasKey("mMoltenDown")) {
            r.mMoltenDown = aNBT.getBoolean("mMoltenDown");
        }
        if (aNBT.hasKey("mCoreTemp")) {
            r.mCoreTemp = aNBT.getDouble("mCoreTemp");
            r.mAvgTemp = aNBT.getDouble("mAvgTemp");
        } else {
            r.mCoreTemp = r.getAmbientTemperature();
            r.mAvgTemp = r.getAmbientTemperature();
        }
        if (aNBT.hasKey("mReactorDamage")) {
            r.mReactorDamage = aNBT.getDouble("mReactorDamage");
        } else {
            r.mReactorDamage = 0.0;
        }
        r.gridSize = aNBT.getInteger("gridSize");
        r.coreDimension = aNBT.getInteger("coreDimension");
        r.mPipeTier = aNBT.getInteger("mPipeTier");
        r.mDirectPowerEUt = aNBT.getLong("mDirectPowerEUt");
        r.mWallNeutronAccumulator = aNBT.getLong("mWallNeutronAccumulator");
        r.mWallMaintenanceTimer = aNBT.getInteger("mWallMaintenanceTimer");
        r.mOutputCoolantRate = aNBT.getInteger("mOutputCoolantRate");
        r.mOutputCoolantName = aNBT.getString("mOutputCoolantName");

        r.mMinTileTemp = aNBT.getDouble("mMinTileTemp");
        r.mMaxTileTemp = aNBT.getDouble("mMaxTileTemp");
        r.mAvgTileTemp = aNBT.getDouble("mAvgTileTemp");
        r.mMinCoolantItemDur = aNBT.getDouble("mMinCoolantItemDur");
        r.mMaxCoolantItemDur = aNBT.getDouble("mMaxCoolantItemDur");
        r.mAvgCoolantItemDur = aNBT.getDouble("mAvgCoolantItemDur");
        r.mMinFuelItemDur = aNBT.getDouble("mMinFuelItemDur");
        r.mMaxFuelItemDur = aNBT.getDouble("mMaxFuelItemDur");
        r.mAvgFuelItemDur = aNBT.getDouble("mAvgFuelItemDur");
        r.mMinCoolantHatchFill = aNBT.getDouble("mMinCoolantHatchFill");
        r.mMaxCoolantHatchFill = aNBT.getDouble("mMaxCoolantHatchFill");
        r.mAvgCoolantHatchFill = aNBT.getDouble("mAvgCoolantHatchFill");
        r.mMinFuelHatchFill = aNBT.getDouble("mMinFuelHatchFill");
        r.mMaxFuelHatchFill = aNBT.getDouble("mMaxFuelHatchFill");
        r.mAvgFuelHatchFill = aNBT.getDouble("mAvgFuelHatchFill");

        r.mTotalFuelItems = aNBT.getInteger("mTotalFuelItems");
        r.mTotalCoolantItems = aNBT.getInteger("mTotalCoolantItems");
        r.mTotalCoolantFluid = aNBT.getLong("mTotalCoolantFluid");
        r.mTotalCoolantCapacity = aNBT.getLong("mTotalCoolantCapacity");
        r.mTotalFuelFluid = aNBT.getLong("mTotalFuelFluid");
        r.mTotalFuelCapacity = aNBT.getLong("mTotalFuelCapacity");
        r.mCoolantHatchCount = aNBT.getInteger("mCoolantHatchCount");
        r.mFuelHatchCount = aNBT.getInteger("mFuelHatchCount");

        r.mZeroedCoolantItemsLastCycle = aNBT.getInteger("mZeroedCoolantItemsLastCycle");
        r.mZeroedFuelItemsLastCycle = aNBT.getInteger("mZeroedFuelItemsLastCycle");
        r.mConsumedCoolantLastCycle = aNBT.getLong("mConsumedCoolantLastCycle");
        r.mProducedHotCoolantLastCycle = aNBT.getLong("mProducedHotCoolantLastCycle");
        r.mTransmutationLossLastCycle = aNBT.getLong("mTransmutationLossLastCycle");
        r.mTransmutationByproductsLastCycle = aNBT.getLong("mTransmutationByproductsLastCycle");
        r.mDepletedLiquidFuelLastCycle = aNBT.getLong("mDepletedLiquidFuelLastCycle");
    }

    /** Saves the custom name as an item's display name, or removes it. */
    static void saveCustomName(MTENuclearReactor r, NBTTagCompound aNBT) {
        if (r.hasCustomName()) {
            NBTTagCompound display;
            if (aNBT.hasKey("display", 10)) {
                display = aNBT.getCompoundTag("display");
            } else {
                display = new NBTTagCompound();
                aNBT.setTag("display", display);
            }
            display.setString("Name", r.mCustomName);
        } else if (aNBT.hasKey("display", 10)) {
            NBTTagCompound display = aNBT.getCompoundTag("display");
            display.removeTag("Name");
            if (display.hasNoTags()) {
                aNBT.removeTag("display");
            }
        }
    }
}
