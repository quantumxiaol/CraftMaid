package com.github.quantumxiaol.craftmaid.perception;

import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;

public final class EntityContextCollector {
  public EntityPerceptionSnapshot collect(
      Player player, CraftMaidConfig.EntityPerceptionSettings settings) {
    if (settings == null || !settings.enabled()) {
      return new EntityPerceptionSnapshot(List.of());
    }

    List<Entity> nearby =
        player
            .getNearbyEntities(settings.radiusXz(), settings.radiusY(), settings.radiusXz())
            .stream()
            .sorted(
                Comparator.comparingDouble(
                    entity -> entity.getLocation().distanceSquared(player.getLocation())))
            .limit(Math.max(1, settings.maxEntities()))
            .toList();
    EnumMap<EntityGroupType, EnumMap<EntityType, Integer>> counts =
        new EnumMap<>(EntityGroupType.class);
    for (Entity entity : nearby) {
      EntityGroupType groupType = classify(entity, settings);
      if (groupType == null) {
        continue;
      }
      counts
          .computeIfAbsent(groupType, ignored -> new EnumMap<>(EntityType.class))
          .merge(entity.getType(), 1, Integer::sum);
    }

    List<EntityPerceptionSnapshot.EntityGroup> groups =
        counts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(
                entry ->
                    new EntityPerceptionSnapshot.EntityGroup(
                        entry.getKey().label(), topTypes(entry.getValue())))
            .filter(group -> !group.topTypes().isEmpty())
            .toList();
    return new EntityPerceptionSnapshot(groups);
  }

  private EntityGroupType classify(
      Entity entity, CraftMaidConfig.EntityPerceptionSettings settings) {
    if (entity instanceof Player) {
      return EntityGroupType.PLAYER;
    }
    if (entity instanceof Monster) {
      return EntityGroupType.HOSTILE;
    }
    if ((entity instanceof Villager) || (entity instanceof WanderingTrader)) {
      return EntityGroupType.VILLAGER;
    }
    if ((entity instanceof IronGolem) || (entity instanceof Snowman)) {
      return EntityGroupType.GOLEM;
    }
    if (settings.includeItems()
        && ((entity instanceof Item) || (entity instanceof ExperienceOrb))) {
      return EntityGroupType.ITEM;
    }
    if (entity instanceof Display
        || entity instanceof ArmorStand
        || entity instanceof Boat
        || entity instanceof Minecart) {
      return EntityGroupType.DISPLAY_OR_VEHICLE;
    }
    if (settings.includeNeutral() && isNeutralType(entity.getType())) {
      return EntityGroupType.NEUTRAL;
    }
    if (settings.includePassive() && entity instanceof Animals) {
      return EntityGroupType.PASSIVE;
    }
    return null;
  }

  private boolean isNeutralType(EntityType type) {
    return type == EntityType.BEE
        || type == EntityType.WOLF
        || type == EntityType.PIGLIN
        || type == EntityType.ZOMBIFIED_PIGLIN
        || type == EntityType.ENDERMAN
        || type == EntityType.POLAR_BEAR
        || type == EntityType.LLAMA
        || type == EntityType.TRADER_LLAMA
        || type == EntityType.DOLPHIN;
  }

  private List<EntityPerceptionSnapshot.EntityTypeCount> topTypes(
      EnumMap<EntityType, Integer> counts) {
    return counts.entrySet().stream()
        .sorted(Map.Entry.<EntityType, Integer>comparingByValue(Comparator.reverseOrder()))
        .limit(4)
        .map(
            entry ->
                new EntityPerceptionSnapshot.EntityTypeCount(
                    entry.getKey().getKey().getKey().toLowerCase(Locale.ROOT), entry.getValue()))
        .toList();
  }

  private enum EntityGroupType {
    PLAYER("玩家"),
    HOSTILE("敌对"),
    NEUTRAL("中立"),
    PASSIVE("被动"),
    VILLAGER("村民/商人"),
    GOLEM("傀儡"),
    ITEM("掉落物"),
    DISPLAY_OR_VEHICLE("展示/载具");

    private final String label;

    EntityGroupType(String label) {
      this.label = label;
    }

    String label() {
      return label;
    }
  }
}
