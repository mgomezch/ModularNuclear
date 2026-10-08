package com.gtnewhorizons.modularnuclear.common.metatileentity.multi;

import ic2.api.reactor.IReactorComponent;
import ic2.core.item.reactor.ItemReactorMOX;
import ic2.core.item.reactor.ItemReactorUranium;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearBus;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearControlRod;
import com.gtnewhorizons.modularnuclear.common.metatileentity.hatch.MTEHatchNuclearHatch;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearFuelType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;

import gregtech.api.items.ItemRadioactiveCell;
import gregtech.api.items.ItemRadioactiveCellIC;
import gregtech.api.util.GTRecipe;

public class NuclearReactorPhysics {

    public static double getTileTemperature(MTENuclearReactor reactor, NuclearGridTile tile) {
        if (tile.isBus()) return tile.getBus().mTemperature;
        if (tile.isHatch()) return tile.getHatch().mTemperature;
        if (tile.isHighPressureHatch()) return tile.getHighPressureHatch().getTemperature();
        if (tile.hasControlRod()) return tile.getBottomControlRod().mTemperature;
        return reactor.getAmbientTemperature();
    }

    public static void setTileTemperature(MTENuclearReactor reactor, NuclearGridTile tile, double temp) {
        double ambient = reactor.getAmbientTemperature();
        if (tile.isBus()) {
            tile.getBus().mTemperature = Math.max(ambient, temp);
        } else if (tile.isHatch()) {
            double minTemp = tile.getHatch().hasWaterCoolant() ? Math.max(0.0, ambient) : ambient;
            tile.getHatch().mTemperature = Math.max(minTemp, temp);
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch().setTemperature(Math.max(ambient, temp));
        }
        if (tile.hasControlRod()) {
            tile.getBottomControlRod().mTemperature = Math.max(ambient, temp);
        }
    }

    public static void addTileHeat(NuclearGridTile tile, double heatEU) {
        if (tile.isBus()) {
            tile.getBus().mHeatEU += heatEU;
            tile.getBus().mTemperature += heatEU / NuclearSimulationEngine.EU_PER_DEGREE;
        } else if (tile.isHatch()) {
            tile.getHatch().mHeatEU += heatEU;
            tile.getHatch().mTemperature += heatEU / NuclearSimulationEngine.EU_PER_DEGREE;
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch().addHeat(heatEU);
        }
        if (tile.hasControlRod()) {
            tile.getBottomControlRod().mHeatEU += heatEU;
            tile.getBottomControlRod().mTemperature += heatEU / NuclearSimulationEngine.EU_PER_DEGREE;
        }
    }

    public static double getTileHeatTransferCoeff(MTENuclearReactor reactor, NuclearGridTile tile) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null) return 0.02;
            if (reactor.isItemInsulator(stack)) return 0.01;
            if (reactor.isItemRadiovoltaic(stack)) return 0.10;
            String name = stack.getUnlocalizedName().toLowerCase();
            if (name.contains("coolant") || name.contains("vent")
                || name.contains("switch")
                || name.contains("heatexchanger")) return 0.40;
            if (name.contains("reflector")) return 0.15;
            if (name.contains("fuel") || name.contains("uranium") || name.contains("mox") || name.contains("thorium"))
                return 0.05;
            return 0.03;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.05;
            String name = fluid.getFluid().getName().toLowerCase();
            if (name.contains("water")) return 0.25;
            if (name.contains("coolant")) return 0.50;
            if (name.contains("sodium") || name.contains("lead")) return 0.70;
            return 0.15;
        } else if (tile.isControlRod()) {
            return tile.getControlRod().getHeatTransferCoeff();
        } else if (tile.isHighPressureHatch()) {
            return tile.getHighPressureHatch().getHeatTransferCoeff();
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
                return NuclearFuelType.fromName(fluid.getFluid().getName());
            }
        }
        return null;
    }

    public static int generateTileNeutrons(MTENuclearReactor reactor, NuclearGridTile tile, double efficiency) {
        NuclearFuelType fuel = getTileFuelType(tile);
        double baseFissMult = (fuel != null) ? fuel.baseThermalFissionMultiplier : 1.0;
        double effectiveFissMult = baseFissMult * NuclearSimulationEngine.globalThermalFissionMultiplier;

        if (tile.isBus()) {
            MTEHatchNuclearBus bus = tile.getBus();
            ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (!reactor.isItemFuel(stack)) {
                bus.mLastNeutronsGenerated = 0;
                return 0;
            }
            int baseNeutrons = 4;
            if (stack.getItem() instanceof ItemRadioactiveCellIC icCell) {
                baseNeutrons = 4 * icCell.numberOfCells;
                if (icCell.sMox) baseNeutrons *= 2;
            } else if (stack.getItem() instanceof ItemReactorUranium ic2Uran) {
                baseNeutrons = 4 * ic2Uran.numberOfCells;
                if (ic2Uran instanceof ItemReactorMOX) baseNeutrons *= 2;
            } else {
                String name = stack.getUnlocalizedName();
                if (name != null) {
                    String lower = name.toLowerCase();
                    if (lower.contains("naquadah32") || lower.contains("thecore")
                        || (lower.contains("naquadah") && lower.contains("32"))) {
                        baseNeutrons = 128;
                    } else if (lower.contains("quad") || lower.contains("4")) {
                        baseNeutrons = 16;
                    } else if (lower.contains("dual") || lower.contains("2")) {
                        baseNeutrons = 8;
                    }
                    if (lower.contains("mox")) baseNeutrons *= 2;
                }
            }
            String name = stack.getUnlocalizedName();
            if (name != null) {
                String lower = name.toLowerCase();
                if (lower.contains("glowstone") || lower.contains("lithium")) baseNeutrons = 1;
                if (lower.contains("thorium")) baseNeutrons = Math.max(1, baseNeutrons / 2);
                if (lower.contains("naquadah")) baseNeutrons *= 4;
                if (lower.contains("naquadria")) baseNeutrons *= 4;
                if (lower.contains("tiberium")) baseNeutrons *= 2;
            }

            int chainNeutrons = (int) Math.round(bus.mLastThermalAbsorbed * effectiveFissMult);
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
            String name = fluid.getFluid().getName().toLowerCase();
            int baseNeutrons = 8;
            if (name.contains("thorium")) {
                baseNeutrons = name.contains("excited") ? 8 : 4;
            } else if (name.contains("uranium")) {
                baseNeutrons = name.contains("excited") ? 16 : 8;
            } else if (name.contains("plutonium")) {
                baseNeutrons = name.contains("excited") ? 32 : 16;
            } else if (name.contains("uraniumhexafluoride")) {
                baseNeutrons = 12;
            }

            int chainNeutrons = (int) Math.round(hatch.mLastThermalAbsorbed * effectiveFissMult);
            int produced = (int) Math.round((baseNeutrons + chainNeutrons) * efficiency);
            hatch.mLastNeutronsGenerated = produced;
            return produced;
        }
        return 0;
    }

    public static int getTileNeutronEmissionCount(NuclearGridTile tile) {
        if (tile.isHatch()) {
            return 4;
        }
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null) return 1;
            if (stack.getItem() instanceof ItemRadioactiveCellIC icCell) {
                return Math.max(1, icCell.numberOfCells);
            } else if (stack.getItem() instanceof ItemReactorUranium ic2Uran) {
                return Math.max(1, ic2Uran.numberOfCells);
            } else {
                String name = stack.getUnlocalizedName();
                if (name != null) {
                    String lower = name.toLowerCase();
                    if (lower.contains("naquadah32") || lower.contains("thecore")
                        || (lower.contains("naquadah") && lower.contains("32"))) {
                        return 16;
                    } else if (lower.contains("quad") || lower.contains("4")) {
                        return 4;
                    } else if (lower.contains("dual") || lower.contains("2")) {
                        return 2;
                    }
                }
            }
        }
        return 1;
    }

    public static double getBaseAbsorptionProbability(MTENuclearReactor reactor, NuclearGridTile tile,
        NeutronType type) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null) return 0.01;
            if (reactor.isNaquariteInsulatorFoil(stack)) return 1.0;
            if (reactor.isItemInsulator(stack)) return 0.01;
            if (reactor.isItemRadiovoltaic(stack)) return 1.0;
            String name = stack.getUnlocalizedName().toLowerCase();
            if (name.contains("graphite") || name.contains("carbon") || name.contains("moderator")) {
                return (type == NeutronType.THERMAL) ? 0.009 : 0.002;
            }
            if (name.contains("reflector")) return (type == NeutronType.THERMAL) ? 0.02 : 0.01;
            if (name.contains("boron") || name.contains("cadmium") || name.contains("control")) {
                return (type == NeutronType.THERMAL) ? 0.95 : 0.85;
            }
            if (reactor.isItemFuel(stack)) {
                return (type == NeutronType.THERMAL) ? 0.80 : 0.25;
            }
            if (name.contains("coolant")) return 0.05;
            return 0.02;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.01;
            String name = fluid.getFluid().getName().toLowerCase();
            if (name.contains("heavywater")) return (type == NeutronType.THERMAL) ? 0.01 : 0.005;
            if (name.contains("distilledwater")) return (type == NeutronType.THERMAL) ? 0.10 : 0.05;
            if (name.contains("coolant")) return (type == NeutronType.THERMAL) ? 0.12 : 0.03;
            if (name.contains("boron")) return 0.95;
            if (MTENuclearReactor.isFluidFuel(fluid)) return (type == NeutronType.THERMAL) ? 0.85 : 0.25;
            return 0.05;
        } else if (tile.isHighPressureHatch()) {
            return tile.getHighPressureHatch().getAbsorptionProbability(type);
        }
        return 0.01;
    }

    public static double getTileAbsorptionProbability(MTENuclearReactor reactor, NuclearGridTile tile,
        NeutronType type) {
        double pBase = getBaseAbsorptionProbability(reactor, tile, type);
        if (tile.hasControlRod()) {
            double pRod = tile.getBottomControlRod().getAbsorptionProbability(type);
            return Math.min(1.0, 1.0 - (1.0 - pBase) * (1.0 - pRod));
        }
        return pBase;
    }

    public static double getBaseScatteringProbability(MTENuclearReactor reactor, NuclearGridTile tile,
        NeutronType type) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack == null) return 0.02;
            if (reactor.isNaquariteInsulatorFoil(stack)) return 0.0;
            if (reactor.isItemInsulator(stack)) return 0.05;
            if (reactor.isItemRadiovoltaic(stack)) return 0.0;
            String name = stack.getUnlocalizedName().toLowerCase();
            if (name.contains("graphite") || name.contains("carbon") || name.contains("moderator")) {
                return (type == NeutronType.THERMAL) ? 0.621 : 0.93;
            }
            if (name.contains("reflector")) return (type == NeutronType.THERMAL) ? 0.98 : 0.95;
            if (name.contains("boron") || name.contains("cadmium") || name.contains("control")) {
                return (type == NeutronType.THERMAL) ? 0.05 : 0.10;
            }
            if (name.contains("coolant")) return 0.45;
            if (reactor.isItemFuel(stack)) return (type == NeutronType.THERMAL) ? 0.10 : 0.15;
            return 0.05;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.02;
            String name = fluid.getFluid().getName().toLowerCase();
            if (name.contains("heavywater")) return 0.85;
            if (name.contains("distilledwater")) return 0.70;
            if (name.contains("coolant")) return 0.45;
            if (name.contains("sodium")) return 0.20;
            return 0.10;
        } else if (tile.isHighPressureHatch()) {
            return tile.getHighPressureHatch().getScatteringProbability(type);
        }
        return 0.02;
    }

    public static double getTileScatteringProbability(MTENuclearReactor reactor, NuclearGridTile tile,
        NeutronType type) {
        double pBase = getBaseScatteringProbability(reactor, tile, type);
        if (tile.hasControlRod()) {
            double pRod = tile.getBottomControlRod().getScatteringProbability(type);
            return Math.min(1.0, 1.0 - (1.0 - pBase) * (1.0 - pRod));
        }
        return pBase;
    }

    public static double getTileModerationProbability(NuclearGridTile tile) {
        if (tile.isBus()) {
            ItemStack stack = tile.getBus().mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (tile.getReactor().isItemRadiovoltaic(stack)) return 0.0;
            if (stack == null) return 0.05;
            String name = stack.getUnlocalizedName().toLowerCase();
            if (name.contains("graphite") || name.contains("carbon") || name.contains("moderator")) return 0.50;
            if (name.contains("reflector")) return 0.20;
            if (name.contains("coolant")) return 0.40;
            if (tile.getReactor().isItemFuel(stack)) return 0.10;
            if (name.contains("boron") || name.contains("cadmium") || name.contains("control")) return 0.05;
            return 0.05;
        } else if (tile.isHatch()) {
            FluidStack fluid = tile.getHatch().mInputFluid;
            if (fluid == null) return 0.05;
            String name = fluid.getFluid().getName().toLowerCase();
            if (name.contains("heavywater")) return 0.90;
            if (name.contains("distilledwater")) return 0.80;
            if (name.contains("coolant")) return 0.40;
            if (name.contains("sodium")) return 0.05;
            return 0.20;
        } else if (tile.isHighPressureHatch()) {
            return tile.getHighPressureHatch().getModerationProbability();
        }
        return 0.05;
    }

    public static void onTileNeutronAbsorbed(MTENuclearReactor reactor, NuclearGridTile tile, NeutronType type,
        int count) {
        if (count <= 0) return;
        if (tile.hasControlRod()) {
            double pRod = tile.getBottomControlRod().getAbsorptionProbability(type);
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
                String name = hatch.mInputFluid.getFluid().getName().toLowerCase();
                boolean isHP = name.contains("highpressure");
                int chance = isHP ? Math.min(100, count * 10) : Math.min(100, count * 5);
                int yield = isHP ? 2 : 1;

                if (name.contains("distilledwater")) {
                    if (reactor.getRandomNumber(100) < chance) {
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
                    if (reactor.getRandomNumber(100) < chance) {
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
            tile.getHighPressureHatch().onNeutronAbsorbed(type, count);
        }
    }

    public static void onTileNeutronScattered(MTENuclearReactor reactor, NuclearGridTile tile, NeutronType type,
        int count) {
        if (tile.isBus()) {
            MTEHatchNuclearBus bus = tile.getBus();
            ItemStack stack = bus.mInventory[MTEHatchNuclearBus.SLOT_INPUT];
            if (stack != null && stack.isItemStackDamageable()) {
                String name = stack.getUnlocalizedName().toLowerCase();
                if (name.contains("reflector")) {
                    if (reactor.getRandomNumber(20) == 0) {
                        reactor.damageItemComponent(bus, 1);
                    }
                }
            }
        } else if (tile.isHighPressureHatch()) {
            tile.getHighPressureHatch().onNeutronScattered(type, count);
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
            tile.getHighPressureHatch().addNeutronFlux(type, count);
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
            tile.getHighPressureHatch().nuclearTick(efficiency);
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
        if (stack == null) {
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
            NuclearFuelType fuelType = NuclearFuelType.fromName(stack.getUnlocalizedName());
            double tempDmg = (fuelType != null) ? fuelType.calculateTemperatureDamage(bus.mTemperature, ambient)
                : (bus.mTemperature > ambient ? (bus.mTemperature - ambient) / 100.0 : 0.0);

            double totalDmg = Math.max(0.0, bus.mLastNeutronsGenerated * 0.25)
                + (bus.mFastAbsorbed + bus.mThermalAbsorbed) * 1.0
                + tempDmg;
            int damage = (int) totalDmg;
            double remainder = totalDmg - damage;
            if (remainder > 0.0 && Math.random() < remainder) {
                damage++;
            }
            if (stack.getItem() instanceof ItemRadioactiveCell radCell) {
                radCell.damageItemStack(stack, damage);
                if (radCell.getDamageOfStack(stack) >= radCell.getMaxDamageEx()) {
                    reactor.mCycleZeroedFuelItems++;
                    ItemStack depleted = null;
                    if (radCell instanceof ItemRadioactiveCellIC icCell && icCell.sDepleted != null) {
                        depleted = icCell.sDepleted.copy();
                    } else {
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
                int maxDmg = stack.getMaxDamage();
                int newDmg = curDmg + damage;
                if (newDmg >= maxDmg) {
                    reactor.mCycleZeroedFuelItems++;
                    ItemStack depleted = reactor.getItemDepletedForm(stack);
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
        // 2. RADIOVOLTAIC DIRECT EU GENERATION
        else if (reactor.isItemRadiovoltaic(stack)) {
            int tier = reactor.getRadiovoltaicTier(stack);
            long maxEU = (tier >= 2) ? 4096 : 1024;
            double weightedFlux = bus.mFastAbsorbed * 4.0 + bus.mThermalAbsorbed * 1.0;
            double satFlux = 60.0;
            double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
            long genEU = (long) Math.round(maxEU * Math.tanh(weightedFlux / satFlux) * effFactor);
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
                reactor.getReactorDummy().setCurrentTile(tile);
                int gx = tile.getGx();
                int gy = tile.getGy();

                if (comp.canStoreHeat(reactor.getReactorDummy(), stack, gx, gy)) {
                    int maxHeat = comp.getMaxHeat(reactor.getReactorDummy(), stack, gx, gy);
                    int curHeat = comp.getCurrentHeat(reactor.getReactorDummy(), stack, gx, gy);
                    double ambient = reactor.getAmbientTemperature();
                    if (maxHeat > 0 && bus.mTemperature > ambient) {
                        int maxTransferPerTick = Math.max(20, maxHeat / 50);
                        double heatAvailable = (bus.mTemperature - ambient) * NuclearSimulationEngine.EU_PER_DEGREE;
                        double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
                        int heatToTake = (int) Math
                            .round(Math.min(heatAvailable / 25.0, (double) maxTransferPerTick) * effFactor);
                        int room = maxHeat - curHeat;
                        heatToTake = Math.min(heatToTake, room);

                        if (heatToTake > 0) {
                            comp.alterHeat(reactor.getReactorDummy(), stack, gx, gy, heatToTake);
                            double heatConsumed = heatToTake * 25.0;
                            bus.mTemperature -= (heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE);
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
            reactor.getReactorDummy().setCurrentTile(tile);
            if (comp.canStoreHeat(reactor.getReactorDummy(), stack, 0, 0)) {
                bus.mDirectEUProduced = 0;
                int maxHeat = comp.getMaxHeat(reactor.getReactorDummy(), stack, 0, 0);
                int curHeat = comp.getCurrentHeat(reactor.getReactorDummy(), stack, 0, 0);
                double ambient = reactor.getAmbientTemperature();
                if (maxHeat > 0 && bus.mTemperature > ambient) {
                    int maxTransferPerTick = Math.max(1, maxHeat / 100);
                    double heatAvailable = (bus.mTemperature - ambient) * NuclearSimulationEngine.EU_PER_DEGREE;
                    double effFactor = Math.max(0.0, Math.min(1.0, efficiency));
                    int heatToTake = (int) Math
                        .round(Math.min(heatAvailable / 25.0, (double) maxTransferPerTick) * effFactor);
                    int room = maxHeat - curHeat;
                    heatToTake = Math.min(heatToTake, room);

                    if (heatToTake > 0) {
                        comp.alterHeat(reactor.getReactorDummy(), stack, 0, 0, heatToTake);
                        double heatConsumed = heatToTake * 25.0;
                        bus.mTemperature -= (heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE);
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
        // 4. GENERIC COOLANT/VENT FALLBACK
        else if (stack.getUnlocalizedName().toLowerCase().contains("coolant")) {
            bus.mDirectEUProduced = 0;
            double ambient = reactor.getAmbientTemperature();
            if (bus.mTemperature > ambient) {
                double heatToAbsorb = Math.min(bus.mTemperature - ambient, 100.0)
                    * NuclearSimulationEngine.EU_PER_DEGREE;
                if (heatToAbsorb > 0) {
                    bus.mTemperature -= (heatToAbsorb / NuclearSimulationEngine.EU_PER_DEGREE);
                    int cellDamage = Math.max(1, (int) (heatToAbsorb / 50.0));
                    reactor.damageItemComponent(bus, cellDamage);
                    bus.mLastHeatOutput = heatToAbsorb;
                }
            }
        }
        // 5. EASTER EGG: MOLTEN CHEESE EXTRACTION ABOVE 65°C
        else if (bus.mTemperature > 65.0) {
            bus.mDirectEUProduced = 0;
            if (reactor.getBaseMetaTileEntity() != null) {
                long aTick = reactor.getBaseMetaTileEntity().getTimer();
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
        double tempDrop = heatAbsorbed / NuclearSimulationEngine.EU_PER_DEGREE;
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
                fluid.getFluid().getName()) : null;
            double tempDmg = (fuelType != null) ? fuelType.calculateTemperatureDamage(hatch.mTemperature, ambient)
                : (hatch.mTemperature > ambient ? (hatch.mTemperature - ambient) / 100.0 : 0.0);

            double totalBurn = Math.max(0.0, hatch.mLastNeutronsGenerated * 0.25)
                + (hatch.mFastAbsorbed + hatch.mThermalAbsorbed) * 1.0
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

        String name = hatch.mInputFluid.getFluid().getName().toLowerCase();
        if (name.equals("water")) return; // Regular water is completely disallowed

        int reqTier = MTEHatchNuclearHatch.getRequiredFluidTier(name);
        if (reactor.mPipeTier >= 0 && reactor.mPipeTier < reqTier) {
            return;
        }

        double minOperatingTemp = NuclearSimulationEngine.getCoolantSinkTemperature(name, reactor.getAmbientTemperature());
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
                    .max(minOperatingTemp, hatch.mTemperature - (heatConsumed / NuclearSimulationEngine.EU_PER_DEGREE));
                hatch.markTileDirty();
            }
        }
    }
}
