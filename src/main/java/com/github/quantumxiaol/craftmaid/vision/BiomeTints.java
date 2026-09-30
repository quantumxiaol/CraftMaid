package com.github.quantumxiaol.craftmaid.vision;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Vanilla biome definitions and color maps. Custom server datapack colors are not available here.
 */
final class BiomeTints {
  private final ResourcePack pack;
  private final Map<String, Colors> cache = new HashMap<>();

  BiomeTints(ResourcePack pack) {
    this.pack = pack;
  }

  int color(String state, String biome) throws IOException {
    Colors colors = cache.get(biome);
    if (colors == null) {
      colors = colors(biome);
      cache.put(biome, colors);
    }
    if (state.startsWith("minecraft:water")) return colors.water;
    if (state.startsWith("minecraft:spruce_leaves")) return 0x619961;
    if (state.startsWith("minecraft:birch_leaves")) return 0x80a755;
    if (state.contains("leaves") || state.startsWith("minecraft:vine")) return colors.foliage;
    if (state.startsWith("minecraft:lily_pad")) return 0x208030;
    if (state.startsWith("minecraft:redstone_wire")) {
      int power = Integer.parseInt(BlockModelLoader.properties(state).getOrDefault("power", "0"));
      double strength = power / 15.0;
      int r = (int) ((power == 0 ? .3 : strength * .6 + .4) * 255);
      int g = (int) (Math.max(0, strength * strength * .7 - .5) * 255);
      int b = (int) (Math.max(0, strength * strength * .6 - .7) * 255);
      return r << 16 | g << 8 | b;
    }
    return colors.grass;
  }

  private Colors colors(String biome) throws IOException {
    String path =
        ResourcePack.resource("worldgen/biome", biome, ".json").replaceFirst("^assets/", "data/");
    JsonObject json = pack.json(path);
    if (json == null) json = pack.json("data/minecraft/worldgen/biome/plains.json");
    double temperature =
        json != null && json.has("temperature") ? json.get("temperature").getAsDouble() : .8;
    double downfall =
        json != null && json.has("downfall") ? json.get("downfall").getAsDouble() : .4;
    double u = 1 - Math.clamp(temperature, 0, 1);
    double v = 1 - Math.clamp(downfall, 0, 1) * Math.clamp(temperature, 0, 1);
    int grass = pack.texture("minecraft:colormap/grass").sample(u, v) & 0xffffff;
    int foliage = pack.texture("minecraft:colormap/foliage").sample(u, v) & 0xffffff;
    int water = 0x3f76e4;
    if (json != null && json.has("effects")) {
      JsonObject effects = json.getAsJsonObject("effects");
      if (effects.has("grass_color")) grass = rgb(effects.get("grass_color"));
      if (effects.has("foliage_color")) foliage = rgb(effects.get("foliage_color"));
      if (effects.has("water_color")) water = rgb(effects.get("water_color"));
      String modifier =
          effects.has("grass_color_modifier")
              ? effects.get("grass_color_modifier").getAsString()
              : "none";
      if (modifier.equals("dark_forest")) grass = ((grass & 0xfefefe) + 0x28340a) >> 1;
      // Vanilla's swamp noise chooses between two colors; use the common color without noise.
      if (modifier.equals("swamp")) grass = 0x6a7039;
    }
    return new Colors(grass, foliage, water);
  }

  private static int rgb(JsonElement value) {
    String text = value.getAsString();
    return text.startsWith("#") ? Integer.parseInt(text.substring(1), 16) : value.getAsInt();
  }

  private record Colors(int grass, int foliage, int water) {}
}
