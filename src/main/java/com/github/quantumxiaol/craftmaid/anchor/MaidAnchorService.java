package com.github.quantumxiaol.craftmaid.anchor;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public final class MaidAnchorService {
  public static final String DEFAULT_NAME = "default";

  private static final int VERSION = 1;
  private static final int MAX_REGION_EDGE_PARTICLES = 2500;
  private static final Pattern NAME_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");

  private final CraftMaid plugin;
  private final File file;
  private YamlConfiguration data;

  public MaidAnchorService(CraftMaid plugin) {
    this.plugin = plugin;
    this.file = new File(plugin.getDataFolder(), "anchors.yml");
  }

  public void load() {
    if (!plugin.getDataFolder().exists()) {
      plugin.getDataFolder().mkdirs();
    }

    data = YamlConfiguration.loadConfiguration(file);
    if (!data.contains("version")) {
      data.set("version", VERSION);
      save();
    }
  }

  public AnchorOperationResult setAnchor(AnchorType type, String name, Location location) {
    if (type == null) {
      return AnchorOperationResult.failure("未知 anchor 类型。");
    }
    Optional<String> normalizedName = normalizeName(name);
    if (normalizedName.isEmpty()) {
      return invalidNameResult();
    }
    if (location == null || location.getWorld() == null) {
      return AnchorOperationResult.failure("当前位置没有有效世界，无法设置 anchor。");
    }

    MaidAnchor anchor = MaidAnchor.fromLocation(location);
    writeAnchor(anchorPath(type, normalizedName.get()), anchor);
    save();
    return AnchorOperationResult.success(
        "已设置 anchor " + type.key() + "/" + normalizedName.get() + ": " + anchor.shortText());
  }

  public Optional<MaidAnchor> getAnchor(AnchorType type, String name) {
    if (type == null) {
      return Optional.empty();
    }
    Optional<String> normalizedName = normalizeName(name);
    if (normalizedName.isEmpty()) {
      return Optional.empty();
    }
    return readAnchor(anchorPath(type, normalizedName.get()));
  }

  public Location getLocationOrNull(AnchorType type, String name) {
    return getAnchor(type, name).map(MaidAnchor::toLocationOrNull).orElse(null);
  }

  public Location getHomeLocation() {
    return getLocationOrNull(AnchorType.HOME, DEFAULT_NAME);
  }

  public AnchorOperationResult removeAnchor(AnchorType type, String name) {
    if (type == null) {
      return AnchorOperationResult.failure("未知 anchor 类型。");
    }
    Optional<String> normalizedName = normalizeName(name);
    if (normalizedName.isEmpty()) {
      return invalidNameResult();
    }

    data.set(anchorPath(type, normalizedName.get()), null);
    save();
    return AnchorOperationResult.success(
        "已移除 anchor " + type.key() + "/" + normalizedName.get() + "。");
  }

  public AnchorOperationResult setRegionCorner(
      RegionType type, String name, RegionCorner corner, Location location) {
    if (type == null) {
      return AnchorOperationResult.failure("未知 region 类型。");
    }
    if (corner == null) {
      return AnchorOperationResult.failure("未知 region 角点，请使用 pos1 或 pos2。");
    }
    Optional<String> normalizedName = normalizeName(name);
    if (normalizedName.isEmpty()) {
      return invalidNameResult();
    }
    if (location == null || location.getWorld() == null) {
      return AnchorOperationResult.failure("当前位置没有有效世界，无法设置 region。");
    }

    MaidAnchor cornerAnchor = MaidAnchor.fromLocation(location);
    AnchorOperationResult validation =
        validateRegionWorld(type, normalizedName.get(), corner, cornerAnchor);
    if (!validation.success()) {
      return validation;
    }

    writeAnchor(regionCornerPath(type, normalizedName.get(), corner), cornerAnchor);
    save();
    return AnchorOperationResult.success(
        "已设置 region "
            + type.key()
            + "/"
            + normalizedName.get()
            + "/"
            + corner.key()
            + ": "
            + cornerAnchor.shortText());
  }

  public Optional<AnchorRegion> getRegion(RegionType type, String name) {
    if (type == null) {
      return Optional.empty();
    }
    Optional<String> normalizedName = normalizeName(name);
    if (normalizedName.isEmpty()) {
      return Optional.empty();
    }

    Optional<MaidAnchor> first =
        readAnchor(regionCornerPath(type, normalizedName.get(), RegionCorner.POS1));
    Optional<MaidAnchor> second =
        readAnchor(regionCornerPath(type, normalizedName.get(), RegionCorner.POS2));
    if (first.isEmpty() || second.isEmpty()) {
      return Optional.empty();
    }
    if (!first.get().worldName().equals(second.get().worldName())) {
      return Optional.empty();
    }
    return Optional.of(AnchorRegion.from(first.get(), second.get()));
  }

  public AnchorOperationResult removeRegion(RegionType type, String name) {
    if (type == null) {
      return AnchorOperationResult.failure("未知 region 类型。");
    }
    Optional<String> normalizedName = normalizeName(name);
    if (normalizedName.isEmpty()) {
      return invalidNameResult();
    }

    data.set(regionPath(type, normalizedName.get()), null);
    save();
    return AnchorOperationResult.success(
        "已移除 region " + type.key() + "/" + normalizedName.get() + "。");
  }

  public AnchorOperationResult showRegion(Player player, RegionType type, String name) {
    if (player == null) {
      return AnchorOperationResult.failure("只有玩家可以显示 region。");
    }
    Optional<String> normalizedName = normalizeName(name);
    if (normalizedName.isEmpty()) {
      return invalidNameResult();
    }

    Optional<AnchorRegion> region = getRegion(type, normalizedName.get());
    if (region.isEmpty()) {
      return AnchorOperationResult.failure(
          "region 未完整设置: " + type.key() + "/" + normalizedName.get());
    }

    AnchorRegion anchorRegion = region.get();
    World world = plugin.getServer().getWorld(anchorRegion.worldName());
    if (world == null) {
      return AnchorOperationResult.failure("region 所在世界未加载: " + anchorRegion.worldName());
    }
    if (!player.getWorld().equals(world)) {
      return AnchorOperationResult.failure("你需要和 region 在同一个世界才能显示边框。");
    }

    int edgeParticleCount = edgeParticleCount(anchorRegion);
    if (edgeParticleCount > MAX_REGION_EDGE_PARTICLES) {
      return AnchorOperationResult.failure(
          "region 边框太大，预计粒子点 " + edgeParticleCount + "，上限 " + MAX_REGION_EDGE_PARTICLES + "。");
    }

    new BukkitRunnable() {
      private int remaining = 8;

      @Override
      public void run() {
        if (remaining <= 0 || !player.isOnline()) {
          cancel();
          return;
        }
        spawnRegionEdges(player, anchorRegion);
        remaining--;
      }
    }.runTaskTimer(plugin, 0L, 10L);

    return AnchorOperationResult.success(
        "正在显示 region " + type.key() + "/" + normalizedName.get() + ": " + anchorRegion.shortText());
  }

  public List<String> anchorStatusLines() {
    List<String> lines = new ArrayList<>();
    for (AnchorType type : AnchorType.values()) {
      ConfigurationSection section = data.getConfigurationSection("anchors." + type.key());
      if (section == null || section.getKeys(false).isEmpty()) {
        lines.add("anchor " + type.key() + ": 未设置");
        continue;
      }
      for (String name : section.getKeys(false).stream().sorted().toList()) {
        String label = "anchor " + type.key() + "/" + name;
        lines.add(
            readAnchor(anchorPath(type, name))
                .map(anchor -> label + ": " + anchor.shortText())
                .orElse(label + ": 无效"));
      }
    }
    return lines;
  }

  public List<String> regionStatusLines() {
    List<String> lines = new ArrayList<>();
    for (RegionType type : RegionType.values()) {
      ConfigurationSection section = data.getConfigurationSection("regions." + type.key());
      if (section == null || section.getKeys(false).isEmpty()) {
        lines.add("region " + type.key() + ": 未设置");
        continue;
      }
      for (String name : section.getKeys(false).stream().sorted().toList()) {
        String label = "region " + type.key() + "/" + name;
        Optional<AnchorRegion> region = getRegion(type, name);
        if (region.isPresent()) {
          lines.add(label + ": " + region.get().shortText());
        } else {
          lines.add(label + ": 未完整设置");
        }
      }
    }
    return lines;
  }

  public String homeStatusLine() {
    return getAnchor(AnchorType.HOME, DEFAULT_NAME)
        .map(anchor -> "anchor home/default: " + anchor.shortText())
        .orElse("anchor home/default: 未设置");
  }

  public String defaultFarmStatusLine() {
    return getRegion(RegionType.FARM, DEFAULT_NAME)
        .map(region -> "region farm/default: " + region.shortText())
        .orElse("region farm/default: 未完整设置");
  }

  public String defaultPondStatusLine() {
    return getRegion(RegionType.POND, DEFAULT_NAME)
        .map(region -> "region pond/default: " + region.shortText())
        .orElse("region pond/default: 未完整设置");
  }

  public String defaultRedstoneStatusLine() {
    return getRegion(RegionType.REDSTONE, DEFAULT_NAME)
        .map(region -> "region redstone/default: " + region.shortText())
        .orElse("region redstone/default: 未完整设置");
  }

  public List<String> anchorNames(AnchorType type) {
    if (type == null) {
      return List.of();
    }
    ConfigurationSection section = data.getConfigurationSection("anchors." + type.key());
    if (section == null) {
      return List.of();
    }
    return section.getKeys(false).stream().sorted().toList();
  }

  public List<String> regionNames(RegionType type) {
    if (type == null) {
      return List.of();
    }
    ConfigurationSection section = data.getConfigurationSection("regions." + type.key());
    if (section == null) {
      return List.of();
    }
    return section.getKeys(false).stream().sorted().toList();
  }

  private AnchorOperationResult validateRegionWorld(
      RegionType type, String name, RegionCorner corner, MaidAnchor cornerAnchor) {
    RegionCorner peerCorner = corner == RegionCorner.POS1 ? RegionCorner.POS2 : RegionCorner.POS1;
    Optional<MaidAnchor> peer = readAnchor(regionCornerPath(type, name, peerCorner));
    if (peer.isPresent() && !peer.get().worldName().equals(cornerAnchor.worldName())) {
      return AnchorOperationResult.failure("region 两个角点必须在同一个世界。");
    }
    return AnchorOperationResult.success("");
  }

  private Optional<MaidAnchor> readAnchor(String path) {
    ConfigurationSection section = data.getConfigurationSection(path);
    if (section == null) {
      return Optional.empty();
    }

    String worldName = section.getString("world", "");
    if (worldName == null || worldName.isBlank()) {
      return Optional.empty();
    }

    return Optional.of(
        new MaidAnchor(
            worldName,
            section.getDouble("x"),
            section.getDouble("y"),
            section.getDouble("z"),
            (float) section.getDouble("yaw"),
            (float) section.getDouble("pitch")));
  }

  private void writeAnchor(String path, MaidAnchor anchor) {
    data.set(path + ".world", anchor.worldName());
    data.set(path + ".x", anchor.x());
    data.set(path + ".y", anchor.y());
    data.set(path + ".z", anchor.z());
    data.set(path + ".yaw", anchor.yaw());
    data.set(path + ".pitch", anchor.pitch());
  }

  private String anchorPath(AnchorType type, String name) {
    return "anchors." + type.key() + "." + name;
  }

  private String regionPath(RegionType type, String name) {
    return "regions." + type.key() + "." + name;
  }

  private String regionCornerPath(RegionType type, String name, RegionCorner corner) {
    return regionPath(type, name) + "." + corner.key();
  }

  private Optional<String> normalizeName(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    String normalized = name.trim().toLowerCase(Locale.ROOT);
    return NAME_PATTERN.matcher(normalized).matches() ? Optional.of(normalized) : Optional.empty();
  }

  private AnchorOperationResult invalidNameResult() {
    return AnchorOperationResult.failure("名称只能使用 1-32 位小写字母、数字、下划线或连字符。");
  }

  private int edgeParticleCount(AnchorRegion region) {
    int sizeX = region.maxX() - region.minX() + 2;
    int sizeY = region.maxY() - region.minY() + 2;
    int sizeZ = region.maxZ() - region.minZ() + 2;
    return 4 * (sizeX + sizeY + sizeZ);
  }

  private void spawnRegionEdges(Player player, AnchorRegion region) {
    double minX = region.minX();
    double minY = region.minY();
    double minZ = region.minZ();
    double maxX = region.maxX() + 1.0;
    double maxY = region.maxY() + 1.0;
    double maxZ = region.maxZ() + 1.0;

    for (double x = minX; x <= maxX; x += 1.0) {
      spawnParticle(player, x, minY, minZ);
      spawnParticle(player, x, minY, maxZ);
      spawnParticle(player, x, maxY, minZ);
      spawnParticle(player, x, maxY, maxZ);
    }
    for (double y = minY; y <= maxY; y += 1.0) {
      spawnParticle(player, minX, y, minZ);
      spawnParticle(player, minX, y, maxZ);
      spawnParticle(player, maxX, y, minZ);
      spawnParticle(player, maxX, y, maxZ);
    }
    for (double z = minZ; z <= maxZ; z += 1.0) {
      spawnParticle(player, minX, minY, z);
      spawnParticle(player, minX, maxY, z);
      spawnParticle(player, maxX, minY, z);
      spawnParticle(player, maxX, maxY, z);
    }
  }

  private void spawnParticle(Player player, double x, double y, double z) {
    player.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
  }

  private void save() {
    try {
      data.save(file);
    } catch (IOException ex) {
      plugin.getLogger().warning("保存 anchors.yml 失败: " + ex.getMessage());
    }
  }

  public record AnchorOperationResult(boolean success, String message) {
    public static AnchorOperationResult success(String message) {
      return new AnchorOperationResult(true, message);
    }

    public static AnchorOperationResult failure(String message) {
      return new AnchorOperationResult(false, message);
    }
  }
}
