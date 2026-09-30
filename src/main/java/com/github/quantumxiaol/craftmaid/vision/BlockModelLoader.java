package com.github.quantumxiaol.craftmaid.vision;

import com.github.quantumxiaol.craftmaid.vision.TexturedFace.Vec;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves Java resource-pack blockstates, inherited models, UVs, rotations and multipart models.
 */
final class BlockModelLoader {
  private static final Vec CENTER = new Vec(.5, .5, .5);
  private final ResourcePack pack;
  private final Map<String, Model> models = new HashMap<>();
  private final Map<String, JsonObject> states = new HashMap<>();

  BlockModelLoader(ResourcePack pack) {
    this.pack = pack;
  }

  BlockAppearance load(String serialized) throws IOException {
    int bracket = serialized.indexOf('[');
    String id = bracket < 0 ? serialized : serialized.substring(0, bracket);
    String name = id.substring(id.indexOf(':') + 1).toUpperCase(java.util.Locale.ROOT);
    Map<String, String> properties = properties(serialized);
    if (name.equals("WATER") || name.equals("LAVA")) return fluid(name, properties);
    JsonObject state = states.get(id);
    if (state == null) {
      state = pack.json(ResourcePack.resource("blockstates", id, ".json"));
      if (state == null) return null;
      states.put(id, state);
    }
    List<JsonObject> applications = new ArrayList<>();
    if (state.has("variants")) {
      for (var variant : state.getAsJsonObject("variants").entrySet()) {
        boolean matches = true;
        for (String property : variant.getKey().split(",")) {
          if (property.isBlank()) continue;
          String[] pair = property.split("=", 2);
          if (pair.length != 2 || !pair[1].equals(properties.get(pair[0]))) matches = false;
        }
        if (matches) {
          applications.add(first(variant.getValue()));
          break;
        }
      }
    }
    if (state.has("multipart")) {
      for (var item : state.getAsJsonArray("multipart")) {
        JsonObject part = item.getAsJsonObject();
        if (!part.has("when") || matches(part.getAsJsonObject("when"), properties))
          applications.add(first(part.get("apply")));
      }
    }
    if (applications.isEmpty()) return null;
    List<TexturedFace> faces = new ArrayList<>();
    boolean blend =
        name.contains("STAINED_GLASS")
            || name.equals("TINTED_GLASS")
            || name.equals("ICE")
            || name.equals("SLIME_BLOCK")
            || name.equals("HONEY_BLOCK");
    for (JsonObject application : applications) {
      Model model = model(application.get("model").getAsString(), new java.util.HashSet<>());
      bake(model, application, faces, blend);
    }
    if (faces.size() > 4096) throw new IOException("单个方块模型面数过多：" + id);
    // Chest, bed and other block-entity models may contain only a particle texture.
    if (faces.isEmpty()) return null;
    return new BlockAppearance(name, 0xffffff, 1, List.of(), faces);
  }

  private Model model(String id, java.util.Set<String> ancestry) throws IOException {
    Model cached = models.get(id);
    if (cached != null) return cached;
    if (ancestry.size() >= 32 || !ancestry.add(id)) throw new IOException("模型继承循环：" + id);
    if (id.contains("builtin/")) return new Model(Map.of(), new JsonArray(), true);
    JsonObject json = pack.json(ResourcePack.resource("models", id, ".json"));
    if (json == null) throw new IOException("缺少方块模型：" + id);
    Model parent =
        json.has("parent")
            ? model(json.get("parent").getAsString(), ancestry)
            : new Model(Map.of(), new JsonArray(), false);
    Map<String, TextureRef> textures = new HashMap<>(parent.textures);
    if (json.has("textures"))
      for (var texture : json.getAsJsonObject("textures").entrySet()) {
        JsonElement value = texture.getValue();
        textures.put(
            texture.getKey(),
            value.isJsonObject()
                ? new TextureRef(
                    value.getAsJsonObject().get("sprite").getAsString(),
                    bool(value.getAsJsonObject(), "force_translucent", false))
                : new TextureRef(value.getAsString(), false));
      }
    Model result =
        new Model(
            Map.copyOf(textures),
            json.has("elements") ? json.getAsJsonArray("elements") : parent.elements,
            parent.builtin);
    models.put(id, result);
    ancestry.remove(id);
    return result;
  }

  private void bake(Model model, JsonObject application, List<TexturedFace> result, boolean blend)
      throws IOException {
    double rx = -number(application, "x", 0),
        ry = -number(application, "y", 0),
        rz = -number(application, "z", 0);
    boolean uvlock = bool(application, "uvlock", false);
    for (JsonElement value : model.elements) {
      JsonObject element = value.getAsJsonObject();
      Vec from = vector(element.getAsJsonArray("from")), to = vector(element.getAsJsonArray("to"));
      for (var faceEntry : element.getAsJsonObject("faces").entrySet()) {
        String direction = faceEntry.getKey();
        JsonObject face = faceEntry.getValue().getAsJsonObject();
        String textureId = face.get("texture").getAsString();
        boolean translucent = blend;
        int depth = 0;
        while (textureId.startsWith("#") || model.textures.containsKey(textureId)) {
          if (++depth > 32) throw new IOException("贴图引用循环。");
          TextureRef ref =
              model.textures.get(textureId.startsWith("#") ? textureId.substring(1) : textureId);
          if (ref == null) throw new IOException("模型贴图引用不存在。");
          textureId = ref.id();
          translucent |= ref.translucent();
        }
        Vec[] points = corners(direction, from, to);
        double[] uv = uv(face, direction, from, to);
        if (uvlock) lockUv(uv, direction, rx, ry, rz);
        for (int i = 0; i < points.length; i++) {
          if (element.has("rotation"))
            points[i] = rotateElement(points[i], element.getAsJsonObject("rotation"));
          points[i] = points[i].subtract(CENTER).rotate(rx, ry, rz).add(CENTER);
        }
        result.add(
            new TexturedFace(
                points,
                uv,
                pack.texture(textureId),
                number(face, "tintindex", -1) >= 0,
                bool(element, "shade", true),
                translucent));
      }
    }
  }

  private static Vec rotateElement(Vec point, JsonObject rotation) throws IOException {
    Vec origin = rotation.has("origin") ? vector(rotation.getAsJsonArray("origin")) : CENTER;
    double x = number(rotation, "x", 0), y = number(rotation, "y", 0), z = number(rotation, "z", 0);
    String axis = rotation.has("axis") ? rotation.get("axis").getAsString() : "";
    double angle = number(rotation, "angle", 0);
    if (!axis.isEmpty()) {
      x = axis.equals("x") ? angle : 0;
      y = axis.equals("y") ? angle : 0;
      z = axis.equals("z") ? angle : 0;
    }
    Vec local = point.subtract(origin).rotate(x, y, z);
    if (bool(rotation, "rescale", false) && !axis.isEmpty()) {
      double cosine = Math.abs(Math.cos(Math.toRadians(angle)));
      if (cosine < .01) throw new IOException("无效模型缩放角度。");
      double scale = 1 / cosine;
      local =
          local.scale(
              axis.equals("x") ? 1 : scale,
              axis.equals("y") ? 1 : scale,
              axis.equals("z") ? 1 : scale);
    }
    return local.add(origin);
  }

  private static void lockUv(double[] uv, String direction, double x, double y, double z)
      throws IOException {
    Vec[] original = corners(direction, new Vec(0, 0, 0), new Vec(1, 1, 1));
    Vec u = original[3].subtract(original[0]).rotate(x, y, z);
    Vec v = original[1].subtract(original[0]).rotate(x, y, z);
    String[] names = {"west", "east", "down", "up", "north", "south"};
    Vec[] target =
        corners(names[TexturedFace.faceIndex(v.cross(u))], new Vec(0, 0, 0), new Vec(1, 1, 1));
    Vec targetU = target[3].subtract(target[0]), targetV = target[1].subtract(target[0]);
    int turns = (int) Math.round(Math.atan2(u.dot(targetV), u.dot(targetU)) / (Math.PI / 2));
    rotateUv(uv, -turns);
  }

  private static double[] uv(JsonObject face, String direction, Vec a, Vec b) throws IOException {
    double[] rectangle;
    if (face.has("uv")) {
      var value = face.getAsJsonArray("uv");
      rectangle =
          new double[] {
            value.get(0).getAsDouble(),
            value.get(1).getAsDouble(),
            value.get(2).getAsDouble(),
            value.get(3).getAsDouble()
          };
    } else {
      rectangle =
          switch (direction) {
            case "down" -> new double[] {a.x() * 16, 16 - b.z() * 16, b.x() * 16, 16 - a.z() * 16};
            case "up" -> new double[] {a.x() * 16, a.z() * 16, b.x() * 16, b.z() * 16};
            case "north" ->
                new double[] {16 - b.x() * 16, 16 - b.y() * 16, 16 - a.x() * 16, 16 - a.y() * 16};
            case "south" -> new double[] {a.x() * 16, 16 - b.y() * 16, b.x() * 16, 16 - a.y() * 16};
            case "west" -> new double[] {a.z() * 16, 16 - b.y() * 16, b.z() * 16, 16 - a.y() * 16};
            case "east" ->
                new double[] {16 - b.z() * 16, 16 - b.y() * 16, 16 - a.z() * 16, 16 - a.y() * 16};
            default -> throw new IOException("无效模型面：" + direction);
          };
    }
    double[] uv = {
      rectangle[0],
      rectangle[1],
      rectangle[0],
      rectangle[3],
      rectangle[2],
      rectangle[3],
      rectangle[2],
      rectangle[1]
    };
    rotateUv(uv, (int) number(face, "rotation", 0) / 90);
    return uv;
  }

  private static void rotateUv(double[] uv, int turns) {
    double[] copy = uv.clone();
    for (int i = 0; i < 4; i++) {
      int index = Math.floorMod(i + turns, 4);
      uv[i * 2] = copy[index * 2];
      uv[i * 2 + 1] = copy[index * 2 + 1];
    }
  }

  static Vec[] corners(String direction, Vec a, Vec b) throws IOException {
    double x = a.x(), y = a.y(), z = a.z(), X = b.x(), Y = b.y(), Z = b.z();
    return switch (direction) {
      case "north" ->
          new Vec[] {new Vec(X, Y, z), new Vec(X, y, z), new Vec(x, y, z), new Vec(x, Y, z)};
      case "south" ->
          new Vec[] {new Vec(x, Y, Z), new Vec(x, y, Z), new Vec(X, y, Z), new Vec(X, Y, Z)};
      case "west" ->
          new Vec[] {new Vec(x, Y, z), new Vec(x, y, z), new Vec(x, y, Z), new Vec(x, Y, Z)};
      case "east" ->
          new Vec[] {new Vec(X, Y, Z), new Vec(X, y, Z), new Vec(X, y, z), new Vec(X, Y, z)};
      case "up" ->
          new Vec[] {new Vec(x, Y, z), new Vec(x, Y, Z), new Vec(X, Y, Z), new Vec(X, Y, z)};
      case "down" ->
          new Vec[] {new Vec(x, y, Z), new Vec(x, y, z), new Vec(X, y, z), new Vec(X, y, Z)};
      default -> throw new IOException("无效模型面：" + direction);
    };
  }

  private BlockAppearance fluid(String name, Map<String, String> properties) throws IOException {
    int level = Integer.parseInt(properties.getOrDefault("level", "0"));
    double height = level >= 8 ? 1 : (8 - level) / 9.0;
    var texture =
        pack.texture("minecraft:block/" + name.toLowerCase(java.util.Locale.ROOT) + "_still");
    List<TexturedFace> faces = new ArrayList<>();
    for (String direction : List.of("up", "down", "north", "south", "west", "east")) {
      faces.add(
          new TexturedFace(
              corners(direction, new Vec(0, 0, 0), new Vec(1, height, 1)),
              new double[] {0, 0, 0, 16, 16, 16, 16, 0},
              texture,
              name.equals("WATER"),
              true,
              name.equals("WATER")));
    }
    return new BlockAppearance(name, 0xffffff, name.equals("WATER") ? .65 : 1, List.of(), faces);
  }

  static Map<String, String> properties(String state) {
    Map<String, String> result = new HashMap<>();
    int bracket = state.indexOf('[');
    if (bracket >= 0 && state.endsWith("]")) {
      for (String property : state.substring(bracket + 1, state.length() - 1).split(",")) {
        String[] pair = property.split("=", 2);
        if (pair.length == 2) result.put(pair[0], pair[1]);
      }
    }
    return result;
  }

  private static boolean matches(JsonObject condition, Map<String, String> properties) {
    for (var entry : condition.entrySet()) {
      if (entry.getKey().equals("OR")) {
        boolean any = false;
        for (var part : entry.getValue().getAsJsonArray())
          any |= matches(part.getAsJsonObject(), properties);
        if (!any) return false;
      } else if (entry.getKey().equals("AND")) {
        for (var part : entry.getValue().getAsJsonArray())
          if (!matches(part.getAsJsonObject(), properties)) return false;
      } else {
        String desired = entry.getValue().getAsString();
        boolean negate = desired.startsWith("!");
        boolean found =
            java.util.Arrays.asList((negate ? desired.substring(1) : desired).split("\\|"))
                .contains(properties.get(entry.getKey()));
        if (found == negate) return false;
      }
    }
    return true;
  }

  private static JsonObject first(JsonElement application) {
    return (application.isJsonArray() ? application.getAsJsonArray().get(0) : application)
        .getAsJsonObject();
  }

  private static Vec vector(JsonArray array) {
    return new Vec(
        array.get(0).getAsDouble() / 16,
        array.get(1).getAsDouble() / 16,
        array.get(2).getAsDouble() / 16);
  }

  private static double number(JsonObject json, String key, double fallback) {
    return json.has(key) ? json.get(key).getAsDouble() : fallback;
  }

  private static boolean bool(JsonObject json, String key, boolean fallback) {
    return json.has(key) ? json.get(key).getAsBoolean() : fallback;
  }

  private record TextureRef(String id, boolean translucent) {}

  private record Model(Map<String, TextureRef> textures, JsonArray elements, boolean builtin) {}
}
