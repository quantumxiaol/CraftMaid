package com.github.quantumxiaol.craftmaid.vision;

interface VoxelScene {
  Cell cell(int x, int y, int z);

  int skyColor();

  double daylight();

  record Cell(boolean known, BlockAppearance appearance, int skyLight, int emittedLight, int tint) {
    Cell(boolean known, BlockAppearance appearance, int skyLight, int emittedLight) {
      this(known, appearance, skyLight, emittedLight, 0xffffff);
    }

    static final Cell UNKNOWN = new Cell(false, null, 0, 0);
    static final Cell SKY = new Cell(true, null, 15, 0);
  }
}
