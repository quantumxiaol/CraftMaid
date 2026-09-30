package com.github.quantumxiaol.craftmaid.vision;

import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;

/** Writes an inspection bundle. The manifest is written last and marks a complete capture. */
final class ObservationArtifacts {
  private static final DateTimeFormatter NAME =
      DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);
  private static final List<String> FILES =
      List.of("north.png", "east.png", "south.png", "west.png", "overview.png", "observation.json");

  Path write(
      Path root,
      ObservationSnapshot snapshot,
      VisionSettings settings,
      SoftwareRenderer.Panorama panorama,
      long renderNanos,
      RenderBudget budget)
      throws IOException {
    return write(
        root,
        snapshot,
        settings,
        panorama,
        renderNanos,
        budget,
        "synthetic",
        false,
        java.util.Set.of());
  }

  Path write(
      Path root,
      ObservationSnapshot snapshot,
      VisionSettings settings,
      SoftwareRenderer.Panorama panorama,
      long renderNanos,
      RenderBudget budget,
      String resourceVersion,
      boolean resourcePackOverlay,
      java.util.Set<String> fallbackMaterials)
      throws IOException {
    budget.check();
    Files.createDirectories(root);
    Path directory =
        root.resolve("capture-" + NAME.format(Instant.now()) + "-" + UUID.randomUUID());
    Files.createDirectory(directory);
    boolean complete = false;
    try {
      for (CompassView view : CompassView.values()) {
        budget.check();
        if (!ImageIO.write(
            panorama.frames().get(view).image(),
            "png",
            directory.resolve(view.fileName()).toFile())) {
          throw new IOException("PNG encoder unavailable");
        }
      }
      budget.check();
      if (!ImageIO.write(panorama.overview(), "png", directory.resolve("overview.png").toFile())) {
        throw new IOException("PNG encoder unavailable");
      }
      budget.check();
      Map<String, Object> metadata = new LinkedHashMap<>();
      metadata.put("formatVersion", 1);
      metadata.put("renderer", "craftmaid-vanilla-models-v2");
      metadata.put("resourceVersion", resourceVersion);
      metadata.put("resourcePackOverlay", resourcePackOverlay);
      metadata.put("fallbackMaterials", fallbackMaterials);
      metadata.put("origin", snapshot.origin());
      metadata.put("settings", settings);
      metadata.put("environment", snapshot.environment());
      metadata.put("worldTime", snapshot.worldTime());
      metadata.put("raining", snapshot.raining());
      metadata.put("capturedChunks", snapshot.chunks().size());
      metadata.put("missingChunks", snapshot.missingChunks());
      metadata.put("snapshotMillis", snapshot.captureNanos() / 1_000_000.0);
      metadata.put("renderMillis", renderNanos / 1_000_000.0);
      metadata.put("nearbyEntities", snapshot.entities());
      metadata.put(
          "limitations",
          List.of(
              "Vanilla block textures/models and optional resource-pack overlay; CPU lighting approximates the client.",
              "Entities are metadata only, not rendered; nearbyEntities does not imply line of sight (up to 64).",
              "Unloaded regions are grey; views cover the horizontal surroundings, not the entire sphere.",
              "Special block-entity renderers use fallback shapes (listed in fallbackMaterials); signs, skins and particles are not rendered.",
              "Animations use their first configured frame; fluids use flat surfaces; custom server datapack biome colors are not resolved."));
      List<Map<String, Object>> views = new ArrayList<>();
      for (CompassView view : CompassView.values()) {
        SoftwareRenderer.Frame frame = panorama.frames().get(view);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("direction", view.name());
        details.put("file", view.fileName());
        details.put("unknownPixels", frame.unknownPixels());
        Map<String, Integer> top = new LinkedHashMap<>();
        frame.surfaceHits().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(24)
            .forEach(entry -> top.put(entry.getKey(), entry.getValue()));
        details.put("surfaceHitCounts", top);
        views.add(details);
      }
      metadata.put("views", views);
      Files.writeString(
          directory.resolve("observation.json"),
          new GsonBuilder().setPrettyPrinting().create().toJson(metadata));
      complete = true;
      return directory;
    } finally {
      if (!complete) deleteBundle(directory);
    }
  }

  void prune(Path root, int keep, Path newest) throws IOException {
    try (var stream = Files.list(root)) {
      List<Path> old =
          stream
              .filter(path -> !path.equals(newest))
              .filter(
                  path ->
                      path.getFileName()
                          .toString()
                          .matches("capture-\\d{8}-\\d{6}-\\d{3}-[a-f0-9-]{36}"))
              .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
              .filter(
                  path ->
                      Files.isRegularFile(
                          path.resolve("observation.json"), LinkOption.NOFOLLOW_LINKS))
              .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
              .toList();
      for (int i = Math.max(0, keep - 1); i < old.size(); i++) deleteBundle(old.get(i));
    }
  }

  private void deleteBundle(Path directory) throws IOException {
    // Delete only our known artifacts, never recursively delete an unrelated directory's contents.
    for (String file : FILES) Files.deleteIfExists(directory.resolve(file));
    Files.deleteIfExists(directory);
  }
}
