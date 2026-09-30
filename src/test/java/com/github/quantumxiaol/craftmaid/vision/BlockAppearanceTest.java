package com.github.quantumxiaol.craftmaid.vision;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Slab;
import org.junit.jupiter.api.Test;

class BlockAppearanceTest {
  @Test
  void topAndBottomSlabsOccupyTheCorrectHalf() {
    Slab data = mock(Slab.class);
    when(data.getMaterial()).thenReturn(Material.STONE_SLAB);
    when(data.getMapColor()).thenReturn(Color.GRAY);
    when(data.getType()).thenReturn(Slab.Type.TOP);
    assertEquals(.5, BlockAppearance.from(data).boxes().getFirst().minY());
    when(data.getType()).thenReturn(Slab.Type.BOTTOM);
    assertEquals(.5, BlockAppearance.from(data).boxes().getFirst().maxY());
  }

  @Test
  void openDoorRotatesItsPlaneInsteadOfFillingTheDoorway() {
    Door data = mock(Door.class);
    when(data.getMaterial()).thenReturn(Material.OAK_DOOR);
    when(data.getMapColor()).thenReturn(Color.ORANGE);
    when(data.getFacing()).thenReturn(BlockFace.NORTH);
    when(data.getHinge()).thenReturn(Door.Hinge.LEFT);
    var closed = BlockAppearance.from(data).boxes().getFirst();
    assertEquals(1, closed.maxX() - closed.minX());
    assertTrue(closed.maxZ() - closed.minZ() < .2);
    when(data.isOpen()).thenReturn(true);
    var open = BlockAppearance.from(data).boxes().getFirst();
    assertTrue(open.maxX() - open.minX() < .2);
    assertEquals(1, open.maxZ() - open.minZ());
  }
}
