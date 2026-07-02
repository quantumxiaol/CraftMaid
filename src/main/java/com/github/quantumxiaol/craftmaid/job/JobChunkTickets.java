package com.github.quantumxiaol.craftmaid.job;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.anchor.AnchorRegion;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;

final class JobChunkTickets {
  private final CraftMaid plugin;
  private final List<ChunkKey> tickets = new ArrayList<>();

  JobChunkTickets(CraftMaid plugin) {
    this.plugin = plugin;
  }

  int addRegion(AnchorRegion region) {
    World world = plugin.getServer().getWorld(region.worldName());
    if (world == null) {
      return 0;
    }

    int before = tickets.size();
    int minChunkX = blockToChunk(region.minX());
    int maxChunkX = blockToChunk(region.maxX());
    int minChunkZ = blockToChunk(region.minZ());
    int maxChunkZ = blockToChunk(region.maxZ());
    for (int x = minChunkX; x <= maxChunkX; x++) {
      for (int z = minChunkZ; z <= maxChunkZ; z++) {
        add(world, x, z);
      }
    }
    return tickets.size() - before;
  }

  int addLocation(Location location) {
    if (location == null || location.getWorld() == null) {
      return 0;
    }
    int before = tickets.size();
    add(
        location.getWorld(),
        blockToChunk(location.getBlockX()),
        blockToChunk(location.getBlockZ()));
    return tickets.size() - before;
  }

  int addAround(Location center, int radiusChunks) {
    if (center == null || center.getWorld() == null) {
      return 0;
    }

    int before = tickets.size();
    int centerX = blockToChunk(center.getBlockX());
    int centerZ = blockToChunk(center.getBlockZ());
    int radius = Math.max(0, radiusChunks);
    for (int x = centerX - radius; x <= centerX + radius; x++) {
      for (int z = centerZ - radius; z <= centerZ + radius; z++) {
        add(center.getWorld(), x, z);
      }
    }
    return tickets.size() - before;
  }

  int size() {
    return tickets.size();
  }

  void release() {
    for (ChunkKey ticket : tickets) {
      World world = plugin.getServer().getWorld(ticket.worldName());
      if (world != null) {
        world.removePluginChunkTicket(ticket.x(), ticket.z(), plugin);
      }
    }
    tickets.clear();
  }

  static long countRegionChunks(AnchorRegion region) {
    int minChunkX = blockToChunk(region.minX());
    int maxChunkX = blockToChunk(region.maxX());
    int minChunkZ = blockToChunk(region.minZ());
    int maxChunkZ = blockToChunk(region.maxZ());
    return 1L * (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);
  }

  static long countRegionAndLocationChunks(AnchorRegion region, Location location) {
    long count = countRegionChunks(region);
    if (location == null
        || location.getWorld() == null
        || !location.getWorld().getName().equals(region.worldName())) {
      return count;
    }

    int locationChunkX = blockToChunk(location.getBlockX());
    int locationChunkZ = blockToChunk(location.getBlockZ());
    int minChunkX = blockToChunk(region.minX());
    int maxChunkX = blockToChunk(region.maxX());
    int minChunkZ = blockToChunk(region.minZ());
    int maxChunkZ = blockToChunk(region.maxZ());
    if (locationChunkX >= minChunkX
        && locationChunkX <= maxChunkX
        && locationChunkZ >= minChunkZ
        && locationChunkZ <= maxChunkZ) {
      return count;
    }
    return count + 1;
  }

  private void add(World world, int x, int z) {
    if (world.addPluginChunkTicket(x, z, plugin)) {
      tickets.add(new ChunkKey(world.getName(), x, z));
    }
  }

  private static int blockToChunk(int block) {
    return block >> 4;
  }

  private record ChunkKey(String worldName, int x, int z) {}
}
