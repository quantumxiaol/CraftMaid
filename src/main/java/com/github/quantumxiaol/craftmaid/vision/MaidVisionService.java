package com.github.quantumxiaol.craftmaid.vision;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.IllegalPluginAccessException;

/** On-demand manual or LLM observation captures. One render at a time, with a global cooldown. */
public final class MaidVisionService {
  private final CraftMaid plugin;
  private final ExecutorService worker;
  private final AtomicBoolean busy = new AtomicBoolean();
  private final AtomicLong generation = new AtomicLong();
  private volatile boolean closed;
  private volatile boolean preparingAssets;
  private volatile String assetStatus = "尚未检查资源；执行 /maid vision prepare 可下载或校验已有缓存。";
  private long nextCaptureNanos;

  public MaidVisionService(CraftMaid plugin) {
    this(
        plugin,
        Executors.newSingleThreadExecutor(
            runnable -> {
              Thread thread = new Thread(runnable, "CraftMaid-vision");
              thread.setDaemon(true);
              return thread;
            }));
  }

  MaidVisionService(CraftMaid plugin, ExecutorService worker) {
    this.plugin = plugin;
    this.worker = worker;
  }

  public String assetStatus() {
    return assetStatus;
  }

  /** Prepare resources without an NPC, world snapshot, image render or LLM call. */
  public StartResult prepareAssets(Consumer<AssetResult> completion) {
    if (!plugin.getServer().isPrimaryThread())
      throw new IllegalStateException("Asset preparation must start on the server thread");
    if (closed) return new StartResult(false, "图片服务已关闭。");
    if (!busy.compareAndSet(false, true))
      return new StartResult(false, "已有视觉任务正在处理。资源状态：" + assetStatus);
    long epoch = generation.get();
    Path dataFolder = plugin.getDataFolder().toPath();
    VisionAssetSettings assets = plugin.getVisionAssetSettings();
    String version = plugin.getServer().getMinecraftVersion();
    preparingAssets = true;
    assetStatus = "正在检查 Minecraft " + version + " 原版资源…";
    try {
      worker.execute(
          () -> {
            AssetResult result;
            try {
              prepareResources(dataFolder, version, assets, epoch, true);
              result = new AssetResult(true, assetStatus);
              plugin.getLogger().info(result.message());
            } catch (CancellationException cancelled) {
              return;
            } catch (Exception exception) {
              result = new AssetResult(false, "资源准备失败：" + exception.getMessage());
              plugin.getLogger().log(Level.WARNING, result.message(), exception);
            } finally {
              busy.set(false);
            }
            AssetResult completed = result;
            notifyOnMain(epoch, () -> completion.accept(completed));
          });
    } catch (RuntimeException exception) {
      busy.set(false);
      preparingAssets = false;
      assetStatus = "资源准备未启动：" + exception.getMessage();
      return new StartResult(false, assetStatus);
    }
    return new StartResult(true, "已开始后台准备原版资源。用 /maid vision status 查看进度，完成后会通知你。");
  }

  private Path prepareResources(
      Path folder, String version, VisionAssetSettings settings, long epoch, boolean allowDownload)
      throws java.io.IOException {
    preparingAssets = true;
    if (generation.get() == epoch) assetStatus = "正在检查 Minecraft " + version + " 原版资源…";
    try {
      RenderBudget budget =
          new RenderBudget(
              1800,
              () -> closed || generation.get() != epoch,
              "原版资源准备超过 30 分钟，请检查网络或设置本地 client_jar。");
      budget.check();
      Path client =
          VanillaAssets.client(
              folder,
              version,
              new VisionAssetSettings(allowDownload, settings.clientJar(), settings.resourcePack()),
              budget,
              message -> {
                if (!closed && generation.get() == epoch) {
                  assetStatus = message;
                  plugin.getLogger().info(message);
                }
              });
      Path overlay =
          settings.resourcePack().isBlank()
              ? null
              : VanillaAssets.resolve(folder, settings.resourcePack());
      try (ResourcePack pack = new ResourcePack(client, overlay)) {
        if (!version.equals(pack.clientVersion()))
          throw new java.io.IOException("客户端资源与服务端 " + version + " 不一致，请更换 client_jar。");
        pack.texture("minecraft:block/stone");
      }
      budget.check();
      if (!closed && generation.get() == epoch)
        assetStatus = "Minecraft " + version + " 原版资源已就绪；可询问附近环境或执行 /maid photo。";
      return client;
    } catch (java.io.IOException | RuntimeException exception) {
      if (!closed && generation.get() == epoch) assetStatus = "资源未就绪：" + exception.getMessage();
      throw exception;
    } finally {
      preparingAssets = false;
    }
  }

  /** Must be called on the server thread. Completion is delivered on that same thread. */
  public StartResult capture(Consumer<CaptureResult> completion) {
    return capture(completion, false);
  }

  public StartResult captureForLlm(Consumer<CaptureResult> completion) {
    return capture(completion, true);
  }

  private StartResult capture(Consumer<CaptureResult> completion, boolean forLlm) {
    if (!plugin.getServer().isPrimaryThread())
      throw new IllegalStateException("Capture must start on the server thread");
    VisionSettings settings = plugin.getVisionSettings();
    if (closed || !settings.enabled()) return new StartResult(false, "图片采集未启用。");
    if (forLlm && !settings.sendToLlm()) return new StartResult(false, "图片发送未启用。");
    LivingEntity maid = plugin.getMaidNpcService().getMaidLivingEntity();
    if (maid == null || !maid.isValid() || maid.isDead()) {
      return new StartResult(false, "女仆尚未生成，无法从她的位置拍摄。");
    }
    if (busy.get())
      return new StartResult(
          false, preparingAssets ? "原版资源仍在后台准备，暂时无法拍摄。" + assetStatus : "上一组图片还在处理中，请稍后再试。");
    long remaining = nextCaptureNanos - System.nanoTime();
    if (remaining > 0)
      return new StartResult(
          false, "图片采集冷却中，还需 " + ((remaining + 999_999_999L) / 1_000_000_000L) + " 秒。");
    if (!busy.compareAndSet(false, true)) return new StartResult(false, "图片正在处理中。");
    long epoch = generation.get();
    nextCaptureNanos = System.nanoTime() + settings.cooldownSeconds() * 1_000_000_000L;
    try {
      ObservationSnapshot snapshot = ObservationSnapshot.capture(maid, settings);
      Path dataFolder = plugin.getDataFolder().toPath();
      VisionAssetSettings assets = plugin.getVisionAssetSettings();
      String version = plugin.getServer().getMinecraftVersion();
      worker.execute(
          () -> render(snapshot, settings, assets, version, dataFolder, epoch, forLlm, completion));
      return new StartResult(true, "正在使用原版材质拍摄四个方向；资源进度可用 /maid vision status 查看。");
    } catch (RuntimeException exception) {
      busy.set(false);
      plugin.getLogger().log(Level.WARNING, "女仆图片采集启动失败", exception);
      return new StartResult(false, "采集失败：" + exception.getMessage());
    }
  }

  private void render(
      ObservationSnapshot snapshot,
      VisionSettings settings,
      VisionAssetSettings assets,
      String version,
      Path dataFolder,
      long epoch,
      boolean forLlm,
      Consumer<CaptureResult> completion) {
    CaptureResult result;
    boolean prepareAfter = false;
    try {
      // Chat never waits for a network download. A missing cache starts background preparation.
      Path client =
          prepareResources(dataFolder, version, assets, epoch, !forLlm && assets.autoDownload());
      Path overlay =
          assets.resourcePack().isBlank()
              ? null
              : VanillaAssets.resolve(dataFolder, assets.resourcePack());
      RenderBudget budget =
          new RenderBudget(settings.timeoutSeconds(), () -> closed || generation.get() != epoch);
      budget.check();
      long started = System.nanoTime();
      SoftwareRenderer.Panorama panorama;
      java.util.Set<String> fallbacks;
      try (ResourcePack pack = new ResourcePack(client, overlay)) {
        if (!version.equals(pack.clientVersion())) {
          throw new java.io.IOException(
              "客户端资源版本 " + pack.clientVersion() + " 与服务端 " + version + " 不一致，请更换 client_jar。");
        }
        SnapshotScene scene = new SnapshotScene(snapshot, settings.distance(), pack);
        panorama = new SoftwareRenderer().render(scene, snapshot.origin(), settings, budget);
        fallbacks = scene.fallbackMaterials();
      }
      long renderNanos = System.nanoTime() - started;
      ObservationArtifacts artifacts = new ObservationArtifacts();
      Path root = dataFolder.resolve("vision/captures");
      Path directory =
          artifacts.write(
              root,
              snapshot,
              settings,
              panorama,
              renderNanos,
              budget,
              version,
              !assets.resourcePack().isBlank(),
              fallbacks);
      VisionObservation observation =
          forLlm
              ? VisionObservation.from(snapshot, settings, panorama, fallbacks, directory, budget)
              : null;
      try {
        artifacts.prune(root, settings.retainedCaptures(), directory);
      } catch (java.io.IOException exception) {
        plugin.getLogger().log(Level.WARNING, "图片已保存，但清理旧采集文件失败", exception);
      }
      result =
          new CaptureResult(
              true,
              directory,
              "四向图片已保存："
                  + directory
                  + "（快照 "
                  + snapshot.captureNanos() / 1_000_000
                  + "ms，渲染 "
                  + renderNanos / 1_000_000
                  + "ms）",
              observation);
      plugin.getLogger().info(result.message());
    } catch (VanillaAssets.AssetsUnavailableException exception) {
      prepareAfter = forLlm && assets.autoDownload();
      result =
          new CaptureResult(
              false,
              null,
              prepareAfter ? "原版资源尚未就绪，已安排后台下载；本轮先用文字观察。完成后请再问一次。" : exception.getMessage());
    } catch (CancellationException exception) {
      return;
    } catch (Exception exception) {
      plugin.getLogger().log(Level.WARNING, "女仆图片渲染失败", exception);
      result = new CaptureResult(false, null, "图片采集失败：" + exception.getMessage());
    } finally {
      busy.set(false);
    }
    if (closed || generation.get() != epoch) return;
    CaptureResult completed = result;
    boolean startPreparation = prepareAfter;
    notifyOnMain(
        epoch,
        () -> {
          if (startPreparation) prepareAssets(ignored -> {});
          completion.accept(completed);
        });
  }

  private void notifyOnMain(long epoch, Runnable callback) {
    if (closed || generation.get() != epoch) return;
    try {
      plugin
          .getServer()
          .getScheduler()
          .runTask(
              plugin,
              () -> {
                if (!closed && generation.get() == epoch) callback.run();
              });
    } catch (IllegalPluginAccessException ignored) {
      // Plugin disabled between the generation check and scheduling the callback.
    }
  }

  public void invalidate() {
    generation.incrementAndGet();
    assetStatus = "配置已更新，下一次准备或拍摄会重新检查资源。";
  }

  public void shutdown() {
    closed = true;
    invalidate();
    worker.shutdownNow();
  }

  public record StartResult(boolean accepted, String message) {}

  public record AssetResult(boolean success, String message) {}

  public record CaptureResult(
      boolean success, Path directory, String message, VisionObservation observation) {
    public CaptureResult(boolean success, Path directory, String message) {
      this(success, directory, message, null);
    }
  }
}
