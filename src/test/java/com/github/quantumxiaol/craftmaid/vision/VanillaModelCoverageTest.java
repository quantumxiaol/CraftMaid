package com.github.quantumxiaol.craftmaid.vision;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class VanillaModelCoverageTest {
  @Test
  void readsRealClientBlockstatesWithoutTextureOrModelErrors() throws Exception {
    String file = System.getProperty("craftmaid.vision.clientJar", "");
    assumeTrue(
        !file.isBlank(), "Supply -Dcraftmaid.vision.clientJar for the vanilla integration check");
    List<String> errors = new ArrayList<>();
    List<String> fallbacks = new ArrayList<>();
    int supported = 0;
    try (ZipFile zip = new ZipFile(file);
        ResourcePack pack = new ResourcePack(Path.of(file), null)) {
      BlockModelLoader loader = new BlockModelLoader(pack);
      var entries = zip.entries();
      while (entries.hasMoreElements()) {
        var entry = entries.nextElement();
        String name = entry.getName();
        if (!name.startsWith("assets/minecraft/blockstates/") || !name.endsWith(".json")) continue;
        String id =
            "minecraft:"
                + name.substring("assets/minecraft/blockstates/".length(), name.length() - 5);
        var json =
            JsonParser.parseString(
                    new String(
                        zip.getInputStream(entry).readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonObject();
        if (json.has("variants"))
          id += "[" + json.getAsJsonObject("variants").keySet().iterator().next() + "]";
        else
          id += "[north=true,south=true,east=true,west=true,up=true,down=true,waterlogged=false]";
        try {
          BlockAppearance model = loader.load(id);
          if (model == null) fallbacks.add(id);
          else supported++;
        } catch (Exception exception) {
          errors.add(id + ": " + exception);
        }
      }
    }
    Files.createDirectories(Path.of("target/vision-preview"));
    Files.writeString(
        Path.of("target/vision-preview/model-coverage.txt"),
        "Supported selected states: "
            + supported
            + "\nFallbacks: "
            + fallbacks
            + "\nErrors: "
            + errors);
    assertTrue(supported > 500, "Loaded too few vanilla models: " + supported);
    assertTrue(errors.isEmpty(), String.join("\n", errors));
  }
}
