package com.github.quantumxiaol.craftmaid.control;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.job.MaidJobService;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaidControlServiceTest {
  CraftMaid plugin;
  MaidNpcService npc;
  MaidJobService jobs;
  MaidControlService control;

  @BeforeEach
  void setup() {
    plugin = mock(CraftMaid.class);
    npc = mock(MaidNpcService.class);
    jobs = mock(MaidJobService.class);
    when(plugin.getMaidNpcService()).thenReturn(npc);
    when(plugin.getJobService()).thenReturn(jobs);
    when(plugin.getMaidName()).thenReturn("Lucy");
    when(npc.isAvailable()).thenReturn(true);
    when(npc.isGuardAvailable()).thenReturn(true);
    when(npc.prepareForJobControl(true)).thenReturn(true);
    control = new MaidControlService(plugin);
  }

  @Test
  void followTakesControlBeforeStartingNavigation() {
    Player player = mock(Player.class);
    when(npc.startFollowing(player)).thenReturn(true);
    assertTrue(control.startFollowing(player));
    var order = inOrder(jobs, npc);
    order.verify(jobs).stopActiveJobForExternalControl(anyString());
    order.verify(npc).prepareForJobControl(true);
    order.verify(npc).startFollowing(player);
  }

  @Test
  void guardSwitchUsesTheSamePreparationAndPreservesChunkJobPolicy() {
    Player player = mock(Player.class);
    control.startGuardingHere(player);
    var order = inOrder(jobs, npc);
    order.verify(jobs).stopJobsForGuarding(anyString());
    order.verify(npc).prepareForJobControl(true);
    order.verify(npc).startGuardingHere(player);
    verify(jobs, never()).stopActiveJobForExternalControl(anyString());
  }

  @Test
  void aStopInvalidatesPlansEvenWhenThereIsNoActiveFollow() {
    long firstPlayersPlan = control.revision();
    long secondPlayersPlan = control.revision();
    control.stopFollowing();
    assertFalse(control.isCurrent(firstPlayersPlan));
    assertFalse(control.isCurrent(secondPlayersPlan));
  }

  @Test
  void failedGuardCleanupDoesNotStartNewNavigation() {
    when(npc.prepareForJobControl(true)).thenReturn(false);
    assertFalse(control.startFollowing(mock(Player.class)));
    verify(npc, never()).startFollowing(any());
  }

  @Test
  void recallStopsWorkAndGuardBeforeMoving() {
    Player player = mock(Player.class);
    control.recall(player);
    var order = inOrder(jobs, npc);
    order.verify(jobs).stopActiveJobForExternalControl(anyString());
    order.verify(npc).prepareForJobControl(true);
    order.verify(npc).spawnAt(player, "Lucy");
  }
}
