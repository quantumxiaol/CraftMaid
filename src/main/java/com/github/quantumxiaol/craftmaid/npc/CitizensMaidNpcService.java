package com.github.quantumxiaol.craftmaid.npc;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.interaction.CitizensMaidInteractionListener;
import com.github.quantumxiaol.craftmaid.menu.MaidMenuService;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

public final class CitizensMaidNpcService implements MaidNpcService {
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
  public boolean setHomeAtMaidLocation(Player fallbackPlayer) {
    NPC npc = getStoredNpcOrNull();
    Location home =
        npc != null && npc.isSpawned() ? npc.getStoredLocation() : fallbackPlayer.getLocation();
    saveHomeLocation(home);
    return true;
  }

  @Override
  public Location getHomeLocation() {
    String worldName = plugin.getConfig().getString("maid.home.world", "");
    if (worldName == null || worldName.isBlank()) {
      return null;
    }

    World world = Bukkit.getWorld(worldName);
    if (world == null) {
      return null;
    }

    double x = plugin.getConfig().getDouble("maid.home.x");
    double y = plugin.getConfig().getDouble("maid.home.y");
    double z = plugin.getConfig().getDouble("maid.home.z");
    float yaw = (float) plugin.getConfig().getDouble("maid.home.yaw");
    float pitch = (float) plugin.getConfig().getDouble("maid.home.pitch");
    return new Location(world, x, y, z, yaw, pitch);
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
  public boolean isGuardAvailable() {
    if (!plugin.getServer().getPluginManager().isPluginEnabled(SENTINEL_PLUGIN)) {
      return false;
    }

    try {
      Class.forName(SENTINEL_TRAIT_CLASS);
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
      setHomeAtMaidLocation(player);
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
    return npc;
  }

  private NPC getStoredNpcOrNull() {
    int npcId = plugin.getConfig().getInt("maid.npc_id", -1);
    if (npcId < 0) {
      return null;
    }
    return CitizensAPI.getNPCRegistry().getById(npcId);
  }

  private void saveHomeLocation(Location home) {
    plugin
        .getConfig()
        .set("maid.home.world", home.getWorld() == null ? "" : home.getWorld().getName());
    plugin.getConfig().set("maid.home.x", home.getX());
    plugin.getConfig().set("maid.home.y", home.getY());
    plugin.getConfig().set("maid.home.z", home.getZ());
    plugin.getConfig().set("maid.home.yaw", home.getYaw());
    plugin.getConfig().set("maid.home.pitch", home.getPitch());
    plugin.saveConfig();
  }

  private Object getSentinelTrait(NPC npc) throws ClassNotFoundException {
    Class<?> rawTraitClass = Class.forName(SENTINEL_TRAIT_CLASS);
    Class<? extends Trait> traitClass = rawTraitClass.asSubclass(Trait.class);
    return npc.getOrAddTrait(traitClass);
  }

  private void configureSentinelCombat(Object trait) throws ReflectiveOperationException {
    invoke(trait, "addTarget", new Class<?>[] {String.class}, "monsters");
    invoke(trait, "addAvoid", new Class<?>[] {String.class}, "creepers");
    setField(trait, "range", 18.0);
    setField(trait, "guardDistanceMinimum", 4.0);
    setField(trait, "guardSelectionRange", 6.0);
  }

  private void invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
      throws ReflectiveOperationException {
    target.getClass().getMethod(methodName, parameterTypes).invoke(target, args);
  }

  private void setField(Object target, String fieldName, Object value)
      throws ReflectiveOperationException {
    target.getClass().getField(fieldName).set(target, value);
  }

  private String rootMessage(Throwable throwable) {
    Throwable cursor = throwable;
    while (cursor.getCause() != null) {
      cursor = cursor.getCause();
    }
    return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage();
  }
}
