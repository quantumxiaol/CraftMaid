package com.github.quantumxiaol.craftmaid.npc;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.anchor.AnchorType;
import com.github.quantumxiaol.craftmaid.anchor.MaidAnchorService;
import com.github.quantumxiaol.craftmaid.combat.MaidCombatPolicy;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig;
import com.github.quantumxiaol.craftmaid.interaction.CitizensMaidInteractionListener;
import com.github.quantumxiaol.craftmaid.inventory.MaidInventoryService.InventoryInsertResult;
import com.github.quantumxiaol.craftmaid.menu.MaidMenuService;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot;
import net.citizensnpcs.api.trait.trait.Inventory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class CitizensMaidNpcService implements MaidNpcService {
  private static final String CITIZENS_PLUGIN = "Citizens";
  private static final String SKIN_TRAIT_CLASS = "net.citizensnpcs.trait.SkinTrait";
  private static final String SENTINEL_PLUGIN = "Sentinel";
  private static final String SENTINEL_TRAIT_CLASS = "org.mcmonkey.sentinel.SentinelTrait";
  private static final long FIGHTBACK_TARGET_TICKS = 15L * 20L;

  private final CraftMaid plugin;
  private final Map<String, BukkitTask> guardFightbackCleanupTasks = new HashMap<>();
  private final Set<String> reportedSentinelCompatibilityIssues = new HashSet<>();
  private BukkitTask followTask;
  private Location followLastLocation;
  private int followStuckTicks;
  private int followStuckRetries;
  private long followNextTeleportAtMillis;
  private boolean guarding;
  private long guardRevision;
  private Player followingPlayer;
  private NavigatorParameters savedNavigationParameters;
  private NPC configuredNavigationNpc;
  private NPC fishingAnimationNpc;

  public CitizensMaidNpcService(CraftMaid plugin) {
    this.plugin = plugin;
  }

  @Override
  public boolean isAvailable() {
    return true;
  }

  @Override
  public boolean isMaidNpc(int npcId) {
    return plugin.getConfig().getInt("maid.npc_id", -1) == npcId;
  }

  @Override
  public boolean isMaidEntity(Entity entity) {
    if (entity == null) {
      return false;
    }
    NPC npc = getStoredNpcOrNull();
    return npc != null
        && npc.isSpawned()
        && npc.getEntity() != null
        && npc.getEntity().getUniqueId().equals(entity.getUniqueId());
  }

  @Override
  public LivingEntity getMaidLivingEntity() {
    NPC npc = getStoredNpcOrNull();
    if (npc == null || !npc.isSpawned() || !(npc.getEntity() instanceof LivingEntity living)) {
      return null;
    }
    return living;
  }

  @Override
  public void registerInteractionListener(MaidMenuService menuService) {
    plugin
        .getServer()
        .getPluginManager()
        .registerEvents(new CitizensMaidInteractionListener(plugin, this, menuService), plugin);
  }

  @Override
  public boolean spawnAt(Player player, String maidName) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, maidName);
      plugin.getConfig().set("maid.npc_id", npc.getId());
      plugin.saveConfig();
    }

    syncConfiguredName(npc);
    applyConfiguredSkin(npc, player);
    if (npc.isSpawned()) {
      npc.despawn();
    }
    return npc.spawn(player.getLocation());
  }

  @Override
  public boolean despawnStored() {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return false;
    }

    resetTransientState(npc);
    resetSentinelState(npc);
    return !npc.isSpawned() || npc.despawn();
  }

  @Override
  public boolean showStored() {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return false;
    }
    syncConfiguredName(npc);
    applyConfiguredSkin(npc, plugin.getOnlineMaster());
    if (npc.isSpawned()) {
      return true;
    }
    Location location = npc.getStoredLocation();
    return location != null && location.getWorld() != null && npc.spawn(location);
  }

  @Override
  public boolean removeStored() {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return false;
    }

    resetTransientState(npc);
    npc.destroy();
    plugin.getConfig().set("maid.npc_id", -1);
    plugin.saveConfig();
    return true;
  }

  @Override
  public boolean reconcileExistingNpc(boolean respawnEntity) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return false;
    }

    boolean wasSpawned = npc.isSpawned();
    Location storedLocation = npc.getStoredLocation();
    if (storedLocation != null) {
      storedLocation = storedLocation.clone();
    }

    resetTransientState(npc);
    boolean sentinelReset = resetSentinelState(npc);
    syncConfiguredName(npc);
    applyConfiguredSkin(npc, plugin.getOnlineMaster());

    if (respawnEntity && wasSpawned) {
      npc.despawn();
      if (storedLocation == null
          || storedLocation.getWorld() == null
          || !npc.spawn(storedLocation)) {
        return false;
      }
    }
    return sentinelReset;
  }

  @Override
  public int getStoredNpcId() {
    return plugin.getConfig().getInt("maid.npc_id", -1);
  }

  @Override
  public java.util.UUID getStoredNpcUniqueId() {
    NPC npc = getStoredNpcOrNull();
    return npc == null ? null : npc.getUniqueId();
  }

  @Override
  public boolean hasStoredNpc() {
    return getStoredNpcOrNull() != null;
  }

  @Override
  public boolean isStoredNpcSpawned() {
    NPC npc = getStoredNpcOrNull();
    return npc != null && npc.isSpawned();
  }

  @Override
  public boolean applyConfiguredSkin(Player fallbackPlayer) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return false;
    }
    syncConfiguredName(npc);
    return applyConfiguredSkin(npc, fallbackPlayer);
  }

  @Override
  public boolean syncConfiguredName() {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return false;
    }
    syncConfiguredName(npc);
    return true;
  }

  @Override
  public boolean setHomeAtMaidLocation(Player fallbackPlayer) {
    NPC npc = getStoredNpcOrNull();
    Location home =
        npc != null && npc.isSpawned() ? npc.getStoredLocation() : fallbackPlayer.getLocation();
    return plugin
        .getAnchorService()
        .setAnchor(AnchorType.HOME, MaidAnchorService.DEFAULT_NAME, home)
        .success();
  }

  @Override
  public Location getHomeLocation() {
    return plugin.getAnchorService().getHomeLocation();
  }

  @Override
  public boolean returnHome() {
    Location home = getHomeLocation();
    if (home == null) {
      return false;
    }

    NPC npc = getStoredNpcOrNull();
    boolean createdNpc = false;
    if (npc == null) {
      npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, plugin.getMaidName());
      plugin.getConfig().set("maid.npc_id", npc.getId());
      plugin.saveConfig();
      createdNpc = true;
    }

    if (createdNpc) {
      applyConfiguredSkin(npc, null);
    }
    syncConfiguredName(npc);
    stopFollowing();
    if (npc.isSpawned()) {
      npc.teleport(home, PlayerTeleportEvent.TeleportCause.PLUGIN);
    } else {
      return npc.spawn(home);
    }
    return true;
  }

  @Override
  public boolean lookAt(Player player) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null || !npc.isSpawned()) {
      return false;
    }

    npc.faceLocation(player.getEyeLocation());
    return true;
  }

  @Override
  public boolean startFollowing(Player player) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      spawnAt(player, plugin.getMaidName());
      npc = getStoredNpcOrNull();
    }
    if (npc == null) {
      return false;
    }

    if (!npc.isSpawned() && !npc.spawn(player.getLocation())) {
      return false;
    }

    stopFollowing();
    followingPlayer = player;
    NPC followNpc = npc;
    configureFollowNavigation(followNpc);
    updateFollowTarget(followNpc, player);
    long updateTicks = Math.max(1L, plugin.getMaidFollowSettings().updateTicks());
    followTask =
        Bukkit.getScheduler()
            .runTaskTimer(
                plugin,
                () -> {
                  if (!player.isOnline() || !followNpc.isSpawned()) {
                    stopFollowing();
                    return;
                  }
                  updateFollowTarget(followNpc, player);
                },
                updateTicks,
                updateTicks);
    return true;
  }

  @Override
  public boolean stopFollowing() {
    boolean wasFollowing = followTask != null;
    followingPlayer = null;
    if (followTask != null) {
      followTask.cancel();
      followTask = null;
    }
    followLastLocation = null;
    followStuckTicks = 0;
    followStuckRetries = 0;
    followNextTeleportAtMillis = 0L;

    NPC npc = getStoredNpcOrNull();
    if (wasFollowing && npc != null) {
      npc.getNavigator().cancelNavigation();
      restoreNavigationParameters(npc);
    }
    return true;
  }

  @Override
  public Player getFollowingPlayer() {
    return followingPlayer;
  }

  @Override
  public boolean isFollowing() {
    return followTask != null;
  }

  @Override
  public boolean stopMoving() {
    NPC npc = getStoredNpcOrNull();
    if (npc == null || !npc.isSpawned()) {
      return false;
    }

    npc.getNavigator().cancelNavigation();
    restoreNavigationParameters(npc);
    return true;
  }

  @Override
  public boolean prepareForJobControl(boolean clearGuarding) {
    stopFollowing();
    stopFishingAnimation();
    plugin.clearMaidSelfDefenseTargets();
    boolean guardCleared = true;
    if (clearGuarding) {
      NPC npc = getStoredNpcOrNull();
      if (npc != null) {
        boolean sentinelAvailable = isGuardAvailable();
        guardCleared = clearSentinelGuardingState(true, true) || !sentinelAvailable;
      }
    } else {
      stopMoving();
    }
    followLastLocation = null;
    followStuckTicks = 0;
    followStuckRetries = 0;
    followNextTeleportAtMillis = 0L;
    return guardCleared;
  }

  @Override
  public boolean moveTo(Location location) {
    if (location == null || location.getWorld() == null) {
      return false;
    }
    if (followTask != null) {
      stopFollowing();
    }

    NPC npc = ensureSpawnedAt(location);
    if (npc == null) {
      return false;
    }
    if (npc.getEntity() == null
        || npc.getEntity().getLocation().getWorld() == null
        || !npc.getEntity().getLocation().getWorld().equals(location.getWorld())) {
      return false;
    }

    npc.getNavigator().cancelNavigation();
    configureDirectedNavigation(npc);
    npc.getNavigator().setTarget(location);
    return true;
  }

  @Override
  public boolean isNavigating() {
    NPC npc = getStoredNpcOrNull();
    return npc != null && npc.isSpawned() && npc.getNavigator().isNavigating();
  }

  @Override
  public boolean isNear(Location location, double distance) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null || !npc.isSpawned() || npc.getEntity() == null || location == null) {
      return false;
    }
    Location npcLocation = npc.getEntity().getLocation();
    if (npcLocation.getWorld() == null || !npcLocation.getWorld().equals(location.getWorld())) {
      return false;
    }
    return npcLocation.distanceSquared(location) <= distance * distance;
  }

  @Override
  public double distanceSquaredTo(Location location) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null || !npc.isSpawned() || npc.getEntity() == null || location == null) {
      return Double.POSITIVE_INFINITY;
    }
    Location npcLocation = npc.getEntity().getLocation();
    if (npcLocation.getWorld() == null || !npcLocation.getWorld().equals(location.getWorld())) {
      return Double.POSITIVE_INFINITY;
    }
    return npcLocation.distanceSquared(location);
  }

  @Override
  public boolean lookAt(Location location) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null || !npc.isSpawned() || location == null) {
      return false;
    }
    npc.faceLocation(location);
    return true;
  }

  @Override
  public boolean swingMainHand() {
    NPC npc = getStoredNpcOrNull();
    if (npc == null
        || !npc.isSpawned()
        || !(npc.getEntity() instanceof LivingEntity livingEntity)) {
      return false;
    }
    livingEntity.swingMainHand();
    return true;
  }

  @Override
  public boolean startFishingAnimation(Location target) {
    if (target == null
        || target.getWorld() == null
        || !plugin.getServer().getPluginManager().isPluginEnabled("Denizen")) {
      return false;
    }
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return false;
    }

    String targetText =
        String.format(
            Locale.ROOT,
            "%.1f,%.1f,%.1f,%s",
            target.getX(),
            target.getY(),
            target.getZ(),
            target.getWorld().getName());
    boolean selected =
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc select " + npc.getId());
    if (!selected) {
      return false;
    }
    boolean started = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc fish " + targetText);
    if (started) {
      fishingAnimationNpc = npc;
    }
    return started;
  }

  @Override
  public void stopFishingAnimation() {
    NPC animationNpc = fishingAnimationNpc;
    fishingAnimationNpc = null;
    if (animationNpc != null
        && CitizensAPI.getNPCRegistry().getById(animationNpc.getId()) == animationNpc
        && plugin.getServer().getPluginManager().isPluginEnabled("Denizen")
        && Bukkit.dispatchCommand(
            Bukkit.getConsoleSender(), "npc select " + animationNpc.getId())) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc stopfishing");
    }
  }

  @Override
  public boolean openInventory(Player player) {
    if (plugin.getMaidMenuService().isEquipmentEditing()) {
      return false;
    }
    NPC npc = ensureSpawnedNear(player);
    if (npc == null) {
      return false;
    }

    npc.getOrAddTrait(Inventory.class).openInventory(player);
    return true;
  }

  @Override
  public InventoryInsertResult addInventoryItem(ItemStack item) {
    if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
      return InventoryInsertResult.success("没有需要放入女仆背包的物品。");
    }
    return addInventoryItemsAllOrNothing(List.of(item));
  }

  @Override
  public boolean canFitInventoryItems(Collection<ItemStack> items) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return false;
    }

    Inventory inventory = npc.getOrAddTrait(Inventory.class);
    ItemStack[] contents = inventory.getContents();
    return fitItems(copyContents(contents), items);
  }

  @Override
  public InventoryInsertResult addInventoryItemsAllOrNothing(Collection<ItemStack> items) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return InventoryInsertResult.failure("还没有已记录的女仆 NPC。");
    }

    Inventory inventory = npc.getOrAddTrait(Inventory.class);
    ItemStack[] candidate = copyContents(inventory.getContents());
    if (!fitItems(candidate, items)) {
      return InventoryInsertResult.failure("女仆背包空间不足。");
    }
    inventory.setContents(candidate);
    return InventoryInsertResult.success("已放入女仆背包。");
  }

  @Override
  public List<ItemStack> getInventoryContents() {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return List.of();
    }

    Inventory inventory = npc.getOrAddTrait(Inventory.class);
    ItemStack[] contents = inventory.getContents();
    if (contents == null || contents.length == 0) {
      return List.of();
    }

    return java.util.Arrays.stream(contents)
        .map(item -> item == null ? null : item.clone())
        .toList();
  }

  private ItemStack[] copyContents(ItemStack[] contents) {
    ItemStack[] copy =
        contents == null || contents.length == 0
            ? new ItemStack[36]
            : new ItemStack[contents.length];
    if (contents == null) {
      return copy;
    }
    for (int i = 0; i < contents.length; i++) {
      copy[i] = contents[i] == null ? null : contents[i].clone();
    }
    return copy;
  }

  private boolean fitItems(ItemStack[] contents, Collection<ItemStack> items) {
    if (items == null || items.isEmpty()) {
      return true;
    }
    for (ItemStack item : items) {
      if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0) {
        continue;
      }
      if (!fitItem(contents, item.clone())) {
        return false;
      }
    }
    return true;
  }

  private boolean fitItem(ItemStack[] contents, ItemStack incoming) {
    int remaining = incoming.getAmount();
    int maxStackSize = Math.max(1, incoming.getMaxStackSize());
    for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
      ItemStack existing = contents[slot];
      if (existing == null || existing.getType() == Material.AIR || !existing.isSimilar(incoming)) {
        continue;
      }
      int space = Math.min(maxStackSize, existing.getMaxStackSize()) - existing.getAmount();
      if (space <= 0) {
        continue;
      }
      int added = Math.min(space, remaining);
      existing.setAmount(existing.getAmount() + added);
      remaining -= added;
    }

    for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
      ItemStack existing = contents[slot];
      if (existing != null && existing.getType() != Material.AIR) {
        continue;
      }
      int added = Math.min(maxStackSize, remaining);
      ItemStack placed = incoming.clone();
      placed.setAmount(added);
      contents[slot] = placed;
      remaining -= added;
    }
    return remaining <= 0;
  }

  @Override
  public MaidEquipment getEquipment() {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return MaidEquipment.empty();
    }

    // Citizens shares the main hand with backpack slot zero. Settle any open view first.
    var backpack = npc.getOrAddTrait(Inventory.class).getInventoryView();
    if (backpack != null) {
      for (var viewer : List.copyOf(backpack.getViewers())) {
        viewer.closeInventory();
      }
    }
    Equipment equipment = npc.getOrAddTrait(Equipment.class);
    return new MaidEquipment(
        equipment.get(EquipmentSlot.HAND),
        equipment.get(EquipmentSlot.OFF_HAND),
        equipment.get(EquipmentSlot.HELMET),
        equipment.get(EquipmentSlot.CHESTPLATE),
        equipment.get(EquipmentSlot.LEGGINGS),
        equipment.get(EquipmentSlot.BOOTS));
  }

  @Override
  public boolean setEquipment(MaidEquipment equipment) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return false;
    }

    Equipment npcEquipment = npc.getOrAddTrait(Equipment.class);
    npcEquipment.set(EquipmentSlot.HAND, MaidEquipment.copyOrNull(equipment.mainHand()));
    npcEquipment.set(EquipmentSlot.OFF_HAND, MaidEquipment.copyOrNull(equipment.offHand()));
    npcEquipment.set(EquipmentSlot.HELMET, MaidEquipment.copyOrNull(equipment.helmet()));
    npcEquipment.set(EquipmentSlot.CHESTPLATE, MaidEquipment.copyOrNull(equipment.chestplate()));
    npcEquipment.set(EquipmentSlot.LEGGINGS, MaidEquipment.copyOrNull(equipment.leggings()));
    npcEquipment.set(EquipmentSlot.BOOTS, MaidEquipment.copyOrNull(equipment.boots()));
    return true;
  }

  @Override
  public boolean isGuardAvailable() {
    if (!plugin.getServer().getPluginManager().isPluginEnabled(SENTINEL_PLUGIN)) {
      return false;
    }

    try {
      loadClassFromPlugin(SENTINEL_PLUGIN, SENTINEL_TRAIT_CLASS);
      return true;
    } catch (ClassNotFoundException | LinkageError ex) {
      return false;
    }
  }

  @Override
  public long guardingRevision() {
    return guardRevision;
  }

  @Override
  public boolean isGuarding() {
    return guarding;
  }

  @Override
  public boolean startGuarding(Player player) {
    NPC npc = ensureSpawnedNear(player);
    if (npc == null || !isGuardAvailable()) {
      return false;
    }

    try {
      Object trait = getSentinelTrait(npc);
      configureSentinelCombat(trait);
      invoke(trait, "setGuarding", new Class<?>[] {java.util.UUID.class}, player.getUniqueId());
      guarding = true;
      guardRevision++;
      plugin.getMaidCombatBuffService().start();
      return true;
    } catch (ReflectiveOperationException | LinkageError ex) {
      plugin.getLogger().warning("启动 Sentinel 护卫失败: " + rootMessage(ex));
      return false;
    }
  }

  @Override
  public boolean startGuardingHere(Player player) {
    NPC npc = ensureSpawnedNear(player);
    if (npc == null || !isGuardAvailable()) {
      return false;
    }

    return configureSentinelGuard(npc, npc.getStoredLocation(), "守卫");
  }

  @Override
  public boolean startGuardingAt(Location location) {
    if (location == null || location.getWorld() == null) {
      return false;
    }
    NPC npc = ensureSpawnedAt(location);
    if (npc == null || !isGuardAvailable()) {
      return false;
    }
    npc.getNavigator().setTarget(location);
    return configureSentinelGuard(npc, location, "定点守卫");
  }

  @Override
  public boolean markGuardFightbackTarget(Entity entity) {
    if (entity == null || !guarding || !isGuardAvailable()) {
      return false;
    }

    MaidCombatPolicy policy = plugin.getMaidCombatPolicy();
    if (policy == null || !policy.isFightbackTarget(entity)) {
      return false;
    }

    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return false;
    }

    String targetKey = policy.sentinelKeyFor(entity.getType());
    if (targetKey.isBlank()) {
      return false;
    }

    try {
      Object trait = getSentinelTrait(npc);
      optionalInvokeIgnored(trait, "removeTarget", new Class<?>[] {String.class}, targetKey);
      optionalInvokeIgnored(trait, "removeAvoid", new Class<?>[] {String.class}, targetKey);
      optionalInvokeIgnored(trait, "removeIgnore", new Class<?>[] {String.class}, targetKey);
      addSentinelLabel(trait, "addTarget", targetKey);
      scheduleGuardFightbackCleanup(targetKey);
      return true;
    } catch (ReflectiveOperationException | LinkageError ex) {
      plugin.getLogger().warning("添加 Sentinel 反击目标失败 " + targetKey + ": " + rootMessage(ex));
      return false;
    }
  }

  @Override
  public boolean addSelfDefenseTarget(Player player) {
    if (player == null || !isGuardAvailable()) {
      return false;
    }

    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return false;
    }

    String targetKey = sentinelPlayerTargetKey(player.getUniqueId());
    try {
      Object trait = getSentinelTrait(npc);
      optionalInvokeIgnored(trait, "removeIgnore", new Class<?>[] {String.class}, targetKey);
      optionalInvokeIgnored(trait, "removeAvoid", new Class<?>[] {String.class}, targetKey);
      optionalInvokeIgnored(trait, "removeTarget", new Class<?>[] {String.class}, targetKey);
      addSentinelLabel(trait, "addTarget", targetKey);
      return true;
    } catch (ReflectiveOperationException | LinkageError ex) {
      plugin.getLogger().warning("添加 Sentinel 自卫目标失败 " + player.getName() + ": " + rootMessage(ex));
      return false;
    }
  }

  @Override
  public void removeSelfDefenseTarget(UUID playerId, String playerName) {
    if (playerId == null || !isGuardAvailable()) {
      return;
    }
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return;
    }

    try {
      Object trait = getSentinelTrait(npc);
      optionalInvokeIgnored(
          trait, "removeTarget", new Class<?>[] {String.class}, sentinelPlayerTargetKey(playerId));
      if (playerName != null && !playerName.isBlank()) {
        for (String legacyKey : sentinelLegacyPlayerTargetKeys(playerName)) {
          optionalInvokeIgnored(trait, "removeTarget", new Class<?>[] {String.class}, legacyKey);
        }
      }
      optionalInvokeIgnored(trait, "whenAnEnemyDies", new Class<?>[] {UUID.class}, playerId);
    } catch (ClassNotFoundException | LinkageError ex) {
      plugin.getLogger().fine("清理 Sentinel 自卫目标失败: " + rootMessage(ex));
    }
  }

  private boolean configureSentinelGuard(NPC npc, Location guardLocation, String label) {
    try {
      Object trait = getSentinelTrait(npc);
      configureSentinelCombat(trait);
      setField(trait, "spawnPoint", guardLocation.clone());
      invoke(trait, "setGuarding", new Class<?>[] {java.util.UUID.class}, new Object[] {null});
      guarding = true;
      guardRevision++;
      plugin.getMaidCombatBuffService().start();
      return true;
    } catch (ReflectiveOperationException | LinkageError ex) {
      plugin.getLogger().warning("启动 Sentinel " + label + "失败: " + rootMessage(ex));
      return false;
    }
  }

  @Override
  public boolean stopGuarding() {
    return clearSentinelGuardingState(true, true);
  }

  private boolean clearSentinelGuardingState(boolean stopNavigation, boolean warnOnFailure) {
    cancelGuardFightbackTargets();
    plugin.clearMaidSelfDefenseTargets();
    guarding = false;
    guardRevision++;
    plugin.getMaidCombatBuffService().stop();
    NPC npc = getStoredNpcOrNull();
    if (npc == null || !isGuardAvailable()) {
      if (stopNavigation) {
        stopMoving();
      }
      return false;
    }

    try {
      Object trait = getSentinelTrait(npc);
      invoke(trait, "setGuarding", new Class<?>[] {java.util.UUID.class}, new Object[] {null});
      cleanupSentinelCombat(trait);
      clearSentinelNavigationState(trait);
      if (stopNavigation) {
        stopMoving();
      }
      return true;
    } catch (ReflectiveOperationException | LinkageError ex) {
      if (warnOnFailure) {
        plugin.getLogger().warning("停止 Sentinel 护卫失败: " + rootMessage(ex));
      }
      if (stopNavigation) {
        stopMoving();
      }
      return false;
    }
  }

  private NPC ensureSpawnedNear(Player player) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      spawnAt(player, plugin.getMaidName());
      npc = getStoredNpcOrNull();
    }
    if (npc != null) {
      syncConfiguredName(npc);
    }
    if (npc != null && !npc.isSpawned() && !npc.spawn(player.getLocation())) {
      return null;
    }
    return npc;
  }

  private NPC ensureSpawnedAt(Location location) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, plugin.getMaidName());
      plugin.getConfig().set("maid.npc_id", npc.getId());
      plugin.saveConfig();
      applyConfiguredSkin(npc, null);
    }
    syncConfiguredName(npc);
    if (!npc.isSpawned() && !npc.spawn(location)) {
      return null;
    }
    return npc;
  }

  private NPC getStoredNpcOrNull() {
    int npcId = plugin.getConfig().getInt("maid.npc_id", -1);
    if (npcId < 0) {
      return null;
    }
    return CitizensAPI.getNPCRegistry().getById(npcId);
  }

  private void resetTransientState(NPC npc) {
    stopFollowing();
    stopFishingAnimation();
    cancelGuardFightbackTargets();
    plugin.clearMaidSelfDefenseTargets();
    plugin.getMaidCombatBuffService().stop();
    guarding = false;
    guardRevision++;
    if (npc.getNavigator().isNavigating()) {
      npc.getNavigator().cancelNavigation();
    }
    restoreNavigationParameters(npc);
  }

  private boolean resetSentinelState(NPC npc) {
    if (!isGuardAvailable()) {
      return true;
    }
    try {
      Object trait = getSentinelTrait(npc);
      invoke(trait, "setGuarding", new Class<?>[] {UUID.class}, new Object[] {null});
      cleanupSentinelCombat(trait);
      clearSentinelNavigationState(trait);
      clearSentinelPlayerTargets(trait);
      List<String> unsupported = new ArrayList<>();
      applySentinelBaseConfiguration(trait, unsupported);
      reportSentinelCompatibilityIssues(unsupported);
      return true;
    } catch (ReflectiveOperationException | LinkageError ex) {
      plugin.getLogger().warning("迁移现存 NPC 的 Sentinel 状态失败: " + rootMessage(ex));
      return false;
    }
  }

  private void clearSentinelPlayerTargets(Object trait) {
    for (Player player : Bukkit.getOnlinePlayers()) {
      UUID playerId = player.getUniqueId();
      String uuidTarget = sentinelPlayerTargetKey(playerId);
      optionalInvokeIgnored(trait, "removeTarget", new Class<?>[] {String.class}, uuidTarget);
      optionalInvokeIgnored(trait, "removeAvoid", new Class<?>[] {String.class}, uuidTarget);
      optionalInvokeIgnored(trait, "removeIgnore", new Class<?>[] {String.class}, uuidTarget);
      for (String legacyTarget : sentinelLegacyPlayerTargetKeys(player.getName())) {
        optionalInvokeIgnored(trait, "removeTarget", new Class<?>[] {String.class}, legacyTarget);
        optionalInvokeIgnored(trait, "removeAvoid", new Class<?>[] {String.class}, legacyTarget);
        optionalInvokeIgnored(trait, "removeIgnore", new Class<?>[] {String.class}, legacyTarget);
      }
      optionalInvokeIgnored(trait, "whenAnEnemyDies", new Class<?>[] {UUID.class}, playerId);
    }
  }

  private void configureFollowNavigation(NPC npc) {
    CraftMaidConfig.FollowSettings settings = plugin.getMaidFollowSettings();
    var parameters = navigationParameters(npc);
    parameters.speedModifier((float) settings.speed());
    parameters.updatePathRate(settings.updateTicks());
    parameters.distanceMargin(settings.stopDistance());
    parameters.pathDistanceMargin(settings.stopDistance());
    parameters.straightLineTargetingDistance(0.0F);
    // Citizens' destination teleport is too eager for a companion NPC. CraftMaid handles the
    // rare teleport fallback explicitly, with distance gates and cooldowns.
    parameters.destinationTeleportMargin(-1.0);
    parameters.stuckAction(null);
    parameters.avoidWater(true);
  }

  private void configureDirectedNavigation(NPC npc) {
    CraftMaidConfig.JobNavigationSettings settings = plugin.getJobNavigationSettings();
    var parameters = navigationParameters(npc);
    parameters.speedModifier((float) settings.speed());
    parameters.updatePathRate(settings.updateTicks());
    parameters.distanceMargin(settings.arrivalDistance());
    parameters.pathDistanceMargin(settings.arrivalDistance());
    parameters.straightLineTargetingDistance((float) settings.straightLineDistance());
    parameters.destinationTeleportMargin(-1.0);
    parameters.stuckAction(null);
    parameters.avoidWater(true);
  }

  private NavigatorParameters navigationParameters(NPC npc) {
    var parameters = npc.getNavigator().getDefaultParameters();
    if (configuredNavigationNpc != npc) {
      configuredNavigationNpc = npc;
      savedNavigationParameters = parameters.clone();
    }
    return parameters;
  }

  private void restoreNavigationParameters(NPC npc) {
    if (configuredNavigationNpc != npc || savedNavigationParameters == null) {
      return;
    }
    var parameters = npc.getNavigator().getDefaultParameters();
    parameters.speedModifier(savedNavigationParameters.speedModifier());
    parameters.updatePathRate(savedNavigationParameters.updatePathRate());
    parameters.distanceMargin(savedNavigationParameters.distanceMargin());
    parameters.pathDistanceMargin(savedNavigationParameters.pathDistanceMargin());
    parameters.straightLineTargetingDistance(
        savedNavigationParameters.straightLineTargetingDistance());
    parameters.destinationTeleportMargin(savedNavigationParameters.destinationTeleportMargin());
    parameters.stuckAction(savedNavigationParameters.stuckAction());
    parameters.avoidWater(savedNavigationParameters.avoidWater());
    configuredNavigationNpc = null;
    savedNavigationParameters = null;
  }

  private void updateFollowTarget(NPC npc, Player player) {
    if (npc.getEntity() == null) {
      return;
    }

    CraftMaidConfig.FollowSettings settings = plugin.getMaidFollowSettings();
    Location npcLocation = npc.getEntity().getLocation();
    Location playerLocation = player.getLocation();

    if (npcLocation.getWorld() == null
        || playerLocation.getWorld() == null
        || !npcLocation.getWorld().equals(playerLocation.getWorld())) {
      if (!maybeTeleportNearPlayer(npc, player, settings)) {
        npc.getNavigator().cancelNavigation();
        resetFollowStuck(npcLocation);
      }
      return;
    }

    double distanceSquared = npcLocation.distanceSquared(playerLocation);
    double stopDistanceSquared = settings.stopDistance() * settings.stopDistance();
    double startDistanceSquared = settings.startDistance() * settings.startDistance();
    double teleportDistanceSquared = settings.teleportDistance() * settings.teleportDistance();

    if (distanceSquared <= stopDistanceSquared) {
      npc.getNavigator().cancelNavigation();
      npc.faceLocation(player.getEyeLocation());
      resetFollowStuck(npcLocation);
      return;
    }

    if (distanceSquared < startDistanceSquared) {
      npc.getNavigator().cancelNavigation();
      npc.faceLocation(player.getEyeLocation());
      resetFollowStuck(npcLocation);
      return;
    }

    if (distanceSquared >= teleportDistanceSquared
        && maybeTeleportNearPlayer(npc, player, settings)) {
      return;
    }

    if (isFollowStuck(npcLocation, settings)) {
      followStuckRetries++;
      followLastLocation = npcLocation;
      followStuckTicks = 0;
      npc.getNavigator().cancelNavigation();
      double stuckTeleportMinDistanceSquared =
          settings.stuckTeleportMinDistance() * settings.stuckTeleportMinDistance();
      if (settings.teleportOnStuckSeconds() > 0
          && followStuckRetries >= settings.stuckRetryBeforeTeleport()
          && distanceSquared >= stuckTeleportMinDistanceSquared
          && maybeTeleportNearPlayer(npc, player, settings)) {
        return;
      }
    }

    var navigator = npc.getNavigator();
    var target = navigator.getEntityTarget();
    if (!navigator.isNavigating()
        || target == null
        || target.isAggressive()
        || !player.equals(target.getTarget())) {
      navigator.setTarget(player, false);
    }
    // Straight-line steering is only appropriate with a clear line of sight.
    boolean visible =
        npc.getEntity() instanceof LivingEntity living && living.hasLineOfSight(player);
    navigator
        .getLocalParameters()
        .straightLineTargetingDistance(visible ? (float) settings.straightLineDistance() : 0.0F);
  }

  private boolean isFollowStuck(Location npcLocation, CraftMaidConfig.FollowSettings settings) {
    if (followLastLocation == null
        || followLastLocation.getWorld() == null
        || !followLastLocation.getWorld().equals(npcLocation.getWorld())) {
      resetFollowStuck(npcLocation);
      return false;
    }

    if (followLastLocation.distanceSquared(npcLocation) < 0.0625) {
      followStuckTicks += settings.updateTicks();
    } else {
      resetFollowStuck(npcLocation);
    }
    int retrySeconds =
        settings.teleportOnStuckSeconds() > 0 ? settings.teleportOnStuckSeconds() : 5;
    return followStuckTicks >= retrySeconds * 20;
  }

  private void resetFollowStuck(Location npcLocation) {
    followLastLocation = npcLocation;
    followStuckTicks = 0;
    followStuckRetries = 0;
  }

  private boolean maybeTeleportNearPlayer(
      NPC npc, Player player, CraftMaidConfig.FollowSettings settings) {
    if (!settings.teleportEnabled()) {
      return false;
    }
    long now = System.currentTimeMillis();
    if (now < followNextTeleportAtMillis) {
      return false;
    }
    // Failed searches must back off too, including cross-world attempts.
    followNextTeleportAtMillis = now + Math.max(1, settings.teleportCooldownSeconds()) * 1000L;
    Location target = findSafeFollowLocation(player);
    if (target == null) {
      return false;
    }
    npc.getNavigator().cancelNavigation();
    npc.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
    npc.faceLocation(player.getEyeLocation());
    resetFollowStuck(target);
    followNextTeleportAtMillis = now + settings.teleportCooldownSeconds() * 1000L;
    return true;
  }

  private Location findSafeFollowLocation(Player player) {
    Location playerLocation = player.getLocation();
    Vector backward = playerLocation.getDirection().setY(0);
    if (backward.lengthSquared() < 0.0001) {
      backward = new Vector(0, 0, 1);
    } else {
      backward.normalize().multiply(-1);
    }

    double[][] offsets = {
      {backward.getX() * 2.0, backward.getZ() * 2.0},
      {backward.getZ() * 2.0, -backward.getX() * 2.0},
      {-backward.getZ() * 2.0, backward.getX() * 2.0},
      {0.0, 0.0},
      {2.0, 0.0},
      {-2.0, 0.0},
      {0.0, 2.0},
      {0.0, -2.0}
    };

    for (double[] offset : offsets) {
      Location candidate = playerLocation.clone().add(offset[0], 0.0, offset[1]);
      candidate.setYaw(playerLocation.getYaw());
      candidate.setPitch(0.0F);
      Location safe = findSafeVerticalLocation(candidate);
      if (safe != null) {
        return safe;
      }
    }
    return null;
  }

  private Location findSafeVerticalLocation(Location candidate) {
    if (candidate.getWorld() == null) {
      return null;
    }

    int baseY = candidate.getBlockY();
    int minY = candidate.getWorld().getMinHeight() + 1;
    int maxY = candidate.getWorld().getMaxHeight() - 2;
    for (int yOffset = 0; yOffset <= 3; yOffset++) {
      Location up = candidate.clone();
      up.setY(Math.min(maxY, baseY + yOffset));
      if (isSafeStandingLocation(up)) {
        return centerOnBlock(up);
      }

      Location down = candidate.clone();
      down.setY(Math.max(minY, baseY - yOffset));
      if (isSafeStandingLocation(down)) {
        return centerOnBlock(down);
      }
    }
    return null;
  }

  private boolean isSafeStandingLocation(Location location) {
    Material feet = location.getBlock().getType();
    Material head = location.clone().add(0, 1, 0).getBlock().getType();
    return feet != Material.WATER
        && feet != Material.LAVA
        && head != Material.WATER
        && head != Material.LAVA
        && location.getBlock().isPassable()
        && location.clone().add(0, 1, 0).getBlock().isPassable()
        && location.clone().add(0, -1, 0).getBlock().getType().isSolid();
  }

  private Location centerOnBlock(Location location) {
    Location centered = location.clone();
    centered.setX(location.getBlockX() + 0.5);
    centered.setZ(location.getBlockZ() + 0.5);
    return centered;
  }

  private boolean applyConfiguredSkin(NPC npc, Player fallbackPlayer) {
    String skinName = resolveSkinName(fallbackPlayer);
    if (skinName == null || skinName.isBlank()) {
      return false;
    }

    try {
      Object trait = getTraitFromPlugin(npc, CITIZENS_PLUGIN, SKIN_TRAIT_CLASS);
      optionalInvoke(trait, "setShouldUpdateSkins", new Class<?>[] {boolean.class}, true);
      try {
        invoke(trait, "setSkinName", new Class<?>[] {String.class, boolean.class}, skinName, true);
      } catch (NoSuchMethodException ex) {
        invoke(trait, "setSkinName", new Class<?>[] {String.class}, skinName);
      }
      return true;
    } catch (ReflectiveOperationException | LinkageError ex) {
      plugin.getLogger().warning("设置 Citizens 皮肤失败: " + rootMessage(ex));
      return false;
    }
  }

  private void syncConfiguredName(NPC npc) {
    String maidName = plugin.getMaidName();
    if (maidName == null || maidName.isBlank()) {
      return;
    }
    if (!maidName.equals(npc.getName()) && !maidName.equals(npc.getRawName())) {
      npc.setName(maidName);
    }
  }

  private String resolveSkinName(Player fallbackPlayer) {
    String skin = plugin.getMaidSkin();
    if (skin.equalsIgnoreCase("none") || skin.equalsIgnoreCase("default")) {
      return null;
    }
    if (skin.equalsIgnoreCase("master")) {
      Player master = plugin.getOnlineMaster();
      return master == null ? plugin.getMasterName() : master.getName();
    }
    if (skin.equalsIgnoreCase("player")) {
      return fallbackPlayer == null ? plugin.getMasterName() : fallbackPlayer.getName();
    }
    return skin;
  }

  private Object getSentinelTrait(NPC npc) throws ClassNotFoundException {
    return getTraitFromPlugin(npc, SENTINEL_PLUGIN, SENTINEL_TRAIT_CLASS);
  }

  private Object getTraitFromPlugin(NPC npc, String pluginName, String className)
      throws ClassNotFoundException {
    Class<?> rawTraitClass = loadClassFromPlugin(pluginName, className);
    Class<? extends Trait> traitClass = rawTraitClass.asSubclass(Trait.class);
    return npc.getOrAddTrait(traitClass);
  }

  private Class<?> loadClassFromPlugin(String pluginName, String className)
      throws ClassNotFoundException {
    Plugin dependency = plugin.getServer().getPluginManager().getPlugin(pluginName);
    if (dependency == null) {
      return Class.forName(className);
    }
    return Class.forName(className, true, dependency.getClass().getClassLoader());
  }

  private void configureSentinelCombat(Object trait) throws ReflectiveOperationException {
    cancelGuardFightbackTargets();
    plugin.clearMaidSelfDefenseTargets();
    cleanupSentinelCombat(trait);
    List<String> unsupported = new ArrayList<>();
    MaidCombatPolicy policy = plugin.getMaidCombatPolicy();
    int supportedTargets = 0;
    for (String target : policy.hostileTargetKeys()) {
      if (!optionalAddSentinelLabel(trait, "addTarget", target)) {
        unsupported.add("target:" + target);
      } else {
        supportedTargets++;
      }
    }
    for (String avoid : policy.avoidTargetKeys()) {
      if (!optionalAddSentinelLabel(trait, "addAvoid", avoid)) {
        unsupported.add("avoid:" + avoid);
      }
      optionalAddSentinelLabel(trait, "addIgnore", avoid);
    }

    applySentinelBaseConfiguration(trait, unsupported);
    reportSentinelCompatibilityIssues(unsupported);
    if (!policy.hostileTargetKeys().isEmpty() && supportedTargets == 0) {
      throw new ReflectiveOperationException("Sentinel 没有成功添加任何护卫攻击目标");
    }
  }

  private void applySentinelBaseConfiguration(Object trait, List<String> unsupported) {
    collectUnsupportedField(
        unsupported,
        "enemyDrops",
        optionalSetField(trait, "enemyDrops", plugin.isMaidEnemyDropsEnabled()));
    collectUnsupportedField(unsupported, "range", optionalSetField(trait, "range", 18.0));
    collectUnsupportedField(
        unsupported, "guardDistanceMinimum", optionalSetField(trait, "guardDistanceMinimum", 4.0));
    collectUnsupportedField(
        unsupported, "guardSelectionRange", optionalSetField(trait, "guardSelectionRange", 6.0));
    configureSentinelSurvivability(trait, unsupported);
  }

  private void scheduleGuardFightbackCleanup(String targetKey) {
    BukkitTask existingTask = guardFightbackCleanupTasks.remove(targetKey);
    if (existingTask != null) {
      existingTask.cancel();
    }
    BukkitTask task =
        Bukkit.getScheduler()
            .runTaskLater(
                plugin, () -> clearGuardFightbackTarget(targetKey), FIGHTBACK_TARGET_TICKS);
    guardFightbackCleanupTasks.put(targetKey, task);
  }

  private void clearGuardFightbackTarget(String targetKey) {
    guardFightbackCleanupTasks.remove(targetKey);
    if (!guarding || !isGuardAvailable()) {
      return;
    }
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return;
    }

    try {
      Object trait = getSentinelTrait(npc);
      optionalInvokeIgnored(trait, "removeTarget", new Class<?>[] {String.class}, targetKey);
      MaidCombatPolicy policy = plugin.getMaidCombatPolicy();
      if (policy != null && policy.avoidTargetKeys().contains(targetKey)) {
        optionalAddSentinelLabel(trait, "addAvoid", targetKey);
        optionalAddSentinelLabel(trait, "addIgnore", targetKey);
      }
    } catch (ClassNotFoundException | LinkageError ex) {
      plugin.getLogger().fine("清理 Sentinel 反击目标失败 " + targetKey + ": " + rootMessage(ex));
    }
  }

  private void cancelGuardFightbackTargets() {
    for (BukkitTask task : guardFightbackCleanupTasks.values()) {
      task.cancel();
    }
    guardFightbackCleanupTasks.clear();
  }

  private String sentinelPlayerTargetKey(UUID playerId) {
    return "uuid:" + playerId;
  }

  private List<String> sentinelLegacyPlayerTargetKeys(String playerName) {
    return List.of("PLAYER:" + playerName, "player:" + playerName);
  }

  private void cleanupSentinelCombat(Object trait) {
    MaidCombatPolicy policy = plugin.getMaidCombatPolicy();
    if (policy == null) {
      return;
    }
    for (String target : policy.managedTargetKeys()) {
      optionalInvokeIgnored(trait, "removeTarget", new Class<?>[] {String.class}, target);
    }
    for (String avoid : policy.managedAvoidKeys()) {
      optionalInvokeIgnored(trait, "removeAvoid", new Class<?>[] {String.class}, avoid);
      optionalInvokeIgnored(trait, "removeIgnore", new Class<?>[] {String.class}, avoid);
    }
  }

  private void clearSentinelNavigationState(Object trait) throws ReflectiveOperationException {
    // Removing target labels alone leaves Sentinel's live chase/return-home state active.
    Object helper = trait.getClass().getField("targetingHelper").get(trait);
    for (String field : List.of("currentTargets", "currentAvoids")) {
      ((Map<?, ?>) helper.getClass().getField(field).get(helper)).clear();
    }
    invoke(trait, "tryUpdateChaseTarget", new Class<?>[] {LivingEntity.class}, new Object[] {null});
    if (trait.getClass().getField("chasing").get(trait) != null) {
      throw new ReflectiveOperationException("Sentinel chase cancellation was rejected");
    }
    setField(trait, "pathingTo", null);
    setField(trait, "chased", false);
    setField(trait, "needsSafeReturn", false);
    optionalInvokeIgnored(trait, "stopBlocking", new Class<?>[0]);
  }

  private void configureSentinelSurvivability(Object trait, List<String> unsupported) {
    CraftMaidConfig.SurvivabilitySettings settings = plugin.getMaidSurvivabilitySettings();
    if (settings == null || !settings.enabled()) {
      return;
    }
    collectUnsupportedField(
        unsupported, "health", optionalSetField(trait, "health", settings.sentinelHealth()));
    optionalInvokeIgnored(
        trait, "setHealth", new Class<?>[] {double.class}, settings.sentinelHealth());
    collectUnsupportedField(
        unsupported, "armor", optionalSetField(trait, "armor", settings.sentinelArmor()));
    collectUnsupportedField(
        unsupported,
        "healRate",
        optionalSetField(trait, "healRate", Math.round(settings.sentinelHealrateSeconds() * 20.0)));
    collectUnsupportedField(
        unsupported,
        "respawnTime",
        optionalSetField(trait, "respawnTime", settings.sentinelRespawnSeconds() * 20L));
    collectUnsupportedField(
        unsupported,
        "invincible",
        optionalSetField(trait, "invincible", settings.sentinelInvincible()));
    boolean protectedSet =
        optionalSetField(trait, "protectFromIgnores", settings.sentinelProtected())
            || optionalSetField(trait, "protected", settings.sentinelProtected());
    collectUnsupportedField(unsupported, "protected", protectedSet);
    collectUnsupportedField(
        unsupported,
        "fightback",
        optionalSetField(trait, "fightback", settings.sentinelFightback()));
  }

  private void invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
      throws ReflectiveOperationException {
    target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);
  }

  private void addSentinelLabel(Object trait, String methodName, String key)
      throws ReflectiveOperationException {
    // Sentinel's addTarget/addAvoid/addIgnore skip the validation its commands perform.
    // An unknown label can clear the entire list during recalculateTargetsCache().
    Class<?> labelClass =
        Class.forName(
            "org.mcmonkey.sentinel.targeting.SentinelTargetLabel",
            true,
            trait.getClass().getClassLoader());
    Object label = labelClass.getConstructor(String.class).newInstance(key);
    for (String check : List.of("isValidTarget", "isValidPrefix", "isValidRegex", "isValidMulti")) {
      if (!Boolean.TRUE.equals(labelClass.getMethod(check).invoke(label))) {
        throw new ReflectiveOperationException("Sentinel 不支持目标: " + key);
      }
    }
    invoke(trait, methodName, new Class<?>[] {String.class}, key);
  }

  private void optionalInvoke(
      Object target, String methodName, Class<?>[] parameterTypes, Object... args)
      throws ReflectiveOperationException {
    try {
      invoke(target, methodName, parameterTypes, args);
    } catch (NoSuchMethodException ignored) {
      // Older Citizens builds do not expose every SkinTrait helper; setSkinName is enough.
    }
  }

  private void optionalInvokeIgnored(
      Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
    try {
      invoke(target, methodName, parameterTypes, args);
    } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException ignored) {
      // Sentinel versions differ in helper names; missing optional helpers are harmless.
    }
  }

  private boolean optionalAddSentinelLabel(Object target, String methodName, String key) {
    try {
      addSentinelLabel(target, methodName, key);
      return true;
    } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException ex) {
      return false;
    }
  }

  private void setField(Object target, String fieldName, Object value)
      throws ReflectiveOperationException {
    Field field = target.getClass().getField(fieldName);
    field.set(target, convertFieldValue(field.getType(), value));
  }

  private Object convertFieldValue(Class<?> fieldType, Object value) {
    if (!(value instanceof Number number)) {
      return value;
    }
    if (fieldType == int.class || fieldType == Integer.class) {
      return number.intValue();
    }
    if (fieldType == long.class || fieldType == Long.class) {
      return number.longValue();
    }
    if (fieldType == float.class || fieldType == Float.class) {
      return number.floatValue();
    }
    if (fieldType == double.class || fieldType == Double.class) {
      return number.doubleValue();
    }
    return value;
  }

  private boolean optionalSetField(Object target, String fieldName, Object value) {
    try {
      setField(target, fieldName, value);
      return true;
    } catch (ReflectiveOperationException | IllegalArgumentException ex) {
      return false;
    }
  }

  private void collectUnsupportedField(
      List<String> unsupported, String fieldName, boolean supported) {
    if (!supported) {
      unsupported.add("field:" + fieldName);
    }
  }

  private void reportSentinelCompatibilityIssues(List<String> unsupported) {
    List<String> newlyUnsupported =
        unsupported.stream().filter(reportedSentinelCompatibilityIssues::add).toList();
    if (!newlyUnsupported.isEmpty()) {
      plugin
          .getLogger()
          .warning("Sentinel compatibility unsupported: " + String.join(", ", newlyUnsupported));
    }
  }

  private String rootMessage(Throwable throwable) {
    Throwable cursor = throwable;
    while (cursor.getCause() != null) {
      cursor = cursor.getCause();
    }
    return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
  }
}
