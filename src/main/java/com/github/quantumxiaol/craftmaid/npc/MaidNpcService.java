package com.github.quantumxiaol.craftmaid.npc;

import com.github.quantumxiaol.craftmaid.inventory.MaidInventoryService.InventoryInsertResult;
import com.github.quantumxiaol.craftmaid.menu.MaidMenuService;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface MaidNpcService {
  boolean isAvailable();

  boolean isMaidNpc(int npcId);

  boolean isMaidEntity(Entity entity);

  void registerInteractionListener(MaidMenuService menuService);

  boolean spawnAt(Player player, String maidName);

  boolean despawnStored();

  boolean applyConfiguredSkin(Player fallbackPlayer);

  boolean setHomeAtMaidLocation(Player fallbackPlayer);

  Location getHomeLocation();

  boolean returnHome();

  boolean lookAt(Player player);

  boolean startFollowing(Player player);

  boolean stopFollowing();

  boolean isFollowing();

  boolean moveTo(Location location);

  boolean isNear(Location location, double distance);

  boolean lookAt(Location location);

  boolean swingMainHand();

  boolean openInventory(Player player);

  InventoryInsertResult addInventoryItem(ItemStack item);

  MaidEquipment getEquipment();

  boolean setEquipment(MaidEquipment equipment);

  boolean isGuardAvailable();

  boolean isGuarding();

  boolean startGuarding(Player player);

  boolean startGuardingHere(Player player);

  boolean stopGuarding();
}
