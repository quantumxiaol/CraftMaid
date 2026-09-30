package com.github.quantumxiaol.craftmaid.vision;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MaidVisionServiceTest {
  @TempDir Path directory;
  private CraftMaid plugin;
  private ExecutorService worker;
  private MaidVisionService service;
  private MaidNpcService npc;
  private final AtomicReference<Runnable> render = new AtomicReference<>();
  private final AtomicReference<Runnable> callback = new AtomicReference<>();

  @BeforeEach
  void setUp() throws Exception {
    plugin = mock(CraftMaid.class);
    Server server = mock(Server.class);
    BukkitScheduler scheduler = mock(BukkitScheduler.class);
    npc = mock(MaidNpcService.class);
    worker = mock(ExecutorService.class);
    when(plugin.getServer()).thenReturn(server);
    when(server.isPrimaryThread()).thenReturn(true);
    when(server.getScheduler()).thenReturn(scheduler);
    when(plugin.getVisionSettings())
        .thenReturn(new VisionSettings(true, 128, 96, 8, 100, 10, 300, 10, 2));
    when(plugin.getMaidNpcService()).thenReturn(npc);
    when(plugin.getDataFolder()).thenReturn(directory.toFile());
    Path client = TestResourcePack.create(directory.resolve("client.jar"), java.util.Map.of());
    when(plugin.getVisionAssetSettings())
        .thenReturn(new VisionAssetSettings(false, client.toString(), ""));
    when(server.getMinecraftVersion()).thenReturn("26.1.2");
    when(plugin.getLogger()).thenReturn(Logger.getLogger("vision-test"));
    doAnswer(
            invocation -> {
              render.set(invocation.getArgument(0));
              return null;
            })
        .when(worker)
        .execute(any(Runnable.class));
    doAnswer(
            invocation -> {
              callback.set(invocation.getArgument(1));
              return null;
            })
        .when(scheduler)
        .runTask(eq(plugin), any(Runnable.class));
    service = new MaidVisionService(plugin, worker);
  }

  private void spawnedMaid() {
    LivingEntity maid = mock(LivingEntity.class);
    World world = mock(World.class);
    when(npc.getMaidLivingEntity()).thenReturn(maid);
    when(maid.isValid()).thenReturn(true);
    when(maid.getUniqueId()).thenReturn(UUID.randomUUID());
    when(maid.getEyeLocation()).thenReturn(new Location(world, .5, 65.6, .5, 179, -80));
    when(world.getUID()).thenReturn(UUID.randomUUID());
    when(world.getName()).thenReturn("maid-world");
    when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
    when(world.getMinHeight()).thenReturn(-64);
    when(world.getMaxHeight()).thenReturn(320);
    when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble()))
        .thenReturn(List.of());
    Chunk chunk = mock(Chunk.class);
    ChunkSnapshot snapshot = mock(ChunkSnapshot.class);
    BlockData air = mock(BlockData.class);
    when(air.getMaterial()).thenReturn(Material.AIR);
    when(snapshot.getBlockData(anyInt(), anyInt(), anyInt())).thenReturn(air);
    when(world.isChunkLoaded(0, 0)).thenReturn(true);
    when(world.getChunkAt(0, 0)).thenReturn(chunk);
    when(chunk.getChunkSnapshot(false, true, false)).thenReturn(snapshot);
  }

  @Test
  void preparesAssetsWithoutNpcOrCaptureAndReportsCompletionOnMainThread() {
    AtomicReference<MaidVisionService.AssetResult> result = new AtomicReference<>();
    assertTrue(service.prepareAssets(result::set).accepted());
    assertTrue(service.assetStatus().contains("正在检查"));
    assertFalse(service.prepareAssets(ignored -> fail()).accepted());
    verifyNoInteractions(npc);
    render.get().run();
    assertNull(result.get());
    callback.get().run();
    assertTrue(result.get().success());
    assertTrue(service.assetStatus().contains("已就绪"));
    assertFalse(Files.exists(directory.resolve("vision/captures")));
    spawnedMaid();
    assertTrue(
        service.capture(ignored -> {}).accepted(), "Preparation must not consume photo cooldown");
    service.shutdown();
  }

  @Test
  void explicitPreparationAllowsDownloadAndExposesProgressEvenWhenAutomaticDownloadIsOff()
      throws Exception {
    Path client = Path.of(plugin.getVisionAssetSettings().clientJar());
    when(plugin.getVisionAssetSettings()).thenReturn(new VisionAssetSettings(false, "", ""));
    try (var assets = mockStatic(VanillaAssets.class)) {
      assets
          .when(() -> VanillaAssets.client(any(), anyString(), any(), any(), any()))
          .thenAnswer(
              call -> {
                VisionAssetSettings requested = call.getArgument(2);
                assertTrue(
                    requested.autoDownload(), "Explicit prepare is a one-time download request");
                java.util.function.Consumer<String> progress = call.getArgument(4);
                progress.accept("原版资源下载：19.1 / 38.1 MB（50%）");
                assertTrue(service.assetStatus().contains("50%"));
                return client;
              });
      AtomicReference<MaidVisionService.AssetResult> result = new AtomicReference<>();
      assertTrue(service.prepareAssets(result::set).accepted());
      render.get().run();
      callback.get().run();
      assertTrue(result.get().success());
    }
    service.shutdown();
  }

  @Test
  void reloadCancelsQueuedPreparationAndDoesNotRestoreStaleStatus() {
    assertTrue(service.prepareAssets(result -> fail()).accepted());
    service.invalidate();
    render.get().run();
    assertNull(callback.get());
    assertTrue(service.assetStatus().contains("配置已更新"));
    service.shutdown();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void missingChatCacheCompletesBeforeAnyNetworkDownload(boolean autoDownload) {
    spawnedMaid();
    when(plugin.getVisionAssetSettings()).thenReturn(new VisionAssetSettings(autoDownload, "", ""));
    AtomicReference<MaidVisionService.CaptureResult> result = new AtomicReference<>();
    assertTrue(service.captureForLlm(result::set).accepted());
    Runnable captureWorker = render.get();
    captureWorker.run();
    callback.get().run();
    assertFalse(result.get().success());
    assertNull(result.get().observation());
    assertFalse(Files.exists(directory.resolve("vision/captures")));
    verify(worker, times(autoDownload ? 2 : 1)).execute(any(Runnable.class));
    if (autoDownload) {
      assertNotSame(captureWorker, render.get());
      assertTrue(result.get().message().contains("本轮先用文字观察"));
      assertFalse(service.prepareAssets(ignored -> fail()).accepted());
      // Cancel the queued downloader: this test never needs network access.
      service.shutdown();
      render.get().run();
    } else service.shutdown();
  }

  @Test
  void preparationValidatesLocalClientVersionBeforeReportingReady() {
    when(plugin.getServer().getMinecraftVersion()).thenReturn("different-version");
    AtomicReference<MaidVisionService.AssetResult> result = new AtomicReference<>();
    assertTrue(service.prepareAssets(result::set).accepted());
    render.get().run();
    callback.get().run();
    assertFalse(result.get().success());
    assertTrue(service.assetStatus().contains("不一致"));
    assertFalse(Files.exists(directory.resolve("vision/captures")));
    service.shutdown();
  }

  @Test
  void missingNpcDoesNotFallBackToPlayerOrStartAWorker() {
    assertFalse(service.capture(result -> fail()).accepted());
    verifyNoInteractions(worker);
    service.shutdown();
  }

  @Test
  void blocksConcurrentCapturesAndReloadCancelsQueuedWork() {
    spawnedMaid();
    assertTrue(service.capture(result -> fail()).accepted());
    assertFalse(service.capture(result -> fail()).accepted());
    verify(worker, times(1)).execute(any(Runnable.class));
    service.invalidate();
    render.get().run();
    assertNull(callback.get());
    assertFalse(Files.exists(directory.resolve("vision/captures")));
    assertTrue(service.capture(result -> fail()).message().contains("冷却"));
    service.shutdown();
  }

  @Test
  void writesFourViewsAndManifestThenNotifiesOnServerThread() throws Exception {
    spawnedMaid();
    AtomicReference<MaidVisionService.CaptureResult> result = new AtomicReference<>();
    assertTrue(service.capture(result::set).accepted());
    render.get().run();
    assertNull(result.get());
    assertNotNull(callback.get());
    callback.get().run();
    assertTrue(result.get().success());
    assertNull(result.get().observation(), "Manual captures must not prepare an LLM attachment");
    Path bundle = result.get().directory();
    for (String name :
        List.of(
            "north.png", "east.png", "south.png", "west.png", "overview.png", "observation.json")) {
      assertTrue(Files.size(bundle.resolve(name)) > 0);
    }
    String json = Files.readString(bundle.resolve("observation.json"));
    assertTrue(json.contains("maid-world"));
    assertTrue(json.contains("65.6"));
    assertTrue(json.contains("unknownPixels"));
    service.shutdown();
  }

  @Test
  void llmImagesMatchSavedPngsAndKeepTheOriginalObservationLocation() throws Exception {
    spawnedMaid();
    AtomicReference<MaidVisionService.CaptureResult> result = new AtomicReference<>();
    assertTrue(service.captureForLlm(result::set).accepted());
    when(npc.getMaidLivingEntity().getEyeLocation())
        .thenReturn(new Location(null, 1000, 200, 1000));
    render.get().run();
    assertNull(result.get());
    callback.get().run();
    assertTrue(result.get().success());
    var observation = result.get().observation();
    assertEquals(4, observation.images().size());
    assertTrue(observation.summary().contains("maid-world"));
    assertTrue(observation.summary().contains("65.60"));
    assertFalse(observation.summary().contains("1000"));
    for (CompassView view : CompassView.values()) {
      var image = observation.images().get(view.ordinal());
      assertTrue(image.label().contains(view.name()));
      assertArrayEquals(
          Files.readAllBytes(result.get().directory().resolve(view.fileName())),
          java.util.Base64.getDecoder()
              .decode(image.dataUrl().substring("data:image/png;base64,".length())));
    }
    service.shutdown();
  }

  @Test
  void imageSendingCanBeDisabledWithoutDisablingManualCaptures() {
    spawnedMaid();
    when(plugin.getVisionSettings())
        .thenReturn(new VisionSettings(true, 128, 96, 8, 100, 10, 300, 10, 2, false));
    assertFalse(service.captureForLlm(result -> fail()).accepted());
    verifyNoInteractions(worker);
    assertTrue(service.capture(result -> {}).accepted());
    service.shutdown();
  }

  @Test
  void reloadSuppressesAnAlreadyQueuedCompletion() {
    spawnedMaid();
    assertTrue(service.capture(result -> fail("Stale callback after reload")).accepted());
    render.get().run();
    assertNotNull(callback.get());
    service.invalidate();
    callback.get().run();
    service.shutdown();
    verify(worker).shutdownNow();
  }
}
