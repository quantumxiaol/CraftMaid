package com.github.quantumxiaol.craftmaid.vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Snow;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;

/** Baked textured geometry, with approximate boxes reserved for unsupported special renderers. */
record BlockAppearance(
    String material, int color, double opacity, List<Box> boxes, List<TexturedFace> faces) {
  static final Box CUBE = new Box(0, 0, 0, 1, 1, 1);

  BlockAppearance {
    boxes = List.copyOf(boxes);
    faces = List.copyOf(faces);
  }

  BlockAppearance(String material, int color, double opacity, List<Box> boxes) {
    this(material, color, opacity, boxes, List.of());
  }

  TexturedFace.Surface intersect(
      double ox, double oy, double oz, CompassView.Ray ray, double min, double max, int tint) {
    TexturedFace.Surface nearest = null;
    for (TexturedFace face : faces) {
      TexturedFace.Surface hit = face.intersect(ox, oy, oz, ray, min, max, tint);
      // Later coplanar quads are overlays (notably the tinted grass side overlay).
      if (hit != null && (nearest == null || hit.distance() <= nearest.distance() + 1e-7))
        nearest = hit;
    }
    if (nearest != null)
      return new TexturedFace.Surface(
          nearest.distance(),
          nearest.face(),
          nearest.color(),
          nearest.opacity() * opacity,
          nearest.shade());
    for (Box box : boxes) {
      Hit hit = box.intersect(ox, oy, oz, ray);
      if (hit == null
          || hit.distance() < min
          || hit.distance() > max
          || nearest != null && hit.distance() >= nearest.distance()) continue;
      double t = hit.distance();
      nearest =
          new TexturedFace.Surface(
              t,
              hit.face(),
              surfaceColor(ox + ray.x() * t, oy + ray.y() * t, oz + ray.z() * t, hit.face()),
              opacity,
              true);
    }
    return nearest;
  }

  static BlockAppearance from(BlockData data) {
    Material material = data.getMaterial();
    String name = material.name();
    if (isAir(material)
        || name.equals("LIGHT")
        || name.equals("BARRIER")
        || name.equals("STRUCTURE_VOID")) {
      return new BlockAppearance(name, 0, 0, List.of());
    }
    int color = data.getMapColor().asRGB();
    if (color == 0) color = 0x96968d;
    double opacity = 1;
    if (name.contains("GLASS")) {
      opacity = name.contains("STAINED") ? .38 : .18;
      if (!name.contains("STAINED")) color = 0xb8e0e5;
    } else if (material == Material.WATER) {
      color = 0x396ec1;
      opacity = .48;
    } else if (name.endsWith("LEAVES")) {
      opacity = .92;
    }
    return new BlockAppearance(name, color, opacity, geometry(data));
  }

  static boolean isAir(Material material) {
    return material == Material.AIR
        || material == Material.CAVE_AIR
        || material == Material.VOID_AIR;
  }

  private static List<Box> geometry(BlockData data) {
    String name = data.getMaterial().name();
    if (data instanceof Slab slab) {
      return switch (slab.getType()) {
        case DOUBLE -> List.of(CUBE);
        case TOP -> List.of(new Box(0, .5, 0, 1, 1, 1));
        case BOTTOM -> List.of(new Box(0, 0, 0, 1, .5, 1));
      };
    }
    if (data instanceof Stairs stairs) {
      boolean top = stairs.getHalf() == Bisected.Half.TOP;
      List<Box> boxes = new ArrayList<>();
      boxes.add(new Box(0, top ? .5 : 0, 0, 1, top ? 1 : .5, 1));
      BlockFace facing = stairs.getFacing();
      BlockFace left = turn(facing, false);
      for (int x = 0; x < 2; x++) {
        for (int z = 0; z < 2; z++) {
          boolean forward = half(facing, x, z);
          boolean side = half(left, x, z);
          boolean occupied =
              switch (stairs.getShape()) {
                case STRAIGHT -> forward;
                case INNER_LEFT -> forward || side;
                case INNER_RIGHT -> forward || !side;
                case OUTER_LEFT -> forward && side;
                case OUTER_RIGHT -> forward && !side;
              };
          if (occupied)
            boxes.add(
                new Box(x * .5, top ? 0 : .5, z * .5, (x + 1) * .5, top ? .5 : 1, (z + 1) * .5));
        }
      }
      return boxes;
    }
    if (data instanceof Door door) {
      BlockFace face =
          door.isOpen()
              ? turn(door.getFacing(), door.getHinge() == Door.Hinge.RIGHT)
              : door.getFacing();
      return List.of(edge(face, 3.0 / 16));
    }
    if (data instanceof TrapDoor trap) {
      if (trap.isOpen()) return List.of(edge(trap.getFacing(), 3.0 / 16));
      return List.of(
          trap.getHalf() == Bisected.Half.TOP
              ? new Box(0, 13.0 / 16, 0, 1, 1, 1)
              : new Box(0, 0, 0, 1, 3.0 / 16, 1));
    }
    if (data instanceof Gate gate) {
      if (gate.isOpen()) return List.of();
      return List.of(
          gate.getFacing().getModX() == 0
              ? new Box(0, .25, .375, 1, 1, .625)
              : new Box(.375, .25, 0, .625, 1, 1));
    }
    if (data instanceof MultipleFacing connections
        && (name.contains("FENCE") || name.contains("PANE") || name.equals("IRON_BARS"))) {
      double a = name.contains("FENCE") ? .375 : .4375;
      double b = 1 - a;
      List<Box> boxes = new ArrayList<>();
      boxes.add(new Box(a, 0, a, b, 1, b));
      for (BlockFace face : connections.getFaces()) {
        switch (face) {
          case NORTH -> boxes.add(new Box(a, 0, 0, b, 1, a));
          case SOUTH -> boxes.add(new Box(a, 0, b, b, 1, 1));
          case EAST -> boxes.add(new Box(b, 0, a, 1, 1, b));
          case WEST -> boxes.add(new Box(0, 0, a, a, 1, b));
          default -> {}
        }
      }
      return boxes;
    }
    if (data instanceof Snow snow) return List.of(new Box(0, 0, 0, 1, snow.getLayers() / 8.0, 1));
    if (name.endsWith("CARPET")) return List.of(new Box(0, 0, 0, 1, .0625, 1));
    if (name.equals("FARMLAND") || name.equals("DIRT_PATH")) {
      return List.of(new Box(0, 0, 0, 1, .9375, 1));
    }
    if (name.endsWith("CHEST")) return List.of(new Box(.0625, 0, .0625, .9375, .875, .9375));
    if (name.endsWith("TORCH")) return List.of(new Box(.4375, 0, .4375, .5625, .625, .5625));
    if (name.contains("LANTERN")) return List.of(new Box(.3125, 0, .3125, .6875, .625, .6875));
    if (name.equals("REDSTONE_WIRE") || name.endsWith("RAIL")) {
      return List.of(new Box(0, 0, 0, 1, .03125, 1));
    }
    // Crossed thin boxes retain plant silhouettes without turning crops into opaque cubes.
    if (isPlant(name)) {
      double height =
          data instanceof Ageable age
              ? .25 + .65 * age.getAge() / Math.max(1, age.getMaximumAge())
              : .8;
      return List.of(
          new Box(.15, 0, .46, .85, height, .54), new Box(.46, 0, .15, .54, height, .85));
    }
    return List.of(CUBE);
  }

  private static boolean isPlant(String name) {
    return name.endsWith("SAPLING")
        || name.endsWith("TULIP")
        || name.endsWith("FLOWER")
        || name.endsWith("MUSHROOM")
        || name.equals("SHORT_GRASS")
        || name.equals("TALL_GRASS")
        || name.equals("FERN")
        || name.equals("LARGE_FERN")
        || name.equals("POPPY")
        || name.equals("DANDELION")
        || name.equals("WHEAT")
        || name.equals("CARROTS")
        || name.equals("POTATOES")
        || name.equals("BEETROOTS")
        || name.equals("SUGAR_CANE")
        || name.equals("DEAD_BUSH")
        || name.equals("NETHER_WART");
  }

  private static BlockFace turn(BlockFace face, boolean clockwise) {
    return switch (face) {
      case NORTH -> clockwise ? BlockFace.EAST : BlockFace.WEST;
      case EAST -> clockwise ? BlockFace.SOUTH : BlockFace.NORTH;
      case SOUTH -> clockwise ? BlockFace.WEST : BlockFace.EAST;
      default -> clockwise ? BlockFace.NORTH : BlockFace.SOUTH;
    };
  }

  private static boolean half(BlockFace facing, int x, int z) {
    return switch (facing) {
      case NORTH -> z == 0;
      case SOUTH -> z == 1;
      case EAST -> x == 1;
      default -> x == 0;
    };
  }

  private static Box edge(BlockFace facing, double thickness) {
    return switch (facing) {
      case NORTH -> new Box(0, 0, 1 - thickness, 1, 1, 1);
      case SOUTH -> new Box(0, 0, 0, 1, 1, thickness);
      case WEST -> new Box(1 - thickness, 0, 0, 1, 1, 1);
      default -> new Box(0, 0, 0, thickness, 1, 1);
    };
  }

  int surfaceColor(double x, double y, double z, int face) {
    double u = face < 2 ? z : x;
    double v = face == 2 || face == 3 ? z : y;
    int px = (int) Math.floor(u * 16);
    int py = (int) Math.floor(v * 16);
    int noise = px * 734287 + py * 912931 + material.hashCode();
    noise = (noise ^ (noise >>> 16)) * 0x45d9f3b;
    noise ^= noise >>> 16;
    double detail = .94 + (noise & 15) / 125.0;
    int base = color;
    if (material.equals("GRASS_BLOCK") && face != 3) {
      base = y > .82 && face != 2 ? color : 0x856044;
    } else if (material.endsWith("PLANKS")) {
      if (py % 4 == 0 || Math.floorMod(px + (py / 4 % 2) * 8, 16) == 0) detail *= .7;
    } else if (material.contains("BRICK")) {
      if (py % 4 == 0 || Math.floorMod(px + (py / 4 % 2) * 4, 8) == 0) detail *= .65;
    } else if (material.endsWith("LOG") || material.endsWith("WOOD")) {
      if (px % 4 == 0) detail *= .7;
    } else if (material.contains("GLASS")) {
      if (px == 0 || px == 15 || py == 0 || py == 15) detail *= .65;
    }
    return scale(base, detail);
  }

  static int scale(int rgb, double factor) {
    return ((int) Math.clamp(((rgb >> 16) & 255) * factor, 0, 255) << 16)
        | ((int) Math.clamp(((rgb >> 8) & 255) * factor, 0, 255) << 8)
        | (int) Math.clamp((rgb & 255) * factor, 0, 255);
  }

  String label() {
    return material.toLowerCase(Locale.ROOT);
  }

  /** Local block coordinates. Face order: -X,+X,-Y,+Y,-Z,+Z. */
  record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    Hit intersect(double ox, double oy, double oz, CompassView.Ray ray) {
      double near = Double.NEGATIVE_INFINITY;
      double far = Double.POSITIVE_INFINITY;
      int nearFace = 0, farFace = 0;
      for (int axis = 0; axis < 3; axis++) {
        double origin = axis == 0 ? ox : axis == 1 ? oy : oz;
        double direction = axis == 0 ? ray.x() : axis == 1 ? ray.y() : ray.z();
        double min = axis == 0 ? minX : axis == 1 ? minY : minZ;
        double max = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
        if (Math.abs(direction) < 1e-12) {
          if (origin < min || origin > max) return null;
          continue;
        }
        double a = (min - origin) / direction;
        double b = (max - origin) / direction;
        int entering = axis * 2 + (direction < 0 ? 1 : 0);
        if (Math.min(a, b) > near) {
          near = Math.min(a, b);
          nearFace = entering;
        }
        if (Math.max(a, b) < far) {
          far = Math.max(a, b);
          farFace = entering ^ 1;
        }
        if (near > far || far < 1e-6) return null;
      }
      return near >= 1e-6 ? new Hit(near, nearFace) : new Hit(far, farFace);
    }
  }

  record Hit(double distance, int face) {}
}
