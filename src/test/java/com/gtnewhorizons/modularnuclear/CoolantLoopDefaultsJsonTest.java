package com.gtnewhorizons.modularnuclear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopConfigData;
import com.gtnewhorizons.modularnuclear.common.config.CoolantLoopDefaultsJson;
import com.gtnewhorizons.modularnuclear.common.config.MiniJson;

/** The embedded coolant loop defaults must be exactly what SnakeYAML reads from the shipped default YAML files. */
public class CoolantLoopDefaultsJsonTest {

    private static Object yaml(String name) throws Exception {
        try (InputStream in = CoolantLoopDefaultsJsonTest.class
            .getResourceAsStream("/assets/coolantloops/default_config/" + name + ".yaml")) {
            assertNotNull(in, name + ".yaml resource");
            return new Yaml().load(in);
        }
    }

    @Test
    void testEmbeddedDefaultsMatchYaml() throws Exception {
        assertEquals(yaml("physics"), MiniJson.parse(CoolantLoopDefaultsJson.physics()));
        assertEquals(yaml("fluids"), MiniJson.parse(CoolantLoopDefaultsJson.fluids()));
        assertEquals(yaml("pipes"), MiniJson.parse(CoolantLoopDefaultsJson.pipes()));
        assertEquals(yaml("devices"), MiniJson.parse(CoolantLoopDefaultsJson.devices()));
    }

    @Test
    void testSharedDefaultsLoad() {
        CoolantLoopConfigData.loadDefaults();
        assertEquals(
            80.0,
            CoolantLoopConfigData.getPhysicsData()
                .getPumpMechanicalPowerPerEU(),
            1e-9);
        assertNotNull(
            CoolantLoopConfigData.getDeviceData()
                .getPhe());
    }
}
