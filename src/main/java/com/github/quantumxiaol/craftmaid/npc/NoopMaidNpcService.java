package com.github.quantumxiaol.craftmaid.npc;

import com.github.quantumxiaol.craftmaid.menu.MaidMenuService;
import org.bukkit.entity.Player;

final class NoopMaidNpcService implements MaidNpcService {
  @Override
  public boolean isAvailable() {
    return false;
  }

  @Override
  public boolean isMaidNpc(int npcId) {
    return false;
  }

  @Override
  public void registerInteractionListener(MaidMenuService menuService) {}

  @Override
  public boolean spawnAt(Player player, String maidName) {
    return false;
  }

  @Override
  public boolean despawnStored() {
    return false;
  }
}
