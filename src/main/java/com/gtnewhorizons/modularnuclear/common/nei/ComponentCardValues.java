package com.gtnewhorizons.modularnuclear.common.nei;

import net.minecraft.item.ItemStack;

import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ControlRodData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantFluidData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.CoolantItemData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatExchangerData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.HeatVentData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.InsulatorData;
import com.gtnewhorizons.modularnuclear.common.config.data.ComponentData.ModeratorData;
import com.gtnewhorizons.modularnuclear.common.metatileentity.multi.NuclearReactorPhysics;
import com.gtnewhorizons.modularnuclear.common.nuclear.EngineParameters;
import com.gtnewhorizons.modularnuclear.common.nuclear.NeutronType;
import com.gtnewhorizons.modularnuclear.common.nuclear.NuclearSimulationEngine;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileContent;
import com.gtnewhorizons.modularnuclear.common.nuclear.TileProperties;

import ic2.api.reactor.IReactorComponent;

/*-
Note [Thermal and heat cards] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Topic.  The "MPTR reactor components" NEI page explains a component with one card per subject.  The thermal and heat
cards draw numbers the game derives rather than numbers it stores, and they must agree with what a reactor does.

Design.  ComponentCardValues works out those numbers once per component from the same code the reactor reads (and
the fast card's shares too, in ComponentCardValues.Fast):
TileContent and TileProperties for neutrons and conduction, and the configuration record for what else it does with
heat.  The thermal card shows where arriving neutrons go, with arrow widths in proportion, and a 3×3 view of the
spread.  The heat card has two parts: heat crossing from a hot neighbour to a cold one, with arrow width set by k, and
below it a row of tiles heating up, run with the real conduction law.

Moving parts.
    ComponentCardValues                  the numbers; ComponentStatsData.cardValues() holds them per component
    HeatRowSimulation                    the row of tiles
    NEINeutronInteractionHandler         drawThermalNeutronDiagram, drawHeatDiagram, drawHeatRow and their text in
                                         drawExtras; getTooltipsAt explains each part

Wrinkles.
(W1) A thermal neutron has three outcomes, and "lost" isn't configured anywhere.  NuclearSimulationEngine.simulate
     absorbs first and scatters what survives, and what neither absorbs nor scatters leaves the reactor.  Distilled
     water's 10% absorption and 70% scattering therefore give 10% absorbed, 63% scattered and 27% lost.  An empty tile
     loses 97%.

(W2) Unlike the fast card, the thermal card has no reflected arrow and no moderated share.  Components never reflect
     thermal neutrons, not even reflectors, whose thermal scattering just spreads like any other: only the grid's edge
     can, through wall_reflection_chance.  See wrinkle (W1) of Note [Fast neutrons are reflected or scattered].  Scattered neutrons go 1/6 to each side and 1/12 to each corner
     (DIR_WEIGHTS).

(W3) Control rods aren't TileContents, since the reactor reads them from their hatch.  The card shows a fully inserted
     rod from its ControlRodData, as the fast card and the page's header do.

(W4) Only core hatches have a heat capacity of their own: it depends on the hatch's tier, not on its fluid, so a fluid
     shows the range from LV to UHV.  Every other tile holds huPerDegree.

(W5) The row is an illustration with fixed surroundings, not a prediction for a real layout.  See
     HeatRowSimulation.run for the setup.  It uses the game's constants, so changing them in the config changes it.

(W6) Vents and exchangers are shown at full power.  In a reactor, they do nothing without EU.
*/

/** What the thermal and heat cards show for a component. */
public final class ComponentCardValues {

    public enum HeatAction {
        CONDUCTS_ONLY,
        BOILS,
        COOLS_TO_AMBIENT,
        VENTS,
        EXCHANGES,
        STORES,
        BLOCKS,
        BURNS
    }

    /** The k of a typical neighbour, such as a fuel rod: the heat card's conductance and row assume it. */
    public static final double NEIGHBOUR_K = 0.05;
    /** The ambient temperature the cards assume, in °C; a real reactor's depends on its biome. */
    public static final double AMBIENT = NuclearSimulationEngine.DEFAULT_AMBIENT_TEMP;

    private static final int MIN_CORE_HATCH_TIER = 1; // LV
    private static final int MAX_CORE_HATCH_TIER = 9; // UHV

    /**
     * Shares of the fast neutrons arriving at the tile that it absorbs, reflects and scatters; the rest pass through.
     * See Note [Fast neutrons are reflected or scattered]
     */
    public final Fast fast;

    /** Shares of the thermal neutrons arriving at the tile; they add up to 1. See wrinkle (W1). */
    public final double absorbed;
    public final double scattered;
    public final double lost;

    public final double k;
    public final double dampening;
    /** Heat capacity in HU/°C. The two differ only for a fluid, by its hatch's tier. See wrinkle (W4). */
    public final double minCapacity;
    public final double maxCapacity;
    public final boolean inHatch;

    public final HeatAction action;
    /** VENTS, EXCHANGES: HU/t. BOILS, COOLS_TO_AMBIENT: HU per litre. STORES: total HU. */
    public final double actionHeat;
    /** VENTS, EXCHANGES: EU/t. STORES: HU/s. BOILS: the boiling point. BURNS: the temperature. */
    public final double actionRate;

    private float[][] heatRow;

    /**
     * What happens to the fast neutrons arriving at a tile, as NuclearSimulationEngine.simulate draws it: shares of all
     * that arrive, and the share of scattered ones moderated.
     */
    public record Fast(double absorbed, double reflected, double scattered, double moderated) {

        static Fast of(double absorption, double reflection, double scattering, double moderation) {
            double a = clamp01(absorption), r = clamp01(reflection), s = clamp01(scattering);
            double scale = Math.max(1.0, a + r + s);
            return new Fast(a / scale, r / scale, s / scale, clamp01(moderation));
        }

        /** The share that neither is absorbed, reflected nor scattered, and carries on. */
        public double passed() {
            return Math.max(0.0, 1.0 - absorbed - reflected - scattered);
        }
    }

    private ComponentCardValues(Fast fast, double absorption, double scatteringProbability, double k, double dampening,
        double minCapacity, double maxCapacity, boolean inHatch, HeatAction action, double actionHeat,
        double actionRate) {
        this.fast = fast;
        double a = clamp01(absorption);
        this.absorbed = a;
        this.scattered = TileProperties.thermalScatteredShare(a, scatteringProbability);
        this.lost = Math.max(0.0, 1.0 - a - this.scattered);
        this.k = k;
        this.dampening = clamp01(dampening);
        this.minCapacity = minCapacity;
        this.maxCapacity = maxCapacity;
        this.inHatch = inHatch;
        this.action = action;
        this.actionHeat = actionHeat;
        this.actionRate = actionRate;
    }

    private static double clamp01(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }

    public static ComponentCardValues of(NEINeutronInteractionHandler.ComponentStatsData d) {
        double tileCapacity = NuclearSimulationEngine.params().huPerDegree;
        ControlRodData rod = ReactorDataRegistry.findControlRod(d.stack);
        if (rod != null) { // See wrinkle (W3)
            return new ComponentCardValues(
                Fast.of(rod.maxFastAbsorption, 0.0, rod.scatteringFast, 0.0),
                rod.maxThermalAbsorption,
                rod.scatteringThermal,
                rod.heatTransferCoeff,
                0.0,
                tileCapacity,
                tileCapacity,
                false,
                HeatAction.CONDUCTS_ONLY,
                0.0,
                0.0);
        }
        TileContent content = contentOf(d);
        if (content.kind == TileContent.Kind.OTHER) {
            return new ComponentCardValues(
                Fast.of(d.fastAbsorption, d.fastReflection, d.fastScattering, d.slowingProbability),
                d.thermalAbsorption,
                d.thermalScattering,
                d.heatTransferCoeff,
                d.thermalInsulationFactor,
                tileCapacity,
                tileCapacity,
                false,
                d.thermalInsulationFactor > 0.0 ? HeatAction.BLOCKS : HeatAction.CONDUCTS_ONLY,
                0.0,
                0.0);
        }
        return ofContent(content, d.stack, d.thermalInsulationFactor);
    }

    private static TileContent contentOf(NEINeutronInteractionHandler.ComponentStatsData d) {
        if (NEINeutronInteractionHandler.CATEGORY_EMPTY.equals(d.category)) return TileContent.EMPTY;
        CoolantFluidData fluid = ReactorDataRegistry.findCoolantFluid(d.stack);
        if (fluid != null) return TileContent.of(TileContent.Kind.COOLANT_FLUID, fluid);
        return NuclearReactorPhysics.resolveItemContent(null, d.stack);
    }

    /**
     * @param stack                 the item, for a coolant cell's real capacity; may be null
     * @param unconfiguredDampening the dampening of an insulator that has no configuration entry
     */
    public static ComponentCardValues ofContent(TileContent c, ItemStack stack, double unconfiguredDampening) {
        EngineParameters p = NuclearSimulationEngine.params();
        InsulatorData insulator = c.insulator();
        double dampening = insulator != null ? insulator.dampening
            : (c.kind == TileContent.Kind.GENERIC_INSULATOR || c.kind == TileContent.Kind.GENERIC_NAQUARITE)
                ? unconfiguredDampening
                : 0.0;

        HeatAction action = dampening > 0.0 ? HeatAction.BLOCKS : HeatAction.CONDUCTS_ONLY;
        double actionHeat = 0.0;
        double actionRate = 0.0;
        double minCapacity = p.huPerDegree;
        double maxCapacity = p.huPerDegree;
        boolean inHatch = false;

        CoolantFluidData fluid = c.coolantFluid();
        HeatVentData vent = c.heatVent();
        HeatExchangerData exchanger = c.heatExchanger();
        CoolantItemData cell = c.coolantItem();
        ModeratorData moderator = c.moderator();
        if (fluid != null) {
            inHatch = true;
            minCapacity = p.hatchHeatCapacity(MIN_CORE_HATCH_TIER);
            maxCapacity = p.hatchHeatCapacity(MAX_CORE_HATCH_TIER);
            double sink = NuclearSimulationEngine.coolantSinkTemperature(fluid, AMBIENT);
            action = sink > AMBIENT ? HeatAction.BOILS : HeatAction.COOLS_TO_AMBIENT;
            actionHeat = fluid.heatPerLiter;
            actionRate = sink;
        } else if (vent != null) {
            action = HeatAction.VENTS;
            actionHeat = vent.maxHUt;
            actionRate = vent.maxEUt;
        } else if (exchanger != null) {
            action = HeatAction.EXCHANGES;
            actionHeat = exchanger.maxHUt;
            actionRate = exchanger.maxEUt;
        } else if (cell != null) {
            action = HeatAction.STORES;
            actionHeat = coolantCellCapacity(stack, cell);
            actionRate = actionHeat / 100.0; // TileTick.coolantCellUptake: 1% of its capacity per second
        } else if (moderator != null && moderator.maxTemperature > 0.0) {
            action = HeatAction.BURNS;
            actionRate = moderator.maxTemperature;
        }

        return new ComponentCardValues(
            Fast.of(
                TileProperties.absorption(c, NeutronType.FAST),
                TileProperties.reflectionFast(c),
                TileProperties.scattering(c, NeutronType.FAST),
                TileProperties.moderation(c)),
            TileProperties.absorption(c, NeutronType.THERMAL),
            TileProperties.scattering(c, NeutronType.THERMAL),
            TileProperties.heatTransferCoeff(c),
            dampening,
            minCapacity,
            maxCapacity,
            inHatch,
            action,
            actionHeat,
            actionRate);
    }

    /** The heat a coolant cell holds when full, in HU. */
    public static double coolantCellCapacity(ItemStack stack, CoolantItemData cell) {
        if (stack != null && stack.getItem() instanceof IReactorComponent comp) {
            return comp.getMaxHeat(null, stack, 0, 0) * cell.getHuPerDurability();
        }
        return cell.heatCapacity;
    }

    /** The share of arriving thermal neutrons each side neighbour receives (DIR_WEIGHTS). */
    public double perSide() {
        return scattered / 6.0;
    }

    /** The share of arriving thermal neutrons each corner neighbour receives (DIR_WEIGHTS). */
    public double perCorner() {
        return scattered / 12.0;
    }

    public boolean removesHeat() {
        return action == HeatAction.BOILS || action == HeatAction.COOLS_TO_AMBIENT
            || action == HeatAction.VENTS
            || action == HeatAction.STORES;
    }

    /** Heat conducted to or from a neighbour of k {@link #NEIGHBOUR_K}, in HU/s per °C of difference. */
    public double conductance() {
        EngineParameters p = NuclearSimulationEngine.params();
        return p.huPerDegree * p.gridConductionMultiplier
            * (Math.max(NuclearSimulationEngine.BASE_HEAT_CONDUCTION, k) + NEIGHBOUR_K)
            / 2.0
            * (1.0 - dampening);
    }

    /** Temperatures along the heat card's row, by frame. Worked out when first asked for. */
    public float[][] heatRow() {
        if (heatRow == null) heatRow = HeatRowSimulation.run(this, NuclearSimulationEngine.params());
        return heatRow;
    }
}
