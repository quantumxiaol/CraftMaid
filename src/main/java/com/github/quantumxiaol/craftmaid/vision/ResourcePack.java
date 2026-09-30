package com.github.quantumxiaol.craftmaid.vision;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;

/** A vanilla client jar plus an optional resource-pack overlay, read as archives only. */
final class ResourcePack implements AutoCloseable {
  private final ZipFile vanilla;
  private final ZipFile overlay;
  private final Map<String, Texture> textures = new HashMap<>();

  ResourcePack(Path client, Path resourcePack) throws IOException {
    vanilla = new ZipFile(client.toFile());
    if (vanilla.getEntry("assets/minecraft/textures/block/stone.png") == null
        || vanilla.getEntry("version.json") == null) {
      vanilla.close();
      throw new IOException("client_jar 必须是原版客户端 jar，不能使用服务端 jar 或只有部分文件的资源包。");
    }
    try {
      overlay = resourcePack == null ? null : new ZipFile(resourcePack.toFile());
    } catch (IOException e) {
      vanilla.close();
      throw e;
    }
  }

  String clientVersion() throws IOException {
    // The overlay must not replace the base client's identity.
    try (InputStream input = vanilla.getInputStream(vanilla.getEntry("version.json"))) {
      return JsonParser.parseString(new String(input.readNBytes(65536), StandardCharsets.UTF_8))
          .getAsJsonObject()
          .get("id")
          .getAsString();
    }
  }

  JsonObject json(String path) throws IOException {
    byte[] bytes = read(path, 2 * 1024 * 1024);
    return bytes == null
        ? null
        : JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
  }

  Texture texture(String id) throws IOException {
    Texture existing = textures.get(id);
    if (existing != null) return existing;
    String path = resource("textures", id, ".png");
    byte[] bytes = read(path, 16 * 1024 * 1024);
    if (bytes == null) throw new IOException("缺少方块贴图：" + id);
    BufferedImage image;
    try (var input = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IOException("无法读取贴图：" + id);
      var reader = readers.next();
      try {
        reader.setInput(input);
        int width = reader.getWidth(0), height = reader.getHeight(0);
        if (width < 1
            || height < 1
            || width > 8192
            || height > 32768
            || (long) width * height > 16_777_216) throw new IOException("贴图尺寸过大：" + id);
        image = reader.read(0);
      } finally {
        reader.dispose();
      }
    }
    int width = image.getWidth(), height = image.getHeight(), first = 0;
    JsonObject animation = json(path + ".mcmeta");
    if (animation != null && animation.has("animation")) {
      JsonObject config = animation.getAsJsonObject("animation");
      width = config.has("width") ? config.get("width").getAsInt() : image.getWidth();
      height = config.has("height") ? config.get("height").getAsInt() : width;
      if (config.has("frames") && !config.getAsJsonArray("frames").isEmpty()) {
        var frame = config.getAsJsonArray("frames").get(0);
        first =
            frame.isJsonObject()
                ? frame.getAsJsonObject().get("index").getAsInt()
                : frame.getAsInt();
      }
    }
    if (width < 1 || height < 1 || width > image.getWidth() || height > image.getHeight())
      throw new IOException("无效动画尺寸：" + id);
    int columns = image.getWidth() / width;
    int frameX = Math.floorMod(first, columns) * width;
    int frameY = Math.max(0, first / columns) * height;
    if (frameY + height > image.getHeight()) throw new IOException("无效动画帧：" + id);
    Texture texture =
        new Texture(width, height, image.getRGB(frameX, frameY, width, height, null, 0, width));
    if (textures.size() >= 4096) throw new IOException("本次渲染贴图数量超过上限。");
    textures.put(id, texture);
    return texture;
  }

  byte[] read(String path, int limit) throws IOException {
    if (path.contains("..") || path.startsWith("/")) throw new IOException("无效资源路径。");
    ZipFile zip = overlay != null && overlay.getEntry(path) != null ? overlay : vanilla;
    var entry = zip.getEntry(path);
    if (entry == null) return null;
    if (entry.getSize() > limit) throw new IOException("资源文件过大：" + path);
    try (InputStream input = zip.getInputStream(entry)) {
      byte[] bytes = input.readNBytes(limit + 1);
      if (bytes.length > limit) throw new IOException("资源文件过大：" + path);
      return bytes;
    }
  }

  static String resource(String type, String id, String suffix) throws IOException {
    if (!id.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+") || id.contains(".."))
      throw new IOException("无效资源标识：" + id);
    int colon = id.indexOf(':');
    return "assets/"
        + (colon < 0 ? "minecraft" : id.substring(0, colon))
        + "/"
        + type
        + "/"
        + (colon < 0 ? id : id.substring(colon + 1))
        + suffix;
  }

  @Override
  public void close() throws IOException {
    try {
      if (overlay != null) overlay.close();
    } finally {
      vanilla.close();
    }
  }

  record Texture(int width, int height, int[] pixels) {
    int sample(double u, double v) {
      int x = Math.clamp((int) Math.floor(u * width), 0, width - 1);
      int y = Math.clamp((int) Math.floor(v * height), 0, height - 1);
      return pixels[y * width + x];
    }
  }
}
