package com.github.quantumxiaol.craftmaid.vision;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;

/** Created on the server thread. Contains no live World, Location or Entity references. */
public record ObservationSnapshot(
    Origin origin,
    int minHeight,
    int maxHeight,
    String environment,
    long worldTime,
    boolean raining,
    Map<Long, ChunkSnapshot> chunks,
    int missingChunks,
    List<EntityInfo> entities,
    long captureNanos) {
  public ObservationSnapshot {
    chunks = Map.copyOf(chunks);
    entities = List.copyOf(entities);
  }

  public static ObservationSnapshot capture(LivingEntity maid, VisionSettings settings) {
    long started = System.nanoTime();
    Location eye = maid.getEyeLocation().clone();
    World world = eye.getWorld();
    Origin origin =
        new Origin(
            world.getUID(),
            world.getName(),
            maid.getUniqueId(),
            eye.getX(),
            eye.getY(),
            eye.getZ(),
            Instant.now().toString(),
            world.getFullTime());
    int radius = settings.distance();
    Map<Long, ChunkSnapshot> chunks = new java.util.HashMap<>();
    int missing = 0;
    for (int x = (eye.getBlockX() - radius) >> 4; x <= (eye.getBlockX() + radius) >> 4; x++) {
      for (int z = (eye.getBlockZ() - radius) >> 4; z <= (eye.getBlockZ() + radius) >> 4; z++) {
        // A single capture is bounded to at most 49 chunks. Do not cause chunk loads/generation.
        if (!world.isChunkLoaded(x, z)) {
          missing++;
          continue;
        }
        // Stop between copies if snapshotting is too expensive for this server's tick budget.
        if (System.nanoTime() - started > 40_000_000L) {
          throw new IllegalStateException("采集区块快照超过 40ms，请调低 perception.vision.distance 后重试。");
        }
        Chunk chunk = world.getChunkAt(x, z);
        chunks.put(chunkKey(x, z), chunk.getChunkSnapshot(false, true, false));
      }
    }
    if (!chunks.containsKey(chunkKey(eye.getBlockX() >> 4, eye.getBlockZ() >> 4))) {
      throw new IllegalStateException("女仆所在区块未加载，无法采集图片。");
    }
    List<EntityInfo> entities =
        world.getNearbyEntities(eye, radius, radius, radius).stream()
            .filter(entity -> !entity.getUniqueId().equals(maid.getUniqueId()))
            .sorted(
                java.util.Comparator.comparingDouble(
                    entity -> entity.getLocation().distanceSquared(eye)))
            .limit(64)
            .map(
                entity -> {
                  Location location = entity.getLocation();
                  return new EntityInfo(
                      entity.getType().name(), location.getX(), location.getY(), location.getZ());
                })
            .toList();
    return new ObservationSnapshot(
        origin,
        world.getMinHeight(),
        world.getMaxHeight(),
        world.getEnvironment().name(),
        world.getTime(),
        world.hasStorm(),
        chunks,
        missing,
        entities,
        System.nanoTime() - started);
  }

  static long chunkKey(int x, int z) {
    return ((long) x << 32) | (z & 0xffffffffL);
  }

  public record Origin(
      UUID worldId,
      String worldName,
      UUID npcId,
      double x,
      double y,
      double z,
      String capturedAt,
      long worldTick) {}

  /**
   * Nearby entities, not a visibility assertion. Entity meshes are not rendered in this version.
   */
  public record EntityInfo(String type, double x, double y, double z) {}
}
