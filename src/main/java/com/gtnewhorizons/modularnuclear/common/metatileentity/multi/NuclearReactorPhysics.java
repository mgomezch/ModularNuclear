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
import com.gtnewhorizons.modularnuclear.common.nuclear.TileContent;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileProperties;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileTick;
import com.gtnewhorizons.modularnuclear.common.util.NuclearFuelClassification;

import gregtech.api.enums.Materials;
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

    private static final java.util.Random RAND = new java.util.Random();

    public static double getTileTemperature(MTENuclearReactor reactor, NuclearGridTile tile) {
        if (tile.isBus()) return tile.getBus().mTemperature;
        if (tile.isHatch()) return tile.getHatch().mTemperature;
        if (tile.isHighPressureHatch()) return tile.getHighPressureHatch()
            .getTemperature();
        if (tile.hasControlRod()) return tile.getBottomControlRod().mTemperature;
        return reactor.getAmbientTemperature();
    }

    public static void setTileTemperature(MTENuclearReactor reactor, NuclearGridTile tile, double temp) {
        double minTemp = -273.15;
        if (tile.isBus()) {
            tile.getBus().mTemperature = Math.max(minTemp, temp);
        } else if (tile.isHatch()) {
            tile.getHatch().mTemperature = Math.max(minTemp, temp);
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch()
                .setTemperature(Math.max(minTemp, temp));
        }
        if (tile.hasControlRod()) {
            tile.getBottomControlRod().mTemperature = Math.max(minTemp, temp);
        }
    }

    public static void addTileHeat(NuclearGridTile tile, double heatHu) {
        // A control rod below shares its cell's temperature, so it warms with the cell's heat capacity
        double deltaTemp = heatHu / tile.getHeatCapacity();
        if (tile.isBus()) {
            tile.getBus().mHeatHu += heatHu;
            tile.getBus().mTemperature += deltaTemp;
        } else if (tile.isHatch()) {
            tile.getHatch().mHeatHu += heatHu;
            tile.getHatch().mTemperature += deltaTemp;
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch()
                .addHeat(heatHu);
        }
        if (tile.hasControlRod()) {
            tile.getBottomControlRod().mHeatHu += heatHu;
            tile.getBottomControlRod().mTemperature += deltaTemp;
        }
    }

    public static double getTileHeatTransferCoeff(MTENuclearReactor reactor, NuclearGridTile tile) {
        if (tile.isBus() || tile.isHatch()) {
            return TileProperties.heatTransferCoeff(resolveTileContent(reactor, tile));
        } else if (tile.isControlRod()) {
            return tile.getControlRod()
                .getHeatTransferCoeff();
        } else if (tile.isHighPressureHatch()) {
            return tile.getHighPressureHatch()
                .getHeatTransferCoeff();
        }
        return 0.05;
    }

    /**
     * Resolves what a bus or hatch holds into a {@link TileContent}, whose neutron and heat properties come from
     * {@link TileProperties} (shared with the standalone simulator). Configured items and fluids resolve to their
     * config record; unconfigured ones fall back to generic kinds by item class or ore dictionary.
     */
    public static TileContent resolveTileContent(MTENuclearReactor reactor, NuclearGridTile tile) {
        if (tile == null) return TileContent.EMPTY;
        if (tile.isBus()) {
            return resolveItemContent(reactor, tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT]);
        } else if (tile.isHatch()) {
            return resolveFluidContent(tile.getHatch().mInputFluid);
        }
        return TileContent.OTHER;
    }

    public static TileContent resolveItemContent(MTENuclearReactor reactor, ItemStack stack) {
        if (stack == null) return TileContent.EMPTY;
        if (NuclearFuelClassification.isItemNeutronSource(stack)) return TileContent.NEUTRON_SOURCE;
        FuelData fuel = ReactorDataRegistry.findFuel(stack);
        if (fuel != null) return TileContent.fuel(fuel);
        InsulatorData ins = ReactorDataRegistry.findInsulator(stack);
        if (ins != null) return TileContent.of(TileContent.Kind.INSULATOR, ins);
        RadiovoltaicData rv = ReactorDataRegistry.findRadiovoltaic(stack);
        if (rv != null) return TileContent.of(TileContent.Kind.RADIOVOLTAIC, rv);
        ModeratorData mod = ReactorDataRegistry.findModerator(stack);
        if (mod != null) return TileContent.of(TileContent.Kind.MODERATOR, mod);
        ReflectorData refl = ReactorDataRegistry.findReflector(stack);
        if (refl != null) return TileContent.of(TileContent.Kind.REFLECTOR, refl);
        CoolantItemData cItem = ReactorDataRegistry.findCoolantItem(stack);
        if (cItem != null) return TileContent.of(TileContent.Kind.COOLANT_ITEM, cItem);
        HeatVentData vent = ReactorDataRegistry.findHeatVent(stack);
        if (vent != null) return TileContent.of(TileContent.Kind.HEAT_VENT, vent);
        HeatExchangerData hex = ReactorDataRegistry.findHeatExchanger(stack);
        if (hex != null) return TileContent.of(TileContent.Kind.HEAT_EXCHANGER, hex);

        if (reactor != null && reactor.isNaquariteInsulatorFoil(stack)) return TileContent.GENERIC_NAQUARITE;
        if (reactor != null && reactor.isItemInsulator(stack)) return TileContent.GENERIC_INSULATOR;
        if (reactor != null && reactor.isItemRadiovoltaic(stack)) {
            return TileContent.genericRadiovoltaic(reactor.getRadiovoltaicTier(stack));
        }
        if (NuclearFuelClassification.isItemModerator(stack)) return TileContent.GENERIC_MODERATOR;
        if (NuclearFuelClassification.isItemReflector(stack)) return TileContent.GENERIC_REFLECTOR;
        if (reactor != null && reactor.isItemFuel(stack)) return TileContent.GENERIC_FUEL;
        if (NuclearFuelClassification.isItemCoolant(stack)) return TileContent.GENERIC_COOLANT;
        if (NuclearFuelClassification.isItemHeatVent(stack) || NuclearFuelClassification.isItemHeatExchanger(stack)) {
            return TileContent.GENERIC_VENT;
        }
        return TileContent.OTHER;
    }

    public static TileContent resolveFluidContent(FluidStack fluid) {
        if (fluid == null) return TileContent.EMPTY_HATCH;
        CoolantFluidData cFluid = ReactorDataRegistry.findCoolantFluid(fluid);
        if (cFluid != null) return TileContent.of(TileContent.Kind.COOLANT_FLUID, cFluid);
        FuelData fFluid = ReactorDataRegistry.findFuel(fluid);
        if (fFluid != null) return TileContent.fuel(fFluid);
        return TileContent.OTHER_FLUID;
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
                FuelData fd = ReactorDataRegistry.findFuel(stack);
                if (fd != null) return NuclearFuelType.fromFuelData(fd);
                return NuclearFuelType.fromName(stack.getUnlocalizedName());
            }
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid != null && fluid.getFluid() != null) {
                FuelData fd = ReactorDataRegistry.findFuel(fluid);
                if (fd != null) return NuclearFuelType.fromFuelData(fd);
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
                    if (beamHatch != null && beamHatch.dataPacket != null
                        && beamHatch.dataPacket.getContent() != null) {
                        BeamInformation beamInfo = beamHatch.dataPacket.getContent();
                        if (beamInfo.getParticle() != Particle.NEUTRON) {
                            reactor.explodeReactor(
                                beamHatch,
                                "Reactor exploded: incompatible non-neutron beam injected via beamline: "
                                    + beamInfo.getParticle()
                                        .getName());
                            bus.mLastNeutronsGenerated = 0;
                            return 0;
                        }
                        double dt = (double) reactor.getTicksPerCycle() / 20.0;
                        double multiplier = (physics != null) ? physics.beamEnergyToFastNeutronMultiplier
                            : NuclearSimulationEngine.params().beamEnergyToFastNeutronMultiplier;
                        double packets = beamInfo.getEnergy() * beamInfo.getRate() * multiplier * dt;
                        bus.mBeamlinePacketAccumulator += packets;
                        int packetSize = (int) bus.mBeamlinePacketAccumulator;
                        bus.mBeamlinePacketAccumulator -= packetSize;
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
                bus.mChainRate = 0.0;
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
                baseNeutrons = TileProperties.effectiveBaseNeutrons(fd);
                baseFissMult = TileProperties.effectiveThermalFissionMultiplier(fd);
                baseFastFissMult = TileProperties.effectiveFastFissionMultiplier(fd);
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

            double dt = (double) reactor.getTicksPerCycle() / 20.0;
            bus.mChainRate = TileProperties.chainRate(
                bus.mChainRate,
                bus.mLastThermalAbsorbed,
                bus.mLastFastAbsorbed,
                baseFissMult,
                baseFastFissMult,
                dt);
            int produced = TileProperties.fuelNeutrons(baseNeutrons, bus.mChainRate, efficiency, dt);
            bus.mLastNeutronsGenerated = produced;
            return produced;
        } else if (tile.isHatch()) {
            MTEHatchNuclearHatch hatch = tile.getHatch();
            FluidStack fluid = hatch.mInputFluid;
            if (!MTENuclearReactor.isFluidFuel(fluid) || fluid == null || fluid.amount <= 0) {
                hatch.mLastNeutronsGenerated = 0;
                hatch.mChainRate = 0.0;
                return 0;
            }
            int baseNeutrons = 8;
            FuelData fd = (tileFuelData != null) ? tileFuelData : ReactorDataRegistry.findFuel(fluid);
            if (fd != null) {
                baseNeutrons = TileProperties.effectiveBaseNeutrons(fd);
                baseFissMult = TileProperties.effectiveThermalFissionMultiplier(fd);
                baseFastFissMult = TileProperties.effectiveFastFissionMultiplier(fd);
            } else if (fuel != null) {
                baseNeutrons = fuel.baseNeutrons;
                baseFissMult = fuel.baseThermalFissionMultiplier;
                baseFastFissMult = fuel.getEffectiveFastFissionMultiplier(physics);
            }

            double dt = (double) reactor.getTicksPerCycle() / 20.0;
            hatch.mChainRate = TileProperties.chainRate(
                hatch.mChainRate,
                hatch.mLastThermalAbsorbed,
                hatch.mLastFastAbsorbed,
                baseFissMult,
                baseFastFissMult,
                dt);
            int produced = TileProperties.fuelNeutrons(baseNeutrons, hatch.mChainRate, efficiency, dt);
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
            if (fluid != null && fluid.amount > 0 && MTENuclearReactor.isFluidFuel(fluid)) {
                return TileProperties.liquidPacketCount(fluid.amount);
            }
            return 0;
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
                return TileProperties.solidPacketCount(fd);
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
        if (tile.isBus() || tile.isHatch()) {
            return TileProperties.absorption(resolveTileContent(reactor, tile), type);
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
            MTEHatchNuclearControlRod rod = tile.getBottomControlRod();
            double pRod = rod.getAbsorptionProbability(type);
            double pMod = (type == NeutronType.FAST) ? rod.getModeratorProbability() : 0.0;
            return TileTick.combinedAbsorption(pBase, pRod, pMod, rod.getModeratorAbsorptionProbability(type));
        }
        return pBase;
    }

    public static double getBaseScatteringProbability(MTENuclearReactor reactor, NuclearGridTile tile,
        NeutronType type) {
        if (tile.isBus() || tile.isHatch()) {
            return TileProperties.scattering(resolveTileContent(reactor, tile), type);
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
            MTEHatchNuclearControlRod rod = tile.getBottomControlRod();
            double pMod = (type == NeutronType.FAST) ? rod.getModeratorProbability() : 0.0;
            return TileTick.combinedScattering(
                pBase,
                rod.getAbsorptionProbability(type),
                pMod,
                rod.getScatteringProbability(type),
                rod.getModeratorScatteringProbability(type));
        }
        return pBase;
    }

    public static double getBaseModerationProbability(NuclearGridTile tile) {
        if (tile == null) return 0.05;
        if (tile.isBus() || tile.isHatch()) {
            return TileProperties.moderation(resolveTileContent(tile.getReactor(), tile));
        } else if (tile.isHighPressureHatch()) {
            return tile.getHighPressureHatch()
                .getModerationProbability();
        }
        return 0.05;
    }

    public static double getTileModerationProbability(NuclearGridTile tile) {
        double pBase = getBaseModerationProbability(tile);
        if (tile != null && tile.hasControlRod()) {
            MTEHatchNuclearControlRod rod = tile.getBottomControlRod();
            return TileTick.combinedModeration(
                pBase,
                rod.getAbsorptionProbability(NeutronType.FAST),
                rod.getModeratorProbability());
        }
        return pBase;
    }

    public static void onTileNeutronAbsorbed(MTENuclearReactor reactor, NuclearGridTile tile, NeutronType type,
        int count) {
        if (count <= 0) return;
        if (tile.hasControlRod()) {
            MTEHatchNuclearControlRod rod = tile.getBottomControlRod();
            double pRod = rod.getAbsorptionProbability(type);
            double pBase = getBaseAbsorptionProbability(reactor, tile, type);
            int nRod = TileTick.rodShareOfAbsorbed(count, pBase, pRod);
            int nBase = count - nRod;

            if (nRod > 0) {
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

            if (type == NeutronType.FAST && hatch.mInputFluid != null && hatch.mInputFluid.amount >= 2) {
                CoolantFluidData cfd = ReactorDataRegistry.findCoolantFluid(hatch.mInputFluid);
                if (cfd != null && cfd.transmutationProduct != null && cfd.transmutationChancePerNeutron > 0.0) {
                    if (TileTick.transmutes(cfd, count, reactor.getRandomNumber(10000))) {
                        hatch.mInputFluid.amount -= 2;
                        if (hatch.mInputFluid.amount <= 0) hatch.mInputFluid = null;
                        reactor.mCycleTransmutationLoss += 2;
                        Fluid prod = FluidRegistry.getFluid(cfd.transmutationProduct);
                        if (prod == null && !cfd.transmutationProduct.startsWith("fluid.")) {
                            prod = FluidRegistry.getFluid("fluid." + cfd.transmutationProduct);
                        }
                        if (prod == null) {
                            if ("deuterium".equalsIgnoreCase(cfd.transmutationProduct) && Materials.Deuterium != null) {
                                prod = Materials.Deuterium.mGas != null ? Materials.Deuterium.mGas
                                    : Materials.Deuterium.mFluid;
                            } else
                                if ("tritium".equalsIgnoreCase(cfd.transmutationProduct) && Materials.Tritium != null) {
                                    prod = Materials.Tritium.mGas != null ? Materials.Tritium.mGas
                                        : Materials.Tritium.mFluid;
                                }
                        }
                        if (prod != null) {
                            reactor.addOutputPartial(new FluidStack(prod, 2));
                            reactor.mCycleTransmutationByproducts += 2;
                        }
                        if ("deuterium".equalsIgnoreCase(cfd.transmutationProduct)
                            || "tritium".equalsIgnoreCase(cfd.transmutationProduct)) {
                            Fluid ox = (Materials.Oxygen != null)
                                ? (Materials.Oxygen.mGas != null ? Materials.Oxygen.mGas : Materials.Oxygen.mFluid)
                                : null;
                            if (ox == null) ox = FluidRegistry.getFluid("oxygen");
                            if (ox == null) ox = FluidRegistry.getFluid("fluid.oxygen");
                            if (ox != null) {
                                reactor.addOutputPartial(new FluidStack(ox, 1));
                                reactor.mCycleTransmutationByproducts += 1;
                            }
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
            if (stack != null && NuclearFuelClassification.isItemReflector(stack)) {
                ReflectorData refl = ReactorDataRegistry.findReflector(stack);
                int maxDur = refl != null ? refl.durability
                    : (stack.isItemStackDamageable() ? stack.getMaxDamage() : 0);
                if (maxDur > 0 && reactor.getRandomNumber(20) == 0) {
                    if (stack.isItemStackDamageable()) {
                        reactor.damageItemComponent(bus, 1);
                    } else {
                        int curDmg = 0;
                        if (stack.stackTagCompound != null && stack.stackTagCompound.hasKey("advDmg")) {
                            curDmg = stack.stackTagCompound.getInteger("advDmg");
                        }
                        int newDmg = curDmg + 1;
                        if (newDmg >= maxDur) {
                            reactor.mCycleZeroedFuelItems++;
                            if (stack.stackSize > 1) {
                                stack.stackSize--;
                                if (stack.stackTagCompound != null) {
                                    stack.stackTagCompound.removeTag("advDmg");
                                }
                            } else {
                                bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
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
            CustomFuelMix customMix = CustomFuelMix.fromStack(stack);
            FuelData fd = ReactorDataRegistry.findFuel(stack);
            NuclearFuelType fuelType = getTileFuelType(tile);
            double dt = (double) reactor.getTicksPerCycle() / 20.0;
            double rawTempDmg = (customMix != null) ? customMix.calculateTemperatureDamage(bus.mTemperature)
                : (fd != null) ? fd.calculateTemperatureDamage(bus.mTemperature)
                    : (fuelType != null) ? fuelType.calculateTemperatureDamage(bus.mTemperature)
                        : TileTick.genericFuelTemperatureDamage(bus.mTemperature);
            double totalDmg = TileTick
                .fuelWear(rawTempDmg, bus.mLastNeutronsGenerated, bus.mFastAbsorbed, bus.mThermalAbsorbed, dt);

            int configuredDurability = (customMix != null) ? customMix.defaultDurability
                : (fd != null && fd.defaultDurability > 0) ? fd.defaultDurability : 0;

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

            int damage = TileTick.stochasticRound(scaledDmg);

            if (stack.getItem() instanceof ItemRadioactiveCell radCell) {
                radCell.damageItemStack(stack, damage);
                if (radCell.getDamageOfStack(stack) >= intrinsicMax) {
                    reactor.mCycleZeroedFuelItems++;
                    ItemStack depleted = null;
                    if (fd != null && fd.spentProduct != null && !fd.spentProduct.isEmpty()) {
                        depleted = ReactorDataRegistry.resolveItemStack(fd.spentProduct);
                    }
                    if (depleted == null && radCell instanceof ItemRadioactiveCellIC icCell
                        && icCell.sDepleted != null) {
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
                double dt = (double) reactor.getTicksPerCycle() / 20.0;
                double totalDmg = TileTick.moderatorWear(
                    mod,
                    bus.mTemperature,
                    bus.mFastAbsorbed,
                    bus.mThermalAbsorbed,
                    bus.mFastFlux + bus.mThermalFlux,
                    dt);
                int damage = TileTick.stochasticRound(totalDmg);

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
            RadiovoltaicData rv = ReactorDataRegistry.findRadiovoltaic(stack);
            if (rv == null) {
                rv = new RadiovoltaicData();
                rv.tier = reactor.getRadiovoltaicTier(stack);
            }
            double dt = (double) reactor.getTicksPerCycle() / 20.0;
            double weightedAbsorbed = bus.mFastAbsorbed * 4.0 + bus.mThermalAbsorbed * 1.0;
            bus.mRadiovoltaicRate = TileTick.radiovoltaicRate(bus.mRadiovoltaicRate, weightedAbsorbed, dt);
            long genEU = Math.round(rv.calculatePower(bus.mRadiovoltaicRate));
            bus.mDirectEUProduced = genEU;
            double excessHeat = RadiovoltaicData.excessHeat(weightedAbsorbed, genEU, dt);
            if (excessHeat > 0.0) {
                addTileHeat(tile, excessHeat);
            }
        }
        // 4. ACTIVE HEAT VENTS & HEAT EXCHANGERS (Processed actively each tick via processActiveVentsAndExchangers)
        else if (MTENuclearReactor.isItemHeatVent(stack) || MTENuclearReactor.isItemHeatExchanger(stack)) {
            bus.mDirectEUProduced = 0;
            double maxTemp = 0.0;
            if (MTENuclearReactor.isItemHeatVent(stack)) {
                HeatVentData vent = ReactorDataRegistry.findHeatVent(stack);
                if (vent != null) maxTemp = vent.maxTemperature;
            } else {
                HeatExchangerData hex = ReactorDataRegistry.findHeatExchanger(stack);
                if (hex != null) maxTemp = hex.maxTemperature;
            }
            if (maxTemp > 0.0 && bus.mTemperature > maxTemp) {
                bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT] = null;
                reactor.mCycleZeroedCoolantItems++;
                bus.markTileDirty();
            }
        }
        // 5. COOLANT CELL HEAT ABSORPTION (Capacity-based scaling via IReactorComponent)
        else if (stack.getItem() instanceof IReactorComponent comp) {
            reactor.getReactorDummy()
                .setCurrentTile(tile);
            if (comp.canStoreHeat(reactor.getReactorDummy(), stack, 0, 0)) {
                bus.mDirectEUProduced = 0;
                int maxHeat = comp.getMaxHeat(reactor.getReactorDummy(), stack, 0, 0);
                int curHeat = comp.getCurrentHeat(reactor.getReactorDummy(), stack, 0, 0);
                double ambient = reactor.getAmbientTemperature();
                if (maxHeat > 0 && bus.mTemperature > ambient) {
                    CoolantItemData cItem = ReactorDataRegistry.findCoolantItem(stack);
                    double huPerDurability = (cItem != null) ? cItem.getHuPerDurability() : 25.0;
                    double dt = (double) reactor.getTicksPerCycle() / 20.0;
                    int heatToTake = TileTick.coolantCellUptake(
                        maxHeat,
                        curHeat,
                        bus.mTemperature,
                        ambient,
                        huPerDurability,
                        efficiency,
                        dt);

                    if (heatToTake > 0) {
                        comp.alterHeat(reactor.getReactorDummy(), stack, 0, 0, heatToTake);
                        double heatConsumed = heatToTake * huPerDurability;
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
        else if (bus.mTemperature > 65.0
            && (com.gtnewhorizons.modularnuclear.common.recipe.CheeseRecipeRegistry.getInstance()
                .isCheeseInput(stack)
                || (bus.getClass() != MTEHatchNuclearBus.class && bus.findCheeseExtractionRecipe(stack) != null))) {
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

    /**
     * The coolant record a hatch can cool with right now, or null (empty, fuel, unknown fluid or pipe tier too low).
     */
    private static CoolantFluidData activeCoolant(MTENuclearReactor reactor, MTEHatchNuclearHatch hatch) {
        if (hatch.mInputFluid == null || hatch.mInputFluid.amount <= 0) return null;
        if (MTENuclearReactor.isFluidFuel(hatch.mInputFluid)) return null;
        CoolantFluidData cfd = ReactorDataRegistry.findCoolantFluid(hatch.mInputFluid);
        if (cfd == null || cfd.hotFluidName == null) return null;
        if (reactor.mPipeTier >= 0 && reactor.mPipeTier < cfd.requiredTier) return null;
        return cfd;
    }

    public static double hatchCoolantSubstep(MTENuclearReactor reactor, MTEHatchNuclearHatch hatch, double dtSub,
        double efficiency) {
        CoolantFluidData cfd = activeCoolant(reactor, hatch);
        if (cfd == null) return 0.0;
        double sink = TileTick.coolantSinkTemperature(cfd, reactor.getAmbientTemperature());
        double remaining = hatch.mInputFluid.amount * cfd.heatPerLiter - hatch.mPendingCoolantHeat;
        double q = TileTick.coolantSubstepHeat(hatch.mTemperature, sink, hatch.mTier, efficiency, dtSub, remaining);
        if (q > 0.0) {
            hatch.mPendingCoolantHeat += q;
            hatch.mTemperature -= q / NuclearSimulationEngine.getCoreHatchHeatCapacity(hatch.mTier);
        }
        return q;
    }

    public static void processHatchNuclearTick(MTENuclearReactor reactor, MTEHatchNuclearHatch hatch,
        NuclearGridTile tile, double efficiency) {
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
            FuelData fuelData = ReactorDataRegistry.findFuel(fluid);
            double dt = (double) reactor.getTicksPerCycle() / 20.0;
            double rawTempDmg = (fuelData != null) ? fuelData.calculateTemperatureDamage(hatch.mTemperature)
                : TileTick.genericFuelTemperatureDamage(hatch.mTemperature);
            double totalBurn = TileTick.fuelWear(
                rawTempDmg,
                hatch.mLastNeutronsGenerated,
                hatch.mLastFastAbsorbed,
                hatch.mLastThermalAbsorbed,
                dt);
            int burn = Math.max(1, TileTick.stochasticRound(totalBurn));
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

        CoolantFluidData cfd = activeCoolant(reactor, hatch);
        double pendingHeat = hatch.mPendingCoolantHeat;
        hatch.mPendingCoolantHeat = 0.0;
        if (cfd == null) {
            return;
        }

        int steamRatio = cfd.expansionRatio;
        String outputFluidName = cfd.hotFluidName;

        hatch.mLastProducedAmount = 0;
        hatch.mLastProducedFluidName = "";
        // The heat itself left the hatch during the conduction sub-steps (hatchCoolantSubstep); here it becomes coolant
        // consumed and hot fluid produced.
        if (pendingHeat > 0.0) {
            int fluidToProcess = Math.min(hatch.mInputFluid.amount, TileTick.coolantConsumed(cfd, pendingHeat));

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
                hatch.mLastHeatOutput = pendingHeat;
                hatch.markTileDirty();
            }
        }
    }
}
