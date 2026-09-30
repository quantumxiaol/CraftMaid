package com.github.quantumxiaol.craftmaid.vision;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** CPU voxel traversal with local shape intersections; never accesses a live Bukkit world. */
final class SoftwareRenderer {
  record Frame(BufferedImage image, Map<String, Integer> surfaceHits, int unknownPixels) {}

  record Panorama(Map<CompassView, Frame> frames, BufferedImage overview) {}

  Panorama render(
      VoxelScene scene,
      ObservationSnapshot.Origin origin,
      VisionSettings settings,
      RenderBudget budget) {
    Map<CompassView, Frame> frames = new EnumMap<>(CompassView.class);
    for (CompassView view : CompassView.values()) {
      budget.check();
      frames.put(view, renderView(scene, origin, settings, view, budget));
    }
    return new Panorama(Map.copyOf(frames), overview(frames, settings));
  }

  Frame renderView(
      VoxelScene scene,
      ObservationSnapshot.Origin origin,
      VisionSettings settings,
      CompassView view,
      RenderBudget budget) {
    BufferedImage image =
        new BufferedImage(settings.width(), settings.height(), BufferedImage.TYPE_INT_RGB);
    int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    Map<String, Integer> hits = new HashMap<>();
    int unknown = 0;
    for (int y = 0; y < settings.height(); y++) {
      budget.check();
      for (int x = 0; x < settings.width(); x++) {
        CompassView.Ray ray =
            view.ray(
                2 * (x + .5) / settings.width() - 1,
                1 - 2 * (y + .5) / settings.height(),
                settings);
        Pixel pixel = trace(scene, origin, ray, settings.distance(), hits);
        pixels[y * settings.width() + x] = pixel.color();
        if (pixel.unknown()) unknown++;
      }
    }
    return new Frame(image, Map.copyOf(hits), unknown);
  }

  private Pixel trace(
      VoxelScene scene,
      ObservationSnapshot.Origin origin,
      CompassView.Ray ray,
      int distance,
      Map<String, Integer> materials) {
    double ox = origin.x(), oy = origin.y(), oz = origin.z();
    int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
    int stepX = ray.x() >= 0 ? 1 : -1;
    int stepY = ray.y() >= 0 ? 1 : -1;
    int stepZ = ray.z() >= 0 ? 1 : -1;
    double deltaX = 1 / Math.abs(ray.x()),
        deltaY = 1 / Math.abs(ray.y()),
        deltaZ = 1 / Math.abs(ray.z());
    double nextX = boundary(ox, x, ray.x());
    double nextY = boundary(oy, y, ray.y());
    double nextZ = boundary(oz, z, ray.z());
    double travelled = 0, transmission = 1, r = 0, g = 0, b = 0;
    int background = scene.skyColor();
    boolean unknown = false;
    for (int steps = 0; steps < distance * 3 + 6 && travelled <= distance; steps++) {
      VoxelScene.Cell cell = scene.cell(x, y, z);
      if (!cell.known()) {
        // Grey marks missing data, never pretend an unloaded chunk is open sky.
        background = 0x666971;
        unknown = true;
        break;
      }
      double exit = Math.min(nextX, Math.min(nextY, nextZ));
      BlockAppearance appearance = cell.appearance();
      if (appearance != null && appearance.opacity() > 0) {
        double cursor = Math.max(1e-6, travelled - 1e-7);
        for (int layer = 0; layer < 32; layer++) {
          TexturedFace.Surface nearest =
              appearance.intersect(
                  ox - x,
                  oy - y,
                  oz - z,
                  ray,
                  cursor,
                  Math.min(exit + 1e-6, distance),
                  cell.tint());
          if (nearest == null) break;
          double t = nearest.distance();
          int face = nearest.face();
          int color = nearest.color();
          int ax = x + (face == 0 ? -1 : face == 1 ? 1 : 0);
          int ay = y + (face == 2 ? -1 : face == 3 ? 1 : 0);
          int az = z + (face == 4 ? -1 : face == 5 ? 1 : 0);
          VoxelScene.Cell light = scene.cell(ax, ay, az);
          double lightLevel =
              Math.max(
                  Math.max(cell.emittedLight(), light.emittedLight()),
                  Math.max(cell.skyLight(), light.skyLight()) * scene.daylight());
          double faceShade =
              !nearest.shade() ? 1 : face == 3 ? 1 : face == 2 ? .5 : face < 2 ? .6 : .8;
          color = BlockAppearance.scale(color, faceShade * (.22 + .78 * lightLevel / 15));
          double fog = Math.pow(t / distance, 3) * .35;
          color = mix(color, scene.skyColor(), fog);
          double amount = transmission * nearest.opacity();
          r += ((color >> 16) & 255) * amount;
          g += ((color >> 8) & 255) * amount;
          b += (color & 255) * amount;
          transmission *= 1 - nearest.opacity();
          materials.merge(appearance.label(), 1, Integer::sum);
          if (transmission < .015) break;
          cursor = t + 1e-6;
        }
        if (transmission < .015) break;
      }
      travelled = exit;
      if (nextX <= exit + 1e-9) {
        x += stepX;
        nextX += deltaX;
      }
      if (nextY <= exit + 1e-9) {
        y += stepY;
        nextY += deltaY;
      }
      if (nextZ <= exit + 1e-9) {
        z += stepZ;
        nextZ += deltaZ;
      }
    }
    r += ((background >> 16) & 255) * transmission;
    g += ((background >> 8) & 255) * transmission;
    b += (background & 255) * transmission;
    return new Pixel(
        ((int) Math.min(255, r) << 16) | ((int) Math.min(255, g) << 8) | (int) Math.min(255, b),
        unknown);
  }

  private static double boundary(double origin, int block, double direction) {
    if (Math.abs(direction) < 1e-12) return Double.POSITIVE_INFINITY;
    return ((direction > 0 ? block + 1 : block) - origin) / direction;
  }

  private static int mix(int a, int b, double factor) {
    return ((int) (((a >> 16) & 255) * (1 - factor) + ((b >> 16) & 255) * factor) << 16)
        | ((int) (((a >> 8) & 255) * (1 - factor) + ((b >> 8) & 255) * factor) << 8)
        | (int) ((a & 255) * (1 - factor) + (b & 255) * factor);
  }

  private static BufferedImage overview(Map<CompassView, Frame> frames, VisionSettings settings) {
    int labelHeight = 24;
    BufferedImage image =
        new BufferedImage(
            settings.width() * 2,
            (settings.height() + labelHeight) * 2,
            BufferedImage.TYPE_INT_RGB);
    int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    java.util.Arrays.fill(pixels, 0x20242c);
    for (CompassView view : CompassView.values()) {
      int x = (view.ordinal() % 2) * settings.width();
      int y = (view.ordinal() / 2) * (settings.height() + labelHeight);
      drawLabel(image, view.name(), x + 8, y + 5);
      int[] source =
          ((DataBufferInt) frames.get(view).image().getRaster().getDataBuffer()).getData();
      for (int row = 0; row < settings.height(); row++) {
        System.arraycopy(
            source,
            row * settings.width(),
            pixels,
            (y + labelHeight + row) * image.getWidth() + x,
            settings.width());
      }
    }
    return image;
  }

  /**
   * Tiny built-in direction labels avoid native font/desktop initialization on headless servers.
   */
  private static void drawLabel(BufferedImage image, String label, int x, int y) {
    for (char letter : label.toCharArray()) {
      int[] rows =
          switch (letter) {
            case 'N' -> new int[] {17, 25, 25, 21, 19, 19, 17};
            case 'O' -> new int[] {14, 17, 17, 17, 17, 17, 14};
            case 'R' -> new int[] {30, 17, 17, 30, 20, 18, 17};
            case 'T' -> new int[] {31, 4, 4, 4, 4, 4, 4};
            case 'H' -> new int[] {17, 17, 17, 31, 17, 17, 17};
            case 'E' -> new int[] {31, 16, 16, 30, 16, 16, 31};
            case 'A' -> new int[] {14, 17, 17, 31, 17, 17, 17};
            case 'S' -> new int[] {15, 16, 16, 14, 1, 1, 30};
            case 'U' -> new int[] {17, 17, 17, 17, 17, 17, 14};
            case 'W' -> new int[] {17, 17, 17, 21, 21, 21, 10};
            default -> new int[7];
          };
      for (int row = 0; row < 7; row++) {
        for (int column = 0; column < 5; column++) {
          if ((rows[row] & (1 << (4 - column))) == 0) continue;
          for (int dy = 0; dy < 2; dy++)
            for (int dx = 0; dx < 2; dx++) {
              image.setRGB(x + column * 2 + dx, y + row * 2 + dy, 0xffffff);
            }
        }
      }
      x += 12;
    }
  }

  private record Pixel(int color, boolean unknown) {}
}
