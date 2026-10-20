package gregtech.api.enums;

/**
 * Zero-dependency WebAssembly compilation stub for GregTech's GTValues.
 * Used exclusively by TeaVM during WebAssembly compilation to resolve canonical voltages (V)
 * and tier names (VN) without pulling in Minecraft or Forge runtime dependencies.
 */
public class GTValues {

    public static final long[] V = new long[] { 8L, 32L, 128L, 512L, 2048L, 8192L, 32768L, 131072L, 524288L, 2097152L,
        8388608L, 33554432L, 134217728L, 536870912L, 1073741824L, Integer.MAX_VALUE };

    public static final String[] VN = new String[] { "ULV", "LV", "MV", "HV", "EV", "IV", "LuV", "ZPM", "UV", "UHV",
        "UEV", "UIV", "UMV", "UXV", "MAX", "MAX" };
}
