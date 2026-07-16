package com.github.quantumxiaol.craftmaid.intent;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.IntentSettings;
import com.github.quantumxiaol.craftmaid.job.MaidJobService.JobActionResult;
import org.bukkit.entity.Player;

public final class MaidIntentExecutor {
  private final CraftMaid plugin;

  public MaidIntentExecutor(CraftMaid plugin) {
    this.plugin = plugin;
  }

  public MaidIntentResult execute(Player player, MaidIntent intent) {
    IntentSettings settings = plugin.getIntentSettings();
    if (settings.masterOnly() && !canControl(player)) {
      return MaidIntentResult.consumed(false, maidPrefix() + "对不起，只有主人或授权玩家可以让我去工作。");
    }

    JobActionResult result =
        switch (intent) {
          case FISHING_START -> plugin.getJobService().startFishingAuto(player);
          case CHUNK_KEEPER_START -> plugin.getJobService().startChunkKeeperAuto(player);
          case HARVEST_START -> plugin.getJobService().startHarvestAuto(player);
          case JOB_STOP -> plugin.getJobService().stopActiveJob("job 已通过聊天停止。");
        };

    String message =
        result.success()
            ? maidPrefix() + successMessage(player, intent)
            : maidPrefix() + failureMessage(player, result);
    return new MaidIntentResult(true, settings.consumeOnMatch(), result.success(), message);
  }

  private boolean canControl(Player player) {
    return plugin.canControlMaid(player);
  }

  private String successMessage(Player player, MaidIntent intent) {
    String address = addressName(player);
    String maidName = plugin.getMaidName();
    return switch (intent) {
      case FISHING_START -> "好的" + address + "，" + maidName + "这就去钓鱼。";
      case CHUNK_KEEPER_START -> "好的" + address + "，" + maidName + "会去看住机器。";
      case HARVEST_START -> "好的" + address + "，" + maidName + "去把成熟的作物收一下。";
      case JOB_STOP -> "好的" + address + "，" + maidName + "先停下手头的工作。";
    };
  }

  private String failureMessage(Player player, JobActionResult result) {
    return addressName(player) + "，" + plugin.getMaidName() + "现在还做不到：" + result.message();
  }

  private String addressName(Player player) {
    if (player.getName().equalsIgnoreCase(plugin.getMasterName())) {
      return "主人";
    }
    return player.getName();
  }

  private String maidPrefix() {
    return plugin.getReplyPrefix().replace("{name}", plugin.getMaidName());
  }
}
