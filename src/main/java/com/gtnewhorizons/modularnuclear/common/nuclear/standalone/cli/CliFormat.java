package com.gtnewhorizons.modularnuclear.common.nuclear.standalone.cli;

/** Terminal colours and value formatting shared by the command-line reports. */
public final class CliFormat {

    public static final String RESET = "\u001B[0m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE_BOLD = "\u001B[1;37m";

    /** GregTech voltage tiers from LV, each four times the last. */
    private static final String[] VOLTAGE_TIERS = { "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV", "UEV",
        "UIV", "UMV" };
    private static final double LV_VOLTAGE = 32.0;

    private CliFormat() {}

    /** The lowest GregTech voltage tier whose voltage covers a power, "OFF" for none, "UXV+" above UMV. */
    public static String voltageTierName(double eut) {
        if (eut <= 0) return "OFF";
        double voltage = LV_VOLTAGE;
        for (String tier : VOLTAGE_TIERS) {
            if (eut <= voltage) return tier;
            voltage *= 4.0;
        }
        return "UXV+";
    }
}
