package com.github.quantumxiaol.craftmaid.job;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.control.MaidControlService;
import com.github.quantumxiaol.craftmaid.job.MaidJobService.JobActionResult;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaidJobServiceTest {
  CraftMaid plugin;
  MaidNpcService npc;
  MaidJobService jobs;
  MaidJob job;

  @BeforeEach
  void setup() {
    plugin = mock(CraftMaid.class);
    npc = mock(MaidNpcService.class);
    when(plugin.getMaidNpcService()).thenReturn(npc);
    when(plugin.getMaidControlService()).thenReturn(new MaidControlService(plugin));
    when(npc.isAvailable()).thenReturn(true);
    when(npc.prepareForJobControl(true)).thenReturn(true);
    jobs = new MaidJobService(plugin);
    job = mock(MaidJob.class);
    when(job.type()).thenReturn(MaidJobType.FISHING);
    when(job.prepare()).thenReturn(JobActionResult.success("prepared"));
    when(job.start()).thenReturn(JobActionResult.success("started"));
  }

  @Test
  void invalidWaterOrStandingPointDoesNotStopFollowing() {
    when(job.prepare()).thenReturn(JobActionResult.failure("no water"));
    assertFalse(jobs.startJob(job).success());
    verify(npc, never()).stopFollowing();
    verify(npc, never()).prepareForJobControl(anyBoolean());
    verify(job).discardPreparation();
    verify(job, never()).start();
  }

  @Test
  void preflightFinishesBeforeTakingBodyControl() {
    assertTrue(jobs.startJob(job).success());
    var order = inOrder(job, npc);
    order.verify(job).prepare();
    order.verify(npc).stopFollowing();
    order.verify(npc).prepareForJobControl(true);
    order.verify(job).start();
  }

  @Test
  void navigationStartFailureRestoresPreviousFollow() {
    Player followed = mock(Player.class);
    when(followed.isOnline()).thenReturn(true);
    when(npc.getFollowingPlayer()).thenReturn(followed);
    when(job.start()).thenReturn(JobActionResult.failure("spawn rejected"));
    assertFalse(jobs.startJob(job).success());
    verify(npc).startFollowing(followed);
    verify(job).discardPreparation();
  }

  @Test
  void generalStopClearsBothLegacyControllersAndInvalidatesPendingPlans() {
    when(npc.isFollowing()).thenReturn(true);
    when(npc.isGuarding()).thenReturn(true);
    when(npc.stopGuarding()).thenReturn(true);
    long before = plugin.getMaidControlService().revision();
    assertTrue(jobs.stopActiveJob("stop").success());
    verify(npc).prepareForJobControl(true);
    assertFalse(plugin.getMaidControlService().isCurrent(before));
  }
}
