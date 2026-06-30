package com.github.quantumxiaol.craftmaid.inventory;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
