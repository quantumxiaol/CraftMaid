package com.github.quantumxiaol.craftmaid.combat;

import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Tameable;

public final class MaidCombatPolicy {
  private static final Map<String, String> ENTITY_ALIASES =
      Map.of(
          "wolves", "wolf",
          "endermen", "enderman",
          "snowmen", "snow_golem");
  private static final Set<EntityType> ABSOLUTE_DENY = Set.of(EntityType.BEE);
  private static final List<String> LEGACY_MANAGED_TARGETS =
      List.of("monsters", "mobs", "passive_mobs", "bees");

  private final List<String> hostileTargetKeys;
  private final List<String> fightbackTargetKeys;
  private final List<String> avoidTargetKeys;
  private final Set<EntityType> fightbackTypes;
  private final Map<EntityType, String> sentinelKeysByType;

  private MaidCombatPolicy(
      List<String> hostileTargetKeys,
      List<String> fightbackTargetKeys,
      List<String> avoidTargetKeys,
      Set<EntityType> fightbackTypes) {
    this.hostileTargetKeys = hostileTargetKeys;
    this.fightbackTargetKeys = fightbackTargetKeys;
    this.avoidTargetKeys = avoidTargetKeys;
    this.fightbackTypes = fightbackTypes;
    this.sentinelKeysByType =
        buildSentinelKeys(hostileTargetKeys, fightbackTargetKeys, avoidTargetKeys);
  }

  public static MaidCombatPolicy from(CraftMaidConfig.CombatSettings settings) {
    List<String> hostileTargets =
        settings == null ? List.of() : normalizeKeys(settings.hostileTargets());
    List<String> fightbackTargets =
        settings == null ? List.of() : normalizeKeys(settings.fightbackTargets());
    List<String> avoidTargets =
        settings == null ? List.of() : normalizeKeys(settings.avoidTargets());
    return new MaidCombatPolicy(
        hostileTargets, fightbackTargets, avoidTargets, parseTypes(fightbackTargets));
  }

  public boolean isFightbackTarget(Entity entity) {
    if (entity == null) {
      return false;
    }
    EntityType entityType = entity.getType();
    if (ABSOLUTE_DENY.contains(entityType)) {
      return false;
    }
    if (entity instanceof Tameable tameable && tameable.isTamed()) {
      return false;
    }
    return fightbackTypes.contains(entityType);
  }

  public List<String> hostileTargetKeys() {
    return hostileTargetKeys;
  }

  public List<String> fightbackTargetKeys() {
    return fightbackTargetKeys;
  }

  public List<String> avoidTargetKeys() {
    return avoidTargetKeys;
  }

  public String sentinelKeyFor(EntityType entityType) {
    if (entityType == null) {
      return "";
    }
    return sentinelKeysByType.getOrDefault(entityType, entityType.getKey().getKey());
  }

  public List<String> managedTargetKeys() {
    LinkedHashSet<String> keys = new LinkedHashSet<>(LEGACY_MANAGED_TARGETS);
    keys.addAll(hostileTargetKeys);
    keys.addAll(fightbackTargetKeys);
    return List.copyOf(keys);
  }

  public List<String> managedAvoidKeys() {
    LinkedHashSet<String> keys = new LinkedHashSet<>(avoidTargetKeys);
    keys.add("creepers");
    keys.add("bees");
    return List.copyOf(keys);
  }

  private static List<String> normalizeKeys(List<String> rawKeys) {
    if (rawKeys == null) {
      return List.of();
    }
    List<String> keys = new ArrayList<>();
    for (String rawKey : rawKeys) {
      if (rawKey == null || rawKey.isBlank()) {
        continue;
      }
      keys.add(normalizeKey(rawKey));
    }
    return List.copyOf(new LinkedHashSet<>(keys));
  }

  private static Set<EntityType> parseTypes(List<String> keys) {
    Set<EntityType> types = new LinkedHashSet<>();
    for (String key : keys) {
      parseEntityType(key).ifPresent(types::add);
    }
    return Set.copyOf(types);
  }

  @SafeVarargs
  private static Map<EntityType, String> buildSentinelKeys(List<String>... keyGroups) {
    Map<EntityType, String> keysByType = new LinkedHashMap<>();
    for (List<String> keys : keyGroups) {
      if (keys == null) {
        continue;
      }
      for (String key : keys) {
        parseEntityType(key).ifPresent(type -> keysByType.putIfAbsent(type, key));
      }
    }
    return Map.copyOf(keysByType);
  }

  private static Optional<EntityType> parseEntityType(String key) {
    String normalized = ENTITY_ALIASES.getOrDefault(normalizeKey(key), normalizeKey(key));
    List<String> candidates = new ArrayList<>();
    candidates.add(normalized);
    if (normalized.endsWith("s")) {
      candidates.add(normalized.substring(0, normalized.length() - 1));
    }
    if (normalized.endsWith("es")) {
      candidates.add(normalized.substring(0, normalized.length() - 2));
    }
    if (normalized.endsWith("ies")) {
      candidates.add(normalized.substring(0, normalized.length() - 3) + "y");
    }

    for (String candidate : candidates) {
      try {
        return Optional.of(EntityType.valueOf(candidate.toUpperCase(Locale.ROOT)));
      } catch (IllegalArgumentException ignored) {
        // Try the next plural/singular variant.
      }
    }
    return Optional.empty();
  }

  private static String normalizeKey(String key) {
    String normalized = key.trim().toLowerCase(Locale.ROOT);
    if (normalized.startsWith("minecraft:")) {
      normalized = normalized.substring("minecraft:".length());
    }
    return normalized.replace('-', '_').replace(' ', '_');
  }
}
