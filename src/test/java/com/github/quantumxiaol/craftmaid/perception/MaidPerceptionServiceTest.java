package com.github.quantumxiaol.craftmaid.perception;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.*;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class MaidPerceptionServiceTest {
  @Test
  void inspectionScansMaidEyePositionAndBypassesCachedBlockCounts() {
    var plugin = mock(CraftMaid.class);
    var npc = mock(MaidNpcService.class);
    var maid = mock(LivingEntity.class);
    var player = mock(Player.class);
    var world = mock(World.class);
    var eye = new Location(world, 32.5, 65.6, -16.5);
    when(plugin.getMaidNpcService()).thenReturn(npc);
    when(npc.getMaidLivingEntity()).thenReturn(maid);
    when(maid.isValid()).thenReturn(true);
    when(maid.getUniqueId()).thenReturn(UUID.randomUUID());
    when(maid.getEyeLocation()).thenReturn(eye);
    when(world.getName()).thenReturn("maid-world");
    when(world.getUID()).thenReturn(UUID.randomUUID());
    when(world.getMinHeight()).thenReturn(-64);
    when(world.getMaxHeight()).thenReturn(320);
    when(world.isChunkLoaded(2, -2)).thenReturn(true);
    when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of());
    var block = mock(Block.class);
    when(world.getBlockAt(32, 65, -17)).thenReturn(block);
    // Paper's isAir() resolves a live registry; stub that API while keeping enum names/ordinals.
    var stone = spy(Material.STONE);
    var water = spy(Material.WATER);
    doReturn(false).when(stone).isAir();
    doReturn(false).when(water).isAir();
    when(block.getType()).thenReturn(stone, water);
    when(plugin.getPerceptionSettings())
        .thenReturn(
            new PerceptionSettings(
                true,
                new EntityPerceptionSettings(true, 12, 6, 24, true, true, true),
                new BlockPerceptionSettings(true, "on_demand", 0, 0, 0, 8, 1, 60),
                new TargetPerceptionSettings(true, 10)));
    var service = new MaidPerceptionService(plugin);
    String first = service.inspectSurroundings(player);
    String second = service.inspectSurroundings(player);
    assertTrue(first.contains("maid-world"));
    assertTrue(first.contains("32.50, 65.60, -16.50"));
    assertFalse(first.contains("以玩家为中心"));
    assertTrue(first.toLowerCase().contains("stone"));
    assertTrue(second.toLowerCase().contains("water"));
    verify(world, times(2)).getBlockAt(32, 65, -17);
    verify(world, times(2)).getNearbyEntities(eye, 12, 6, 12);
    verifyNoInteractions(player);
  }

  @Test
  void missingMaidNeverSubstitutesThePlayersEnvironment() {
    var plugin = mock(CraftMaid.class);
    var player = mock(Player.class);
    when(plugin.getMaidNpcService()).thenReturn(mock(MaidNpcService.class));
    when(plugin.getPerceptionSettings()).thenReturn(new PerceptionSettings(true, null, null, null));
    var service = new MaidPerceptionService(plugin);
    assertTrue(service.inspectSurroundings(player).contains("女仆尚未生成"));
    verifyNoInteractions(player);
  }
}
