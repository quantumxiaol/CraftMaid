package com.github.quantumxiaol.craftmaid.vision;

import org.bukkit.configuration.ConfigurationSection;

/** Bounds apply to both configuration and callers constructing settings directly. */
public record VisionSettings(
    boolean enabled,
    int width,
    int height,
    int distance,
    double horizontalFov,
    double pitch,
    int cooldownSeconds,
    int timeoutSeconds,
    int retainedCaptures,
    boolean sendToLlm) {
  public VisionSettings(
      boolean enabled,
      int width,
      int height,
      int distance,
      double horizontalFov,
      double pitch,
      int cooldownSeconds,
      int timeoutSeconds,
      int retainedCaptures) {
    this(
        enabled,
        width,
        height,
        distance,
        horizontalFov,
        pitch,
        cooldownSeconds,
        timeoutSeconds,
        retainedCaptures,
        true);
  }

  public VisionSettings {
    width = Math.clamp(width, 128, 640);
    height = Math.clamp(height, 96, 480);
    distance = Math.clamp(distance, 8, 48);
    horizontalFov = finiteClamp(horizontalFov, 100, 90, 110);
    pitch = finiteClamp(pitch, 10, -30, 30);
    cooldownSeconds = Math.clamp(cooldownSeconds, 1, 300);
    timeoutSeconds = Math.clamp(timeoutSeconds, 2, 30);
    retainedCaptures = Math.clamp(retainedCaptures, 1, 50);
  }

  public static VisionSettings load(ConfigurationSection config) {
    String p = "perception.vision.";
    return new VisionSettings(
        config.getBoolean(p + "enabled", true),
        config.getInt(p + "width", 384),
        config.getInt(p + "height", 256),
        config.getInt(p + "distance", 24),
        config.getDouble(p + "horizontal_fov", 100),
        config.getDouble(p + "pitch", 10),
        config.getInt(p + "cooldown_seconds", 10),
        config.getInt(p + "timeout_seconds", 10),
        config.getInt(p + "retained_captures", 10),
        config.getBoolean(p + "send_to_llm", true));
  }

  private static double finiteClamp(double value, double fallback, double min, double max) {
    return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
  }
}
