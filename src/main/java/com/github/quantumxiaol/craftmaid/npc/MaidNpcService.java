package com.github.quantumxiaol.craftmaid.npc;

import org.bukkit.entity.Player;

public interface MaidNpcService {
  boolean isAvailable();

  boolean spawnAt(Player player, String maidName);

  boolean despawnStored();
}
