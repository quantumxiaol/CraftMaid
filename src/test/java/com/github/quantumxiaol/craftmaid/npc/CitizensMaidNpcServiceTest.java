package com.github.quantumxiaol.craftmaid.npc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.FollowSettings;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.SurvivabilitySettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.ai.EntityTarget;
import net.citizensnpcs.api.ai.Navigator;
import net.citizensnpcs.api.ai.NavigatorParameters;
import net.citizensnpcs.api.ai.StuckAction;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

class CitizensMaidNpcServiceTest {
  @Test
  void followingReusesTargetRepathsOnlyWhenStalledAndRestoresParameters() {
    CraftMaid plugin = mock(CraftMaid.class);
    NPC npc = mock(NPC.class);
    NPCRegistry registry = mock(NPCRegistry.class);
    Navigator navigator = mock(Navigator.class);
    LivingEntity entity = mock(LivingEntity.class);
    Player player = mock(Player.class);
    World world = mock(World.class);
    BukkitScheduler scheduler = mock(BukkitScheduler.class);
    var config = new YamlConfiguration();
    config.set("maid.npc_id", 1);
    when(plugin.getConfig()).thenReturn(config);
    when(plugin.getMaidFollowSettings())
        .thenReturn(new FollowSettings(1.75, 10, 3, 8, false, 128, 0, 30, 3, 24, 12, -1));
    when(registry.getById(1)).thenReturn(npc);
    when(npc.isSpawned()).thenReturn(true);
    when(npc.getEntity()).thenReturn(entity);
    when(npc.getNavigator()).thenReturn(navigator);
    when(player.isOnline()).thenReturn(true);
    when(entity.getLocation()).thenReturn(new Location(world, 0, 64, 0));
    when(player.getLocation()).thenReturn(new Location(world, 20, 64, 0));
    StuckAction originalStuckAction = mock(StuckAction.class);
    var defaults =
        new NavigatorParameters()
            .baseSpeed(0.3F)
            .speedModifier(1.2F)
            .distanceMargin(1.1)
            .pathDistanceMargin(0.7)
            .updatePathRate(19)
            .straightLineTargetingDistance(4)
            .destinationTeleportMargin(15)
            .avoidWater(false)
            .stuckAction(originalStuckAction);
    var local = new AtomicReference<>(defaults);
    var target = new AtomicReference<EntityTarget>();
    EntityTarget playerTarget = mock(EntityTarget.class);
    when(playerTarget.getTarget()).thenReturn(player);
    when(navigator.getDefaultParameters()).thenReturn(defaults);
    when(navigator.getLocalParameters()).thenAnswer(call -> local.get());
    when(navigator.getEntityTarget()).thenAnswer(call -> target.get());
    when(navigator.isNavigating()).thenAnswer(call -> target.get() != null);
    doAnswer(
            call -> {
              target.set(null);
              return null;
            })
        .when(navigator)
        .cancelNavigation();
    doAnswer(
            call -> {
              target.set(playerTarget);
              local.set(defaults.clone());
              return null;
            })
        .when(navigator)
        .setTarget(player, false);
    var tick = new AtomicReference<Runnable>();
    when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(10L), eq(10L)))
        .thenAnswer(
            call -> {
              tick.set(call.getArgument(1));
              return mock(BukkitTask.class);
            });
    try (var citizens = mockStatic(CitizensAPI.class);
        var bukkit = mockStatic(Bukkit.class)) {
      citizens.when(CitizensAPI::getNPCRegistry).thenReturn(registry);
      bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
      var service = new CitizensMaidNpcService(plugin);
      assertTrue(service.startFollowing(player));
      for (int i = 0; i < 9; i++) tick.get().run();
      verify(navigator, times(1)).setTarget(player, false);
      assertEquals(0, local.get().straightLineTargetingDistance(), "Walls disable direct steering");
      assertEquals(0.3F, defaults.baseSpeed());
      assertEquals(1.75F, defaults.speedModifier());
      tick.get().run();
      verify(navigator, times(2)).setTarget(player, false);
      when(entity.hasLineOfSight(player)).thenReturn(true);
      tick.get().run();
      assertEquals(12, local.get().straightLineTargetingDistance());
      when(player.getLocation()).thenReturn(new Location(world, 6, 64, 0));
      tick.get().run();
      assertFalse(navigator.isNavigating(), "Documented 3–8 block idle band is preserved");
      service.stopFollowing();
      assertEquals(1.2F, defaults.speedModifier());
      assertEquals(0.3F, defaults.baseSpeed());
      assertEquals(1.1, defaults.distanceMargin());
      assertEquals(0.7, defaults.pathDistanceMargin());
      assertEquals(19, defaults.updatePathRate());
      assertEquals(4, defaults.straightLineTargetingDistance());
      assertEquals(15, defaults.destinationTeleportMargin());
      assertFalse(defaults.avoidWater());
      assertSame(originalStuckAction, defaults.stuckAction());
      clearInvocations(navigator);
      service.stopFollowing();
      verify(navigator, never()).cancelNavigation();
    }
  }

  @Test
  void sentinelUsesTicksAndClearsLiveChaseState() throws Exception {
    CraftMaid plugin = mock(CraftMaid.class);
    when(plugin.getMaidSurvivabilitySettings())
        .thenReturn(
            new SurvivabilitySettings(true, 40, .5, 2, 10, false, true, true, false, 1, 1, 2, 20));
    var service = new CitizensMaidNpcService(plugin);
    var trait = new SentinelFixture();
    var configure =
        CitizensMaidNpcService.class.getDeclaredMethod(
            "configureSentinelSurvivability", Object.class, java.util.List.class);
    configure.setAccessible(true);
    configure.invoke(service, trait, new ArrayList<String>());
    assertEquals(40, trait.healRate);
    assertEquals(200L, trait.respawnTime);
    trait.targetingHelper.currentTargets.put(UUID.randomUUID(), new Object());
    trait.targetingHelper.currentAvoids.put(UUID.randomUUID(), new Object());
    trait.chasing = mock(LivingEntity.class);
    var cleanup =
        CitizensMaidNpcService.class.getDeclaredMethod(
            "clearSentinelNavigationState", Object.class);
    cleanup.setAccessible(true);
    cleanup.invoke(service, trait);
    assertTrue(trait.targetingHelper.currentTargets.isEmpty());
    assertTrue(trait.targetingHelper.currentAvoids.isEmpty());
    assertNull(trait.chasing);
    assertNull(trait.pathingTo);
    assertFalse(trait.needsSafeReturn);
    assertFalse(trait.chased);
  }

  // Reflection contract only; actual Sentinel runtime remains an integration check.
  public static class SentinelFixture {
    public int healRate;
    public long respawnTime;
    public Helper targetingHelper = new Helper();
    public LivingEntity chasing;
    public Location pathingTo = new Location(null, 0, 0, 0);
    public boolean needsSafeReturn = true;
    public boolean chased = true;

    public boolean tryUpdateChaseTarget(LivingEntity entity) {
      chasing = entity;
      return true;
    }
  }

  public static class Helper {
    public Map<UUID, Object> currentTargets = new HashMap<>();
    public Map<UUID, Object> currentAvoids = new HashMap<>();
  }
}
