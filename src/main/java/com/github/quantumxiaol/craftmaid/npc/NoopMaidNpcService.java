package com.github.quantumxiaol.craftmaid.npc;

import org.bukkit.entity.Player;

final class NoopMaidNpcService implements MaidNpcService {
  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public boolean spawnAt(Player player, String maidName) {
    return false;
  }

  @Override
  public boolean despawnStored() {
    return false;
  }
}
