package com.gtnewhorizons.modularnuclear.common.gui;

import com.gtnewhorizon.gtnhlib.color.ColorResource;

/*-
Note [Resource packs restyle our GUIs] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Topic.  Our GUIs, NEI pages and Waila bars draw their own panels, plots and text.  A resource pack such as Shadow UI
restyles the GUIs around them, so everything we draw has to be restyleable by packs as well.

Design.  We don't decide on a look for any pack in the code, and in particular there is no dark mode here.  The
defaults are the designed look, and packs override them through the same channels the rest of GTNH uses:

    textures       where we can, we draw with a texture: NEI's own slot.png for slots and bevels on the NEI pages
                   (NEIPalette.drawSlot, drawBevel), and ours for icons such as gui/lock.png (LockIconWidget)
    colours        every other colour is a GTNHLib ColorResource in ColorUtils.  A pack overrides one with a lang entry,
                   such as color.resource.modularnuclear.neiInk=BFBFBF, and F3+T reloads it
    colour scales  the heat map scales are textures a pack may add; see Note [Heat map scales come from textures]

Moving parts.
    ColorUtils                every colour, named by where and what it is: nei*, reactorGrid*, graph*, plot*, ...
    NEIPalette                the NEI colours, read once per resource reload, with the few worked out by blending
    ColorScales               the heat map scales

Wrinkles.
(W1) Angelica recolours text as well.  A pack's dark_mode_utils block in pack.mcmeta makes Angelica replace text in a
     GuiContainer whose channels are all near black or all near white.  That works on the defaults here as on any other
     mod's, and a pack can still override a colour if Angelica's choice doesn't read well.

(W2) NEI's window shows through our cards rather than being painted over, and neiPanel stands for its colour where
     we need it: the faint thermal arrows and the scattering spread on the NEI component cards mix neiThermal or
     neiInk into it, and the gaps in the heat card's insulating walls are drawn in it.  A pack that restyles NEI's
     window sets neiPanel to the window's colour.

(W3) MUI2 text in our machine GUIs uses IKey.color(IntSupplier) with getColor, so a reload applies without reopening
     the GUI.

(W4) Text coloured with formatting codes (EnumChatFormatting) stays as it is: tooltips, MUI2 labels on the
     GTWidgetThemes text themes, and the like.  Those follow the font's colour codes, as they do in the rest of GTNH.
*/
public final class ColorUtils {

    private ColorUtils() {}

    private static final ColorResource.Factory color = new ColorResource.Factory("modularnuclear");

    public static final ColorResource
    // spotless:off
        // NEI pages.  See wrinkle (W2) for neiPanel
        neiPanel                    = color.rgb("neiPanel",                     "0xC6C6C6"),
        neiPlotWell                 = color.rgb("neiPlotWell",                  "0xE6E6E6"),
        neiDivider                  = color.rgb("neiDivider",                   "0x8B8B8B"),
        neiPlotGrid                 = color.argb("neiPlotGrid",                 "0x20000000"),
        neiPlotCursor               = color.argb("neiPlotCursor",               "0xA0000000"),
        neiInkStrong                = color.rgb("neiInkStrong",                 "0x000000"),
        neiInk                      = color.rgb("neiInk",                       "0x404040"),
        neiInkDim                   = color.rgb("neiInkDim",                    "0x606060"),
        neiInkFaint                 = color.rgb("neiInkFaint",                  "0x505050"),
        neiTitle                    = color.rgb("neiTitle",                     "0x111111"),
        neiSubtitle                 = color.rgb("neiSubtitle",                  "0x333333"),
        neiLabel                    = color.rgb("neiLabel",                     "0x555555"),
        neiCategory                 = color.rgb("neiCategory",                  "0x103080"),
        neiReflectedText            = color.rgb("neiReflectedText",             "0x9A6B00"),
        neiFast                     = color.rgb("neiFast",                      "0x0033EE"),
        neiFastText                 = color.rgb("neiFastText",                  "0x0033CC"),
        neiAbsorbed                 = color.rgb("neiAbsorbed",                  "0xDD0000"),
        neiAbsorbedText             = color.rgb("neiAbsorbedText",              "0xCC0000"),
        neiScattered                = color.rgb("neiScattered",                 "0x00CC00"),
        neiScatteredText            = color.rgb("neiScatteredText",             "0x00A000"),
        neiThermal                  = color.rgb("neiThermal",                   "0x7F00FF"),
        neiThermalCornerText        = color.rgb("neiThermalCornerText",         "0x7200E6"),
        neiLost                     = color.rgb("neiLost",                      "0x7A7A7A"),
        neiHeatArrow                = color.rgb("neiHeatArrow",                 "0xF2913D"),
        neiConductanceText          = color.rgb("neiConductanceText",           "0x8A4A00"),
        neiRemoval                  = color.rgb("neiRemoval",                   "0x0055AA"),
        neiBlocksText               = color.rgb("neiBlocksText",                "0x902020"),
        neiBurnsText                = color.rgb("neiBurnsText",                 "0xAA5500"),
        neiByproductText            = color.rgb("neiByproductText",             "0x205090"),
        neiIndestructibleText       = color.rgb("neiIndestructibleText",        "0x008000"),
        neiReactivityCurve          = color.rgb("neiReactivityCurve",           "0xC87800"),
        neiReactivityLabel          = color.rgb("neiReactivityLabel",           "0x8A5F00"),
        neiDamageCurve              = color.rgb("neiDamageCurve",               "0xCC0000"),
        neiPowerCurve               = color.rgb("neiPowerCurve",                "0x0055CC"),
        neiPowerLabel               = color.rgb("neiPowerLabel",                "0x0044AA"),
        neiPeakStat                 = color.rgb("neiPeakStat",                  "0x00AAAA"),
        neiFloorStat                = color.rgb("neiFloorStat",                 "0xFF5555"),
        neiPointStat                = color.rgb("neiPointStat",                 "0x5555FF"),
        neiGoodStat                 = color.rgb("neiGoodStat",                  "0x00AA00"),
        neiThermalFissionStat       = color.rgb("neiThermalFissionStat",        "0xAA00AA"),
        neiYieldStat                = color.rgb("neiYieldStat",                 "0xAA0000"),
        neiHotTile                  = color.rgb("neiHotTile",                   "0xE5582A"),
        neiColdTile                 = color.rgb("neiColdTile",                  "0x2B6CDE"),
        neiTileSign                 = color.rgb("neiTileSign",                  "0xFFFFFF"),
        neiTileHighlight            = color.argb("neiTileHighlight",            "0x59FFFFFF"),
        neiTileShadow               = color.argb("neiTileShadow",               "0x4D000000"),

        // Reactor core grid
        reactorGridBackground       = color.argb("reactorGridBackground",       "0xEE12161E"),
        reactorGridBorder           = color.rgb("reactorGridBorder",            "0x2A3645"),
        reactorGridOfflineText      = color.rgb("reactorGridOfflineText",       "0x888888"),
        reactorGridSlotBorder       = color.rgb("reactorGridSlotBorder",        "0x373737"),
        reactorGridSlotFill         = color.rgb("reactorGridSlotFill",          "0x1E1E1E"),
        reactorGridStackSize        = color.rgb("reactorGridStackSize",         "0xFFFFFF"),
        reactorGridDurabilityTrack  = color.rgb("reactorGridDurabilityTrack",   "0x000000"),
        reactorGridHighPressure     = color.argb("reactorGridHighPressure",     "0x40FFA500"),
        reactorGridFluid            = color.argb("reactorGridFluid",            "0x300055AA"),
        reactorGridRodBackdrop      = color.argb("reactorGridRodBackdrop",      "0x881E293B"),
        reactorGridRodBorder        = color.rgb("reactorGridRodBorder",         "0x00AAFF"),
        reactorGridNoRod            = color.argb("reactorGridNoRod",            "0x77000000"),
        reactorGridRodSilver        = color.rgb("reactorGridRodSilver",         "0xC0C0C0"),
        reactorGridRodBoron         = color.rgb("reactorGridRodBoron",          "0x5A5A6E"),
        reactorGridRodCadmium       = color.rgb("reactorGridRodCadmium",        "0x5588AA"),
        reactorGridRodIndium        = color.rgb("reactorGridRodIndium",         "0x3366BB"),
        reactorGridRodHafnium       = color.rgb("reactorGridRodHafnium",        "0xAA3333"),
        reactorGridRodNeutronium    = color.rgb("reactorGridRodNeutronium",     "0xE8E8F0"),
        reactorGridRodNone          = color.argb("reactorGridRodNone",          "0x88666666"),
        reactorGridOverheat         = color.argb("reactorGridOverheat",         "0x60FF0000"),
        reactorGridDragPreview      = color.argb("reactorGridDragPreview",      "0x6000FF00"),
        reactorGridControllerBorder = color.rgb("reactorGridControllerBorder",  "0x4A5568"),
        reactorGridControllerFill   = color.rgb("reactorGridControllerFill",    "0x1A202C"),
        reactorGridHoverBorder      = color.rgb("reactorGridHoverBorder",       "0xFFFFFF"),
        reactorGridHoverFill        = color.argb("reactorGridHoverFill",        "0x30FFFFFF"),

        // Reactor controller GUI
        reactorCoreTemp             = color.rgb("reactorCoreTemp",              "0xFFC800"),
        reactorReactivity           = color.rgb("reactorReactivity",            "0x64C8FF"),
        reactorNeutrons             = color.rgb("reactorNeutrons",              "0x64DCFF"),
        reactorNeutronBreakdown     = color.rgb("reactorNeutronBreakdown",      "0xC8C8C8"),
        reactorDamage               = color.rgb("reactorDamage",                "0xFF5050"),
        reactorIdle                 = color.rgb("reactorIdle",                  "0xFFAA3C"),
        scramBorder                 = color.rgb("scramBorder",                  "0x330000"),
        scramButton                 = color.rgb("scramButton",                  "0xAA0000"),
        scramButtonHover            = color.rgb("scramButtonHover",             "0xDD1111"),
        scramButtonActive           = color.rgb("scramButtonActive",            "0xCC0000"),
        scramButtonActiveHover      = color.rgb("scramButtonActiveHover",       "0xFF3333"),
        scramHighlight              = color.argb("scramHighlight",              "0x55FFFFFF"),
        scramText                   = color.rgb("scramText",                    "0xFFFFFF"),

        // Reactor metrics plots
        plotBackground              = color.argb("plotBackground",              "0xEE111827"),
        plotBorder                  = color.rgb("plotBorder",                   "0x1F2937"),
        plotArea                    = color.argb("plotArea",                    "0x44030712"),
        plotGridLine                = color.argb("plotGridLine",                "0x1AFFFFFF"),
        plotCursor                  = color.argb("plotCursor",                  "0x73FFFFFF"),
        plotTitle                   = color.rgb("plotTitle",                    "0xE5E7EB"),
        plotLatest                  = color.rgb("plotLatest",                   "0x9CA3AF"),
        plotNoData                  = color.rgb("plotNoData",                   "0x6B7280"),
        plotWaiting                 = color.rgb("plotWaiting",                  "0x4B5563"),
        plotAxisLabel               = color.rgb("plotAxisLabel",                "0x6B7280"),
        plotSinglePoint             = color.rgb("plotSinglePoint",              "0xFFFFFF"),
        plotCoreTemp                = color.rgb("plotCoreTemp",                 "0xFFB020"),
        plotCoreTempMin             = color.argb("plotCoreTempMin",             "0x993B82F6"),
        plotCoreTempMax             = color.argb("plotCoreTempMax",             "0xCCEF4444"),
        plotReactivity              = color.rgb("plotReactivity",               "0x00D4FF"),
        plotReactivityMin           = color.argb("plotReactivityMin",           "0x996366F1"),
        plotReactivityMax           = color.argb("plotReactivityMax",           "0xCC38BDF8"),
        plotFastNeutrons            = color.rgb("plotFastNeutrons",             "0xA855F7"),
        plotThermalNeutrons         = color.rgb("plotThermalNeutrons",          "0x06B6D4"),
        plotEscapedNeutrons         = color.rgb("plotEscapedNeutrons",          "0xF97316"),
        plotDirectEu                = color.rgb("plotDirectEu",                 "0xEAB308"),
        plotCoolantOutput           = color.rgb("plotCoolantOutput",            "0x3B82F6"),

        // Coolant loop graph
        graphBackground             = color.rgb("graphBackground",              "0x0A0A0A"),
        graphBorder                 = color.rgb("graphBorder",                  "0x222222"),
        graphMessage                = color.rgb("graphMessage",                 "0xFF8888"),
        graphGridDots               = color.argb("graphGridDots",               "0x40383838"),
        graphPipe                   = color.argb("graphPipe",                   "0xE659616B"),
        graphPipeWarm               = color.argb("graphPipeWarm",               "0xE6E08C33"),
        graphPipeHot                = color.argb("graphPipeHot",                "0xE6E04033"),
        graphPipeHover              = color.rgb("graphPipeHover",               "0xD9D9D9"),
        graphPipeOutline            = color.argb("graphPipeOutline",            "0xE6141414"),
        graphArrow                  = color.argb("graphArrow",                  "0xD9BFBFBF"),
        graphArrowHover             = color.rgb("graphArrowHover",              "0xFFFFFF"),
        graphNode                   = color.argb("graphNode",                   "0xEE1E1E1E"),
        graphNodeHover              = color.argb("graphNodeHover",              "0xF02A2A2A"),
        graphNodeBorder             = color.rgb("graphNodeBorder",              "0x3A3A3A"),
        graphNodeBorderHover        = color.rgb("graphNodeBorderHover",         "0x888888"),
        graphNodeTitle              = color.rgb("graphNodeTitle",               "0xE0E0E0"),
        graphTemperature            = color.rgb("graphTemperature",             "0xFF5555"),
        graphPressure               = color.rgb("graphPressure",                "0xFFFF55"),
        graphFlow                   = color.rgb("graphFlow",                    "0x55FF55"),

        // Coolant loop machines
        loopStatus                  = color.rgb("loopStatus",                   "0xE0E0E0"),
        loopPressure                = color.rgb("loopPressure",                 "0xFFFF55"),
        loopFlow                    = color.rgb("loopFlow",                     "0x55FF55"),
        loopTemperature             = color.rgb("loopTemperature",              "0xFF5555"),
        loopHeatCapacity            = color.rgb("loopHeatCapacity",             "0xFFFF55"),
        loopArea                    = color.rgb("loopArea",                     "0x55FFFF"),
        loopHeatTransfer            = color.rgb("loopHeatTransfer",             "0xFFAA00"),

        // Coolant bath
        bathBackground              = color.rgb("bathBackground",               "0x22262B"),
        bathInner                   = color.rgb("bathInner",                    "0x15181C"),
        bathSlotBorder              = color.rgb("bathSlotBorder",               "0x374151"),
        bathSlotFill                = color.rgb("bathSlotFill",                 "0x1F2937"),
        bathProgress                = color.rgb("bathProgress",                 "0x10B981"),
        bathHover                   = color.argb("bathHover",                   "0x55FFFFFF"),

        // Floating panels
        panelZoomText               = color.rgb("panelZoomText",                "0xFFFFFF"),
        panelResizeHandle           = color.argb("panelResizeHandle",           "0xAA707882"),
        panelResizeHandleLine       = color.argb("panelResizeHandleLine",       "0xDD95A0AA"),
        panelResizeHandleHover      = color.rgb("panelResizeHandleHover",       "0x55D8FF"),
        panelResizeHandleLineHover  = color.rgb("panelResizeHandleLineHover",   "0xFFFFFF"),
        panelSnapArea               = color.argb("panelSnapArea",               "0x4055D8FF"),
        panelSnapAreaBorder         = color.argb("panelSnapAreaBorder",         "0xCC55D8FF"),
        panelDimBelow               = color.argb("panelDimBelow",               "0x7D101010"),

        // Hatch GUIs
        hatchSlotLabel              = color.rgb("hatchSlotLabel",               "0xFFFFFF"),
        hatchBusTitle               = color.rgb("hatchBusTitle",                "0x00FF80"),
        hatchControlTitle           = color.rgb("hatchControlTitle",            "0x00FF80"),
        hatchControlRodTitle        = color.rgb("hatchControlRodTitle",         "0xB464FF"),
        hatchHighPressureTitle      = color.rgb("hatchHighPressureTitle",       "0xFF8000"),
        hatchPressurizedTitle       = color.rgb("hatchPressurizedTitle",        "0xA855F7"),
        hatchStatsTitle             = color.rgb("hatchStatsTitle",              "0x00FFC8"),
        hatchTemperature            = color.rgb("hatchTemperature",             "0xFFC800"),
        hatchFastFlux               = color.rgb("hatchFastFlux",                "0x64C8FF"),
        hatchThermalFlux            = color.rgb("hatchThermalFlux",             "0x96B4FF"),
        hatchInsertion              = color.rgb("hatchInsertion",               "0x64FFC8"),
        hatchRodType                = color.rgb("hatchRodType",                 "0xC8C8FF"),
        hatchModerator              = color.rgb("hatchModerator",               "0x96FF96"),
        hatchHousing                = color.rgb("hatchHousing",                 "0xC8C864"),
        hatchPaired                 = color.rgb("hatchPaired",                  "0x96FF96"),
        hatchPump                   = color.rgb("hatchPump",                    "0x96B4FF"),
        hatchRole                   = color.rgb("hatchRole",                    "0xC8C8C8"),
        hatchFluidAmount            = color.rgb("hatchFluidAmount",             "0xB4B4B4"),
        hatchControlMetric          = color.rgb("hatchControlMetric",           "0x64C8FF"),
        hatchControlStatistic       = color.rgb("hatchControlStatistic",        "0xFFDC64"),
        hatchControlOutput          = color.rgb("hatchControlOutput",           "0xFF5050"),
        hatchControlBundled         = color.rgb("hatchControlBundled",          "0xC8A0FF"),

        // Waila core temperature bar, from cool to critical
        wailaTempNormalTop          = color.rgb("wailaTempNormalTop",           "0x00E676"),
        wailaTempNormalBottom       = color.rgb("wailaTempNormalBottom",        "0x007A33"),
        wailaTempWarmTop            = color.rgb("wailaTempWarmTop",             "0xFFD700"),
        wailaTempWarmBottom         = color.rgb("wailaTempWarmBottom",          "0xB8860B"),
        wailaTempHighTop            = color.rgb("wailaTempHighTop",             "0xFF9900"),
        wailaTempHighBottom         = color.rgb("wailaTempHighBottom",          "0xCC5500"),
        wailaTempCriticalTop        = color.rgb("wailaTempCriticalTop",         "0xFF3333"),
        wailaTempCriticalBottom     = color.rgb("wailaTempCriticalBottom",      "0x880000");
    // spotless:on
}
