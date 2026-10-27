package com.gtnewhorizons.modularnuclear.common.metatileentity.hatch;

import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;

/**
 * Nuclear Core High-Pressure Output Hatch.
 *
 * Dedicated hermetic outlet endpoint bridging the bottom of the MPTR core (slice 4)
 * to closed coolant loops. Pairs strictly with a top inlet hatch of the same material in the same column.
 */
public class MTEHatchNuclearHighPressureOutput extends MTEHatchNuclearHighPressure {

    public MTEHatchNuclearHighPressureOutput(int aID, String aName, String aNameRegional, int aTier) {
        this(aID, aName, aNameRegional, aTier, -1);
    }

    /** A material-tiered hatch: aMaterialIndex indexes the coolant pipe materials, aTier is that material's tier. */
    public MTEHatchNuclearHighPressureOutput(int aID, String aName, String aNameRegional, int aTier,
        int aMaterialIndex) {
        super(aID, aName, aNameRegional, aTier, aMaterialIndex);
        this.mIsInlet = false;
    }

    public MTEHatchNuclearHighPressureOutput(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        this(aName, aTier, -1, aDescription, aTextures);
    }

    public MTEHatchNuclearHighPressureOutput(String aName, int aTier, int aMaterialIndex, String[] aDescription,
        ITexture[][][] aTextures) {
        super(aName, aTier, aMaterialIndex, aDescription, aTextures);
        this.mIsInlet = false;
    }

    @Override
    public String[] getDescription() {
        return describe("gt.blockmachines.hatch.nuclearhighpressure.output.desc");
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchNuclearHighPressureOutput(mName, mTier, mMaterialIndex, mDescriptionArray, mTextures);
    }

    @Override
    public boolean isPassageInlet() {
        return false;
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT),
            TextureFactory.of(MTEHatchPressurizedFluid.OVERLAY_PRESSURIZED_OUT) };
    }

    @Override
    public ITexture[] getTexturesInactive(ITexture aBaseTexture) {
        return getTexturesActive(aBaseTexture);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
        int colorIndex, boolean aActive, boolean redstoneLevel) {
        ITexture baseTexture = getCasingTexture();
        if (baseTexture == null) {
            baseTexture = Textures.BlockIcons.casingTexturePages[0][16];
        }
        if (side != aFacing) {
            return new ITexture[] { baseTexture };
        }
        return new ITexture[] { baseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_OUT),
            TextureFactory.of(MTEHatchPressurizedFluid.OVERLAY_PRESSURIZED_OUT) };
    }
}
