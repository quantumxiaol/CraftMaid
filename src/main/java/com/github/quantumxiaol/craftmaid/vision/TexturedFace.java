package com.github.quantumxiaol.craftmaid.vision;

/** A baked model quad. Texture holes are tested before selecting the nearest visible surface. */
final class TexturedFace {
  final Vec origin, edgeV, edgeU, normal;
  private final double vv, uu, vu, inverse;
  private final double[] uv;
  private final ResourcePack.Texture texture;
  private final boolean tinted, shade, blend;

  TexturedFace(
      Vec[] corners,
      double[] uv,
      ResourcePack.Texture texture,
      boolean tinted,
      boolean shade,
      boolean blend) {
    origin = corners[0];
    edgeV = corners[1].subtract(origin);
    edgeU = corners[3].subtract(origin);
    normal = edgeV.cross(edgeU).unit();
    vv = edgeV.dot(edgeV);
    uu = edgeU.dot(edgeU);
    vu = edgeV.dot(edgeU);
    inverse = 1 / (vv * uu - vu * vu);
    this.uv = uv.clone();
    this.texture = texture;
    this.tinted = tinted;
    this.shade = shade;
    this.blend = blend;
  }

  Surface intersect(
      double ox, double oy, double oz, CompassView.Ray ray, double min, double max, int tint) {
    double denominator = normal.x * ray.x() + normal.y * ray.y() + normal.z * ray.z();
    if (denominator >= -1e-9 || !Double.isFinite(inverse)) return null;
    double distance =
        (normal.x * (origin.x - ox) + normal.y * (origin.y - oy) + normal.z * (origin.z - oz))
            / denominator;
    if (distance < min || distance > max) return null;
    double dx = ox + ray.x() * distance - origin.x;
    double dy = oy + ray.y() * distance - origin.y;
    double dz = oz + ray.z() * distance - origin.z;
    double dv = dx * edgeV.x + dy * edgeV.y + dz * edgeV.z;
    double du = dx * edgeU.x + dy * edgeU.y + dz * edgeU.z;
    double v = (dv * uu - du * vu) * inverse;
    double u = (du * vv - dv * vu) * inverse;
    if (u < -1e-7 || u > 1.0000001 || v < -1e-7 || v > 1.0000001) return null;
    double tu = uv[0] + (uv[6] - uv[0]) * u + (uv[2] - uv[0]) * v;
    double tv = uv[1] + (uv[7] - uv[1]) * u + (uv[3] - uv[1]) * v;
    int argb = texture.sample(tu / 16, tv / 16);
    int alpha = argb >>> 24;
    if (alpha < 26) return null;
    int color = argb & 0xffffff;
    if (tinted) color = multiply(color, tint);
    return new Surface(distance, faceIndex(normal), color, blend ? alpha / 255.0 : 1, shade);
  }

  static int multiply(int color, int tint) {
    return ((((color >> 16) & 255) * ((tint >> 16) & 255) / 255) << 16)
        | ((((color >> 8) & 255) * ((tint >> 8) & 255) / 255) << 8)
        | ((color & 255) * (tint & 255) / 255);
  }

  static int faceIndex(Vec normal) {
    if (Math.abs(normal.y) >= Math.abs(normal.x) && Math.abs(normal.y) >= Math.abs(normal.z))
      return normal.y > 0 ? 3 : 2;
    if (Math.abs(normal.x) >= Math.abs(normal.z)) return normal.x > 0 ? 1 : 0;
    return normal.z > 0 ? 5 : 4;
  }

  record Surface(double distance, int face, int color, double opacity, boolean shade) {}

  record Vec(double x, double y, double z) {
    Vec subtract(Vec other) {
      return new Vec(x - other.x, y - other.y, z - other.z);
    }

    Vec add(Vec other) {
      return new Vec(x + other.x, y + other.y, z + other.z);
    }

    Vec scale(double a, double b, double c) {
      return new Vec(x * a, y * b, z * c);
    }

    double dot(Vec other) {
      return x * other.x + y * other.y + z * other.z;
    }

    Vec cross(Vec other) {
      return new Vec(
          y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
    }

    Vec unit() {
      double length = Math.sqrt(dot(this));
      return length < 1e-12 ? new Vec(0, 0, 0) : scale(1 / length, 1 / length, 1 / length);
    }

    Vec rotate(double xDegrees, double yDegrees, double zDegrees) {
      double a = Math.toRadians(xDegrees),
          b = Math.toRadians(yDegrees),
          c = Math.toRadians(zDegrees);
      double y1 = y * Math.cos(a) - z * Math.sin(a), z1 = y * Math.sin(a) + z * Math.cos(a);
      double x2 = x * Math.cos(b) + z1 * Math.sin(b), z2 = -x * Math.sin(b) + z1 * Math.cos(b);
      return new Vec(x2 * Math.cos(c) - y1 * Math.sin(c), x2 * Math.sin(c) + y1 * Math.cos(c), z2);
    }
  }
}
