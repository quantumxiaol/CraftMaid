package com.github.quantumxiaol.craftmaid.vision;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/** Generated test resources only; no copyrighted game assets in the test tree. */
final class TestResourcePack {
  static Path create(Path path, Map<String, String> json) throws IOException {
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
      zip.putNextEntry(new ZipEntry("version.json"));
      zip.write("{\"id\":\"26.1.2\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      zip.closeEntry();
      for (var entry : json.entrySet()) {
        zip.putNextEntry(new ZipEntry("assets/minecraft/" + entry.getKey()));
        zip.write(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        zip.closeEntry();
      }
      texture(zip, "four", new int[] {0xffff0000, 0xff00ff00, 0xff0000ff, 0xffffff00});
      texture(zip, "cutout", new int[] {0xffff0000, 0xff00ff00, 0xff0000ff, 0x00000000});
      texture(zip, "white", new int[] {-1, -1, -1, -1});
      texture(zip, "stone", new int[] {-1, -1, -1, -1});
    }
    return path;
  }

  private static void texture(ZipOutputStream zip, String name, int[] pixels) throws IOException {
    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, 2, 2, pixels, 0, 2);
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ImageIO.write(image, "png", output);
    zip.putNextEntry(new ZipEntry("assets/minecraft/textures/block/" + name + ".png"));
    zip.write(output.toByteArray());
    zip.closeEntry();
  }
}
