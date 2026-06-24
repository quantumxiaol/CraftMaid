package com.github.quantumxiaol.craftmaid.npc;

import com.github.quantumxiaol.craftmaid.menu.MaidMenuService;
import org.bukkit.entity.Player;

public interface MaidNpcService {
  boolean isAvailable();

  boolean isMaidNpc(int npcId);

  void registerInteractionListener(MaidMenuService menuService);

  boolean spawnAt(Player player, String maidName);

  boolean despawnStored();
}
