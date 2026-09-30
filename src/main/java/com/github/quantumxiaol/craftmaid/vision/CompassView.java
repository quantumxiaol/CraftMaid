package com.github.quantumxiaol.craftmaid.vision;

/** World axes, independent of NPC/player head rotation. Positive pitch looks down. */
public enum CompassView {
  NORTH(0, -1),
  EAST(1, 0),
  SOUTH(0, 1),
  WEST(-1, 0);

  final double x;
  final double z;

  CompassView(double x, double z) {
    this.x = x;
    this.z = z;
  }

  public String fileName() {
    return name().toLowerCase(java.util.Locale.ROOT) + ".png";
  }

  public Ray ray(double screenX, double screenY, VisionSettings settings) {
    double pitch = Math.toRadians(settings.pitch());
    double scale = Math.tan(Math.toRadians(settings.horizontalFov()) / 2);
    double u = screenX * scale;
    double v = screenY * scale * settings.height() / settings.width();
    double dx = x * Math.cos(pitch) - z * u + x * Math.sin(pitch) * v;
    double dy = -Math.sin(pitch) + Math.cos(pitch) * v;
    double dz = z * Math.cos(pitch) + x * u + z * Math.sin(pitch) * v;
    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
    return new Ray(dx / length, dy / length, dz / length);
  }

  public record Ray(double x, double y, double z) {}
}
