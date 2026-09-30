package com.github.quantumxiaol.craftmaid.vision;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VanillaAssetsTest {
  @TempDir Path directory;

  @Test
  void explicitlyConfiguredLocalClientWorksWithoutDownloading() throws Exception {
    Path local = TestResourcePack.create(directory.resolve("local.jar"), Map.of());
    assertEquals(local, client(new VisionAssetSettings(false, "local.jar", "")));
  }

  @Test
  void verifiedCacheIsReusableOfflineAndCorruptionIsRejected() throws Exception {
    Path cache = directory.resolve("vision/assets/26.1.2");
    Files.createDirectories(cache);
    Path jar = TestResourcePack.create(cache.resolve("client.jar"), Map.of());
    String sha1 =
        HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(jar)));
    Files.writeString(cache.resolve("client.sha1"), sha1);
    var settings = new VisionAssetSettings(false, "", "");
    assertEquals(jar, client(settings));
    Files.writeString(jar, "corrupted");
    assertThrows(IOException.class, () -> client(settings));
  }

  @Test
  void missingOfflineResourcesExplainHowToProvideClient() {
    IOException error =
        assertThrows(IOException.class, () -> client(new VisionAssetSettings(false, "", "")));
    assertTrue(error.getMessage().contains("client_jar"));
  }

  private Path client(VisionAssetSettings settings) throws IOException {
    return VanillaAssets.client(
        directory,
        "26.1.2",
        settings,
        new RenderBudget(10, () -> false),
        message -> fail("Unexpected download: " + message));
  }
}
