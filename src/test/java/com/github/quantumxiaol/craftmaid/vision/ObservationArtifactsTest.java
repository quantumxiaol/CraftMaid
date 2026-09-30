package com.github.quantumxiaol.craftmaid.vision;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ObservationArtifactsTest {
  @TempDir Path root;

  @Test
  void keepsNewestCaptureWithoutDeletingUnrelatedFiles() throws Exception {
    var writer = new ObservationArtifacts();
    var panorama = panorama();
    Path old =
        writer.write(
            root,
            snapshot(),
            SoftwareRendererTest.SETTINGS,
            panorama,
            1,
            SoftwareRendererTest.budget());
    Path newest =
        writer.write(
            root,
            snapshot(),
            SoftwareRendererTest.SETTINGS,
            panorama,
            1,
            SoftwareRendererTest.budget());
    Path unrelated = Files.createDirectory(root.resolve("my-pictures"));
    Files.writeString(unrelated.resolve("keep.txt"), "keep");
    writer.prune(root, 1, newest);
    assertFalse(Files.exists(old));
    assertTrue(Files.exists(newest.resolve("observation.json")));
    assertEquals("keep", Files.readString(unrelated.resolve("keep.txt")));
  }

  @Test
  void cancellationRemovesPartialOutput() throws Exception {
    AtomicInteger checks = new AtomicInteger();
    assertThrows(
        CancellationException.class,
        () ->
            new ObservationArtifacts()
                .write(
                    root,
                    snapshot(),
                    SoftwareRendererTest.SETTINGS,
                    panorama(),
                    1,
                    new RenderBudget(10, () -> checks.incrementAndGet() >= 3)));
    try (var files = Files.list(root)) {
      assertEquals(0, files.count());
    }
  }

  private SoftwareRenderer.Panorama panorama() {
    return new SoftwareRenderer()
        .render(
            SoftwareRendererTest.scene((x, y, z) -> VoxelScene.Cell.SKY),
            SoftwareRendererTest.ORIGIN,
            SoftwareRendererTest.SETTINGS,
            SoftwareRendererTest.budget());
  }

  static ObservationSnapshot snapshot() {
    return new ObservationSnapshot(
        SoftwareRendererTest.ORIGIN, -64, 320, "NORMAL", 6000, false, Map.of(), 0, List.of(), 0);
  }
}
