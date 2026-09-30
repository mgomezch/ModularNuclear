package com.gtnewhorizons.modularnuclear.common.textures;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.IIconContainer;

public class ModularNuclearTextures {

    public static class ModIcon implements IIconContainer, Runnable {

        protected IIcon mIcon;
        protected final String mIconName;
        protected final String mModID;

        public ModIcon(String aModID, String aIconName) {
            this.mModID = aModID;
            this.mIconName = aIconName;
            GregTechAPI.sGTBlockIconload.add(this);
        }

        @Override
        public IIcon getIcon() {
            return this.mIcon;
        }

        @Override
        public IIcon getOverlayIcon() {
            return null;
        }

        @Override
        public void run() {
            this.mIcon = GregTechAPI.sBlockIcons.registerIcon(this.mModID + ":" + this.mIconName);
        }

        @Override
        public ResourceLocation getTextureFile() {
            return TextureMap.locationBlocksTexture;
        }
    }

    public static final ModIcon OVERLAY_FRONT_FISSION_REACTOR = new ModIcon(
        "modularnuclear",
        "OVERLAY_FRONT_FISSION_REACTOR");
    public static final ModIcon OVERLAY_FRONT_FISSION_REACTOR_ACTIVE = new ModIcon(
        "modularnuclear",
        "OVERLAY_FRONT_FISSION_REACTOR_ACTIVE");
    public static final ModIcon MACHINE_CASING_NUCLEAR = new ModIcon("modularnuclear", "MACHINE_CASING_NUCLEAR");
}
