package com.github.quantumxiaol.craftmaid.job;

import com.github.quantumxiaol.craftmaid.anchor.AnchorRegion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

final class JobNavigationTargets {
  private JobNavigationTargets() {}

  static Location findSafeVerticalLocation(Location candidate) {
    if (candidate == null || candidate.getWorld() == null) {
      return null;
    }

    int baseY = candidate.getBlockY();
    int minY = candidate.getWorld().getMinHeight() + 1;
    int maxY = candidate.getWorld().getMaxHeight() - 2;
    for (int yOffset = 0; yOffset <= 4; yOffset++) {
      Location up = candidate.clone();
      up.setY(Math.min(maxY, baseY + yOffset));
      if (isSafeStandingLocation(up)) {
        return centerOnBlock(up, candidate);
      }

      Location down = candidate.clone();
      down.setY(Math.max(minY, baseY - yOffset));
      if (isSafeStandingLocation(down)) {
        return centerOnBlock(down, candidate);
      }
    }
    return null;
  }

  static Location findSafeStandPointAroundRegion(World world, AnchorRegion region) {
    if (world == null || region == null) {
      return null;
    }
    for (int ring = 1; ring <= 4; ring++) {
      int minX = region.minX() - ring;
      int maxX = region.maxX() + ring;
      int minZ = region.minZ() - ring;
      int maxZ = region.maxZ() + ring;

      for (int x = minX; x <= maxX; x++) {
        Location north = findSafeVerticalLocation(baseLocation(world, x, region.minY(), minZ));
        if (north != null) {
          return north;
        }
        Location south = findSafeVerticalLocation(baseLocation(world, x, region.minY(), maxZ));
        if (south != null) {
          return south;
        }
      }

      for (int z = minZ + 1; z < maxZ; z++) {
        Location west = findSafeVerticalLocation(baseLocation(world, minX, region.minY(), z));
        if (west != null) {
          return west;
        }
        Location east = findSafeVerticalLocation(baseLocation(world, maxX, region.minY(), z));
        if (east != null) {
          return east;
        }
      }
    }
    return null;
  }

  private static Location baseLocation(World world, int x, int y, int z) {
    return new Location(world, x + 0.5, y, z + 0.5);
  }

  private static boolean isSafeStandingLocation(Location location) {
    Material feet = location.getBlock().getType();
    Material head = location.clone().add(0, 1, 0).getBlock().getType();
    Material ground = location.clone().add(0, -1, 0).getBlock().getType();
    return feet != Material.WATER
        && feet != Material.LAVA
        && head != Material.WATER
        && head != Material.LAVA
        && location.getBlock().isPassable()
        && location.clone().add(0, 1, 0).getBlock().isPassable()
        && ground.isSolid();
  }

  private static Location centerOnBlock(Location location, Location source) {
    Location centered = location.clone();
    centered.setX(location.getBlockX() + 0.5);
    centered.setZ(location.getBlockZ() + 0.5);
    centered.setYaw(source.getYaw());
    centered.setPitch(0.0F);
    return centered;
  }
}
