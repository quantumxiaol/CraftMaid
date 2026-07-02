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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citizensnpcs.api.CitizensAPI;
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
  private static final float DEFAULT_NAVIGATOR_SPEED = 1.0F;
  private static final long FIGHTBACK_TARGET_TICKS = 15L * 20L;

  private final CraftMaid plugin;
  private final Map<String, BukkitTask> guardFightbackCleanupTasks = new HashMap<>();
  private BukkitTask followTask;
  private Location followLastLocation;
  private int followStuckTicks;
  private int followStuckRetries;
  private long followNextTeleportAtMillis;
  private boolean guarding;

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
    npc.spawn(player.getLocation());
    return true;
  }

  @Override
  public boolean despawnStored() {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return false;
    }

    stopFollowing();
    cancelGuardFightbackTargets();
    plugin.getMaidCombatBuffService().stop();
    guarding = false;
    if (npc.isSpawned()) {
      npc.despawn();
    }
    npc.destroy();
    plugin.getConfig().set("maid.npc_id", -1);
    plugin.saveConfig();
    return true;
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
      npc.spawn(home);
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

    if (!npc.isSpawned()) {
      npc.spawn(player.getLocation());
    }

    stopFollowing();
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
                  configureFollowNavigation(followNpc);
                  updateFollowTarget(followNpc, player);
                },
                updateTicks,
                updateTicks);
    return true;
  }

  @Override
  public boolean stopFollowing() {
    if (followTask != null) {
      followTask.cancel();
      followTask = null;
    }
    followLastLocation = null;
    followStuckTicks = 0;
    followStuckRetries = 0;
    followNextTeleportAtMillis = 0L;

    NPC npc = getStoredNpcOrNull();
    if (npc != null && npc.isSpawned()) {
      npc.getNavigator().cancelNavigation();
      resetNavigatorSpeed(npc);
    }
    return true;
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
    resetNavigatorSpeed(npc);
    return true;
  }

  @Override
  public boolean prepareForJobControl(boolean clearGuarding) {
    stopFollowing();
    stopFishingAnimation();
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

    configureDirectedNavigation(npc);
    npc.getNavigator().cancelNavigation();
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
    boolean started = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc fish " + targetText);
    return selected && started;
  }

  @Override
  public void stopFishingAnimation() {
    if (plugin.getServer().getPluginManager().isPluginEnabled("Denizen")) {
      Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "npc stopfishing");
    }
  }

  @Override
  public boolean openInventory(Player player) {
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
      invoke(trait, "setGuarding", new Class<?>[] {java.util.UUID.class}, player.getUniqueId());
      configureSentinelCombat(trait);
      guarding = true;
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
      invoke(trait, "addTarget", new Class<?>[] {String.class}, targetKey);
      scheduleGuardFightbackCleanup(targetKey);
      return true;
    } catch (ReflectiveOperationException | LinkageError ex) {
      plugin.getLogger().warning("添加 Sentinel 反击目标失败 " + targetKey + ": " + rootMessage(ex));
      return false;
    }
  }

  private boolean configureSentinelGuard(NPC npc, Location guardLocation, String label) {
    try {
      Object trait = getSentinelTrait(npc);
      setField(trait, "spawnPoint", guardLocation.clone());
      invoke(trait, "setGuarding", new Class<?>[] {java.util.UUID.class}, new Object[] {null});
      configureSentinelCombat(trait);
      guarding = true;
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
    guarding = false;
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
    if (npc != null && !npc.isSpawned()) {
      npc.spawn(player.getLocation());
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
    if (!npc.isSpawned()) {
      npc.spawn(location);
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

  private void configureFollowNavigation(NPC npc) {
    CraftMaidConfig.FollowSettings settings = plugin.getMaidFollowSettings();
    var parameters = npc.getNavigator().getLocalParameters();
    parameters.speed((float) settings.speed());
    parameters.updatePathRate(settings.updateTicks());
    parameters.distanceMargin(settings.stopDistance());
    parameters.pathDistanceMargin(settings.stopDistance());
    parameters.straightLineTargetingDistance((float) settings.straightLineDistance());
    // Citizens' destination teleport is too eager for a companion NPC. CraftMaid handles the
    // rare teleport fallback explicitly, with distance gates and cooldowns.
    parameters.destinationTeleportMargin(-1.0);
    parameters.avoidWater(true);
  }

  private void configureDirectedNavigation(NPC npc) {
    CraftMaidConfig.JobNavigationSettings settings = plugin.getJobNavigationSettings();
    var parameters = npc.getNavigator().getLocalParameters();
    parameters.speed((float) settings.speed());
    parameters.updatePathRate(settings.updateTicks());
    parameters.distanceMargin(settings.arrivalDistance());
    parameters.pathDistanceMargin(settings.arrivalDistance());
    parameters.straightLineTargetingDistance((float) settings.straightLineDistance());
    parameters.destinationTeleportMargin(-1.0);
    parameters.avoidWater(true);
  }

  private void resetNavigatorSpeed(NPC npc) {
    npc.getNavigator().getLocalParameters().speed(DEFAULT_NAVIGATOR_SPEED);
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
      if (!maybeTeleportNearPlayer(npc, player, settings, true)) {
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
        && maybeTeleportNearPlayer(npc, player, settings, false)) {
      return;
    }

    if (isFollowStuck(npcLocation, settings)) {
      followStuckRetries++;
      followLastLocation = npcLocation;
      followStuckTicks = 0;
      npc.getNavigator().cancelNavigation();
      npc.getNavigator().setTarget(player, false);
      double stuckTeleportMinDistanceSquared =
          settings.stuckTeleportMinDistance() * settings.stuckTeleportMinDistance();
      if (followStuckRetries >= settings.stuckRetryBeforeTeleport()
          && distanceSquared >= stuckTeleportMinDistanceSquared
          && maybeTeleportNearPlayer(npc, player, settings, false)) {
        return;
      }
      return;
    }

    npc.getNavigator().setTarget(player, false);
  }

  private boolean isFollowStuck(Location npcLocation, CraftMaidConfig.FollowSettings settings) {
    if (settings.teleportOnStuckSeconds() <= 0) {
      resetFollowStuck(npcLocation);
      return false;
    }
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
    return followStuckTicks >= settings.teleportOnStuckSeconds() * 20;
  }

  private void resetFollowStuck(Location npcLocation) {
    followLastLocation = npcLocation;
    followStuckTicks = 0;
    followStuckRetries = 0;
  }

  private boolean maybeTeleportNearPlayer(
      NPC npc, Player player, CraftMaidConfig.FollowSettings settings, boolean ignoreCooldown) {
    if (!settings.teleportEnabled()) {
      return false;
    }
    long now = System.currentTimeMillis();
    if (!ignoreCooldown && now < followNextTeleportAtMillis) {
      return false;
    }
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
      return plugin.getMasterName();
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
    cleanupSentinelCombat(trait);
    MaidCombatPolicy policy = plugin.getMaidCombatPolicy();
    for (String target : policy.hostileTargetKeys()) {
      optionalInvokeWarn(
          trait, "addTarget", new Class<?>[] {String.class}, target, "Sentinel target");
    }
    for (String avoid : policy.avoidTargetKeys()) {
      optionalInvokeWarn(trait, "addAvoid", new Class<?>[] {String.class}, avoid, "Sentinel avoid");
      optionalInvokeIgnored(trait, "addIgnore", new Class<?>[] {String.class}, avoid);
    }

    optionalSetField(trait, "enemyDrops", plugin.isMaidEnemyDropsEnabled());
    optionalSetField(trait, "range", 18.0);
    optionalSetField(trait, "guardDistanceMinimum", 4.0);
    optionalSetField(trait, "guardSelectionRange", 6.0);
    configureSentinelSurvivability(trait);
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
        optionalInvokeIgnored(trait, "addAvoid", new Class<?>[] {String.class}, targetKey);
        optionalInvokeIgnored(trait, "addIgnore", new Class<?>[] {String.class}, targetKey);
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

  private void configureSentinelSurvivability(Object trait) {
    CraftMaidConfig.SurvivabilitySettings settings = plugin.getMaidSurvivabilitySettings();
    if (settings == null || !settings.enabled()) {
      return;
    }
    optionalSetField(trait, "health", settings.sentinelHealth());
    optionalInvokeIgnored(
        trait, "setHealth", new Class<?>[] {double.class}, settings.sentinelHealth());
    optionalSetField(trait, "armor", settings.sentinelArmor());
    optionalSetField(trait, "healRate", settings.sentinelHealrateSeconds());
    optionalSetField(trait, "respawnTime", settings.sentinelRespawnSeconds());
    optionalSetField(trait, "invincible", settings.sentinelInvincible());
    optionalSetField(trait, "protected", settings.sentinelProtected());
    optionalSetField(trait, "fightback", settings.sentinelFightback());
  }

  private void invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
      throws ReflectiveOperationException {
    target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);
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

  private void optionalInvokeWarn(
      Object target, String methodName, Class<?>[] parameterTypes, Object arg, String label) {
    try {
      invoke(target, methodName, parameterTypes, arg);
    } catch (ReflectiveOperationException | LinkageError | IllegalArgumentException ex) {
      plugin.getLogger().warning("跳过 " + label + " " + arg + ": " + rootMessage(ex));
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

  private void optionalSetField(Object target, String fieldName, Object value) {
    try {
      setField(target, fieldName, value);
    } catch (ReflectiveOperationException | IllegalArgumentException ex) {
      plugin.getLogger().warning("跳过 Sentinel 可选字段 " + fieldName + ": " + rootMessage(ex));
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
