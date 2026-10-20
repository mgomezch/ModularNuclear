package com.gtnewhorizons.modularnuclear.common.nuclear.standalone;

/**
 * Thermodynamic and physical properties of secondary fluids used in heat exchangers.
 */
public class SecondaryCoolant {

    public final String name;
    public final String displayName;
    public final String fluidName;
    public final double boilingPointCelsius;
    public final double latentHeatJoulesPerLiter;
    public final int expansionRatio;
    public final String outputGasName;
    public final boolean isSteam;

    public SecondaryCoolant(String name, String displayName, String fluidName, double boilingPointCelsius,
        double latentHeatJoulesPerLiter, int expansionRatio, String outputGasName, boolean isSteam) {
        this.name = name != null ? name : (fluidName != null ? fluidName.toUpperCase() : "UNKNOWN");
        this.displayName = displayName != null ? displayName : this.name;
        this.fluidName = fluidName != null ? fluidName : "";
        this.boilingPointCelsius = boilingPointCelsius;
        this.latentHeatJoulesPerLiter = latentHeatJoulesPerLiter;
        this.expansionRatio = expansionRatio > 0 ? expansionRatio : 160;
        this.outputGasName = outputGasName != null ? outputGasName : "steam";
        this.isSteam = isSteam;
    }

    public String name() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFluidName() {
        return fluidName;
    }

    public double getBoilingPointCelsius() {
        return boilingPointCelsius;
    }

    public double getLatentHeatJoulesPerLiter() {
        return latentHeatJoulesPerLiter;
    }

    public int getExpansionRatio() {
        return expansionRatio;
    }

    public String getOutputGasName() {
        return outputGasName;
    }

    public boolean isSteam() {
        return isSteam;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SecondaryCoolant that = (SecondaryCoolant) o;
        return name.equalsIgnoreCase(that.name) || (fluidName != null && fluidName.equalsIgnoreCase(that.fluidName));
    }

    @Override
    public int hashCode() {
        return name.toLowerCase()
            .hashCode();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
