package com.github.quantumxiaol.craftmaid.perception;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;
import org.bukkit.Material;

public enum BlockCategory {
  NATURAL("自然方块"),
  BUILDING("人工建筑方块"),
  WOOD("木质结构"),
  STONE_BUILDING("石质建筑"),
  GLASS("玻璃/窗户"),
  CONTAINER("容器"),
  WORKSTATION("工作站"),
  LIGHTING("光源"),
  REDSTONE("红石组件"),
  CROP("作物/农田"),
  LIQUID("水体/液体"),
  DECOR("装饰");

  private static final EnumMap<Material, EnumSet<BlockCategory>> CACHE =
      new EnumMap<>(Material.class);
  private static final Set<String> WOOD_PREFIXES =
      Set.of(
          "OAK",
          "SPRUCE",
          "BIRCH",
          "JUNGLE",
          "ACACIA",
          "DARK_OAK",
          "MANGROVE",
          "CHERRY",
          "BAMBOO",
          "CRIMSON",
          "WARPED");
  private static final Set<Material> CONTAINERS =
      Set.of(
          Material.CHEST,
          Material.TRAPPED_CHEST,
          Material.BARREL,
          Material.HOPPER,
          Material.DISPENSER,
          Material.DROPPER);
  private static final Set<Material> WORKSTATIONS =
      Set.of(
          Material.CRAFTING_TABLE,
          Material.FURNACE,
          Material.BLAST_FURNACE,
          Material.SMOKER,
          Material.ANVIL,
          Material.CHIPPED_ANVIL,
          Material.DAMAGED_ANVIL,
          Material.ENCHANTING_TABLE,
          Material.BREWING_STAND,
          Material.LECTERN,
          Material.LOOM,
          Material.STONECUTTER,
          Material.GRINDSTONE,
          Material.CARTOGRAPHY_TABLE,
          Material.FLETCHING_TABLE,
          Material.SMITHING_TABLE);
  private static final Set<Material> CROPS =
      Set.of(
          Material.FARMLAND,
          Material.WHEAT,
          Material.CARROTS,
          Material.POTATOES,
          Material.BEETROOTS,
          Material.NETHER_WART,
          Material.SUGAR_CANE,
          Material.BAMBOO,
          Material.CACTUS,
          Material.MELON,
          Material.PUMPKIN,
          Material.COCOA,
          Material.SWEET_BERRY_BUSH);
  private static final Set<Material> LIQUIDS = Set.of(Material.WATER, Material.LAVA);

  private final String label;

  BlockCategory(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }

  public static EnumSet<BlockCategory> classify(Material material) {
    if (material == null) {
      return EnumSet.noneOf(BlockCategory.class);
    }
    EnumSet<BlockCategory> cached = CACHE.get(material);
    if (cached != null) {
      return cached;
    }
    EnumSet<BlockCategory> categories = classifyUncached(material);
    CACHE.put(material, categories);
    return categories;
  }

  private static EnumSet<BlockCategory> classifyUncached(Material material) {
    EnumSet<BlockCategory> categories = EnumSet.noneOf(BlockCategory.class);
    String name = material.name();
    boolean wood = isWood(name);
    boolean glass = name.equals("GLASS") || name.endsWith("_GLASS") || name.endsWith("_GLASS_PANE");
    boolean stoneBuilding = isStoneBuilding(name);
    boolean container = CONTAINERS.contains(material) || name.endsWith("SHULKER_BOX");
    boolean workstation = WORKSTATIONS.contains(material);
    boolean lighting = isLighting(name);
    boolean redstone = isRedstone(name);
    boolean crop = CROPS.contains(material);
    boolean liquid = LIQUIDS.contains(material);
    boolean decor = isDecor(name);
    boolean natural = isNatural(name, material);
    boolean building = isBuilding(name, wood, glass, stoneBuilding, decor);

    if (natural) {
      categories.add(NATURAL);
    }
    if (building) {
      categories.add(BUILDING);
    }
    if (wood) {
      categories.add(WOOD);
    }
    if (stoneBuilding) {
      categories.add(STONE_BUILDING);
    }
    if (glass) {
      categories.add(GLASS);
    }
    if (container) {
      categories.add(CONTAINER);
    }
    if (workstation) {
      categories.add(WORKSTATION);
    }
    if (lighting) {
      categories.add(LIGHTING);
    }
    if (redstone) {
      categories.add(REDSTONE);
    }
    if (crop) {
      categories.add(CROP);
    }
    if (liquid) {
      categories.add(LIQUID);
    }
    if (decor) {
      categories.add(DECOR);
    }
    return categories;
  }

  private static boolean isWood(String name) {
    for (String prefix : WOOD_PREFIXES) {
      if (name.startsWith(prefix + "_")
          && (name.endsWith("_PLANKS")
              || name.endsWith("_LOG")
              || name.endsWith("_WOOD")
              || name.endsWith("_STEM")
              || name.endsWith("_HYPHAE")
              || name.endsWith("_STAIRS")
              || name.endsWith("_SLAB")
              || name.endsWith("_FENCE")
              || name.endsWith("_FENCE_GATE")
              || name.endsWith("_DOOR")
              || name.endsWith("_TRAPDOOR")
              || name.endsWith("_BUTTON")
              || name.endsWith("_PRESSURE_PLATE"))) {
        return true;
      }
    }
    return name.startsWith("STRIPPED_");
  }

  private static boolean isStoneBuilding(String name) {
    return name.contains("BRICKS")
        || name.endsWith("_BRICK")
        || name.contains("TILES")
        || name.startsWith("POLISHED_")
        || name.startsWith("CHISELED_")
        || name.startsWith("CUT_")
        || name.contains("CONCRETE")
        || name.contains("TERRACOTTA")
        || name.equals("SMOOTH_STONE")
        || name.equals("COBBLESTONE")
        || name.equals("MOSSY_COBBLESTONE");
  }

  private static boolean isLighting(String name) {
    return name.endsWith("TORCH")
        || name.endsWith("LANTERN")
        || name.contains("CAMPFIRE")
        || name.equals("GLOWSTONE")
        || name.equals("SEA_LANTERN")
        || name.equals("REDSTONE_LAMP")
        || name.equals("END_ROD");
  }

  private static boolean isRedstone(String name) {
    return name.contains("REDSTONE")
        || name.contains("PISTON")
        || name.equals("REPEATER")
        || name.equals("COMPARATOR")
        || name.equals("OBSERVER")
        || name.equals("DISPENSER")
        || name.equals("DROPPER")
        || name.equals("HOPPER")
        || name.equals("LEVER")
        || name.endsWith("_BUTTON")
        || name.endsWith("_PRESSURE_PLATE")
        || name.contains("SCULK_SENSOR");
  }

  private static boolean isDecor(String name) {
    return name.endsWith("_BED")
        || name.endsWith("_CARPET")
        || name.endsWith("_WOOL")
        || name.endsWith("_BANNER")
        || name.endsWith("_SIGN")
        || name.endsWith("_HANGING_SIGN")
        || name.equals("FLOWER_POT")
        || name.equals("BOOKSHELF")
        || name.equals("CHISELED_BOOKSHELF")
        || name.endsWith("_HEAD")
        || name.endsWith("_SKULL");
  }

  private static boolean isNatural(String name, Material material) {
    return material == Material.STONE
        || material == Material.DEEPSLATE
        || material == Material.DIRT
        || material == Material.GRASS_BLOCK
        || material == Material.SAND
        || material == Material.GRAVEL
        || material == Material.CLAY
        || material == Material.NETHERRACK
        || material == Material.END_STONE
        || material == Material.SNOW
        || material == Material.ICE
        || material == Material.PACKED_ICE
        || material == Material.BLUE_ICE
        || name.endsWith("_LEAVES")
        || name.endsWith("_ORE")
        || LIQUIDS.contains(material);
  }

  private static boolean isBuilding(
      String name, boolean wood, boolean glass, boolean stoneBuilding, boolean decor) {
    return wood
        || glass
        || stoneBuilding
        || decor
        || name.endsWith("_DOOR")
        || name.endsWith("_TRAPDOOR")
        || name.endsWith("_STAIRS")
        || name.endsWith("_SLAB")
        || name.endsWith("_FENCE")
        || name.endsWith("_FENCE_GATE")
        || name.endsWith("_WALL");
  }
}
