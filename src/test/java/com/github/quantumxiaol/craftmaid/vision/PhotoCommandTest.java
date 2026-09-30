package com.github.quantumxiaol.craftmaid.vision;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.command.CraftMaidCommand;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class PhotoCommandTest {
  @Test
  void assetCommandsAreAvailableToAdminsWithoutRequiringMaidControl() {
    CraftMaid plugin = mock(CraftMaid.class);
    MaidVisionService vision = mock(MaidVisionService.class);
    Player player = mock(Player.class);
    Command command = mock(Command.class);
    when(plugin.getVisionService()).thenReturn(vision);
    CraftMaidCommand executor = new CraftMaidCommand(plugin);
    executor.onCommand(player, command, "maid", new String[] {"vision", "prepare"});
    verifyNoInteractions(vision);
    assertTrue(
        executor.onTabComplete(player, command, "maid", new String[] {"vision", ""}).isEmpty());
    when(player.hasPermission("craftmaid.admin")).thenReturn(true);
    when(vision.prepareAssets(any())).thenReturn(new MaidVisionService.StartResult(true, "准备中"));
    when(vision.assetStatus()).thenReturn("下载：10 / 38 MB");
    executor.onCommand(player, command, "maid", new String[] {"vision", "prepare"});
    executor.onCommand(player, command, "maid", new String[] {"vision", "status"});
    verify(vision).prepareAssets(any());
    verify(vision).assetStatus();
    verify(vision, never()).capture(any());
    assertEquals(
        java.util.List.of("prepare", "status"),
        executor.onTabComplete(player, command, "maid", new String[] {"vision", ""}));
    assertTrue(
        executor.onTabComplete(player, command, "maid", new String[] {"vi"}).contains("vision"));
  }

  @Test
  void photoRequiresControlAndIsOnlySuggestedToControllers() {
    CraftMaid plugin = mock(CraftMaid.class);
    MaidVisionService vision = mock(MaidVisionService.class);
    Player player = mock(Player.class);
    Command command = mock(Command.class);
    when(plugin.getVisionService()).thenReturn(vision);
    CraftMaidCommand executor = new CraftMaidCommand(plugin);
    assertTrue(executor.onCommand(player, command, "maid", new String[] {"photo"}));
    verifyNoInteractions(vision);
    assertFalse(
        executor.onTabComplete(player, command, "maid", new String[] {"ph"}).contains("photo"));
    when(plugin.canControlMaid(player)).thenReturn(true);
    when(vision.capture(any())).thenReturn(new MaidVisionService.StartResult(true, "started"));
    assertTrue(executor.onCommand(player, command, "maid", new String[] {"photo"}));
    verify(vision).capture(any());
    assertTrue(
        executor.onTabComplete(player, command, "maid", new String[] {"ph"}).contains("photo"));
  }
}
