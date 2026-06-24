package com.github.quantumxiaol.craftmaid.menu;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

final class MaidMenuHolder implements InventoryHolder {
  private final Map<Integer, MaidMenuAction> actions = new HashMap<>();
  private Inventory inventory;

  void setInventory(Inventory inventory) {
    this.inventory = inventory;
  }

  void setAction(int slot, MaidMenuAction action) {
    actions.put(slot, action);
  }

  MaidMenuAction getAction(int slot) {
    return actions.get(slot);
  }

  @Override
  public @NotNull Inventory getInventory() {
    return inventory;
  }
}
