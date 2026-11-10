package com.gtnewhorizons.modularnuclear.common.gui;

import com.gtnewhorizon.gtnhlib.color.ColorResource;

/*-
Note [Resource packs restyle our GUIs] (generated)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
Topic.  Our GUIs, NEI pages and Waila bars draw their own panels, plots and text.  A resource pack such as Shadow UI
restyles the GUIs around them, so everything we draw has to be restyleable by packs as well.

Design.  We don't decide on a look for any pack in the code, and in particular there is no dark mode here.  The
defaults are the designed look, and packs override them through the same channels the rest of GTNH uses:

    textures       where we can, we draw with a texture: NEI's own slot.png for slots, card bevels and plot
                   backgrounds on the NEI pages (NEIPalette.drawSlot, drawBevel, drawPlotWell), and ours for icons
                   such as gui/lock.png (LockIconWidget).  A plot is a stretched slot, so the curves' default
                   colours are picked to read on the slot's grey
    MUI2 themes    our machine GUIs draw what GregTech's draw from the GUI's theme, as GregTech's widgets do
                   (ThemedDraw): item slots in the core grid and the coolant bath, and GregTech's terminal screen
                   behind the metrics plots and the coolant loop graph.  GregTech's themes and resource packs
                   restyle those along with the rest of the GUI.  The colours drawn on a terminal default to
                   Minecraft's text colours (0x55FFFF, 0xFFAA00 and so on) and 0xFAFAFA, as GregTech's own
                   terminals use, and nothing here defaults to a tinted dark slate or navy
    colours        every other colour is a GTNHLib ColorResource in ColorUtils.  A pack overrides one with a lang entry,
                   such as color.resource.modularnuclear.neiInk=BFBFBF, and F3+T reloads it
    colour scales  the heat map scales are textures a pack may add; see Note [Heat map scales come from textures]

Moving parts.
    ColorUtils                every colour, named by where and what it is: nei*, reactorGrid*, graph*, plot*, ...
    ThemedDraw                slot and terminal backgrounds from the MUI2 theme
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
        neiDivider                  = color.rgb("neiDivider",                   "0x8B8B8B"),
        neiPlotGrid                 = color.argb("neiPlotGrid",                 "0x28000000"),
        neiPlotCursor               = color.argb("neiPlotCursor",               "0xC0FFFFFF"),
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
        neiReactivityCurve          = color.rgb("neiReactivityCurve",           "0xFFFF55"),
        neiReactivityLabel          = color.rgb("neiReactivityLabel",           "0x8A5F00"),
        neiDamageCurve              = color.rgb("neiDamageCurve",               "0xAA0000"),
        neiPowerCurve               = color.rgb("neiPowerCurve",                "0x55FFFF"),
        neiPowerLabel               = color.rgb("neiPowerLabel",                "0x0044AA"),
        neiSteamRoute               = color.rgb("neiSteamRoute",                "0x505050"),
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

        // Reactor core grid: on the panel, its slots from the GUI's theme
        reactorGridOfflineText      = color.rgb("reactorGridOfflineText",       "0x404040"),
        reactorGridStackSize        = color.rgb("reactorGridStackSize",         "0xFFFFFF"),
        reactorGridDurabilityTrack  = color.rgb("reactorGridDurabilityTrack",   "0x000000"),
        reactorGridHighPressure     = color.argb("reactorGridHighPressure",     "0x40FFAA00"),
        reactorGridFluid            = color.argb("reactorGridFluid",            "0x285555FF"),
        reactorGridRodBackdrop      = color.argb("reactorGridRodBackdrop",      "0x88000000"),
        reactorGridRodBorder        = color.rgb("reactorGridRodBorder",         "0x00AAAA"),
        reactorGridNoRod            = color.argb("reactorGridNoRod",            "0x77000000"),
        reactorGridRodSilver        = color.rgb("reactorGridRodSilver",         "0xC0C0C0"),
        reactorGridRodBoron         = color.rgb("reactorGridRodBoron",          "0x5A5A6E"),
        reactorGridRodCadmium       = color.rgb("reactorGridRodCadmium",        "0x7A8C99"),
        reactorGridRodIndium        = color.rgb("reactorGridRodIndium",         "0x6A6AB0"),
        reactorGridRodHafnium       = color.rgb("reactorGridRodHafnium",        "0xAA3333"),
        reactorGridRodNeutronium    = color.rgb("reactorGridRodNeutronium",     "0xE8E8F0"),
        reactorGridRodNone          = color.argb("reactorGridRodNone",          "0x88666666"),
        reactorGridOverheat         = color.argb("reactorGridOverheat",         "0x60FF0000"),
        reactorGridDragPreview      = color.argb("reactorGridDragPreview",      "0x6000FF00"),

        // Reactor controller GUI
        reactorCoreTemp             = color.rgb("reactorCoreTemp",              "0xFFAA00"),
        reactorReactivity           = color.rgb("reactorReactivity",            "0x55FFFF"),
        reactorNeutrons             = color.rgb("reactorNeutrons",              "0x55FFFF"),
        reactorNeutronBreakdown     = color.rgb("reactorNeutronBreakdown",      "0xAAAAAA"),
        reactorDamage               = color.rgb("reactorDamage",                "0xFF5555"),
        reactorIdle                 = color.rgb("reactorIdle",                  "0xFFAA00"),
        scramBorder                 = color.rgb("scramBorder",                  "0x330000"),
        scramButton                 = color.rgb("scramButton",                  "0xAA0000"),
        scramButtonHover            = color.rgb("scramButtonHover",             "0xDD1111"),
        scramButtonActive           = color.rgb("scramButtonActive",            "0xCC0000"),
        scramButtonActiveHover      = color.rgb("scramButtonActiveHover",       "0xFF3333"),
        scramHighlight              = color.argb("scramHighlight",              "0x55FFFFFF"),
        scramText                   = color.rgb("scramText",                    "0xFFFFFF"),

        // Reactor metrics plots, on GregTech's terminal screen
        plotGridLine                = color.argb("plotGridLine",                "0x26FFFFFF"),
        plotCursor                  = color.argb("plotCursor",                  "0x80FFFFFF"),
        plotTitle                   = color.rgb("plotTitle",                    "0xFAFAFA"),
        plotLatest                  = color.rgb("plotLatest",                   "0xAAAAAA"),
        plotNoData                  = color.rgb("plotNoData",                   "0x808080"),
        plotWaiting                 = color.rgb("plotWaiting",                  "0x808080"),
        plotAxisLabel               = color.rgb("plotAxisLabel",                "0x808080"),
        plotSinglePoint             = color.rgb("plotSinglePoint",              "0xFFFFFF"),
        plotCoreTemp                = color.rgb("plotCoreTemp",                 "0xFFAA00"),
        plotCoreTempMin             = color.argb("plotCoreTempMin",             "0x995555FF"),
        plotCoreTempMax             = color.argb("plotCoreTempMax",             "0xCCFF5555"),
        plotReactivity              = color.rgb("plotReactivity",               "0x55FFFF"),
        plotReactivityMin           = color.argb("plotReactivityMin",           "0x9900AAAA"),
        plotReactivityMax           = color.argb("plotReactivityMax",           "0xCCAAFFFF"),
        plotFastNeutrons            = color.rgb("plotFastNeutrons",             "0xFF55FF"),
        plotThermalNeutrons         = color.rgb("plotThermalNeutrons",          "0x5555FF"),
        plotEscapedNeutrons         = color.rgb("plotEscapedNeutrons",          "0xFF5555"),
        plotDirectEu                = color.rgb("plotDirectEu",                 "0xFFFF55"),
        plotCoolantOutput           = color.rgb("plotCoolantOutput",            "0x55FF55"),

        // Coolant loop graph, on GregTech's terminal screen
        graphMessage                = color.rgb("graphMessage",                 "0xFF5555"),
        graphGridDots               = color.argb("graphGridDots",               "0x40555555"),
        graphPipe                   = color.argb("graphPipe",                   "0xE6808080"),
        graphPipeWarm               = color.argb("graphPipeWarm",               "0xE6FFAA00"),
        graphPipeHot                = color.argb("graphPipeHot",                "0xE6FF5555"),
        graphPipeHover              = color.rgb("graphPipeHover",               "0xD9D9D9"),
        graphPipeOutline            = color.argb("graphPipeOutline",            "0xE6141414"),
        graphArrow                  = color.argb("graphArrow",                  "0xD9BFBFBF"),
        graphArrowHover             = color.rgb("graphArrowHover",              "0xFFFFFF"),
        graphNode                   = color.argb("graphNode",                   "0xEE2B2B2B"),
        graphNodeHover              = color.argb("graphNodeHover",              "0xF0373737"),
        graphNodeBorder             = color.rgb("graphNodeBorder",              "0x555555"),
        graphNodeBorderHover        = color.rgb("graphNodeBorderHover",         "0xAAAAAA"),
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

        // Coolant bath: its slots from the GUI's theme
        bathProgress                = color.rgb("bathProgress",                 "0x55FF55"),

        // Floating panels
        panelZoomText               = color.rgb("panelZoomText",                "0xFFFFFF"),
        panelResizeHandle           = color.argb("panelResizeHandle",           "0xAA8B8B8B"),
        panelResizeHandleLine       = color.argb("panelResizeHandleLine",       "0xDDC6C6C6"),
        panelResizeHandleHover      = color.rgb("panelResizeHandleHover",       "0xC6C6C6"),
        panelResizeHandleLineHover  = color.rgb("panelResizeHandleLineHover",   "0xFFFFFF"),
        panelSnapArea               = color.argb("panelSnapArea",               "0x40FFFFFF"),
        panelSnapAreaBorder         = color.argb("panelSnapAreaBorder",         "0xCCFFFFFF"),
        panelDimBelow               = color.argb("panelDimBelow",               "0x7D101010"),

        // Hatch GUIs
        hatchSlotLabel              = color.rgb("hatchSlotLabel",               "0xFFFFFF"),
        hatchBusTitle               = color.rgb("hatchBusTitle",                "0x55FF55"),
        hatchControlTitle           = color.rgb("hatchControlTitle",            "0x55FF55"),
        hatchControlRodTitle        = color.rgb("hatchControlRodTitle",         "0xFF55FF"),
        hatchHighPressureTitle      = color.rgb("hatchHighPressureTitle",       "0xFFAA00"),
        hatchPressurizedTitle       = color.rgb("hatchPressurizedTitle",        "0xFF55FF"),
        hatchStatsTitle             = color.rgb("hatchStatsTitle",              "0x55FFFF"),
        hatchTemperature            = color.rgb("hatchTemperature",             "0xFFAA00"),
        hatchFastFlux               = color.rgb("hatchFastFlux",                "0x55FFFF"),
        hatchThermalFlux            = color.rgb("hatchThermalFlux",             "0x5555FF"),
        hatchInsertion              = color.rgb("hatchInsertion",               "0x55FFFF"),
        hatchRodType                = color.rgb("hatchRodType",                 "0xAAAAAA"),
        hatchModerator              = color.rgb("hatchModerator",               "0x55FF55"),
        hatchHousing                = color.rgb("hatchHousing",                 "0xFFFF55"),
        hatchPaired                 = color.rgb("hatchPaired",                  "0x55FF55"),
        hatchPump                   = color.rgb("hatchPump",                    "0x5555FF"),
        hatchRole                   = color.rgb("hatchRole",                    "0xAAAAAA"),
        hatchFluidAmount            = color.rgb("hatchFluidAmount",             "0xAAAAAA"),
        hatchControlMetric          = color.rgb("hatchControlMetric",           "0x55FFFF"),
        hatchControlStatistic       = color.rgb("hatchControlStatistic",        "0xFFFF55"),
        hatchControlOutput          = color.rgb("hatchControlOutput",           "0xFF5555"),
        hatchControlBundled         = color.rgb("hatchControlBundled",          "0xFF55FF"),

        // Waila core temperature bar, from cool to critical
        wailaTempNormalTop          = color.rgb("wailaTempNormalTop",           "0x55FF55"),
        wailaTempNormalBottom       = color.rgb("wailaTempNormalBottom",        "0x00AA00"),
        wailaTempWarmTop            = color.rgb("wailaTempWarmTop",             "0xFFFF55"),
        wailaTempWarmBottom         = color.rgb("wailaTempWarmBottom",          "0xAAAA00"),
        wailaTempHighTop            = color.rgb("wailaTempHighTop",             "0xFFAA00"),
        wailaTempHighBottom         = color.rgb("wailaTempHighBottom",          "0xAA5500"),
        wailaTempCriticalTop        = color.rgb("wailaTempCriticalTop",         "0xFF5555"),
        wailaTempCriticalBottom     = color.rgb("wailaTempCriticalBottom",      "0xAA0000");
    // spotless:on
}
