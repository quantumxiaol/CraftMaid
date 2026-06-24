package com.github.quantumxiaol.craftmaid.anchor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public record MaidAnchor(String worldName, double x, double y, double z, float yaw, float pitch) {
  public static MaidAnchor fromLocation(Location location) {
    String worldName = location.getWorld() == null ? "" : location.getWorld().getName();
    return new MaidAnchor(
        worldName,
        location.getX(),
        location.getY(),
        location.getZ(),
        location.getYaw(),
        location.getPitch());
  }

  public Location toLocationOrNull() {
    if (worldName == null || worldName.isBlank()) {
      return null;
    }
    World world = Bukkit.getWorld(worldName);
    if (world == null) {
      return null;
    }
    return new Location(world, x, y, z, yaw, pitch);
  }

  public int xBlock() {
    return (int) Math.floor(x);
  }

  public int yBlock() {
    return (int) Math.floor(y);
  }

  public int zBlock() {
    return (int) Math.floor(z);
  }

  public String shortText() {
    return String.format("%s %.1f %.1f %.1f", worldName, x, y, z);
  }
}
