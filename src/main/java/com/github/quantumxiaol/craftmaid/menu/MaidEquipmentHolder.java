package com.github.quantumxiaol.craftmaid.menu;

import com.github.quantumxiaol.craftmaid.npc.MaidEquipment;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

final class MaidEquipmentHolder implements InventoryHolder {
  static final int SIZE = 27;
  private static final int MAIN_HAND_SLOT = 10;
  private static final int OFF_HAND_SLOT = 12;
  private static final int HELMET_SLOT = 14;
  private static final int CHESTPLATE_SLOT = 15;
  private static final int LEGGINGS_SLOT = 16;
  private static final int BOOTS_SLOT = 17;
  private static final Set<Integer> EQUIPMENT_SLOTS =
      Set.of(
          MAIN_HAND_SLOT, OFF_HAND_SLOT, HELMET_SLOT, CHESTPLATE_SLOT, LEGGINGS_SLOT, BOOTS_SLOT);

  private Inventory inventory;

  void setInventory(Inventory inventory) {
    this.inventory = inventory;
  }

  void populate(MaidEquipment equipment) {
    setLabel(1, "主手");
    setLabel(3, "副手");
    setLabel(5, "头盔");
    setLabel(6, "胸甲");
    setLabel(7, "护腿");
    setLabel(8, "靴子");
    inventory.setItem(MAIN_HAND_SLOT, equipment.mainHand());
    inventory.setItem(OFF_HAND_SLOT, equipment.offHand());
    inventory.setItem(HELMET_SLOT, equipment.helmet());
    inventory.setItem(CHESTPLATE_SLOT, equipment.chestplate());
    inventory.setItem(LEGGINGS_SLOT, equipment.leggings());
    inventory.setItem(BOOTS_SLOT, equipment.boots());
  }

  MaidEquipment readEquipment() {
    return new MaidEquipment(
        inventory.getItem(MAIN_HAND_SLOT),
        inventory.getItem(OFF_HAND_SLOT),
        inventory.getItem(HELMET_SLOT),
        inventory.getItem(CHESTPLATE_SLOT),
        inventory.getItem(LEGGINGS_SLOT),
        inventory.getItem(BOOTS_SLOT));
  }

  static boolean isEquipmentSlot(int slot) {
    return EQUIPMENT_SLOTS.contains(slot);
  }

  @Override
  public @NotNull Inventory getInventory() {
    return inventory;
  }

  private void setLabel(int slot, String name) {
    ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(Component.text(name, NamedTextColor.GRAY));
      item.setItemMeta(meta);
    }
    inventory.setItem(slot, item);
  }
}
