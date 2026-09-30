package com.github.quantumxiaol.craftmaid.vision;

import static com.github.quantumxiaol.craftmaid.vision.SoftwareRendererTest.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Deterministic synthetic village for visual inspection, not a screenshot from a running server.
 */
class VisionPreviewTest {
  @Test
  void renderVillageFixtureAtDefaultResolution() throws Exception {
    var grass = cell(appearance("GRASS_BLOCK", 0x729d46, 1, BlockAppearance.CUBE));
    var wood = cell(appearance("OAK_PLANKS", 0xb18a55, 1, BlockAppearance.CUBE));
    var brick = cell(appearance("BRICKS", 0x9d604c, 1, BlockAppearance.CUBE));
    var stone = cell(appearance("COBBLESTONE", 0x8c8b89, 1, BlockAppearance.CUBE));
    var water = cell(appearance("WATER", 0x396ec1, .48, BlockAppearance.CUBE));
    var glass = cell(appearance("GLASS", 0xb8e0e5, .18, BlockAppearance.CUBE));
    var soil =
        cell(appearance("FARMLAND", 0x70513a, 1, new BlockAppearance.Box(0, 0, 0, 1, .9375, 1)));
    var crop =
        cell(
            new BlockAppearance(
                "WHEAT",
                0xc4b746,
                1,
                List.of(
                    new BlockAppearance.Box(.15, 0, .46, .85, .8, .54),
                    new BlockAppearance.Box(.46, 0, .15, .54, .8, .85))));
    var log = cell(appearance("OAK_LOG", 0x725133, 1, BlockAppearance.CUBE));
    var leaves = cell(appearance("OAK_LEAVES", 0x527e32, .92, BlockAppearance.CUBE));
    var shortGrass =
        cell(
            appearance(
                "SHORT_GRASS", 0x729d46, 1, new BlockAppearance.Box(.1, 0, .45, .9, .8, .55)));
    var flower =
        cell(
            appearance("DANDELION", 0xe3c535, 1, new BlockAppearance.Box(.3, 0, .45, .7, .6, .55)));
    VoxelScene village =
        scene(
            (x, y, z) -> {
              // North: a hollow brick house with windows and a doorway; stairs to its door.
              if (x >= -5 && x <= 4 && z >= -12 && z <= -5 && y >= 1 && y <= 5) {
                if (y == 5) return wood;
                if (x == -5 || x == 4 || z == -12 || z == -5) {
                  if (z == -5 && x == 0 && y <= 2) return VoxelScene.Cell.SKY;
                  if (z == -5 && (x == -3 || x == 2) && (y == 2 || y == 3)) return glass;
                  return brick;
                }
              }
              // East: crops on farmland.
              if (x >= 6 && x <= 17 && z >= -2 && z <= 7) {
                if (y == 0) return x % 5 == 0 ? water : soil;
                if (y == 1 && x % 5 != 0) return crop;
              }
              // South: water and a bridge, with trees on the far bank.
              if (z >= 9 && z <= 13 && y == 0) return water;
              if (z >= 8 && z <= 14 && x >= 0 && x <= 1 && y == 1) return wood;
              for (int treeX : new int[] {-8, 6, 12}) {
                if (x == treeX && z == 18 && y >= 1 && y <= 4) return log;
                if (Math.abs(x - treeX) <= 2 && Math.abs(z - 18) <= 2 && y >= 4 && y <= 6)
                  return leaves;
              }
              // West: a low stone boundary with an opening, and rising terrain.
              if (x == -8 && (z < -1 || z > 2) && y >= 1 && y <= 2) return stone;
              if (x < -12 && y <= (-x - 12) / 3) return grass;
              // Nearby vegetation checks the transparent silhouettes that were missing before.
              if (y == 1 && x >= 2 && x <= 4 && z >= -2 && z <= 5)
                return (x + z) % 4 == 0 ? flower : shortGrass;
              if (y == 1 && x >= -5 && x <= -3 && z >= 0 && z <= 4) return shortGrass;
              if (y <= 0) return x >= -1 && x <= 1 && z < 8 ? stone : grass;
              return VoxelScene.Cell.SKY;
            });
    var settings = new VisionSettings(true, 384, 256, 24, 100, 10, 10, 10, 10);
    var origin =
        new ObservationSnapshot.Origin(
            ORIGIN.worldId(),
            "synthetic-village",
            ORIGIN.npcId(),
            .5,
            2.62,
            3.5,
            ORIGIN.capturedAt(),
            6000);
    long started = System.nanoTime();
    SoftwareRenderer.Panorama panorama;
    String clientJar = System.getProperty("craftmaid.vision.clientJar", "");
    if (!clientJar.isBlank()) {
      try (ResourcePack pack = new ResourcePack(Path.of(clientJar), null)) {
        BlockModelLoader models = new BlockModelLoader(pack);
        BiomeTints tints = new BiomeTints(pack);
        java.util.Map<BlockAppearance, VoxelScene.Cell> textured = new java.util.HashMap<>();
        for (var original :
            List.of(
                grass,
                wood,
                brick,
                stone,
                water,
                glass,
                soil,
                crop,
                log,
                leaves,
                shortGrass,
                flower)) {
          String id = "minecraft:" + original.appearance().label();
          id +=
              switch (original.appearance().material()) {
                case "GRASS_BLOCK" -> "[snowy=false]";
                case "FARMLAND" -> "[moisture=7]";
                case "WHEAT" -> "[age=7]";
                case "OAK_LOG" -> "[axis=y]";
                default -> "";
              };
          BlockAppearance model = models.load(id);
          assertNotNull(model, id);
          assertFalse(model.faces().isEmpty(), id);
          textured.put(
              original.appearance(),
              new VoxelScene.Cell(true, model, 15, 0, tints.color(id, "minecraft:plains")));
        }
        VoxelScene texturedVillage =
            scene(
                (x, y, z) -> {
                  var original = village.cell(x, y, z);
                  return original.appearance() == null
                      ? original
                      : textured.get(original.appearance());
                });
        panorama = new SoftwareRenderer().render(texturedVillage, origin, settings, budget());
      }
    } else {
      panorama = new SoftwareRenderer().render(village, origin, settings, budget());
    }
    long elapsed = System.nanoTime() - started;
    assertTrue(panorama.frames().get(CompassView.NORTH).surfaceHits().containsKey("bricks"));
    assertTrue(panorama.frames().get(CompassView.EAST).surfaceHits().containsKey("wheat"));
    assertTrue(panorama.frames().get(CompassView.SOUTH).surfaceHits().containsKey("water"));
    assertTrue(panorama.frames().get(CompassView.WEST).surfaceHits().containsKey("cobblestone"));
    Path preview = Path.of("target/vision-preview");
    Files.createDirectories(preview);
    for (CompassView view : CompassView.values()) {
      assertTrue(
          ImageIO.write(
              panorama.frames().get(view).image(),
              "png",
              preview.resolve(view.fileName()).toFile()));
    }
    assertTrue(ImageIO.write(panorama.overview(), "png", preview.resolve("overview.png").toFile()));
    Files.writeString(
        preview.resolve("README.txt"),
        "Synthetic test scene, not a live Minecraft capture.\n"
            + "Vanilla resource textures: "
            + !clientJar.isBlank()
            + "\n"
            + "Default 384x256 x4, distance 24, render time: "
            + elapsed / 1_000_000
            + "ms\n");
  }
}
