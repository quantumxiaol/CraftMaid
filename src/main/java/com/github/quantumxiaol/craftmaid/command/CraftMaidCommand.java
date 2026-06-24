package com.github.quantumxiaol.craftmaid.command;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.conversation.ConversationHistory;
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

public class CraftMaidCommand implements TabExecutor {
  private static final List<String> SUBCOMMANDS =
      List.of("help", "spawn", "despawn", "reload", "forget");

  private final CraftMaid plugin;
  private final MaidNpcService maidNpcService;

  public CraftMaidCommand(CraftMaid plugin) {
    this.plugin = plugin;
    this.maidNpcService = plugin.getMaidNpcService();
  }

  @Override
  public boolean onCommand(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!sender.hasPermission("craftmaid.admin")) {
      sender.sendMessage(Component.text("你没有权限执行此命令。", NamedTextColor.RED));
      return true;
    }

    if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
      sendHelp(sender, label);
      return true;
    }

    switch (args[0].toLowerCase(Locale.ROOT)) {
      case "reload" -> {
        plugin.reloadPlugin();
        sender.sendMessage(Component.text("CraftMaid 配置已重新加载。", NamedTextColor.GREEN));
        return true;
      }
      case "spawn" -> {
        spawnMaid(sender);
        return true;
      }
      case "despawn" -> {
        despawnMaid(sender);
        return true;
      }
      case "forget" -> {
        forgetHistory(sender, args);
        return true;
      }
      default -> {
        sender.sendMessage(
            Component.text("未知子命令，输入 /" + label + " help 查看用法。", NamedTextColor.RED));
        return true;
      }
    }
  }

  @Override
  public @Nullable List<String> onTabComplete(
      @NotNull CommandSender sender,
      @NotNull Command command,
      @NotNull String label,
      @NotNull String[] args) {
    if (!sender.hasPermission("craftmaid.admin") || args.length != 1) {
      return Collections.emptyList();
    }

    String prefix = args[0].toLowerCase(Locale.ROOT);
    return SUBCOMMANDS.stream().filter(subcommand -> subcommand.startsWith(prefix)).toList();
  }

  private void spawnMaid(CommandSender sender) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("只有玩家可以召唤女仆。", NamedTextColor.RED));
      return;
    }

    if (!maidNpcService.isAvailable()) {
      sender.sendMessage(Component.text("未安装或未启用 Citizens，无法召唤实体女仆。", NamedTextColor.RED));
      return;
    }

    String maidName = plugin.getMaidName();
    maidNpcService.spawnAt(player, maidName);

    sender.sendMessage(Component.text("已在你的位置召唤女仆 " + maidName + "。", NamedTextColor.GREEN));
  }

  private void despawnMaid(CommandSender sender) {
    if (!maidNpcService.isAvailable()) {
      sender.sendMessage(Component.text("未安装或未启用 Citizens，无法管理实体女仆。", NamedTextColor.RED));
      return;
    }

    boolean removed = maidNpcService.despawnStored();
    if (!removed) {
      sender.sendMessage(Component.text("当前没有已记录的女仆 NPC。", NamedTextColor.YELLOW));
      return;
    }

    sender.sendMessage(Component.text("已移除女仆 NPC。", NamedTextColor.GREEN));
  }

  private void forgetHistory(CommandSender sender, String[] args) {
    ConversationHistory history = plugin.getConversationHistory();
    if (args.length >= 2 && args[1].equalsIgnoreCase("all")) {
      int count = history.clearAll();
      sender.sendMessage(Component.text("已清空 " + count + " 个玩家的对话历史。", NamedTextColor.GREEN));
      return;
    }

    if (args.length >= 2) {
      boolean removed = history.clearByPlayerName(args[1]);
      if (removed) {
        sender.sendMessage(Component.text("已清空玩家 " + args[1] + " 的对话历史。", NamedTextColor.GREEN));
      } else {
        sender.sendMessage(Component.text("没有找到玩家 " + args[1] + " 的对话历史。", NamedTextColor.YELLOW));
      }
      return;
    }

    if (sender instanceof Player player) {
      boolean removed = history.clear(player.getUniqueId());
      if (removed) {
        sender.sendMessage(Component.text("已清空你的对话历史。", NamedTextColor.GREEN));
      } else {
        sender.sendMessage(Component.text("你当前没有对话历史。", NamedTextColor.YELLOW));
      }
      return;
    }

    sender.sendMessage(Component.text("用法: /craftmaid forget <玩家名|all>", NamedTextColor.YELLOW));
  }

  private void sendHelp(CommandSender sender, String label) {
    sender.sendMessage(Component.text("CraftMaid 命令：", NamedTextColor.LIGHT_PURPLE));
    sender.sendMessage(
        Component.text("/" + label + " spawn", NamedTextColor.YELLOW)
            .append(Component.text(" - 在当前位置生成或移动女仆 NPC", NamedTextColor.GRAY)));
    sender.sendMessage(
        Component.text("/" + label + " despawn", NamedTextColor.YELLOW)
            .append(Component.text(" - 移除已记录的女仆 NPC", NamedTextColor.GRAY)));
    sender.sendMessage(
        Component.text("/" + label + " reload", NamedTextColor.YELLOW)
            .append(Component.text(" - 重新加载配置", NamedTextColor.GRAY)));
    sender.sendMessage(
        Component.text("/" + label + " forget [玩家名|all]", NamedTextColor.YELLOW)
            .append(Component.text(" - 清空对话历史", NamedTextColor.GRAY)));
  }
}
