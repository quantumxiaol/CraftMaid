package com.github.quantumxiaol.craftmaid.npc;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.anchor.AnchorType;
import com.github.quantumxiaol.craftmaid.anchor.MaidAnchorService;
import com.github.quantumxiaol.craftmaid.interaction.CitizensMaidInteractionListener;
import com.github.quantumxiaol.craftmaid.menu.MaidMenuService;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.trait.Equipment;
import net.citizensnpcs.api.trait.trait.Equipment.EquipmentSlot;
import net.citizensnpcs.api.trait.trait.Inventory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public final class CitizensMaidNpcService implements MaidNpcService {
  private static final String CITIZENS_PLUGIN = "Citizens";
  private static final String SKIN_TRAIT_CLASS = "net.citizensnpcs.trait.SkinTrait";
  private static final String SENTINEL_PLUGIN = "Sentinel";
  private static final String SENTINEL_TRAIT_CLASS = "org.mcmonkey.sentinel.SentinelTrait";

  private final CraftMaid plugin;
  private BukkitTask followTask;

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
    return applyConfiguredSkin(npc, fallbackPlayer);
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
    if (npc == null) {
      npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, plugin.getMaidName());
      plugin.getConfig().set("maid.npc_id", npc.getId());
      plugin.saveConfig();
    }

    applyConfiguredSkin(npc, null);
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

    applyConfiguredSkin(npc, player);
    stopFollowing();
    NPC followNpc = npc;
    followNpc.getNavigator().setTarget(player, false);
    followTask =
        Bukkit.getScheduler()
            .runTaskTimer(
                plugin,
                () -> {
                  if (!player.isOnline() || !followNpc.isSpawned()) {
                    stopFollowing();
                    return;
                  }
                  followNpc.getNavigator().setTarget(player, false);
                },
                20L,
                20L);
    return true;
  }

  @Override
  public boolean stopFollowing() {
    if (followTask != null) {
      followTask.cancel();
      followTask = null;
    }

    NPC npc = getStoredNpcOrNull();
    if (npc != null && npc.isSpawned()) {
      npc.getNavigator().cancelNavigation();
    }
    return true;
  }

  @Override
  public boolean isFollowing() {
    return followTask != null;
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
  public boolean startGuarding(Player player) {
    NPC npc = ensureSpawnedNear(player);
    if (npc == null || !isGuardAvailable()) {
      return false;
    }

    try {
      Object trait = getSentinelTrait(npc);
      invoke(trait, "setGuarding", new Class<?>[] {java.util.UUID.class}, player.getUniqueId());
      configureSentinelCombat(trait);
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

    try {
      Object trait = getSentinelTrait(npc);
      setField(trait, "spawnPoint", npc.getStoredLocation());
      invoke(trait, "setGuarding", new Class<?>[] {java.util.UUID.class}, new Object[] {null});
      configureSentinelCombat(trait);
      return true;
    } catch (ReflectiveOperationException | LinkageError ex) {
      plugin.getLogger().warning("启动 Sentinel 守卫失败: " + rootMessage(ex));
      return false;
    }
  }

  @Override
  public boolean stopGuarding() {
    NPC npc = getStoredNpcOrNull();
    if (npc == null || !isGuardAvailable()) {
      return false;
    }

    try {
      Object trait = getSentinelTrait(npc);
      invoke(trait, "setGuarding", new Class<?>[] {java.util.UUID.class}, new Object[] {null});
      invoke(trait, "removeTarget", new Class<?>[] {String.class}, "monsters");
      invoke(trait, "removeAvoid", new Class<?>[] {String.class}, "creepers");
      return true;
    } catch (ReflectiveOperationException | LinkageError ex) {
      plugin.getLogger().warning("停止 Sentinel 护卫失败: " + rootMessage(ex));
      return false;
    }
  }

  private NPC ensureSpawnedNear(Player player) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      spawnAt(player, plugin.getMaidName());
      npc = getStoredNpcOrNull();
    }
    if (npc != null && !npc.isSpawned()) {
      npc.spawn(player.getLocation());
    }
    if (npc != null) {
      applyConfiguredSkin(npc, player);
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
    invoke(trait, "addTarget", new Class<?>[] {String.class}, "monsters");
    invoke(trait, "addAvoid", new Class<?>[] {String.class}, "creepers");
    optionalSetField(trait, "enemyDrops", plugin.isMaidEnemyDropsEnabled());
    setField(trait, "range", 18.0);
    setField(trait, "guardDistanceMinimum", 4.0);
    setField(trait, "guardSelectionRange", 6.0);
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

  private void setField(Object target, String fieldName, Object value)
      throws ReflectiveOperationException {
    target.getClass().getField(fieldName).set(target, value);
  }

  private void optionalSetField(Object target, String fieldName, Object value)
      throws ReflectiveOperationException {
    try {
      setField(target, fieldName, value);
    } catch (NoSuchFieldException ignored) {
      // Older or newer Sentinel builds may expose this only through commands.
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
