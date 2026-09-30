package com.github.quantumxiaol.craftmaid.vision;

import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.ChunkSnapshot;
import org.bukkit.block.data.BlockData;

/** Lazily decodes snapshot voxels once, shared by all four views on one render worker. */
final class SnapshotScene implements VoxelScene {
  private final ObservationSnapshot snapshot;
  private final int minX, minY, minZ, size;
  private final Cell[] cells;
  private final BitSet decoded;
  private final Map<String, BlockAppearance> palette = new HashMap<>();
  private final BlockModelLoader models;
  private final BiomeTints tints;
  private final java.util.Set<String> fallbackMaterials = new java.util.TreeSet<>();

  SnapshotScene(ObservationSnapshot snapshot, int radius) {
    this(snapshot, radius, null);
  }

  SnapshotScene(ObservationSnapshot snapshot, int radius, ResourcePack pack) {
    this.snapshot = snapshot;
    this.models = pack == null ? null : new BlockModelLoader(pack);
    this.tints = pack == null ? null : new BiomeTints(pack);
    this.minX = (int) Math.floor(snapshot.origin().x()) - radius;
    this.minY = (int) Math.floor(snapshot.origin().y()) - radius;
    this.minZ = (int) Math.floor(snapshot.origin().z()) - radius;
    this.size = radius * 2 + 1;
    this.cells = new Cell[size * size * size];
    this.decoded = new BitSet(cells.length);
  }

  @Override
  public Cell cell(int x, int y, int z) {
    int lx = x - minX, ly = y - minY, lz = z - minZ;
    if (lx < 0 || ly < 0 || lz < 0 || lx >= size || ly >= size || lz >= size) return Cell.UNKNOWN;
    ChunkSnapshot chunk = snapshot.chunks().get(ObservationSnapshot.chunkKey(x >> 4, z >> 4));
    if (chunk == null || y < snapshot.minHeight()) return Cell.UNKNOWN;
    if (y >= snapshot.maxHeight()) return Cell.SKY;
    int index = (ly * size + lz) * size + lx;
    if (!decoded.get(index)) {
      BlockData data = chunk.getBlockData(x & 15, y, z & 15);
      BlockAppearance appearance =
          BlockAppearance.isAir(data.getMaterial())
              ? null
              : palette.computeIfAbsent(data.getAsString(), key -> appearance(data, key));
      int tint = 0xffffff;
      if (appearance != null && tints != null) {
        try {
          var biome = chunk.getBiome(x & 15, y, z & 15);
          tint =
              tints.color(
                  data.getAsString(),
                  biome == null ? "minecraft:plains" : biome.getKey().toString());
        } catch (java.io.IOException exception) {
          throw new java.io.UncheckedIOException(exception);
        }
      }
      cells[index] =
          new Cell(
              true,
              appearance,
              chunk.getBlockSkyLight(x & 15, y, z & 15),
              chunk.getBlockEmittedLight(x & 15, y, z & 15),
              tint);
      decoded.set(index);
    }
    return cells[index];
  }

  private BlockAppearance appearance(BlockData data, String state) {
    if (models != null) {
      try {
        BlockAppearance result = models.load(state);
        if (result != null) return result;
        fallbackMaterials.add(data.getMaterial().name());
      } catch (java.io.IOException exception) {
        throw new java.io.UncheckedIOException(exception);
      }
    }
    return BlockAppearance.from(data);
  }

  java.util.Set<String> fallbackMaterials() {
    return java.util.Set.copyOf(fallbackMaterials);
  }

  @Override
  public int skyColor() {
    if (snapshot.environment().equals("NETHER")) return 0x35181b;
    if (snapshot.environment().equals("THE_END")) return 0x191421;
    int color = snapshot.raining() ? 0x8998a5 : 0x8db9ed;
    return BlockAppearance.scale(color, .13 + .87 * daylight());
  }

  @Override
  public double daylight() {
    if (!snapshot.environment().equals("NORMAL")) return .35;
    long time = Math.floorMod(snapshot.worldTime(), 24000);
    if (time < 12000) return snapshot.raining() ? .7 : 1;
    if (time < 14000) return 1 - (time - 12000) / 2000.0 * .9;
    if (time < 22000) return .1;
    return .1 + (time - 22000) / 2000.0 * .9;
  }
}
