package com.github.quantumxiaol.craftmaid.vision;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ResourcePackTest {
  @TempDir Path directory;

  @Test
  void overlayReplacesMatchingFilesAndKeepsBaseClientIdentity() throws Exception {
    Path base = TestResourcePack.create(directory.resolve("client.jar"), Map.of());
    byte[] green;
    try (var source = new ResourcePack(base, null)) {
      green = source.read("assets/minecraft/textures/block/four.png", 1024);
    }
    Path overlay =
        zip(
            "pack.zip",
            Map.of(
                "version.json",
                "{\"id\":\"fake\"}".getBytes(StandardCharsets.UTF_8),
                "assets/minecraft/textures/block/stone.png",
                green));
    try (var pack = new ResourcePack(base, overlay)) {
      assertEquals("26.1.2", pack.clientVersion());
      assertEquals(0xff00ff00, pack.texture("block/stone").sample(.75, .25));
      assertEquals(0xffffffff, pack.texture("block/white").sample(.75, .25));
    }
  }

  @Test
  void configuredAnimationFrameIsCroppedInsteadOfStretchingTheStrip() throws Exception {
    Path base =
        TestResourcePack.create(
            directory.resolve("client.jar"),
            Map.of(
                "textures/block/four.png.mcmeta",
                "{\"animation\":{\"width\":2,\"height\":1,\"frames\":[1,0]}}"));
    try (var pack = new ResourcePack(base, null)) {
      var texture = pack.texture("block/four");
      assertEquals(2, texture.width());
      assertEquals(1, texture.height());
      assertEquals(0xff0000ff, texture.sample(.25, .5));
      assertEquals(0xffffff00, texture.sample(.75, .5));
    }
  }

  @Test
  void serverArchiveWithoutVanillaTexturesIsRejected() throws Exception {
    Path server = zip("server.jar", Map.of("version.json", "{}".getBytes(StandardCharsets.UTF_8)));
    IOException error = assertThrows(IOException.class, () -> new ResourcePack(server, null));
    assertTrue(error.getMessage().contains("客户端"));
  }

  private Path zip(String name, Map<String, byte[]> entries) throws IOException {
    Path path = directory.resolve(name);
    try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
      for (var entry : entries.entrySet()) {
        zip.putNextEntry(new ZipEntry(entry.getKey()));
        zip.write(entry.getValue());
        zip.closeEntry();
      }
    }
    return path;
  }
}
