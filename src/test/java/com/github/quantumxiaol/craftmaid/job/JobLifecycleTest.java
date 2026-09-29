package com.github.quantumxiaol.craftmaid.job;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.anchor.AnchorRegion;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.*;
import com.github.quantumxiaol.craftmaid.inventory.MaidInventoryService;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobLifecycleTest {
  CraftMaid plugin;
  MaidNpcService npc;
  MaidJobService jobs;
  World world;
  Location spot;
  AtomicBoolean arrived;
  BukkitTask task;

  @BeforeEach
  void setup() {
    plugin = mock(CraftMaid.class);
    npc = mock(MaidNpcService.class);
    jobs = mock(MaidJobService.class);
    world = mock(World.class);
    when(world.getName()).thenReturn("world");
    spot = new Location(world, 0, 64, 0);
    when(plugin.getMaidNpcService()).thenReturn(npc);
    when(plugin.getMaidInventoryService()).thenReturn(mock(MaidInventoryService.class));
    var server = mock(Server.class);
    when(plugin.getServer()).thenReturn(server);
    when(server.getWorld("world")).thenReturn(world);
    var scheduler = mock(BukkitScheduler.class);
    when(server.getScheduler()).thenReturn(scheduler);
    task = mock(BukkitTask.class);
    when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
        .thenReturn(task);
    when(plugin.getJobEventBuffer()).thenReturn(new MaidJobEventBuffer());
    when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    when(plugin.getJobNavigationSettings())
        .thenReturn(new JobNavigationSettings(1.5, 20, 3, 5, 50, 12));
    when(plugin.getFishingSettings())
        .thenReturn(new FishingSettings(200, 200, 1, 0, 0, false, false));
    when(plugin.getChunkKeeperSettings()).thenReturn(new ChunkKeeperSettings(0, true));
    var tickets = new java.util.HashSet<String>();
    when(world.addPluginChunkTicket(anyInt(), anyInt(), eq(plugin)))
        .thenAnswer(call -> tickets.add(call.getArgument(0) + ":" + call.getArgument(1)));
    when(npc.moveTo(any())).thenReturn(true);
    arrived = new AtomicBoolean(false);
    when(npc.isNear(any(), anyDouble())).thenAnswer(call -> arrived.get());
  }

  @Test
  void fishingStopsWithoutLootWhenMaidLeavesOrDespawns() {
    Block water = mock(Block.class);
    when(water.getType()).thenReturn(Material.WATER);
    when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(water);
    try (var targets = mockStatic(JobNavigationTargets.class)) {
      targets.when(() -> JobNavigationTargets.findSafeVerticalLocation(spot)).thenReturn(spot);
      var job =
          new FishingJob(
              plugin,
              jobs,
              UUID.randomUUID(),
              "pond",
              spot,
              new AnchorRegion("world", 0, 64, 0, 0, 64, 0));
      assertTrue(job.prepare().success());
      assertTrue(job.start().success());
      arrived.set(true);
      job.run();
      assertEquals(JobPhase.RUNNING, job.phase());
      arrived.set(false);
      job.run();
      assertEquals(JobPhase.FAILED, job.phase());
      job.run();
      verifyNoInteractions(plugin.getMaidInventoryService());
      verify(task).cancel();
      verify(world).removePluginChunkTicket(0, 0, plugin);
    }
  }

  @Test
  void chunkJobCannotStopANewerGuard() {
    var revision = new AtomicLong(0);
    when(npc.guardingRevision()).thenAnswer(call -> revision.get());
    when(npc.startGuardingAt(any()))
        .thenAnswer(
            call -> {
              revision.incrementAndGet();
              return true;
            });
    try (var targets = mockStatic(JobNavigationTargets.class)) {
      targets.when(() -> JobNavigationTargets.findSafeVerticalLocation(spot)).thenReturn(spot);
      var job = new ChunkKeeperJob(plugin, jobs, UUID.randomUUID(), "machine", spot);
      assertTrue(job.prepare().success());
      assertTrue(job.start().success());
      assertEquals(JobPhase.TRAVELLING, job.phase());
      verify(npc, never()).startGuardingAt(any());
      arrived.set(true);
      job.run();
      assertEquals(JobPhase.RUNNING, job.phase());
      verify(npc).startGuardingAt(spot);
      revision.incrementAndGet(); // Another guard replaces this job's guard.
      job.stop("stop");
      verify(npc, never()).stopGuarding();
      verify(world).removePluginChunkTicket(0, 0, plugin);
    }
  }

  @Test
  void unreachableChunkWatchPointTimesOutAndReleasesTickets() {
    try (var targets = mockStatic(JobNavigationTargets.class)) {
      targets.when(() -> JobNavigationTargets.findSafeVerticalLocation(spot)).thenReturn(spot);
      var job = new ChunkKeeperJob(plugin, jobs, UUID.randomUUID(), "machine", spot);
      assertTrue(job.prepare().success());
      assertTrue(job.start().success());
      for (int i = 0; i < 5; i++) job.run();
      assertFalse(job.isRunning());
      verify(npc, never()).startGuardingAt(any());
      verify(world).removePluginChunkTicket(0, 0, plugin);
    }
  }

  @Test
  void chunkLoadingAlongsideExistingGuardDoesNotMoveOrStopIt() {
    when(npc.isGuarding()).thenReturn(true);
    var job = new ChunkKeeperJob(plugin, jobs, UUID.randomUUID(), "machine", spot);
    assertTrue(job.prepare().success());
    assertTrue(job.start().success());
    assertTrue(job.statusLine().contains("body=external"));
    assertTrue(job.statusLine().contains("present=false"));
    job.stop("stop");
    verify(npc, never()).moveTo(any());
    verify(npc, never()).stopMoving();
    verify(npc, never()).stopGuarding();
  }
}
