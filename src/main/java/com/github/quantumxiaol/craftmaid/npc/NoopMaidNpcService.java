package com.github.quantumxiaol.craftmaid.npc;

import com.github.quantumxiaol.craftmaid.menu.MaidMenuService;
import org.bukkit.Location;
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

  @Override
  public boolean applyConfiguredSkin(Player fallbackPlayer) {
    return false;
  }

  @Override
  public boolean setHomeAtMaidLocation(Player fallbackPlayer) {
    return false;
  }

  @Override
  public Location getHomeLocation() {
    return null;
  }

  @Override
  public boolean returnHome() {
    return false;
  }

  @Override
  public boolean lookAt(Player player) {
    return false;
  }

  @Override
  public boolean startFollowing(Player player) {
    return false;
  }

  @Override
  public boolean stopFollowing() {
    return false;
  }

  @Override
  public boolean isFollowing() {
    return false;
  }

  @Override
  public boolean openInventory(Player player) {
    return false;
  }

  @Override
  public MaidEquipment getEquipment() {
    return MaidEquipment.empty();
  }

  @Override
  public boolean setEquipment(MaidEquipment equipment) {
    return false;
  }

  @Override
  public boolean isGuardAvailable() {
    return false;
  }

  @Override
  public boolean startGuarding(Player player) {
    return false;
  }

  @Override
  public boolean startGuardingHere(Player player) {
    return false;
  }

  @Override
  public boolean stopGuarding() {
    return false;
  }
}
