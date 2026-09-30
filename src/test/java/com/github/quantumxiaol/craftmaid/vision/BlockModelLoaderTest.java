package com.github.quantumxiaol.craftmaid.vision;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlockModelLoaderTest {
  @TempDir Path directory;
  private static final String PLANE =
      """
      {"textures":{"front":"#all"},"elements":[{"from":[0,0,0],"to":[16,16,16],
       "faces":{"north":{"texture":"#front","tintindex":0},"up":{"texture":"#front"}}}]}
      """;

  private Map<String, String> files() {
    Map<String, String> files = new HashMap<>();
    files.put("models/block/parent.json", PLANE);
    files.put(
        "models/block/child.json",
        """
        {"parent":"block/parent","textures":{"all":"block/cutout"}}
        """);
    files.put(
        "blockstates/test.json",
        """
        {"variants":{"facing=north":{"model":"block/child"},"facing=east":{"model":"block/child","y":90}}}
        """);
    return files;
  }

  @Test
  void inheritsTextureAliasesAndRespectsCutoutPixelsAndTint() throws Exception {
    try (ResourcePack pack =
        new ResourcePack(TestResourcePack.create(directory.resolve("pack.jar"), files()), null)) {
      var block = new BlockModelLoader(pack).load("minecraft:test[facing=north]");
      var towardSouth = new CompassView.Ray(0, 0, 1);
      assertNull(block.intersect(.25, .25, -1, towardSouth, .001, 4, 0xffffff));
      var blue = block.intersect(.75, .25, -1, towardSouth, .001, 4, 0xffffff);
      assertNotNull(blue);
      assertEquals(0x0000ff, blue.color());
      var tinted = block.intersect(.75, .25, -1, towardSouth, .001, 4, 0xffff80);
      assertEquals(0x000080, tinted.color());
    }
  }

  @Test
  void variantRotationMovesFrontFaceToEast() throws Exception {
    try (ResourcePack pack =
        new ResourcePack(TestResourcePack.create(directory.resolve("pack.jar"), files()), null)) {
      var block = new BlockModelLoader(pack).load("minecraft:test[facing=east]");
      var hit = block.intersect(2, .75, .25, new CompassView.Ray(-1, 0, 0), .001, 4, 0xffffff);
      assertNotNull(hit);
      assertEquals(1, hit.face());
      assertEquals(1, hit.distance(), 1e-6);
      assertNull(block.intersect(.5, .5, -1, new CompassView.Ray(0, 0, 1), .001, 4, 0xffffff));
    }
  }

  @Test
  void uvlockKeepsTopTextureAlignedInWorldSpace() throws Exception {
    Map<String, String> files = files();
    files.put(
        "models/block/child.json",
        """
        {"parent":"block/parent","textures":{"all":"block/four"}}
        """);
    files.put(
        "blockstates/test.json",
        """
        {"variants":{"rotation=0":{"model":"block/child"},"rotation=90":{"model":"block/child","y":90,"uvlock":true}}}
        """);
    try (ResourcePack pack =
        new ResourcePack(TestResourcePack.create(directory.resolve("pack.jar"), files), null)) {
      var loader = new BlockModelLoader(pack);
      var base = loader.load("minecraft:test[rotation=0]");
      var locked = loader.load("minecraft:test[rotation=90]");
      for (double x : new double[] {.25, .75})
        for (double z : new double[] {.25, .75}) {
          var ray = new CompassView.Ray(0, -1, 0);
          assertEquals(
              base.intersect(x, 2, z, ray, .001, 4, 0xffffff).color(),
              locked.intersect(x, 2, z, ray, .001, 4, 0xffffff).color());
        }
    }
  }

  @Test
  void multipartMatchesAndOrAndTextureObjectReferences() throws Exception {
    Map<String, String> files = files();
    files.put(
        "models/block/child.json",
        """
        {"parent":"block/parent","textures":{"all":{"sprite":"block/white","force_translucent":true}}}
        """);
    files.put(
        "blockstates/test.json",
        """
        {"multipart":[{"apply":{"model":"block/child"}},
         {"when":{"AND":[{"north":"true"},{"OR":[{"east":"true"},{"west":"true"}]}]},"apply":{"model":"block/child","y":90}}]}
        """);
    try (ResourcePack pack =
        new ResourcePack(TestResourcePack.create(directory.resolve("pack.jar"), files), null)) {
      var loader = new BlockModelLoader(pack);
      assertEquals(
          2, loader.load("minecraft:test[north=false,east=true,west=false]").faces().size());
      assertEquals(
          4, loader.load("minecraft:test[north=true,east=true,west=false]").faces().size());
    }
  }

  @Test
  void coplanarTintedOverlayWinsOnlyAtVisibleTexels() throws Exception {
    Map<String, String> files = files();
    files.put(
        "models/block/child.json",
        """
        {"elements":[
        {"from":[0,0,0],"to":[16,16,16],"faces":{"north":{"texture":"block/white"}}},
        {"from":[0,0,0],"to":[16,16,16],"faces":{"north":{"texture":"block/cutout","tintindex":0}}}
        ]}
        """);
    try (ResourcePack pack =
        new ResourcePack(TestResourcePack.create(directory.resolve("pack.jar"), files), null)) {
      var block = new BlockModelLoader(pack).load("minecraft:test[facing=north]");
      var ray = new CompassView.Ray(0, 0, 1);
      assertEquals(0xffffff, block.intersect(.25, .25, -1, ray, .001, 4, 0xffff80).color());
      assertEquals(0x000080, block.intersect(.75, .25, -1, ray, .001, 4, 0xffff80).color());
    }
  }

  @Test
  void rejectsParentCycles() throws Exception {
    Map<String, String> files = files();
    files.put("models/block/parent.json", "{\"parent\":\"block/child\"}");
    try (ResourcePack pack =
        new ResourcePack(TestResourcePack.create(directory.resolve("pack.jar"), files), null)) {
      assertThrows(
          IOException.class, () -> new BlockModelLoader(pack).load("minecraft:test[facing=north]"));
    }
  }
}
