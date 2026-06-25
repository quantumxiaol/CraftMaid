package com.github.quantumxiaol.craftmaid.anchor;

public record AnchorRegion(
    String worldName, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
  public static AnchorRegion from(MaidAnchor first, MaidAnchor second) {
    int firstX = first.xBlock();
    int firstY = first.yBlock();
    int firstZ = first.zBlock();
    int secondX = second.xBlock();
    int secondY = second.yBlock();
    int secondZ = second.zBlock();
    return new AnchorRegion(
        first.worldName(),
        Math.min(firstX, secondX),
        Math.min(firstY, secondY),
        Math.min(firstZ, secondZ),
        Math.max(firstX, secondX),
        Math.max(firstY, secondY),
        Math.max(firstZ, secondZ));
  }

  public long volume() {
    return 1L * (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
  }

  public String shortText() {
    return String.format(
        "%s [%d,%d,%d] -> [%d,%d,%d]", worldName, minX, minY, minZ, maxX, maxY, maxZ);
  }
}
