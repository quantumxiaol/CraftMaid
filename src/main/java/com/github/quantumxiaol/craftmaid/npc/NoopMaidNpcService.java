package com.github.quantumxiaol.craftmaid.npc;

import com.github.quantumxiaol.craftmaid.inventory.MaidInventoryService.InventoryInsertResult;
import com.github.quantumxiaol.craftmaid.menu.MaidMenuService;
import java.util.Collection;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
  public boolean isMaidEntity(Entity entity) {
    return false;
  }

  @Override
  public LivingEntity getMaidLivingEntity() {
    return null;
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
  public boolean syncConfiguredName() {
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
  public boolean stopMoving() {
    return false;
  }

  @Override
  public boolean prepareForJobControl(boolean clearGuarding) {
    return false;
  }

  @Override
  public boolean moveTo(Location location) {
    return false;
  }

  @Override
  public boolean isNavigating() {
    return false;
  }

  @Override
  public boolean isNear(Location location, double distance) {
    return false;
  }

  @Override
  public double distanceSquaredTo(Location location) {
    return Double.POSITIVE_INFINITY;
  }

  @Override
  public boolean lookAt(Location location) {
    return false;
  }

  @Override
  public boolean swingMainHand() {
    return false;
  }

  @Override
  public boolean startFishingAnimation(Location target) {
    return false;
  }

  @Override
  public void stopFishingAnimation() {}

  @Override
  public boolean openInventory(Player player) {
    return false;
  }

  @Override
  public InventoryInsertResult addInventoryItem(ItemStack item) {
    return InventoryInsertResult.failure("未安装或未启用 Citizens，无法写入女仆背包。");
  }

  @Override
  public boolean canFitInventoryItems(Collection<ItemStack> items) {
    return false;
  }

  @Override
  public InventoryInsertResult addInventoryItemsAllOrNothing(Collection<ItemStack> items) {
    return InventoryInsertResult.failure("未安装或未启用 Citizens，无法写入女仆背包。");
  }

  @Override
  public List<ItemStack> getInventoryContents() {
    return List.of();
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
  public boolean isGuarding() {
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
  public boolean startGuardingAt(Location location) {
    return false;
  }

  @Override
  public boolean markGuardFightbackTarget(Entity entity) {
    return false;
  }

  @Override
  public boolean markSelfDefenseTarget(Player player, int durationSeconds) {
    return false;
  }

  @Override
  public void forgiveCombatTarget(Player player) {}

  @Override
  public boolean stopGuarding() {
    return false;
  }
}
