package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantItemData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatExchangerData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatVentData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.InsulatorData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.PlatingData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.RadiovoltaicData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ReflectorData;
import com.gtnewhorizons.modularnuclear.common.config.data.FuelData;
import com.gtnewhorizons.modularnuclear.common.config.data.PhysicsData;
import com.gtnewhorizons.modularnuclear.common.item.ItemCustomFuelRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.nuclear.CustomFuelMix;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;

import gregtech.api.items.ItemRadioactiveCell;
import gregtech.api.items.ItemRadioactiveCellIC;
import gregtech.api.util.GTRecipe;
import gtnhlanth.common.beamline.BeamInformation;
import gtnhlanth.common.beamline.Particle;
import gtnhlanth.common.hatch.MTEHatchInputBeamline;
import ic2.api.reactor.IReactorComponent;
import ic2.core.item.reactor.ItemReactorMOX;
import ic2.core.item.reactor.ItemReactorUranium;

public class NuclearReactorPhysics {

    public static double getTileTemperature(MTENuclearReactor reactor, NuclearGridTile tile) {
        if (tile.isBus()) return tile.getBus().mTemperature;
        if (tile.isHatch()) return tile.getHatch().mTemperature;
        if (tile.isHighPressureHatch()) return tile.getHighPressureHatch()
            .getTemperature();
        if (tile.hasControlRod()) return tile.getBottomControlRod().mTemperature;
        return reactor.getAmbientTemperature();
    }

    public static void setTileTemperature(MTENuclearReactor reactor, NuclearGridTile tile, double temp) {
        double ambient = reactor.getAmbientTemperature();
        if (tile.isBus()) {
            tile.getBus().mTemperature = Math.max(ambient, temp);
        } else if (tile.isHatch()) {
            double minTemp = tile.getHatch()
                .hasWaterCoolant() ? Math.max(0.0, ambient) : ambient;
            tile.getHatch().mTemperature = Math.max(minTemp, temp);
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch()
                .setTemperature(Math.max(ambient, temp));
        }
        if (tile.hasControlRod()) {
            tile.getBottomControlRod().mTemperature = Math.max(ambient, temp);
        }
    }

    public static void addTileHeat(NuclearGridTile tile, double heatHu) {
        if (tile.isBus()) {
            tile.getBus().mHeatHu += heatHu;
            tile.getBus().mHeatEU = tile.getBus().mHeatHu;
            tile.getBus().mTemperature += heatHu / NuclearSimulationEngine.HU_PER_DEGREE;
        } else if (tile.isHatch()) {
            tile.getHatch().mHeatHu += heatHu;
            tile.getHatch().mHeatEU = tile.getHatch().mHeatHu;
            tile.getHatch().mTemperature += heatHu / NuclearSimulationEngine.HU_PER_DEGREE;
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch()
                .addHeat(heatHu);
        }
        if (tile.hasControlRod()) {
            tile.getBottomControlRod().mHeatHu += heatHu;
            tile.getBottomControlRod().mHeatEU = tile.getBottomControlRod().mHeatHu;
            tile.getBottomControlRod().mTemperature += heatHu / NuclearSimulationEngine.HU_PER_DEGREE;
        }
    }

    public static double getTileHeatTransferCoeff(MTENuclearReactor reactor, NuclearGridTile tile) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null || NuclearFuelClassification.isItemNeutronSource(stack)) return 0.02;

            FuelData fuel = ReactorDataRegistry.findFuel(stack);
            if (fuel != null) return fuel.heatTransferCoeff;
            InsulatorData ins = ReactorDataRegistry.findInsulator(stack);
            if (ins != null) return ins.heatTransferCoeff;
            RadiovoltaicData rv = ReactorDataRegistry.findRadiovoltaic(stack);
            if (rv != null) return rv.heatTransferCoeff;
            ReflectorData refl = ReactorDataRegistry.findReflector(stack);
            if (refl != null) return refl.heatTransferCoeff;
            ModeratorData mod = ReactorDataRegistry.findModerator(stack);
            if (mod != null) return mod.heatTransferCoeff;
            HeatVentData vent = ReactorDataRegistry.findHeatVent(stack);
            if (vent != null) return vent.heatTransferCoeff;
            HeatExchangerData hex = ReactorDataRegistry.findHeatExchanger(stack);
            if (hex != null) return hex.heatTransferCoeff;
            PlatingData plat = ReactorDataRegistry.findPlating(stack);
            if (plat != null) return plat.heatTransferCoeff;
            CoolantItemData cItem = ReactorDataRegistry.findCoolantItem(stack);
            if (cItem != null) return cItem.heatTransferCoeff;

            if (reactor.isItemInsulator(stack)) return 0.01;
            if (reactor.isItemRadiovoltaic(stack)) return 0.10;
            if (NuclearFuelClassification.isItemCoolant(stack) || NuclearFuelClassification.isItemHeatVent(stack)
                || NuclearFuelClassification.isItemHeatExchanger(stack)) return 0.40;
            if (NuclearFuelClassification.isItemReflector(stack)) return 0.15;
            if (reactor.isItemFuel(stack)) return 0.05;
            return 0.03;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.05;

            CoolantFluidData cFluid = ReactorDataRegistry.findCoolantFluid(fluid);
            if (cFluid != null) return cFluid.heatTransferCoeff;
            FuelData fFluid = ReactorDataRegistry.findFuel(fluid);
            if (fFluid != null) return fFluid.heatTransferCoeff;

            String name = fluid.getFluid()
                .getName()
                .toLowerCase();
            if (name.contains("water")) return 0.25;
            if (name.contains("coolant")) return 0.50;
            if (name.contains("sodium") || name.contains("lead")) return 0.70;
            return 0.15;
        } else if (tile.isControlRod()) {
            return tile.getControlRod()
                .getHeatTransferCoeff();
        } else if (tile.isHighPressureHatch()) {
            return tile.getHighPressureHatch()
                .getHeatTransferCoeff();
        }
        return 0.05;
    }

    public static boolean isTileFuel(MTENuclearReactor reactor, NuclearGridTile tile) {
        if (tile.isBus()) {
            return reactor.isItemFuel(tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT]);
        } else if (tile.isHatch()) {
            return MTENuclearReactor.isFluidFuel(tile.getHatch().mInputFluid);
        }
        return false;
    }

    public static NuclearFuelType getTileFuelType(NuclearGridTile tile) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack != null) {
                return NuclearFuelType.fromName(stack.getUnlocalizedName());
            }
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid != null && fluid.getFluid() != null) {
                return NuclearFuelType.fromName(
                    fluid.getFluid()
                        .getName());
            }
        }
        return null;
    }

    public static CustomFuelMix getTileCustomFuelMix(NuclearGridTile tile) {
        if (tile != null && tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack != null) {
                return CustomFuelMix.fromStack(stack);
            }
        }
        return null;
    }

    public static FuelData getTileFuelData(NuclearGridTile tile) {
        if (tile == null) return null;
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack != null) {
                return ReactorDataRegistry.findFuel(stack);
            }
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid != null) {
                return ReactorDataRegistry.findFuel(fluid);
            }
        }
        return null;
    }

    public static ModeratorData getTileModeratorData(NuclearGridTile tile) {
        if (tile != null && tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack != null) {
                return ReactorDataRegistry.findModerator(stack);
            }
        }
        return null;
    }

    public static int generateTileNeutrons(MTENuclearReactor reactor, NuclearGridTile tile, double efficiency) {
        CustomFuelMix customMix = getTileCustomFuelMix(tile);
        FuelData tileFuelData = getTileFuelData(tile);
        NuclearFuelType fuel = getTileFuelType(tile);
        PhysicsData physics = ReactorDataRegistry.getPhysics();
        double baseFissMult = (customMix != null) ? customMix.baseThermalFissionMultiplier
            : (tileFuelData != null) ? tileFuelData.getEffectiveFissionMultiplier(physics)
            : (fuel != null) ? fuel.baseThermalFissionMultiplier : 1.0;
        double baseFastFissMult = (customMix != null) ? customMix.baseFastFissionMultiplier
            : (tileFuelData != null) ? tileFuelData.getEffectiveFastFissionMultiplier(physics)
            : (fuel != null) ? fuel.getEffectiveFastFissionMultiplier(physics) : 0.10;

        if (tile.isBus()) {
            MTEHatchNuclearBus bus = tile.getBus();
            ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (tile.isNeutronSource()) {
                if (tile.hasBottomBeamInput()) {
                    MTEHatchInputBeamline beamHatch = tile.getBottomBeamInput();
                    if (beamHatch != null && beamHatch.dataPacket != null && beamHatch.dataPacket.getContent() != null) {
                        BeamInformation beamInfo = beamHatch.dataPacket.getContent();
                        if (beamInfo.getParticle() != Particle.NEUTRON) {
                            reactor.explodeReactor(false, "Reactor exploded: incompatible non-neutron beam injected via beamline: " + beamInfo.getParticle().getName());
                            bus.mLastNeutronsGenerated = 0;
                            return 0;
                        }
                        double multiplier = (physics != null) ? physics.beamEnergyToFastNeutronMultiplier : NuclearSimulationEngine.beamEnergyToFastNeutronMultiplier;
                        int packetSize = Math.max(0, (int) Math.round(beamInfo.getEnergy() * beamInfo.getRate() * multiplier));
                        int totalNeutrons = packetSize * 4;
                        bus.mLastNeutronsGenerated = totalNeutrons;
                        return totalNeutrons;
                    }
                }
                bus.mLastNeutronsGenerated = 0;
                return 0;
            }
            if (!reactor.isItemFuel(stack)) {
                bus.mLastNeutronsGenerated = 0;
                return 0;
            }
            int baseNeutrons = 4;
            FuelData fd = (tileFuelData != null) ? tileFuelData : ReactorDataRegistry.findFuel(stack);
            if (customMix != null) {
                int rodSize = (stack.getItem() instanceof ItemCustomFuelRod customRod) ? customRod.rodSize : 4;
                baseNeutrons = customMix.getBaseNeutrons(rodSize);
                baseFissMult = customMix.baseThermalFissionMultiplier;
                baseFastFissMult = customMix.baseFastFissionMultiplier;
            } else if (fd != null) {
                baseNeutrons = fd.getEffectiveBaseNeutrons(physics);
                baseFissMult = fd.getEffectiveFissionMultiplier(physics);
                baseFastFissMult = fd.getEffectiveFastFissionMultiplier(physics);
            } else if (stack.getItem() instanceof ItemRadioactiveCellIC icCell) {
                baseNeutrons = 4 * icCell.numberOfCells;
                if (icCell.sMox) baseNeutrons *= 2;
            } else if (stack.getItem() instanceof ItemReactorUranium ic2Uran) {
                baseNeutrons = 4 * ic2Uran.numberOfCells;
                if (ic2Uran instanceof ItemReactorMOX) baseNeutrons *= 2;
            } else if (fuel != null) {
                baseNeutrons = fuel.baseNeutrons;
                baseFissMult = fuel.baseThermalFissionMultiplier;
                baseFastFissMult = fuel.getEffectiveFastFissionMultiplier(physics);
            }

            double effectiveFissMult = baseFissMult * NuclearSimulationEngine.globalThermalFissionMultiplier;
            double effectiveFastFissMult = baseFastFissMult * NuclearSimulationEngine.globalFastFissionMultiplier;
            int chainNeutrons = (int) Math.round(bus.mLastThermalAbsorbed * effectiveFissMult + bus.mLastFastAbsorbed * effectiveFastFissMult);
            int produced = (int) Math.round((baseNeutrons + chainNeutrons) * efficiency);
            bus.mLastNeutronsGenerated = produced;
            return produced;
        } else if (tile.isHatch()) {
            MTEHatchNuclearHatch hatch = tile.getHatch();
            FluidStack fluid = hatch.mInputFluid;
            if (!MTENuclearReactor.isFluidFuel(fluid) || fluid == null || fluid.amount <= 0) {
                hatch.mLastNeutronsGenerated = 0;
                return 0;
            }
            int baseNeutrons = 8;
            FuelData fd = (tileFuelData != null) ? tileFuelData : ReactorDataRegistry.findFuel(fluid);
            if (fd != null) {
                baseNeutrons = fd.getEffectiveBaseNeutrons(physics);
                baseFissMult = fd.getEffectiveFissionMultiplier(physics);
                baseFastFissMult = fd.getEffectiveFastFissionMultiplier(physics);
            } else if (fuel != null) {
                baseNeutrons = fuel.baseNeutrons;
                baseFissMult = fuel.baseThermalFissionMultiplier;
                baseFastFissMult = fuel.getEffectiveFastFissionMultiplier(physics);
            }

            double effectiveFissMult = baseFissMult * NuclearSimulationEngine.globalThermalFissionMultiplier;
            double effectiveFastFissMult = baseFastFissMult * NuclearSimulationEngine.globalFastFissionMultiplier;
            int chainNeutrons = (int) Math.round(hatch.mLastThermalAbsorbed * effectiveFissMult + hatch.mLastFastAbsorbed * effectiveFastFissMult);
            int produced = (int) Math.round((baseNeutrons + chainNeutrons) * efficiency);
            hatch.mLastNeutronsGenerated = produced;
            return produced;
        }
        return 0;
    }

    public static int getTileNeutronEmissionCount(NuclearGridTile tile) {
        if (tile.isNeutronSource()) {
            return 4;
        }
        if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid != null) {
                FuelData fd = ReactorDataRegistry.findFuel(fluid);
                if (fd != null) return fd.packetCount;
            }
            return 4;
        }
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null) return 1;
            if (stack.getItem() instanceof ItemCustomFuelRod customRod) {
                return customRod.rodSize;
            }
            if (CustomFuelMix.hasCustomMix(stack)) {
                return 4;
            }
            FuelData fd = ReactorDataRegistry.findFuel(stack);
            if (fd != null) {
                return fd.packetCount;
            }
            if (stack.getItem() instanceof ItemRadioactiveCellIC icCell) {
                return Math.max(1, icCell.numberOfCells);
            } else if (stack.getItem() instanceof ItemReactorUranium ic2Uran) {
                return Math.max(1, ic2Uran.numberOfCells);
            }
            NuclearFuelType fuel = getTileFuelType(tile);
            if (fuel == NuclearFuelType.THE_CORE) return 16;
        }
        return 1;
    }

    public static double getBaseAbsorptionProbability(MTENuclearReactor reactor, NuclearGridTile tile,
        NeutronType type) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null || NuclearFuelClassification.isItemNeutronSource(stack)) return 0.01;

            InsulatorData ins = ReactorDataRegistry.findInsulator(stack);
            if (ins != null) return (type == NeutronType.FAST) ? ins.absorptionFast : ins.absorptionThermal;
            RadiovoltaicData rv = ReactorDataRegistry.findRadiovoltaic(stack);
            if (rv != null) return (type == NeutronType.FAST) ? rv.absorptionFast : rv.absorptionThermal;
            ModeratorData mod = ReactorDataRegistry.findModerator(stack);
            if (mod != null) return (type == NeutronType.FAST) ? mod.absorptionFast : mod.absorptionThermal;
            ReflectorData refl = ReactorDataRegistry.findReflector(stack);
            if (refl != null) return (type == NeutronType.FAST) ? refl.absorptionFast : refl.absorptionThermal;
            FuelData fuel = ReactorDataRegistry.findFuel(stack);
            if (fuel != null) return (type == NeutronType.FAST) ? fuel.absorptionFast : fuel.absorptionThermal;
            CoolantItemData cItem = ReactorDataRegistry.findCoolantItem(stack);
            if (cItem != null) return (type == NeutronType.FAST) ? cItem.absorptionFast : cItem.absorptionThermal;

            if (reactor.isNaquariteInsulatorFoil(stack)) return 1.0;
            if (reactor.isItemInsulator(stack)) return 0.01;
            if (reactor.isItemRadiovoltaic(stack)) return 1.0;
            if (NuclearFuelClassification.isItemModerator(stack)) {
                return (type == NeutronType.THERMAL) ? 0.009 : 0.002;
            }
            if (NuclearFuelClassification.isItemReflector(stack)) return (type == NeutronType.THERMAL) ? 0.02 : 0.01;
            if (reactor.isItemFuel(stack)) {
                return (type == NeutronType.THERMAL) ? 0.80 : 0.25;
            }
            if (NuclearFuelClassification.isItemCoolant(stack)) return 0.05;
            return 0.02;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.01;

            CoolantFluidData cFluid = ReactorDataRegistry.findCoolantFluid(fluid);
            if (cFluid != null) return (type == NeutronType.FAST) ? cFluid.absorptionFast : cFluid.absorptionThermal;
            FuelData fFluid = ReactorDataRegistry.findFuel(fluid);
            if (fFluid != null) return (type == NeutronType.FAST) ? fFluid.absorptionFast : fFluid.absorptionThermal;

            String name = fluid.getFluid()
                .getName()
                .toLowerCase();
            if (name.contains("heavywater")) return (type == NeutronType.THERMAL) ? 0.01 : 0.005;
            if (name.contains("distilledwater")) return (type == NeutronType.THERMAL) ? 0.10 : 0.05;
            if (name.contains("coolant")) return (type == NeutronType.THERMAL) ? 0.12 : 0.03;
            if (name.contains("boron")) return 0.95;
            if (MTENuclearReactor.isFluidFuel(fluid)) return (type == NeutronType.THERMAL) ? 0.85 : 0.25;
            return 0.05;
        } else if (tile.isHighPressureHatch()) {
            return tile.getHighPressureHatch()
                .getAbsorptionProbability(type);
        }
        return 0.01;
    }

    public static double getTileAbsorptionProbability(MTENuclearReactor reactor, NuclearGridTile tile,
        NeutronType type) {
        double pBase = getBaseAbsorptionProbability(reactor, tile, type);
        if (tile.hasControlRod()) {
            double pRod = tile.getBottomControlRod()
                .getAbsorptionProbability(type);
            return Math.min(1.0, 1.0 - (1.0 - pBase) * (1.0 - pRod));
        }
        return pBase;
    }

    public static double getBaseScatteringProbability(MTENuclearReactor reactor, NuclearGridTile tile,
        NeutronType type) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null || NuclearFuelClassification.isItemNeutronSource(stack)) return 0.02;

            InsulatorData ins = ReactorDataRegistry.findInsulator(stack);
            if (ins != null) return (type == NeutronType.FAST) ? ins.scatteringFast : ins.scatteringThermal;
            RadiovoltaicData rv = ReactorDataRegistry.findRadiovoltaic(stack);
            if (rv != null) return 0.0;
            ModeratorData mod = ReactorDataRegistry.findModerator(stack);
            if (mod != null) return (type == NeutronType.FAST) ? mod.scatteringFast : mod.scatteringThermal;
            ReflectorData refl = ReactorDataRegistry.findReflector(stack);
            if (refl != null) return (type == NeutronType.FAST) ? refl.scatteringFast : refl.scatteringThermal;
            CoolantItemData cItem = ReactorDataRegistry.findCoolantItem(stack);
            if (cItem != null) return (type == NeutronType.FAST) ? cItem.scatteringFast : cItem.scatteringThermal;
            FuelData fuel = ReactorDataRegistry.findFuel(stack);
            if (fuel != null) return (type == NeutronType.FAST) ? fuel.scatteringFast : fuel.scatteringThermal;

            if (reactor.isNaquariteInsulatorFoil(stack)) return 0.0;
            if (reactor.isItemInsulator(stack)) return 0.05;
            if (reactor.isItemRadiovoltaic(stack)) return 0.0;
            if (NuclearFuelClassification.isItemModerator(stack)) {
                return (type == NeutronType.THERMAL) ? 0.621 : 0.93;
            }
            if (NuclearFuelClassification.isItemReflector(stack)) return (type == NeutronType.THERMAL) ? 0.98 : 0.95;
            if (NuclearFuelClassification.isItemCoolant(stack)) return 0.45;
            if (reactor.isItemFuel(stack)) return (type == NeutronType.THERMAL) ? 0.10 : 0.15;
            return 0.05;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.02;

            CoolantFluidData cFluid = ReactorDataRegistry.findCoolantFluid(fluid);
            if (cFluid != null) return (type == NeutronType.FAST) ? cFluid.scatteringFast : cFluid.scatteringThermal;
            FuelData fFluid = ReactorDataRegistry.findFuel(fluid);
            if (fFluid != null) return (type == NeutronType.FAST) ? fFluid.scatteringFast : fFluid.scatteringThermal;

            String name = fluid.getFluid()
                .getName()
                .toLowerCase();
            if (name.contains("heavywater")) return 0.85;
            if (name.contains("distilledwater")) return 0.70;
            if (name.contains("coolant")) return 0.45;
            if (name.contains("sodium")) return 0.20;
            return 0.10;
        } else if (tile.isHighPressureHatch()) {
            return tile.getHighPressureHatch()
                .getScatteringProbability(type);
        }
        return 0.02;
    }

    public static double getTileScatteringProbability(MTENuclearReactor reactor, NuclearGridTile tile,
        NeutronType type) {
        double pBase = getBaseScatteringProbability(reactor, tile, type);
        if (tile.hasControlRod()) {
            double pRod = tile.getBottomControlRod()
                .getScatteringProbability(type);
            return Math.min(1.0, 1.0 - (1.0 - pBase) * (1.0 - pRod));
        }
        return pBase;
    }

    public static double getTileModerationProbability(NuclearGridTile tile) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null || NuclearFuelClassification.isItemNeutronSource(stack)) return 0.05;

            RadiovoltaicData rv = ReactorDataRegistry.findRadiovoltaic(stack);
            if (rv != null) return 0.0;
            ModeratorData mod = ReactorDataRegistry.findModerator(stack);
            if (mod != null) return mod.moderation;
            ReflectorData refl = ReactorDataRegistry.findReflector(stack);
            if (refl != null) return refl.moderation;
            HeatVentData vent = ReactorDataRegistry.findHeatVent(stack);
            if (vent != null) return vent.moderation;
            HeatExchangerData hex = ReactorDataRegistry.findHeatExchanger(stack);
            if (hex != null) return hex.moderation;
            CoolantItemData cItem = ReactorDataRegistry.findCoolantItem(stack);
            if (cItem != null) return cItem.moderation;
            FuelData fuel = ReactorDataRegistry.findFuel(stack);
            if (fuel != null) return fuel.moderation;

            if (tile.getReactor()
                .isItemRadiovoltaic(stack)) return 0.0;
            if (NuclearFuelClassification.isItemModerator(stack)) return 0.50;
            if (NuclearFuelClassification.isItemReflector(stack)) return 0.20;
            if (NuclearFuelClassification.isItemCoolant(stack)) return 0.05;
            if (NuclearFuelClassification.isItemHeatVent(stack)) return 0.05;
            if (NuclearFuelClassification.isItemHeatExchanger(stack)) return 0.05;
            if (tile.getReactor()
                .isItemFuel(stack)) return 0.10;
            return 0.05;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.05;

            CoolantFluidData cFluid = ReactorDataRegistry.findCoolantFluid(fluid);
            if (cFluid != null) return cFluid.moderation;
            FuelData fFluid = ReactorDataRegistry.findFuel(fluid);
            if (fFluid != null) return fFluid.moderation;

            String name = fluid.getFluid()
                .getName()
                .toLowerCase();
            if (name.contains("heavywater")) return 0.90;
            if (name.contains("distilledwater") || name.contains("water")) return 0.25;
            if (name.contains("coolant")) return 0.05;
            if (name.contains("sodium")) return 0.05;
            return 0.05;
        } else if (tile.isHighPressureHatch()) {
            return tile.getHighPressureHatch()
                .getModerationProbability();
        }
        return 0.05;
    }

    public static void onTileNeutronAbsorbed(MTENuclearReactor reactor, NuclearGridTile tile, NeutronType type,
        int count) {
        if (count <= 0) return;
        if (tile.hasControlRod()) {
            double pRod = tile.getBottomControlRod()
                .getAbsorptionProbability(type);
            double pBase = getBaseAbsorptionProbability(reactor, tile, type);
            double sum = pBase + pRod;
            int nRod = (sum > 0) ? (int) Math.round(count * (pRod / sum)) : count / 2;
            nRod = Math.min(count, Math.max(0, nRod));
            int nBase = count - nRod;

            if (nRod > 0) {
                MTEHatchNuclearControlRod rod = tile.getBottomControlRod();
                if (type == NeutronType.FAST) rod.mFastAbsorbed += nRod;
                else rod.mThermalAbsorbed += nRod;
            }
            if (nBase > 0) {
                onBaseTileNeutronAbsorbed(reactor, tile, type, nBase);
            }
        } else {
            onBaseTileNeutronAbsorbed(reactor, tile, type, count);
        }
    }

    public static void onBaseTileNeutronAbsorbed(MTENuclearReactor reactor, NuclearGridTile tile, NeutronType type,
        int count) {
        if (tile.isBus()) {
            MTEHatchNuclearBus bus = tile.getBus();
            if (type == NeutronType.FAST) bus.mFastAbsorbed += count;
            else bus.mThermalAbsorbed += count;
        } else if (tile.isHatch()) {
            MTEHatchNuclearHatch hatch = tile.getHatch();
            if (type == NeutronType.FAST) hatch.mFastAbsorbed += count;
            else hatch.mThermalAbsorbed += count;

            if (type == NeutronType.FAST && hatch.mInputFluid != null && hatch.mInputFluid.amount > 0) {
                String name = hatch.mInputFluid.getFluid()
                    .getName()
                    .toLowerCase();
                boolean isHP = name.contains("highpressure");
                int yield = isHP ? 2 : 1;

                if (name.contains("distilledwater")) {
                    int chance = isHP ? Math.min(1000, count * 50) : Math.min(1000, count * 25);
                    if (reactor.getRandomNumber(1000) < chance) {
                        hatch.mInputFluid.amount -= 1;
                        if (hatch.mInputFluid.amount <= 0) hatch.mInputFluid = null;
                        reactor.mCycleTransmutationLoss += 1;
                        Fluid deut = FluidRegistry.getFluid("deuterium");
                        if (deut == null) deut = FluidRegistry.getFluid("fluid.deuterium");
                        if (deut != null) {
                            reactor.addOutputPartial(new FluidStack(deut, yield));
                            reactor.mCycleTransmutationByproducts += yield;
                        }
                        hatch.markTileDirty();
                    }
                } else if (name.contains("heavywater")) {
                    int chance = isHP ? Math.min(1000, count * 20) : Math.min(1000, count * 10);
                    if (reactor.getRandomNumber(1000) < chance) {
                        hatch.mInputFluid.amount -= 1;
                        if (hatch.mInputFluid.amount <= 0) hatch.mInputFluid = null;
                        reactor.mCycleTransmutationLoss += 1;
                        Fluid trit = FluidRegistry.getFluid("tritium");
                        if (trit == null) trit = FluidRegistry.getFluid("fluid.tritium");
                        if (trit != null) {
                            reactor.addOutputPartial(new FluidStack(trit, yield));
                            reactor.mCycleTransmutationByproducts += yield;
                        }
                        hatch.markTileDirty();
                    }
                }
            }
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch()
                .onNeutronAbsorbed(type, count);
        }
    }

    public static void onTileNeutronScattered(MTENuclearReactor reactor, NuclearGridTile tile, NeutronType type,
        int count) {
        if (tile.isBus()) {
            MTEHatchNuclearBus bus = tile.getBus();
            ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack != null && stack.isItemStackDamageable()) {
                if (NuclearFuelClassification.isItemReflector(stack)) {
                    if (reactor.getRandomNumber(20) == 0) {
                        reactor.damageItemComponent(bus, 1);
                    }
                }
            }
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch()
                .onNeutronScattered(type, count);
        }
    }

    public static void addTileNeutronFlux(NuclearGridTile tile, NeutronType type, int count) {
        if (tile.isBus()) {
            if (type == NeutronType.FAST) tile.getBus().mFastFlux += count;
            else tile.getBus().mThermalFlux += count;
        } else if (tile.isHatch()) {
            if (type == NeutronType.FAST) tile.getHatch().mFastFlux += count;
            else tile.getHatch().mThermalFlux += count;
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch()
                .addNeutronFlux(type, count);
        }

        if (tile.hasControlRod()) {
            MTEHatchNuclearControlRod rod = tile.getBottomControlRod();
            if (type == NeutronType.FAST) rod.mFastFlux += count;
            else rod.mThermalFlux += count;
        }
    }

    public static void processTileNuclearTick(MTENuclearReactor reactor, NuclearGridTile tile, double efficiency) {
        if (tile.isBus()) {
            processBusNuclearTick(reactor, tile.getBus(), tile, efficiency);
        } else if (tile.isHatch()) {
            processHatchNuclearTick(reactor, tile.getHatch(), tile, efficiency);
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch()
                .nuclearTick(efficiency);
        }

        if (tile.hasControlRod()) {
            processControlRodNuclearTick(tile.getBottomControlRod(), tile, efficiency);
        }
    }

    public static void processControlRodNuclearTick(MTEHatchNuclearControlRod rod, NuclearGridTile tile,
        double efficiency) {
        rod.mCumulativeFluence += (rod.mFastFlux + rod.mThermalFlux);
        rod.mLastFastFlux = rod.mFastFlux;
        rod.mLastThermalFlux = rod.mThermalFlux;
        rod.mLastFastAbsorbed = rod.mFastAbsorbed;
        rod.mLastThermalAbsorbed = rod.mThermalAbsorbed;
        rod.mFastFlux = 0;
        rod.mThermalFlux = 0;
        rod.mFastAbsorbed = 0;
        rod.mThermalAbsorbed = 0;
    }

    public static void processBusNuclearTick(MTENuclearReactor reactor, MTEHatchNuclearBus bus, NuclearGridTile tile,
        double efficiency) {
        bus.mLastHeatOutput = 0.0;
        ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
        if (stack == null || NuclearFuelClassification.isItemNeutronSource(stack)) {
            bus.mCumulativeFluence += (bus.mFastFlux + bus.mThermalFlux);
            bus.mLastFastFlux = bus.mFastFlux;
            bus.mLastThermalFlux = bus.mThermalFlux;
            bus.mLastFastAbsorbed = bus.mFastAbsorbed;
            bus.mLastThermalAbsorbed = bus.mThermalAbsorbed;
            bus.mDirectEUProduced = 0;
            bus.mFastFlux = 0;
            bus.mThermalFlux = 0;
            bus.mFastAbsorbed = 0;
            bus.mThermalAbsorbed = 0;
            return;
        }

        // 1. FUEL DEPLETION (Driven by 3 physical processes: emitting fast neutrons, absorbing any neutron, and
        // temperature above ambient)
        if (reactor.isItemFuel(stack)) {
            bus.mDirectEUProduced = 0;
            double ambient = bus.getAmbientTemperature();
            CustomFuelMix customMix = CustomFuelMix.fromStack(stack);
            FuelData fd = ReactorDataRegistry.findFuel(stack);
            NuclearFuelType fuelType = getTileFuelType(tile);
            double tempDmg = (customMix != null) ? customMix.calculateTemperatureDamage(bus.mTemperature, ambient)
                : (fd != null) ? fd.calculateTemperatureDamage(bus.mTemperature, ambient)
                : (fuelType != null) ? fuelType.calculateTemperatureDamage(bus.mTemperature, ambient)
                    : (bus.mTemperature > ambient ? (bus.mTemperature - ambient) / 100.0 : 0.0);

            PhysicsData physics = ReactorDataRegistry.getPhysics();
            double fastWearMult = (physics != null) ? physics.fastAbsorptionDamageMultiplier : NuclearSimulationEngine.fastAbsorptionDamageMultiplier;
            double thermalWearMult = (physics != null) ? physics.thermalAbsorptionDamageMultiplier : NuclearSimulationEngine.thermalAbsorptionDamageMultiplier;

            double totalDmg = Math.max(0.0, bus.mLastNeutronsGenerated * 0.25)
                + (bus.mFastAbsorbed * fastWearMult + bus.mThermalAbsorbed * thermalWearMult)
                + tempDmg;

            int configuredDurability = (customMix != null) ? customMix.defaultDurability
                : (fd != null && fd.defaultDurability > 0) ? fd.defaultDurability
                : 0;

            int intrinsicMax;
            if (stack.getItem() instanceof ItemCustomFuelRod customRod) {
                intrinsicMax = customRod.getMaxDamageEx(stack);
            } else if (stack.getItem() instanceof ItemRadioactiveCell radCell) {
                intrinsicMax = radCell.getMaxDamageEx();
            } else if (stack.getItem() instanceof ItemReactorUranium) {
                intrinsicMax = stack.getMaxDamage();
            } else {
                intrinsicMax = stack.getMaxDamage();
            }

            if (configuredDurability <= 0) {
                configuredDurability = intrinsicMax > 0 ? intrinsicMax : 10000;
            }
            if (intrinsicMax <= 0) {
                intrinsicMax = configuredDurability;
            }

            // Scale physical reactor wear to the item's intrinsic damage domain
            double scale = (double) intrinsicMax / (double) configuredDurability;
            double scaledDmg = totalDmg * scale;

            int damage = (int) scaledDmg;
            double remainder = scaledDmg - damage;
            if (remainder > 0.0 && Math.random() < remainder) {
                damage++;
            }

            if (stack.getItem() instanceof ItemRadioactiveCell radCell) {
                radCell.damageItemStack(stack, damage);
                if (radCell.getDamageOfStack(stack) >= intrinsicMax) {
                    reactor.mCycleZeroedFuelItems++;
                    ItemStack depleted = null;
                    if (fd != null && fd.spentProduct != null && !fd.spentProduct.isEmpty()) {
                        depleted = ReactorDataRegistry.resolveItemStack(fd.spentProduct);
                    }
                    if (depleted == null && radCell instanceof ItemRadioactiveCellIC icCell && icCell.sDepleted != null) {
                        depleted = icCell.sDepleted.copy();
                    } else if (depleted == null) {
                        depleted = reactor.getItemDepletedForm(stack);
                    }
                    bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                    if (depleted != null) {
                        reactor.addOutputPartial(depleted);
                    }
                }
                bus.markTileDirty();
            } else if (stack.getItem() instanceof ItemReactorUranium) {
                int curDmg = stack.getItemDamage();
                int newDmg = curDmg + damage;
                if (newDmg >= intrinsicMax) {
                    reactor.mCycleZeroedFuelItems++;
                    ItemStack depleted = null;
                    if (fd != null && fd.spentProduct != null && !fd.spentProduct.isEmpty()) {
                        depleted = ReactorDataRegistry.resolveItemStack(fd.spentProduct);
                    }
                    if (depleted == null) {
                        depleted = reactor.getItemDepletedForm(stack);
                    }
                    bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                    if (depleted != null) {
                        reactor.addOutputPartial(depleted);
                    }
                } else {
                    stack.setItemDamage(newDmg);
                }
                bus.markTileDirty();
            } else {
                reactor.damageItemComponent(bus, damage);
            }
        }
        // 2. MODERATOR WEAR & DEPLETION
        else if (NuclearFuelClassification.isItemModerator(stack)) {
            bus.mDirectEUProduced = 0;
            ModeratorData mod = ReactorDataRegistry.findModerator(stack);
            if (mod != null) {
                double ambient = bus.getAmbientTemperature();
                double tempDmg = mod.calculateTemperatureDamage(bus.mTemperature, ambient);

                PhysicsData physics = ReactorDataRegistry.getPhysics();
                double fastWearMult = (physics != null) ? physics.fastAbsorptionDamageMultiplier : NuclearSimulationEngine.fastAbsorptionDamageMultiplier;
                double thermalWearMult = (physics != null) ? physics.thermalAbsorptionDamageMultiplier : NuclearSimulationEngine.thermalAbsorptionDamageMultiplier;

                double totalDmg = (bus.mFastAbsorbed * fastWearMult + bus.mThermalAbsorbed * thermalWearMult)
                    + (bus.mFastFlux + bus.mThermalFlux) * 0.05
                    + tempDmg;

                int damage = (int) totalDmg;
                double remainder = totalDmg - damage;
                if (remainder > 0.0 && Math.random() < remainder) {
                    damage++;
                }

                if (damage > 0) {
                    int curDmg = 0;
                    if (stack.stackTagCompound != null && stack.stackTagCompound.hasKey("advDmg")) {
                        curDmg = stack.stackTagCompound.getInteger("advDmg");
                    }
                    int newDmg = curDmg + damage;
                    if (newDmg >= mod.durability) {
                        reactor.mCycleZeroedFuelItems++;
                        ItemStack depleted = null;
                        if (mod.spentProduct != null && !mod.spentProduct.isEmpty()) {
                            depleted = ReactorDataRegistry.resolveItemStack(mod.spentProduct);
                        }
                        if (stack.stackSize > 1) {
                            stack.stackSize--;
                            if (stack.stackTagCompound != null) {
                                stack.stackTagCompound.removeTag("advDmg");
                            }
                        } else {
                            bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                        }
                        if (depleted != null) {
                            reactor.addOutputPartial(depleted);
                        }
                    } else {
                        if (stack.stackTagCompound == null) {
                            stack.stackTagCompound = new net.minecraft.nbt.NBTTagCompound();
                        }
                        stack.stackTagCompound.setInteger("advDmg", newDmg);
                    }
                    bus.markTileDirty();
                }
            }
        }
        // 3. RADIOVOLTAIC DIRECT EU GENERATION
        else if (reactor.isItemRadiovoltaic(stack)) {
            int tier = reactor.getRadiovoltaicTier(stack);
            long maxEU = (tier >= 2) ? 4096 : 1024;
            double weightedFlux = bus.mFastAbsorbed * 4.0 + bus.mThermalAbsorbed * 1.0;
            double satFlux = 60.0;
            long genEU = (long) Math.round(maxEU * Math.tanh(weightedFlux / satFlux));
            bus.mDirectEUProduced = genEU;
            double totalEnergy = weightedFlux * 20.0;
            double excessHeat = Math.max(0.0, totalEnergy - genEU);
            if (excessHeat > 0.0) {
                addTileHeat(tile, excessHeat);
            }
        }
        // 3. IC2 HEAT VENTS & HEAT EXCHANGERS (Passive cooling and adjacent balancing)
        else if (MTENuclearReactor.isItemHeatVent(stack) || MTENuclearReactor.isItemHeatExchanger(stack)) {
            bus.mDirectEUProduced = 0;
            if (stack.getItem() instanceof IReactorComponent comp) {
                reactor.getReactorDummy()
                    .setCurrentTile(tile);
                int gx = tile.getGx();
                int gy = tile.getGy();

                if (comp.canStoreHeat(reactor.getReactorDummy(), stack, gx, gy)) {
                    int maxHeat = comp.getMaxHeat(reactor.getReactorDummy(), stack, gx, gy);
                    int curHeat = comp.getCurrentHeat(reactor.getReactorDummy(), stack, gx, gy);
                    double ambient = reactor.getAmbientTemperature();
                    if (maxHeat > 0 && bus.mTemperature > ambient) {
                        int maxTransferPerTick = Math.max(20, maxHeat / 50);
                        double heatAvailable = (bus.mTemperature - ambient) * NuclearSimulationEngine.HU_PER_DEGREE;
                        double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
                        int heatToTake = (int) Math
                            .round(Math.min(heatAvailable / 25.0, (double) maxTransferPerTick) * effFactor);
                        int room = maxHeat - curHeat;
                        heatToTake = Math.min(heatToTake, room);

                        if (heatToTake > 0) {
                            comp.alterHeat(reactor.getReactorDummy(), stack, gx, gy, heatToTake);
                            double heatConsumed = heatToTake * 25.0;
                            bus.mTemperature -= (heatConsumed / NuclearSimulationEngine.HU_PER_DEGREE);
                            bus.markTileDirty();
                        }
                    }
                }

                // Process IC2 native component chamber tick (cooling self or balancing with adjacent components)
                comp.processChamber(reactor.getReactorDummy(), stack, gx, gy, true);
                bus.markTileDirty();
            }
        }
        // 4. COOLANT CELL HEAT ABSORPTION (Capacity-based scaling via IReactorComponent)
        else if (stack.getItem() instanceof IReactorComponent comp) {
            reactor.getReactorDummy()
                .setCurrentTile(tile);
            if (comp.canStoreHeat(reactor.getReactorDummy(), stack, 0, 0)) {
                bus.mDirectEUProduced = 0;
                int maxHeat = comp.getMaxHeat(reactor.getReactorDummy(), stack, 0, 0);
                int curHeat = comp.getCurrentHeat(reactor.getReactorDummy(), stack, 0, 0);
                double ambient = reactor.getAmbientTemperature();
                if (maxHeat > 0 && bus.mTemperature > ambient) {
                    int maxTransferPerTick = Math.max(1, maxHeat / 100);
                    double heatAvailable = (bus.mTemperature - ambient) * NuclearSimulationEngine.HU_PER_DEGREE;
                    double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
                    int heatToTake = (int) Math
                        .round(Math.min(heatAvailable / 25.0, (double) maxTransferPerTick) * effFactor);
                    int room = maxHeat - curHeat;
                    heatToTake = Math.min(heatToTake, room);

                    if (heatToTake > 0) {
                        comp.alterHeat(reactor.getReactorDummy(), stack, 0, 0, heatToTake);
                        double heatConsumed = heatToTake * 25.0;
                        bus.mTemperature -= (heatConsumed / NuclearSimulationEngine.HU_PER_DEGREE);
                        bus.mLastHeatOutput = heatConsumed;
                        bus.markTileDirty();
                    }
                }

                // Eject hot/full coolant cells to output buses for freezer re-cooling
                if (comp.getCurrentHeat(reactor.getReactorDummy(), stack, 0, 0) >= maxHeat) {
                    reactor.mCycleZeroedCoolantItems++;
                    ItemStack fullCell = stack.copy();
                    bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                    reactor.addOutputPartial(fullCell);
                    bus.markTileDirty();
                }
            } else {
                bus.mDirectEUProduced = 0;
            }
        }
        // 5. EASTER EGG: MOLTEN CHEESE EXTRACTION ABOVE 65°C
        else if (bus.mTemperature > 65.0) {
            bus.mDirectEUProduced = 0;
            if (reactor.getBaseMetaTileEntity() != null) {
                long aTick = reactor.getBaseMetaTileEntity()
                    .getTimer();
                if (aTick != bus.mLastCheeseTick) {
                    bus.mLastCheeseTick = aTick;
                    processCheeseExtraction(reactor, bus);
                }
            } else {
                processCheeseExtraction(reactor, bus);
            }
        } else {
            bus.mDirectEUProduced = 0;
        }

        // Reset transient flux counters for next tick's display
        bus.mCumulativeFluence += (bus.mFastFlux + bus.mThermalFlux);
        bus.mLastFastFlux = bus.mFastFlux;
        bus.mLastThermalFlux = bus.mThermalFlux;
        bus.mLastFastAbsorbed = bus.mFastAbsorbed;
        bus.mLastThermalAbsorbed = bus.mThermalAbsorbed;
        bus.mFastFlux = 0;
        bus.mThermalFlux = 0;
        bus.mFastAbsorbed = 0;
        bus.mThermalAbsorbed = 0;
    }

    public static boolean processCheeseExtraction(MTENuclearReactor reactor, MTEHatchNuclearBus bus) {
        if (bus.mTemperature <= 65.0) return false;
        ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
        if (stack == null || stack.stackSize <= 0) return false;

        GTRecipe recipe = bus.findCheeseExtractionRecipe(stack);
        if (recipe == null) return false;

        FluidStack cheeseOutput = null;
        if (recipe.mFluidOutputs != null) {
            for (FluidStack out : recipe.mFluidOutputs) {
                if (out != null && MTEHatchNuclearBus.isMoltenCheese(out)) {
                    cheeseOutput = out.copy();
                    break;
                }
            }
        }
        if (cheeseOutput == null || cheeseOutput.amount <= 0) return false;

        long totalEU = (long) recipe.mDuration * recipe.mEUt;
        double heatAbsorbed = Math.max(1.0, (double) totalEU);
        double tempDrop = heatAbsorbed / NuclearSimulationEngine.HU_PER_DEGREE;
        bus.mTemperature = Math.max(reactor.getAmbientTemperature(), bus.mTemperature - tempDrop);
        bus.mLastHeatOutput = heatAbsorbed;

        int consumeCount = 1;
        if (recipe.mInputs != null && recipe.mInputs.length > 0 && recipe.mInputs[0] != null) {
            consumeCount = Math.max(1, recipe.mInputs[0].stackSize);
        }
        stack.stackSize -= consumeCount;
        if (stack.stackSize <= 0) {
            bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
        }

        reactor.addOutputPartial(cheeseOutput);

        if (recipe.mOutputs != null) {
            for (ItemStack out : recipe.mOutputs) {
                if (out != null) {
                    reactor.addOutputPartial(out.copy());
                }
            }
        }

        bus.markTileDirty();
        return true;
    }

    public static void processHatchNuclearTick(MTENuclearReactor reactor, MTEHatchNuclearHatch hatch,
        NuclearGridTile tile, double efficiency) {
        hatch.mCumulativeFluence += (hatch.mFastFlux + hatch.mThermalFlux);
        hatch.mLastFastFlux = hatch.mFastFlux;
        hatch.mLastThermalFlux = hatch.mThermalFlux;
        hatch.mLastFastAbsorbed = hatch.mFastAbsorbed;
        hatch.mLastThermalAbsorbed = hatch.mThermalAbsorbed;
        hatch.mFastFlux = 0;
        hatch.mThermalFlux = 0;
        hatch.mFastAbsorbed = 0;
        hatch.mThermalAbsorbed = 0;
        hatch.mLastHeatOutput = 0.0;

        if (hatch.mInputFluid == null || hatch.mInputFluid.amount <= 0) return;

        // 1. LIQUID NUCLEAR FUEL PROCESSING (Turns into spent liquid fuel, emits neutrons)
        if (MTENuclearReactor.isFluidFuel(hatch.mInputFluid)) {
            FluidStack fluid = hatch.mInputFluid;
            Fluid spentFluid = MTENuclearReactor.getSpentFluid(fluid);
            double ambient = hatch.getAmbientTemperature();
            NuclearFuelType fuelType = (fluid != null && fluid.getFluid() != null) ? NuclearFuelType.fromName(
                fluid.getFluid()
                    .getName())
                : null;
            double tempDmg = (fuelType != null) ? fuelType.calculateTemperatureDamage(hatch.mTemperature, ambient)
                : (hatch.mTemperature > ambient ? (hatch.mTemperature - ambient) / 100.0 : 0.0);

            PhysicsData physics = ReactorDataRegistry.getPhysics();
            double fastWearMult = (physics != null) ? physics.fastAbsorptionDamageMultiplier : NuclearSimulationEngine.fastAbsorptionDamageMultiplier;
            double thermalWearMult = (physics != null) ? physics.thermalAbsorptionDamageMultiplier : NuclearSimulationEngine.thermalAbsorptionDamageMultiplier;

            double totalBurn = Math.max(0.0, hatch.mLastNeutronsGenerated * 0.25)
                + (hatch.mLastFastAbsorbed * fastWearMult + hatch.mLastThermalAbsorbed * thermalWearMult)
                + tempDmg;
            int burn = (int) totalBurn;
            double rem = totalBurn - burn;
            if (rem > 0.0 && Math.random() < rem) {
                burn++;
            }
            burn = Math.max(1, burn);
            int toConsume = Math.max(1, Math.min(fluid.amount, burn));

            if (toConsume > 0) {
                fluid.amount -= toConsume;
                reactor.mCycleDepletedLiquidFuel += toConsume;
                if (fluid.amount <= 0) hatch.mInputFluid = null;
                if (spentFluid != null) {
                    reactor.addOutputPartial(new FluidStack(spentFluid, toConsume));
                }
                hatch.markTileDirty();
            }
            return;
        }

        String name = hatch.mInputFluid.getFluid()
            .getName()
            .toLowerCase();

        int reqTier = MTEHatchNuclearHatch.getRequiredFluidTier(name);
        if (reqTier < 0 || (reactor.mPipeTier >= 0 && reactor.mPipeTier < reqTier)) {
            return;
        }

        double minOperatingTemp = NuclearSimulationEngine
            .getCoolantSinkTemperature(name, reactor.getAmbientTemperature());
        double heatPerMB = NuclearSimulationEngine.coolingHeatPerLiter;
        int steamRatio = 1;
        String outputFluidName = "steam";

        if (name.contains("coolant") && !name.contains("hot")) {
            minOperatingTemp = NuclearSimulationEngine.getCoolantSinkTemperature(name, reactor.getAmbientTemperature());
            heatPerMB = NuclearSimulationEngine.ic2CoolantHeatPerLiter;
            steamRatio = 1;
            outputFluidName = "ic2hotcoolant";
        } else if (name.contains("heavywater") && !name.contains("steam")) {
            minOperatingTemp = NuclearSimulationEngine.getCoolantSinkTemperature(name, reactor.getAmbientTemperature());
            heatPerMB = NuclearSimulationEngine.coolingHeatPerLiter;
            steamRatio = 160;
            outputFluidName = "heavywatersteam";
        } else if (name.contains("distilledwater")) {
            minOperatingTemp = NuclearSimulationEngine.getCoolantSinkTemperature(name, reactor.getAmbientTemperature());
            heatPerMB = NuclearSimulationEngine.coolingHeatPerLiter;
            steamRatio = 160;
            outputFluidName = "steam";
        } else {
            return;
        }

        hatch.mLastProducedAmount = 0;
        hatch.mLastProducedFluidName = "";
        if (hatch.mTemperature > minOperatingTemp) {
            double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
            double qMax = NuclearSimulationEngine
                .calculateConductiveHeatTransfer(hatch.mTemperature, minOperatingTemp, hatch.mTier, effFactor);
            int desiredTurnover = (heatPerMB > 0) ? (int) Math.round(qMax / heatPerMB) : 0;
            int fluidToProcess = Math.min(hatch.mInputFluid.amount, desiredTurnover);

            if (fluidToProcess > 0) {
                int outAmount = fluidToProcess * steamRatio;
                hatch.mInputFluid.amount -= fluidToProcess;
                reactor.mCycleConsumedCoolant += fluidToProcess;
                reactor.mCycleProducedHotCoolant += outAmount;
                if (hatch.mInputFluid.amount <= 0) {
                    hatch.mInputFluid = null;
                    hatch.mWasDry = true;
                }

                Fluid outFluid = FluidRegistry.getFluid(outputFluidName);
                if (outFluid == null && outputFluidName.startsWith("fluid.")) {
                    outFluid = FluidRegistry.getFluid(outputFluidName.substring(6));
                }
                if (outFluid == null && !outputFluidName.startsWith("fluid.")) {
                    outFluid = FluidRegistry.getFluid("fluid." + outputFluidName);
                }
                if (outFluid == null) {
                    outFluid = FluidRegistry.getFluid("steam");
                }
                if (outFluid != null && outAmount > 0) {
                    reactor.addOutputPartial(new FluidStack(outFluid, outAmount));
                }

                hatch.mLastProducedAmount = outAmount;
                hatch.mLastProducedFluidName = outputFluidName;
                double heatConsumed = fluidToProcess * heatPerMB;
                hatch.mLastHeatOutput = heatConsumed;
                hatch.mTemperature = Math
                    .max(minOperatingTemp, hatch.mTemperature - (heatConsumed / NuclearSimulationEngine.HU_PER_DEGREE));
                hatch.markTileDirty();
            }
        }
    }
}
