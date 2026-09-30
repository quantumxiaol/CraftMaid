package com.github.quantumxiaol.craftmaid.vision;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.function.Consumer;

/** Downloads data only, never loads or executes client classes. No Mojang assets are in our jar. */
final class VanillaAssets {
  private static final URI MANIFEST =
      URI.create("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
  private static final Set<String> HOSTS =
      Set.of(
          "piston-meta.mojang.com",
          "piston-data.mojang.com",
          "launchermeta.mojang.com",
          "launcher.mojang.com");
  private static final long MAX_CLIENT_BYTES = 256L * 1024 * 1024;

  static Path client(
      Path dataFolder,
      String version,
      VisionAssetSettings settings,
      RenderBudget budget,
      Consumer<String> progress)
      throws IOException {
    if (!settings.clientJar().isBlank()) {
      Path local = resolve(dataFolder, settings.clientJar());
      if (!Files.isRegularFile(local)) throw new IOException("找不到原版客户端资源：" + local);
      return local;
    }
    if (version == null || !version.matches("[A-Za-z0-9._-]+"))
      throw new IOException("无法确定 Minecraft 版本。");
    Path directory = dataFolder.resolve("vision/assets").resolve(version);
    Path jar = directory.resolve("client.jar");
    Path checksum = directory.resolve("client.sha1");
    if (Files.isRegularFile(jar) && Files.isRegularFile(checksum)) {
      String expected = Files.readString(checksum).trim();
      if (expected.matches("[a-f0-9]{40}") && expected.equals(sha1(jar, budget))) return jar;
    }
    if (!settings.autoDownload())
      throw new AssetsUnavailableException(
          "原版资源尚未准备，请执行 /maid vision prepare，或设置 perception.vision.assets.client_jar。");
    progress.accept("正在获取 Minecraft " + version + " 官方资源清单；完成后会缓存。");
    JsonObject manifest = json(MANIFEST, budget);
    JsonObject entry = null;
    for (var element : manifest.getAsJsonArray("versions")) {
      if (version.equals(element.getAsJsonObject().get("id").getAsString())) {
        entry = element.getAsJsonObject();
        break;
      }
    }
    if (entry == null) throw new IOException("官方资源清单中找不到版本 " + version + "，请指定本地 client_jar。");
    JsonObject metadata = json(URI.create(entry.get("url").getAsString()), budget);
    JsonObject client = metadata.getAsJsonObject("downloads").getAsJsonObject("client");
    long size = client.get("size").getAsLong();
    String expected = client.get("sha1").getAsString();
    if (size < 1 || size > MAX_CLIENT_BYTES || !expected.matches("[a-f0-9]{40}"))
      throw new IOException("官方客户端资源信息无效。");
    progress.accept("原版资源下载地址：" + client.get("url").getAsString());
    progress.accept(downloadProgress(0, size));
    Files.createDirectories(directory);
    Path temporary = Files.createTempFile(directory, "download-", ".part");
    try {
      HttpURLConnection connection = connect(URI.create(client.get("url").getAsString()));
      try (InputStream input = connection.getInputStream();
          var output = Files.newOutputStream(temporary)) {
        byte[] buffer = new byte[65536];
        long count = 0;
        long lastProgress = System.nanoTime();
        int read;
        while ((read = input.read(buffer)) != -1) {
          budget.check();
          count += read;
          if (count > size) throw new IOException("原版资源下载超出预期大小。");
          output.write(buffer, 0, read);
          if (count == size || System.nanoTime() - lastProgress >= 5_000_000_000L) {
            progress.accept(downloadProgress(count, size));
            lastProgress = System.nanoTime();
          }
        }
        if (count != size) throw new IOException("原版资源下载不完整。");
      } finally {
        connection.disconnect();
      }
      progress.accept("下载完成，正在校验 SHA-1…");
      if (!expected.equals(sha1(temporary, budget))) throw new IOException("原版资源 SHA-1 校验失败，请重试。");
      budget.check();
      Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING);
      Files.writeString(checksum, expected);
      progress.accept("Minecraft " + version + " 原版资源已缓存。");
      return jar;
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  static String downloadProgress(long downloaded, long total) {
    return String.format(
        java.util.Locale.ROOT,
        "原版资源下载：%.1f / %.1f MB（%.0f%%）",
        downloaded / 1_000_000.0,
        total / 1_000_000.0,
        total > 0 ? downloaded * 100.0 / total : 0);
  }

  static final class AssetsUnavailableException extends IOException {
    AssetsUnavailableException(String message) {
      super(message);
    }
  }

  static Path resolve(Path dataFolder, String configured) {
    Path path = Path.of(configured);
    return path.isAbsolute() ? path : dataFolder.resolve(path).normalize();
  }

  private static JsonObject json(URI uri, RenderBudget budget) throws IOException {
    budget.check();
    HttpURLConnection connection = connect(uri);
    try (InputStream input = connection.getInputStream()) {
      byte[] bytes = input.readNBytes(4 * 1024 * 1024 + 1);
      budget.check();
      if (bytes.length > 4 * 1024 * 1024) throw new IOException("资源清单过大。");
      return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
    } finally {
      connection.disconnect();
    }
  }

  private static HttpURLConnection connect(URI uri) throws IOException {
    if (!"https".equals(uri.getScheme()) || !HOSTS.contains(uri.getHost()))
      throw new IOException("非官方资源下载地址。");
    HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(10_000);
    connection.setReadTimeout(10_000);
    connection.setRequestProperty("User-Agent", "CraftMaid/vision");
    try {
      int status = connection.getResponseCode();
      if (status != 200) throw new IOException("原版资源下载 HTTP " + status + "；可通过 client_jar 使用本地文件。");
      return connection;
    } catch (IOException exception) {
      connection.disconnect();
      throw exception;
    }
  }

  private static String sha1(Path path, RenderBudget budget) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      try (InputStream input = Files.newInputStream(path)) {
        byte[] buffer = new byte[65536];
        int read;
        while ((read = input.read(buffer)) != -1) {
          budget.check();
          digest.update(buffer, 0, read);
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
