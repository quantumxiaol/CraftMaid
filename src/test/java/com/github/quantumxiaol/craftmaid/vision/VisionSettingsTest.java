package com.github.quantumxiaol.craftmaid.vision;

import static org.junit.jupiter.api.Assertions.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class VisionSettingsTest {
  @Test
  void oldConfigsHaveUsableDefaultsAndExtremeValuesCannotAllocateUnboundedImages() {
    var config = new YamlConfiguration();
    var defaults = VisionSettings.load(config);
    assertTrue(defaults.enabled());
    assertEquals(384, defaults.width());
    assertEquals(24, defaults.distance());
    config.set("perception.vision.width", Integer.MAX_VALUE);
    config.set("perception.vision.height", Integer.MAX_VALUE);
    config.set("perception.vision.distance", Integer.MAX_VALUE);
    config.set("perception.vision.pitch", Double.NaN);
    config.set("perception.vision.horizontal_fov", Double.POSITIVE_INFINITY);
    var settings = VisionSettings.load(config);
    assertTrue(settings.width() * settings.height() <= 640 * 480);
    assertTrue(settings.distance() <= 48);
    assertTrue(Double.isFinite(settings.pitch()));
    assertTrue(Double.isFinite(settings.horizontalFov()));
  }
}
