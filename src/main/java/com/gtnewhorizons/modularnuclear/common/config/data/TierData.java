package com.gtnewhorizons.modularnuclear.common.config.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TierData {

    public int tier;
    public String name;
    public String casingMaterial;
    public long voltageEu;
    public double maxOperatingTemp;
    public double repairTempThreshold;
    public boolean allowsCoolantLoops = false;
    public List<String> allowedCoolants = new ArrayList<>();

    public TierData() {}

    public TierData(int tier, String name, String casingMaterial, long voltageEu, double maxOperatingTemp,
        double repairTempThreshold, boolean allowsCoolantLoops, String... allowedCoolants) {
        this.tier = tier;
        this.name = name;
        this.casingMaterial = casingMaterial;
        this.voltageEu = voltageEu;
        this.maxOperatingTemp = maxOperatingTemp;
        this.repairTempThreshold = repairTempThreshold;
        this.allowsCoolantLoops = allowsCoolantLoops;
        if (allowedCoolants != null) {
            this.allowedCoolants.addAll(Arrays.asList(allowedCoolants));
        }
    }

    public static TierData fromMap(Map<String, Object> map) {
        TierData td = new TierData();
        if (map == null) return td;
        td.tier = PhysicsData.getInt(map, "tier", 0);
        td.name = (String) map.getOrDefault("name", "Tier " + td.tier);
        td.casingMaterial = (String) map.getOrDefault("casing_material", "Casing");
        Object vObj = map.get("voltage_eu");
        if (vObj instanceof Number n) td.voltageEu = n.longValue();
        else td.voltageEu = 2048L;

        td.maxOperatingTemp = PhysicsData.getDouble(map, "max_operating_temp", 1200.0);
        td.repairTempThreshold = PhysicsData.getDouble(map, "repair_temp_threshold", 0.10 * td.maxOperatingTemp);
        td.allowsCoolantLoops = PhysicsData.getBoolean(map, "allows_coolant_loops", td.tier >= 2);

        Object coolants = map.get("allowed_coolants");
        if (coolants instanceof List<?>list) {
            for (Object item : list) {
                if (item != null) td.allowedCoolants.add(item.toString());
            }
        }
        return td;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("tier", tier);
        m.put("name", name);
        m.put("casing_material", casingMaterial);
        m.put("voltage_eu", voltageEu);
        m.put("max_operating_temp", maxOperatingTemp);
        m.put("repair_temp_threshold", repairTempThreshold);
        m.put("allows_coolant_loops", allowsCoolantLoops);
        m.put("allowed_coolants", new ArrayList<>(allowedCoolants));
        return m;
    }
}
