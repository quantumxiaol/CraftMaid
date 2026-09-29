package com.github.quantumxiaol.craftmaid.control;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import org.bukkit.entity.Player;

/** Main-thread entry point for player-directed body control, shared by all input channels. */
public final class MaidControlService {
  private final CraftMaid plugin;
  private final AtomicLong revision = new AtomicLong();

  public MaidControlService(CraftMaid plugin) {
    this.plugin = plugin;
  }

  public long revision() {
    return revision.get();
  }

  public boolean isCurrent(long expectedRevision) {
    return revision.get() == expectedRevision;
  }

  /** Invalidates plans for this NPC, including requests made by a different player. */
  public void invalidatePlans() {
    revision.incrementAndGet();
  }

  public boolean startFollowing(Player player) {
    return takeBody(() -> npc().startFollowing(player));
  }

  public boolean stopFollowing() {
    invalidatePlans();
    return npc().stopFollowing();
  }

  public boolean recall(Player player) {
    return takeBody(() -> npc().spawnAt(player, plugin.getMaidName()));
  }

  public boolean returnHome() {
    invalidatePlans();
    if (npc().getHomeLocation() == null) {
      return false;
    }
    return takeBody(npc()::returnHome);
  }

  public boolean startGuarding(Player player) {
    return takeGuard(() -> npc().startGuarding(player));
  }

  public boolean startGuardingHere(Player player) {
    return takeGuard(() -> npc().startGuardingHere(player));
  }

  public boolean stopGuarding() {
    invalidatePlans();
    if (!npc().isGuarding()) {
      return true;
    }
    plugin.getJobService().releaseGuardControl();
    return npc().stopGuarding();
  }

  public boolean hide() {
    return takeBody(npc()::despawnStored);
  }

  public boolean show() {
    return takeBody(npc()::showStored);
  }

  public boolean refresh() {
    return takeBody(() -> npc().reconcileExistingNpc(true));
  }

  public boolean remove() {
    plugin.getMaidMenuService().closeEquipmentEditor();
    return takeBody(npc()::removeStored);
  }

  private boolean takeBody(BooleanSupplier action) {
    invalidatePlans();
    if (!npc().isAvailable()) {
      return false;
    }
    plugin.getJobService().stopActiveJobForExternalControl("女仆收到新的安排，当前工作已停止。");
    if (!npc().prepareForJobControl(true)) {
      return false;
    }
    return action.getAsBoolean();
  }

  private boolean takeGuard(BooleanSupplier action) {
    invalidatePlans();
    if (!npc().isAvailable() || !npc().isGuardAvailable()) {
      return false;
    }
    plugin.getJobService().stopJobsForGuarding("女仆开始护卫，当前工作已停止。");
    if (!npc().prepareForJobControl(true)) {
      return false;
    }
    return action.getAsBoolean();
  }

  private MaidNpcService npc() {
    return plugin.getMaidNpcService();
  }
}
