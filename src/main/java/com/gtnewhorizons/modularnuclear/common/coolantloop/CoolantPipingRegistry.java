package com.gtnewhorizons.modularnuclear.common.coolantloop;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDataRegistry;

import gregtech.api.enums.Materials;
import gregtech.api.metatileentity.implementations.MTEFluidPipe;

/**
 * Registry mapping GregTech fluid pipe materials and sizes to hydrodynamic
 * simulation properties (diameter, roughness, pressure ratings, and thermal limits).
 */
public class CoolantPipingRegistry {

    public static class PipeProperties {

        public final double diameter; // Inner diameter (m)
        public final double roughness; // Wall roughness (m)
        public final double maxPressureBar; // Bursting pressure limit (bar)
        public final double maxTemperatureCelsius;// Maximum safe operating temperature (deg C)
        public final double frictionMultiplier; // Pressure drop multiplier of the pipe material (pipes.yaml)

        public PipeProperties(double diameter, double roughness, double maxPressureBar, double maxTemperatureCelsius) {
            this(diameter, roughness, maxPressureBar, maxTemperatureCelsius, 1.0);
        }

        public PipeProperties(double diameter, double roughness, double maxPressureBar, double maxTemperatureCelsius,
            double frictionMultiplier) {
            this.diameter = diameter;
            this.roughness = roughness;
            this.maxPressureBar = maxPressureBar;
            this.maxTemperatureCelsius = maxTemperatureCelsius;
            this.frictionMultiplier = frictionMultiplier;
        }
    }

    /**
     * Resolves the inner flow diameter (meters) from GregTech pipe thickness.
     */
    public static double getDiameterForThickness(float thickness) {
        return CoolantLoopDataRegistry.getDiameterForThickness(thickness);
    }

    /**
     * Resolves the maximum burst pressure (bar) for a given GT material name.
     */
    public static double getMaxPressureBar(String materialName) {
        if (materialName == null) {
            return 10.0;
        }
        String name = materialName.toLowerCase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "");
        return CoolantLoopDataRegistry.getPipePressureRating(name);
    }

    /**
     * Resolves the maximum burst pressure (bar) for a given GT material.
     */
    public static double getMaxPressureBar(Materials material) {
        if (material == null) {
            return 10.0;
        }
        double rating = getMaxPressureBar(material.mName);
        String clean = material.mName.toLowerCase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "");
        if (rating > 15.0 || CoolantLoopDataRegistry.getPipeData()
            .getMaterialPressureRatingsBar()
            .containsKey(clean)) {
            return rating;
        }
        // Fallback: estimate based on blast furnace temperature
        if (material.mBlastFurnaceTemp > 0) {
            return Math.max(20.0, material.mBlastFurnaceTemp / 25.0);
        }
        return rating;
    }

    /**
     * Resolves the maximum safe operating temperature (Celsius) for a given GT pipe.
     */
    public static double getMaxTemperatureCelsius(MTEFluidPipe pipe) {
        if (pipe == null) {
            return 100.0;
        }
        if (pipe.mMaterial != null) {
            String name = pipe.mMaterial.mName.toLowerCase()
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");
            Double yamlLimit = CoolantLoopDataRegistry.getPipeData()
                .getMaterialTemperatureLimitsCelsius()
                .get(name);
            if (yamlLimit != null) {
                return yamlLimit;
            }
        }
        // pipe.mHeatResistance is in Kelvin in GT5
        if (pipe.mHeatResistance > 0) {
            return pipe.mHeatResistance - 273.15;
        }
        if (pipe.mMaterial != null && pipe.mMaterial.mBlastFurnaceTemp > 0) {
            return pipe.mMaterial.mBlastFurnaceTemp - 273.15;
        }
        return 200.0;
    }

    /**
     * Resolves full pipe simulation properties for a GT fluid pipe.
     */
    public static PipeProperties getProperties(MTEFluidPipe pipe) {
        if (pipe == null) {
            return new PipeProperties(0.10, 0.000045, 15.0, 200.0);
        }
        double diameter = getDiameterForThickness(pipe.mThickNess);
        double maxPressure = getMaxPressureBar(pipe.mMaterial);
        double maxTemp = getMaxTemperatureCelsius(pipe);
        double roughness = CoolantLoopDataRegistry.getPipeData()
            .getDefaultRoughness();
        double friction = CoolantLoopDataRegistry.getPipeData()
            .getFrictionMultiplier(pipe.mMaterial != null ? pipe.mMaterial.mName : null);
        return new PipeProperties(diameter, roughness, maxPressure, maxTemp, friction);
    }

    /**
     * Checks if a material is allowed for coolant loop piping and tanks.
     * Only materials with GregTech fluid pipes and a Railcraft tank a pump can use for them are allowed (pipes.yaml):
     * Iron, Steel, Stainless Steel, Titanium, TungstenSteel, Osmium, Neutronium, and Awakened Draconium and Infinity on
     * neutronium tanks.
     */
    public static boolean isAllowedCoolantPipeMaterial(String materialName) {
        if (materialName == null) return false;
        return CoolantLoopDataRegistry.isAllowedCoolantPipeMaterial(materialName);
    }

    public static boolean isAllowedCoolantPipeMaterial(Materials mat) {
        if (mat == null) return false;
        return isAllowedCoolantPipeMaterial(mat.mName);
    }

    /**
     * Whether a pump with a Railcraft tank of tankMaterial can drive a loop of pipeMaterial pipes: a tank of the
     * pipes' own material, or for pipe materials Railcraft has no tank of, the tank pipes.yaml gives them.
     */
    public static boolean takesTank(Materials pipeMaterial, Materials tankMaterial) {
        if (pipeMaterial == null || tankMaterial == null) return false;
        return CoolantLoopDataRegistry.getPipeData()
            .takesTank(pipeMaterial.mName, tankMaterial.mName);
    }

    /**
     * Checks if two materials are the same for coolant loop material matching (ignoring case, spaces, '_' and '-').
     */
    public static boolean areMaterialsEqual(String m1, String m2) {
        if (m1 == null || m2 == null) return false;
        if (CoolantLoopDataRegistry.getPipeData() != null) {
            return CoolantLoopDataRegistry.getPipeData()
                .areMaterialsEqual(m1, m2);
        }
        String n1 = m1.toLowerCase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "");
        String n2 = m2.toLowerCase()
            .replace(" ", "")
            .replace("_", "")
            .replace("-", "");
        return n1.equals(n2);
    }

    public static boolean areMaterialsEqual(Materials m1, Materials m2) {
        if (m1 == null || m2 == null) return false;
        if (m1 == m2) return true;
        return areMaterialsEqual(m1.mName, m2.mName);
    }
}
