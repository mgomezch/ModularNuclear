package com.gtnewhorizons.modularnuclear.common.metatileentity.hatch;

import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.render.TextureFactory;

/**
 * Nuclear Core High-Pressure Input Hatch.
 *
 * Dedicated hermetic inlet endpoint bridging the top of the MPTR core (slice 0)
 * to closed coolant loops. Pairs strictly with a bottom outlet hatch of the same material in the same column.
 */
public class MTEHatchNuclearHighPressureInput extends MTEHatchNuclearHighPressure {

    public MTEHatchNuclearHighPressureInput(int aID, String aName, String aNameRegional, int aTier) {
        this(aID, aName, aNameRegional, aTier, -1);
    }

    /** A material-tiered hatch: aMaterialIndex indexes the coolant pipe materials, aTier is that material's tier. */
    public MTEHatchNuclearHighPressureInput(int aID, String aName, String aNameRegional, int aTier,
        int aMaterialIndex) {
        super(aID, aName, aNameRegional, aTier, aMaterialIndex);
        this.mIsInlet = true;
    }

    public MTEHatchNuclearHighPressureInput(String aName, int aTier, String[] aDescription, ITexture[][][] aTextures) {
        this(aName, aTier, -1, aDescription, aTextures);
    }

    public MTEHatchNuclearHighPressureInput(String aName, int aTier, int aMaterialIndex, String[] aDescription,
        ITexture[][][] aTextures) {
        super(aName, aTier, aMaterialIndex, aDescription, aTextures);
        this.mIsInlet = true;
    }

    @Override
    public String[] getDescription() {
        return describe("gt.blockmachines.hatch.nuclearhighpressure.input.desc");
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new MTEHatchNuclearHighPressureInput(mName, mTier, mMaterialIndex, mDescriptionArray, mTextures);
    }

    @Override
    public boolean isPassageInlet() {
        return true;
    }

    @Override
    public ITexture[] getTexturesActive(ITexture aBaseTexture) {
        return new ITexture[] { aBaseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_IN),
            TextureFactory.of(MTEHatchPressurizedFluid.OVERLAY_PRESSURIZED_IN) };
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
        return new ITexture[] { baseTexture, TextureFactory.of(Textures.BlockIcons.OVERLAY_PIPE_IN),
            TextureFactory.of(MTEHatchPressurizedFluid.OVERLAY_PRESSURIZED_IN) };
    }
}
