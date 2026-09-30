package com.github.quantumxiaol.craftmaid.vision;

import org.bukkit.configuration.ConfigurationSection;

public record VisionAssetSettings(boolean autoDownload, String clientJar, String resourcePack) {
  public static VisionAssetSettings load(ConfigurationSection config) {
    return new VisionAssetSettings(
        config.getBoolean("perception.vision.assets.auto_download", true),
        config.getString("perception.vision.assets.client_jar", ""),
        config.getString("perception.vision.assets.resource_pack", ""));
  }
}
