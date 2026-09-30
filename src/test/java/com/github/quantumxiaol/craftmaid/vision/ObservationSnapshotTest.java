package com.github.quantumxiaol.craftmaid.vision;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.Test;

class ObservationSnapshotTest {
  @Test
  void freezesEyeCoordinatesAndNeverLoadsMissingChunks() {
    World world = mock(World.class);
    LivingEntity maid = mock(LivingEntity.class);
    Location eye = new Location(world, -.25, 65.6, -.75, 137, -79);
    when(maid.getEyeLocation()).thenReturn(eye);
    when(maid.getUniqueId()).thenReturn(UUID.randomUUID());
    when(world.getUID()).thenReturn(UUID.randomUUID());
    when(world.getName()).thenReturn("world");
    when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
    when(world.getMinHeight()).thenReturn(-64);
    when(world.getMaxHeight()).thenReturn(320);
    when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of());
    Chunk chunk = mock(Chunk.class);
    ChunkSnapshot chunkSnapshot = mock(ChunkSnapshot.class);
    when(world.isChunkLoaded(-1, -1)).thenReturn(true);
    when(world.getChunkAt(-1, -1)).thenReturn(chunk);
    when(chunk.getChunkSnapshot(false, true, false)).thenReturn(chunkSnapshot);
    var snapshot = ObservationSnapshot.capture(maid, SoftwareRendererTest.SETTINGS);
    eye.setX(200);
    eye.setYaw(0);
    assertEquals(-.25, snapshot.origin().x());
    assertEquals(65.6, snapshot.origin().y());
    assertEquals(1, snapshot.chunks().size());
    assertEquals(3, snapshot.missingChunks());
    verify(world, times(1)).getChunkAt(anyInt(), anyInt());
    verify(world).getChunkAt(-1, -1);
    verify(world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
    var scene = new SnapshotScene(snapshot, 8);
    assertFalse(scene.cell(0, 65, 0).known());
  }
}
