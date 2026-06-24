package com.github.quantumxiaol.craftmaid.command;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FollowCommand implements TabExecutor {
  private static final List<String> ACTIONS = List.of("start", "stop");

  private final CraftMaid plugin;
  private final MaidNpcService maidNpcService;

  public FollowCommand(CraftMaid plugin) {
    this.plugin = plugin;
    this.maidNpcService = plugin.getMaidNpcService();
  }

  @Override
  public boolean onCommand(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("只有玩家可以控制女仆跟随。", NamedTextColor.RED));
      return true;
    }

    if (!canControl(player)) {
      sender.sendMessage(Component.text("只有主人或管理员可以控制女仆。", NamedTextColor.RED));
      return true;
    }

    if (!maidNpcService.isAvailable()) {
      sender.sendMessage(Component.text("未安装或未启用 Citizens，无法控制实体女仆。", NamedTextColor.RED));
      return true;
    }

    if (args.length == 0) {
      sendUsage(sender, label);
      return true;
    }

    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "start" -> startFollowing(player);
      case "stop" -> stopFollowing(player);
      default -> sendUsage(sender, label);
    }
    return true;
  }

  @Override
  public @Nullable List<String> onTabComplete(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!(sender instanceof Player player) || !canControl(player) || args.length != 1) {
      return Collections.emptyList();
    }

    String prefix = args[0].toLowerCase(Locale.ROOT);
    return ACTIONS.stream().filter(action -> action.startsWith(prefix)).toList();
  }

  private boolean canControl(Player player) {
    return player.hasPermission("craftmaid.admin")
        || player.getName().equalsIgnoreCase(plugin.getMasterName());
  }

  private void startFollowing(Player player) {
    if (!maidNpcService.startFollowing(player)) {
      player.sendMessage(Component.text("启动跟随失败，请检查 Citizens 是否正常加载。", NamedTextColor.RED));
      return;
    }
    player.sendMessage(Component.text(plugin.getMaidName() + " 会跟着你。", NamedTextColor.GREEN));
  }

  private void stopFollowing(Player player) {
    maidNpcService.stopFollowing();
    player.sendMessage(Component.text(plugin.getMaidName() + " 会留在这里。", NamedTextColor.GREEN));
  }

  private void sendUsage(CommandSender sender, String label) {
    sender.sendMessage(Component.text("用法: /" + label + " <start|stop>", NamedTextColor.YELLOW));
  }
}
