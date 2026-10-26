package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import com.gtnewhorizons.modularnuclear.common.config.MiniJson;
import com.gtnewhorizons.modularnuclear.common.config.ReactorConfigData;
import com.gtnewhorizons.modularnuclear.common.config.ReactorDataRegistry;
import com.gtnewhorizons.modularnuclear.common.config.ReactorDefaultsJson;

/** The embedded JSON defaults must be exactly what SnakeYAML reads from the default YAML files. */
public class ReactorDefaultsJsonTest {

    private static Object yaml(String name) throws Exception {
        try (InputStream in = ReactorDefaultsJsonTest.class
            .getResourceAsStream("/assets/modularnuclear/default_config/" + name + ".yaml")) {
            assertNotNull(in, name + ".yaml resource");
            return new Yaml().load(in);
        }
    }

    @Test
    void testEmbeddedDefaultsMatchYaml() throws Exception {
        assertEquals(yaml("physics"), MiniJson.parse(ReactorDefaultsJson.physics()));
        assertEquals(yaml("tiers"), MiniJson.parse(ReactorDefaultsJson.tiers()));
        assertEquals(yaml("fuels"), MiniJson.parse(ReactorDefaultsJson.fuels()));
        assertEquals(yaml("components"), MiniJson.parse(ReactorDefaultsJson.components()));
    }

    @Test
    void testRegistryUsesSharedDefaults() {
        ReactorDataRegistry.loadDefaults();
        assertEquals(ReactorConfigData.getAllFuels(), ReactorDataRegistry.getAllFuels());
        assertNotNull(ReactorConfigData.findFuelById("fuel_uranium_4"));
        assertEquals(
            ReactorConfigData.getPhysics().fissionHeatPerNeutron,
            ReactorDataRegistry.getPhysics().fissionHeatPerNeutron);
    }

    @Test
    void testMiniJsonValues() {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) MiniJson.parse(
            " {\"a\": 1, \"b\": -2.5e1, \"c\": [true, false, null], \"d\": \"x\\\"y\\u00b0\\n\", \"e\": {},"
                + " \"f\": Infinity, \"g\": -Infinity, \"h\": 12345678901} ");
        assertEquals(1, m.get("a"));
        assertEquals(-25.0, m.get("b"));
        assertEquals(Arrays.asList(true, false, null), m.get("c"));
        assertEquals("x\"y°\n", m.get("d"));
        assertEquals(Map.of(), m.get("e"));
        assertEquals(Double.POSITIVE_INFINITY, m.get("f"));
        assertEquals(Double.NEGATIVE_INFINITY, m.get("g"));
        assertEquals(12345678901L, m.get("h"));
        assertEquals(List.of(), MiniJson.parse("[]"));
        assertNull(MiniJson.parse("null"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\":1"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("[1] x"));
    }
}
