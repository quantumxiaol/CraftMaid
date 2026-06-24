package com.github.quantumxiaol.craftmaid.npc;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record MaidEquipment(
    ItemStack mainHand,
    ItemStack offHand,
    ItemStack helmet,
    ItemStack chestplate,
    ItemStack leggings,
    ItemStack boots) {
  public MaidEquipment {
    mainHand = copyOrNull(mainHand);
    offHand = copyOrNull(offHand);
    helmet = copyOrNull(helmet);
    chestplate = copyOrNull(chestplate);
    leggings = copyOrNull(leggings);
    boots = copyOrNull(boots);
  }

  public static MaidEquipment empty() {
    return new MaidEquipment(null, null, null, null, null, null);
  }

  public static ItemStack copyOrNull(ItemStack item) {
    if (item == null || item.getType() == Material.AIR) {
      return null;
    }
    return item.clone();
  }
}
