package com.github.quantumxiaol.craftmaid.vision;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class SoftwareRendererTest {
  static final ObservationSnapshot.Origin ORIGIN =
      new ObservationSnapshot.Origin(
          UUID.randomUUID(),
          "fixture",
          UUID.randomUUID(),
          .5,
          1.75,
          .5,
          "2026-01-01T00:00:00Z",
          6000);
  static final VisionSettings SETTINGS = new VisionSettings(true, 128, 96, 8, 90, 0, 1, 10, 2);
  static final BlockAppearance RED = appearance("RED_WOOL", 0xe82020, 1, BlockAppearance.CUBE);
  static final BlockAppearance GREEN = appearance("GREEN_WOOL", 0x20e820, 1, BlockAppearance.CUBE);
  static final BlockAppearance BLUE = appearance("BLUE_WOOL", 0x2020e8, 1, BlockAppearance.CUBE);

  @Test
  void cardinalViewsAndImageRightUseWorldAxes() {
    double[][] expected = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};
    for (CompassView view : CompassView.values()) {
      var ray = view.ray(0, 0, SETTINGS);
      assertArrayEquals(expected[view.ordinal()], new double[] {ray.x(), ray.y(), ray.z()}, 1e-9);
    }
    assertTrue(CompassView.NORTH.ray(1, 0, SETTINGS).x() > 0);
    assertTrue(CompassView.EAST.ray(1, 0, SETTINGS).z() > 0);
    assertTrue(CompassView.SOUTH.ray(1, 0, SETTINGS).x() < 0);
    assertTrue(CompassView.WEST.ray(1, 0, SETTINGS).z() < 0);
    assertTrue(CompassView.NORTH.ray(0, 1, SETTINGS).y() > 0);
    var panorama =
        new SoftwareRenderer()
            .render(
                scene(
                    (x, y, z) -> {
                      if (z == -3) return cell(RED);
                      if (x == 3) return cell(GREEN);
                      if (z == 3) return cell(BLUE);
                      if (x == -3)
                        return cell(appearance("WHITE_WOOL", 0xf0f0f0, 1, BlockAppearance.CUBE));
                      return VoxelScene.Cell.SKY;
                    }),
                ORIGIN,
                SETTINGS,
                budget());
    assertDominant(center(panorama.frames().get(CompassView.NORTH)), 16);
    assertDominant(center(panorama.frames().get(CompassView.EAST)), 8);
    assertDominant(center(panorama.frames().get(CompassView.SOUTH)), 0);
    assertEquals(256, panorama.overview().getWidth());
    assertEquals(240, panorama.overview().getHeight());
  }

  @Test
  void opaqueWallHidesObjectsBehindIt() {
    var frame =
        north(scene((x, y, z) -> z == -2 ? cell(RED) : z == -4 ? cell(BLUE) : VoxelScene.Cell.SKY));
    assertTrue(frame.surfaceHits().containsKey("red_wool"));
    assertFalse(frame.surfaceHits().containsKey("blue_wool"));
    assertDominant(center(frame), 16);
  }

  @Test
  void transparentSurfaceRevealsBackgroundButSlabsLeaveAGap() {
    var glass = appearance("GLASS", 0x20e820, .25, BlockAppearance.CUBE);
    var transparent =
        north(
            scene((x, y, z) -> z == -2 ? cell(glass) : z == -4 ? cell(RED) : VoxelScene.Cell.SKY));
    assertTrue(transparent.surfaceHits().containsKey("glass"));
    assertTrue(transparent.surfaceHits().containsKey("red_wool"));
    var slab = appearance("STONE_SLAB", 0x20e820, 1, new BlockAppearance.Box(0, 0, 0, 1, .5, 1));
    var throughGap =
        north(scene((x, y, z) -> z == -2 ? cell(slab) : z == -4 ? cell(RED) : VoxelScene.Cell.SKY));
    assertDominant(center(throughGap), 16);
  }

  @Test
  void missingChunksAreUnknownInsteadOfOpenSky() {
    var frame = north(scene((x, y, z) -> VoxelScene.Cell.UNKNOWN));
    assertEquals(SETTINGS.width() * SETTINGS.height(), frame.unknownPixels());
    assertEquals(0x666971, center(frame));
    assertTrue(frame.surfaceHits().isEmpty());
  }

  @Test
  void cancelledCaptureStopsBeforeRendering() {
    assertThrows(
        CancellationException.class,
        () ->
            new SoftwareRenderer()
                .render(
                    scene((x, y, z) -> VoxelScene.Cell.SKY),
                    ORIGIN,
                    SETTINGS,
                    new RenderBudget(10, () -> true)));
  }

  @Test
  void boxIntersectionHandlesAxisParallelRaysAndInsideOrigins() {
    assertNull(BlockAppearance.CUBE.intersect(2, .5, .5, new CompassView.Ray(0, 0, 1)));
    var inside = BlockAppearance.CUBE.intersect(.5, .5, .5, new CompassView.Ray(0, 0, -1));
    assertNotNull(inside);
    assertEquals(.5, inside.distance());
    assertEquals(4, inside.face());
  }

  static BlockAppearance appearance(
      String name, int color, double opacity, BlockAppearance.Box box) {
    return new BlockAppearance(name, color, opacity, List.of(box));
  }

  static VoxelScene.Cell cell(BlockAppearance appearance) {
    return new VoxelScene.Cell(true, appearance, 15, 0);
  }

  static VoxelScene scene(Cells cells) {
    return new VoxelScene() {
      public Cell cell(int x, int y, int z) {
        return cells.get(x, y, z);
      }

      public int skyColor() {
        return 0x98bbdf;
      }

      public double daylight() {
        return 1;
      }
    };
  }

  static RenderBudget budget() {
    return new RenderBudget(10, () -> false);
  }

  private SoftwareRenderer.Frame north(VoxelScene scene) {
    return new SoftwareRenderer().renderView(scene, ORIGIN, SETTINGS, CompassView.NORTH, budget());
  }

  private int center(SoftwareRenderer.Frame frame) {
    return frame.image().getRGB(SETTINGS.width() / 2, SETTINGS.height() / 2) & 0xffffff;
  }

  private void assertDominant(int rgb, int shift) {
    int channel = (rgb >> shift) & 255;
    for (int other : new int[] {0, 8, 16})
      if (other != shift) assertTrue(channel > ((rgb >> other) & 255) * 2);
  }

  interface Cells {
    VoxelScene.Cell get(int x, int y, int z);
  }
}
