package com.github.quantumxiaol.craftmaid.npc;

import com.github.quantumxiaol.craftmaid.menu.MaidMenuService;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface MaidNpcService {
  boolean isAvailable();

  boolean isMaidNpc(int npcId);

  void registerInteractionListener(MaidMenuService menuService);

  boolean spawnAt(Player player, String maidName);

  boolean despawnStored();

  boolean setHomeAtMaidLocation(Player fallbackPlayer);

  Location getHomeLocation();

  boolean returnHome();

  boolean lookAt(Player player);

  boolean startFollowing(Player player);

  boolean stopFollowing();

  boolean isFollowing();

  boolean isGuardAvailable();

  boolean startGuarding(Player player);

  boolean startGuardingHere(Player player);

  boolean stopGuarding();
}
