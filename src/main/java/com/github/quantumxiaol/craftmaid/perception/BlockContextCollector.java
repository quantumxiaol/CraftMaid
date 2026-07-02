package com.github.quantumxiaol.craftmaid.perception;

import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class BlockContextCollector {
  private final Map<PerceptionCacheKey, CachedBlockSnapshot> cache = new HashMap<>();

  public BlockPerceptionSnapshot collect(
      Player player, CraftMaidConfig.BlockPerceptionSettings settings) {
    Location center = player.getLocation();
    World world = center.getWorld();
    if (world == null) {
      return empty(settings);
    }

    PerceptionCacheKey cacheKey = PerceptionCacheKey.from(center, settings);
    long now = System.currentTimeMillis();
    CachedBlockSnapshot cached = cache.get(cacheKey);
    if (cached != null && cached.expiresAtMillis() > now) {
      return cached.snapshot();
    }

    BlockPerceptionSnapshot snapshot = scan(world, center, settings);
    int cacheSeconds = Math.max(0, settings.cacheSeconds());
    if (cacheSeconds > 0) {
      cache.put(cacheKey, new CachedBlockSnapshot(snapshot, now + cacheSeconds * 1000L));
    }
    cache.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
    return snapshot;
  }

  private BlockPerceptionSnapshot scan(
      World world, Location center, CraftMaidConfig.BlockPerceptionSettings settings) {
    int radius = Math.max(0, settings.radiusXz());
    int up = Math.max(0, settings.up());
    int down = Math.max(0, settings.down());
    int centerX = center.getBlockX();
    int centerY = center.getBlockY();
    int centerZ = center.getBlockZ();
    int minX = centerX - radius;
    int maxX = centerX + radius;
    int minZ = centerZ - radius;
    int maxZ = centerZ + radius;
    int minY = Math.max(world.getMinHeight(), centerY - down);
    int maxY = Math.min(world.getMaxHeight() - 1, centerY + up);

    int minChunkX = Math.floorDiv(minX, 16);
    int maxChunkX = Math.floorDiv(maxX, 16);
    int minChunkZ = Math.floorDiv(minZ, 16);
    int maxChunkZ = Math.floorDiv(maxZ, 16);
    int maxBlocksScanned = Math.max(1, settings.maxBlocksScanned());

    EnumMap<Material, Integer> materialCounts = new EnumMap<>(Material.class);
    EnumMap<BlockCategory, Integer> categoryCounts = new EnumMap<>(BlockCategory.class);
    int scannedBlocks = 0;
    int nonAirBlocks = 0;
    int skippedChunks = 0;

    scan:
    for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
      for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
          skippedChunks++;
          continue;
        }

        int chunkMinX = Math.max(minX, chunkX << 4);
        int chunkMaxX = Math.min(maxX, (chunkX << 4) + 15);
        int chunkMinZ = Math.max(minZ, chunkZ << 4);
        int chunkMaxZ = Math.min(maxZ, (chunkZ << 4) + 15);
        for (int x = chunkMinX; x <= chunkMaxX; x++) {
          for (int z = chunkMinZ; z <= chunkMaxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
              if (scannedBlocks >= maxBlocksScanned) {
                break scan;
              }
              scannedBlocks++;
              Material material = world.getBlockAt(x, y, z).getType();
              if (material.isAir()) {
                continue;
              }
              nonAirBlocks++;
              materialCounts.merge(material, 1, Integer::sum);
              EnumSet<BlockCategory> categories = BlockCategory.classify(material);
              for (BlockCategory category : categories) {
                categoryCounts.merge(category, 1, Integer::sum);
              }
            }
          }
        }
      }
    }

    List<BlockPerceptionSnapshot.MaterialCount> topMaterials =
        materialCounts.entrySet().stream()
            .sorted(Map.Entry.<Material, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(Math.max(1, settings.topMaterials()))
            .map(
                entry ->
                    new BlockPerceptionSnapshot.MaterialCount(entry.getKey(), entry.getValue()))
            .toList();
    SceneScore sceneScore = guessScene(categoryCounts, materialCounts, nonAirBlocks);
    return new BlockPerceptionSnapshot(
        radius,
        up,
        down,
        scannedBlocks,
        nonAirBlocks,
        skippedChunks,
        topMaterials,
        categoryCounts,
        sceneScore.guess(),
        sceneScore.confidence());
  }

  private SceneScore guessScene(
      EnumMap<BlockCategory, Integer> categories,
      EnumMap<Material, Integer> materials,
      int nonAirBlocks) {
    if (nonAirBlocks <= 0) {
      return new SceneScore(SceneGuess.UNKNOWN, 0.0);
    }
    double buildingRatio = ratio(categories, BlockCategory.BUILDING, nonAirBlocks);
    double woodRatio = ratio(categories, BlockCategory.WOOD, nonAirBlocks);
    double stoneBuildingRatio = ratio(categories, BlockCategory.STONE_BUILDING, nonAirBlocks);
    double naturalRatio = ratio(categories, BlockCategory.NATURAL, nonAirBlocks);
    double liquidRatio = ratio(categories, BlockCategory.LIQUID, nonAirBlocks);
    int redstone = categories.getOrDefault(BlockCategory.REDSTONE, 0);
    int crop = categories.getOrDefault(BlockCategory.CROP, 0);
    int containers = categories.getOrDefault(BlockCategory.CONTAINER, 0);
    int farmland = materials.getOrDefault(Material.FARMLAND, 0);
    int water = materials.getOrDefault(Material.WATER, 0);

    if (redstone >= 4) {
      return new SceneScore(SceneGuess.REDSTONE_MACHINE, confidence(redstone / 12.0 + 0.45));
    }
    if (crop >= 10 && farmland > 0) {
      return new SceneScore(SceneGuess.FARM, confidence(crop / 40.0 + 0.45));
    }
    if (water >= 20 || liquidRatio > 0.25) {
      return new SceneScore(SceneGuess.POND, confidence(liquidRatio + 0.45));
    }
    if (containers >= 6) {
      return new SceneScore(SceneGuess.STORAGE, confidence(containers / 16.0 + 0.45));
    }
    if (buildingRatio > 0.35 && woodRatio > 0.25) {
      return new SceneScore(
          SceneGuess.WOODEN_BUILDING, confidence(buildingRatio * 0.7 + woodRatio * 0.8));
    }
    if (buildingRatio > 0.35 && stoneBuildingRatio > 0.25) {
      return new SceneScore(
          SceneGuess.STONE_BUILDING, confidence(buildingRatio * 0.7 + stoneBuildingRatio * 0.8));
    }
    if (buildingRatio > 0.35) {
      return new SceneScore(SceneGuess.BUILDING, confidence(buildingRatio + 0.25));
    }
    if (naturalRatio > 0.75) {
      return new SceneScore(SceneGuess.NATURAL, confidence(naturalRatio));
    }
    return new SceneScore(SceneGuess.UNKNOWN, 0.25);
  }

  private double ratio(
      EnumMap<BlockCategory, Integer> categories, BlockCategory category, int total) {
    if (total <= 0) {
      return 0.0;
    }
    return categories.getOrDefault(category, 0) / (double) total;
  }

  private double confidence(double value) {
    return Math.max(0.0, Math.min(0.95, value));
  }

  private BlockPerceptionSnapshot empty(CraftMaidConfig.BlockPerceptionSettings settings) {
    return new BlockPerceptionSnapshot(
        settings.radiusXz(),
        settings.up(),
        settings.down(),
        0,
        0,
        0,
        List.of(),
        new EnumMap<>(BlockCategory.class),
        SceneGuess.UNKNOWN,
        0.0);
  }

  private record SceneScore(SceneGuess guess, double confidence) {}

  private record CachedBlockSnapshot(BlockPerceptionSnapshot snapshot, long expiresAtMillis) {}

  private record PerceptionCacheKey(
      UUID worldId, int gridX, int gridY, int gridZ, int radiusXz, int up, int down) {
    static PerceptionCacheKey from(
        Location location, CraftMaidConfig.BlockPerceptionSettings settings) {
      World world = location.getWorld();
      UUID worldId = world == null ? new UUID(0L, 0L) : world.getUID();
      return new PerceptionCacheKey(
          worldId,
          Math.floorDiv(location.getBlockX(), 4),
          Math.floorDiv(location.getBlockY(), 2),
          Math.floorDiv(location.getBlockZ(), 4),
          settings.radiusXz(),
          settings.up(),
          settings.down());
    }
  }
}
