package com.github.quantumxiaol.craftmaid.inventory;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public final class MaidInventoryService {
  private final CraftMaid plugin;

  public MaidInventoryService(CraftMaid plugin) {
    this.plugin = plugin;
  }

  public InventoryInsertResult addItem(ItemStack item) {
    if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
      return InventoryInsertResult.success("没有需要放入女仆背包的物品。");
    }
    return addAllOrNothing(List.of(item));
  }

  public boolean canFitAll(Collection<ItemStack> items) {
    List<ItemStack> normalized = normalizedItems(items);
    return normalized.isEmpty() || plugin.getMaidNpcService().canFitInventoryItems(normalized);
  }

  public InventoryInsertResult addAllOrNothing(Collection<ItemStack> items) {
    List<ItemStack> normalized = normalizedItems(items);
    if (normalized.isEmpty()) {
      return InventoryInsertResult.success("没有需要放入女仆背包的物品。");
    }
    return plugin.getMaidNpcService().addInventoryItemsAllOrNothing(normalized);
  }

  public String summaryLine() {
    List<ItemStack> contents = plugin.getMaidNpcService().getInventoryContents();
    if (contents.isEmpty()) {
      return "背包：不可用或为空。";
    }

    int totalSlots = contents.size();
    int usedSlots = 0;
    Map<Material, Integer> totals = new LinkedHashMap<>();
    for (ItemStack item : contents) {
      if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
        continue;
      }
      usedSlots++;
      totals.merge(item.getType(), item.getAmount(), Integer::sum);
    }

    int freeSlots = Math.max(0, totalSlots - usedSlots);
    boolean nearlyFull = totalSlots > 0 && freeSlots <= Math.max(2, totalSlots / 10);
    String notableItems =
        totals.entrySet().stream()
            .sorted(
                Map.Entry.<Material, Integer>comparingByValue(Comparator.reverseOrder())
                    .thenComparing(entry -> entry.getKey().name()))
            .limit(8)
            .map(entry -> materialName(entry.getKey()) + " x" + entry.getValue())
            .collect(Collectors.joining(", "));
    if (notableItems.isBlank()) {
      notableItems = "暂无";
    }

    return "背包：已用 "
        + usedSlots
        + "/"
        + totalSlots
        + " 格，剩余 "
        + freeSlots
        + " 格，"
        + (nearlyFull ? "接近满。" : "未满。")
        + "主要物品："
        + notableItems
        + "。";
  }

  private List<ItemStack> normalizedItems(Collection<ItemStack> items) {
    List<ItemStack> normalized = new ArrayList<>();
    if (items == null) {
      return normalized;
    }
    for (ItemStack item : items) {
      if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
        continue;
      }
      normalized.add(item.clone());
    }
    return normalized;
  }

  private String materialName(Material material) {
    return material.name().toLowerCase(Locale.ROOT);
  }

  public record InventoryInsertResult(boolean success, ItemStack leftover, String message) {
    public static InventoryInsertResult success(String message) {
      return new InventoryInsertResult(true, null, message);
    }

    public static InventoryInsertResult partial(ItemStack leftover, String message) {
      return new InventoryInsertResult(false, leftover, message);
    }

    public static InventoryInsertResult failure(String message) {
      return new InventoryInsertResult(false, null, message);
    }
  }
}
