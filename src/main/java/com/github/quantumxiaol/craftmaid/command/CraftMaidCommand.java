package com.github.quantumxiaol.craftmaid.command;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.anchor.AnchorType;
import com.github.quantumxiaol.craftmaid.anchor.MaidAnchorService.AnchorOperationResult;
import com.github.quantumxiaol.craftmaid.anchor.RegionCorner;
import com.github.quantumxiaol.craftmaid.anchor.RegionType;
import com.github.quantumxiaol.craftmaid.conversation.ConversationHistory;
import com.github.quantumxiaol.craftmaid.job.MaidJobService.JobActionResult;
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
      List.of(
          "help", "spawn", "despawn", "reload", "forget", "follow", "anchor", "region", "job",
          "fishing", "chunk", "harvest");
  private static final List<String> FOLLOW_ACTIONS = List.of("start", "stop");
  private static final List<String> JOB_ACTIONS = List.of("status", "stop");
  private static final List<String> FISHING_ACTIONS = List.of("start", "stop");
  private static final List<String> CHUNK_ACTIONS = List.of("start", "stop");
  private static final List<String> HARVEST_ACTIONS = List.of("start", "stop");
  private static final List<String> FISHING_NAMES = List.of("main", "default");
  private static final List<String> CHUNK_NAMES = List.of("main", "default", "iron_farm");
  private static final List<String> HARVEST_NAMES = List.of("main", "default", "wheat_field");
  private static final List<String> ANCHOR_ACTIONS = List.of("set", "list", "remove");
  private static final List<String> ANCHOR_TYPES =
      List.of("home", "fishing_spot", "chest", "guard_post", "redstone_watch");
  private static final List<String> ANCHOR_NAME_SUGGESTIONS =
      List.of("default", "main", "drops", "crops", "tools", "gate");
  private static final List<String> REGION_ACTIONS = List.of("set", "list", "remove", "show");
  private static final List<String> REGION_TYPES = List.of("farm", "pond", "redstone");
  private static final List<String> REGION_NAME_SUGGESTIONS =
      List.of("default", "wheat_field", "carrot_field", "backyard", "iron_farm");
  private static final List<String> REGION_CORNERS = List.of("pos1", "pos2");

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
      case "follow" -> {
        followMaid(sender, args);
        return true;
      }
      case "anchor" -> {
        handleAnchor(sender, args);
        return true;
      }
      case "region" -> {
        handleRegion(sender, args);
        return true;
      }
      case "job" -> {
        handleJob(sender, args);
        return true;
      }
      case "fishing" -> {
        handleFishing(sender, args);
        return true;
      }
      case "chunk" -> {
        handleChunk(sender, args);
        return true;
      }
      case "harvest" -> {
        handleHarvest(sender, args);
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
    if (!sender.hasPermission("craftmaid.admin")) {
      return Collections.emptyList();
    }

    if (args.length == 1) {
      String prefix = args[0].toLowerCase(Locale.ROOT);
      return SUBCOMMANDS.stream().filter(subcommand -> subcommand.startsWith(prefix)).toList();
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("follow")) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      return FOLLOW_ACTIONS.stream().filter(action -> action.startsWith(prefix)).toList();
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("job")) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      return filter(JOB_ACTIONS, prefix);
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("fishing")) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      return filter(FISHING_ACTIONS, prefix);
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("chunk")) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      return filter(CHUNK_ACTIONS, prefix);
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("harvest")) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      return filter(HARVEST_ACTIONS, prefix);
    }

    if (args.length == 3
        && args[0].equalsIgnoreCase("fishing")
        && args[1].equalsIgnoreCase("start")) {
      String prefix = args[2].toLowerCase(Locale.ROOT);
      return filter(FISHING_NAMES, prefix);
    }

    if (args.length == 3
        && args[0].equalsIgnoreCase("chunk")
        && args[1].equalsIgnoreCase("start")) {
      String prefix = args[2].toLowerCase(Locale.ROOT);
      return filter(CHUNK_NAMES, prefix);
    }

    if (args.length == 3
        && args[0].equalsIgnoreCase("harvest")
        && args[1].equalsIgnoreCase("start")) {
      String prefix = args[2].toLowerCase(Locale.ROOT);
      return filter(HARVEST_NAMES, prefix);
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("anchor")) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      return ANCHOR_ACTIONS.stream().filter(action -> action.startsWith(prefix)).toList();
    }

    if (args.length == 3 && args[0].equalsIgnoreCase("anchor")) {
      String prefix = args[2].toLowerCase(Locale.ROOT);
      if (args[1].equalsIgnoreCase("set")) {
        return filter(ANCHOR_TYPES, prefix);
      }
      if (args[1].equalsIgnoreCase("remove")) {
        return filter(ANCHOR_TYPES, prefix);
      }
    }

    if (args.length == 4 && args[0].equalsIgnoreCase("anchor")) {
      String prefix = args[3].toLowerCase(Locale.ROOT);
      if (args[1].equalsIgnoreCase("set")) {
        return filter(ANCHOR_NAME_SUGGESTIONS, prefix);
      }
      if (args[1].equalsIgnoreCase("remove")) {
        return AnchorType.fromInput(args[2])
            .map(type -> filter(plugin.getAnchorService().anchorNames(type), prefix))
            .orElse(Collections.emptyList());
      }
    }

    if (args.length == 2 && args[0].equalsIgnoreCase("region")) {
      String prefix = args[1].toLowerCase(Locale.ROOT);
      return filter(REGION_ACTIONS, prefix);
    }

    if (args.length == 3 && args[0].equalsIgnoreCase("region")) {
      String prefix = args[2].toLowerCase(Locale.ROOT);
      if (args[1].equalsIgnoreCase("set")
          || args[1].equalsIgnoreCase("remove")
          || args[1].equalsIgnoreCase("show")) {
        return filter(REGION_TYPES, prefix);
      }
    }

    if (args.length == 4 && args[0].equalsIgnoreCase("region")) {
      String prefix = args[3].toLowerCase(Locale.ROOT);
      if (args[1].equalsIgnoreCase("set")) {
        return filter(REGION_NAME_SUGGESTIONS, prefix);
      }
      if (args[1].equalsIgnoreCase("remove") || args[1].equalsIgnoreCase("show")) {
        return RegionType.fromInput(args[2])
            .map(type -> filter(plugin.getAnchorService().regionNames(type), prefix))
            .orElse(Collections.emptyList());
      }
    }

    if (args.length == 5 && args[0].equalsIgnoreCase("region") && args[1].equalsIgnoreCase("set")) {
      String prefix = args[4].toLowerCase(Locale.ROOT);
      return filter(REGION_CORNERS, prefix);
    }

    return Collections.emptyList();
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

  private void followMaid(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("只有玩家可以控制女仆跟随。", NamedTextColor.RED));
      return;
    }

    if (!maidNpcService.isAvailable()) {
      sender.sendMessage(Component.text("未安装或未启用 Citizens，无法控制实体女仆。", NamedTextColor.RED));
      return;
    }

    if (args.length < 2) {
      sender.sendMessage(
          Component.text("用法: /craftmaid follow <start|stop>", NamedTextColor.YELLOW));
      return;
    }

    switch (args[1].toLowerCase(Locale.ROOT)) {
      case "start" -> {
        plugin.getJobService().stopActiveJobForExternalControl("当前工作停止：玩家开始跟随。");
        if (!maidNpcService.startFollowing(player)) {
          sender.sendMessage(Component.text("启动跟随失败，请检查 Citizens 是否正常加载。", NamedTextColor.RED));
          return;
        }
        sender.sendMessage(Component.text(plugin.getMaidName() + " 会跟着你。", NamedTextColor.GREEN));
      }
      case "stop" -> {
        maidNpcService.stopFollowing();
        sender.sendMessage(Component.text(plugin.getMaidName() + " 会留在这里。", NamedTextColor.GREEN));
      }
      default ->
          sender.sendMessage(
              Component.text("用法: /craftmaid follow <start|stop>", NamedTextColor.YELLOW));
    }
  }

  private void handleJob(CommandSender sender, String[] args) {
    if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
      sender.sendMessage(Component.text(plugin.getJobService().statusLine(), NamedTextColor.GRAY));
      return;
    }

    if (args[1].equalsIgnoreCase("stop")) {
      JobActionResult result = plugin.getJobService().stopActiveJob("job 已手动停止。");
      sender.sendMessage(
          Component.text(
              result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
      return;
    }

    sender.sendMessage(Component.text("用法: /craftmaid job <status|stop>", NamedTextColor.YELLOW));
  }

  private void handleFishing(CommandSender sender, String[] args) {
    if (args.length < 2) {
      sender.sendMessage(
          Component.text("用法: /craftmaid fishing <start|stop> [name]", NamedTextColor.YELLOW));
      return;
    }

    switch (args[1].toLowerCase(Locale.ROOT)) {
      case "start" -> startFishing(sender, args);
      case "stop" -> {
        JobActionResult result = plugin.getJobService().stopFishing("钓鱼任务已手动停止。");
        sender.sendMessage(
            Component.text(
                result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
      }
      default ->
          sender.sendMessage(
              Component.text("用法: /craftmaid fishing <start|stop> [name]", NamedTextColor.YELLOW));
    }
  }

  private void handleChunk(CommandSender sender, String[] args) {
    if (args.length < 2) {
      sender.sendMessage(
          Component.text("用法: /craftmaid chunk <start|stop> [name]", NamedTextColor.YELLOW));
      return;
    }

    switch (args[1].toLowerCase(Locale.ROOT)) {
      case "start" -> startChunkKeeper(sender, args);
      case "stop" -> {
        JobActionResult result = plugin.getJobService().stopChunkKeeper("看守机器任务已手动停止。");
        sender.sendMessage(
            Component.text(
                result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
      }
      default ->
          sender.sendMessage(
              Component.text("用法: /craftmaid chunk <start|stop> [name]", NamedTextColor.YELLOW));
    }
  }

  private void startChunkKeeper(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("只有玩家可以启动看守机器任务。", NamedTextColor.RED));
      return;
    }
    String name = args.length >= 3 ? args[2] : "main";
    JobActionResult result = plugin.getJobService().startChunkKeeper(player, name);
    sender.sendMessage(
        Component.text(
            result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
  }

  private void handleHarvest(CommandSender sender, String[] args) {
    if (args.length < 2) {
      sender.sendMessage(
          Component.text("用法: /craftmaid harvest <start|stop> [name]", NamedTextColor.YELLOW));
      return;
    }

    switch (args[1].toLowerCase(Locale.ROOT)) {
      case "start" -> startHarvest(sender, args);
      case "stop" -> {
        JobActionResult result = plugin.getJobService().stopHarvest("收割任务已手动停止。");
        sender.sendMessage(
            Component.text(
                result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
      }
      default ->
          sender.sendMessage(
              Component.text("用法: /craftmaid harvest <start|stop> [name]", NamedTextColor.YELLOW));
    }
  }

  private void startHarvest(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("只有玩家可以启动收割任务。", NamedTextColor.RED));
      return;
    }
    String name = args.length >= 3 ? args[2] : "main";
    JobActionResult result = plugin.getJobService().startHarvest(player, name);
    sender.sendMessage(
        Component.text(
            result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
  }

  private void startFishing(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("只有玩家可以启动钓鱼任务。", NamedTextColor.RED));
      return;
    }
    String name = args.length >= 3 ? args[2] : "main";
    JobActionResult result = plugin.getJobService().startFishing(player, name);
    sender.sendMessage(
        Component.text(
            result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
  }

  private void handleAnchor(CommandSender sender, String[] args) {
    if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
      sendAnchorList(sender);
      return;
    }

    switch (args[1].toLowerCase(Locale.ROOT)) {
      case "set" -> setAnchor(sender, args);
      case "remove" -> removeAnchor(sender, args);
      default ->
          sender.sendMessage(
              Component.text(
                  "用法: /craftmaid anchor <set|list|remove> [type] [name]", NamedTextColor.YELLOW));
    }
  }

  private void setAnchor(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("只有玩家可以设置锚点。", NamedTextColor.RED));
      return;
    }

    if (args.length < 4) {
      sender.sendMessage(
          Component.text(
              "用法: /craftmaid anchor set <home|fishing_spot|chest|guard_post|redstone_watch> <name>",
              NamedTextColor.YELLOW));
      return;
    }

    AnchorType.fromInput(args[2])
        .ifPresentOrElse(
            type -> {
              AnchorOperationResult result =
                  plugin.getAnchorService().setAnchor(type, args[3], player.getLocation());
              sender.sendMessage(
                  Component.text(
                      result.message(),
                      result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            },
            () -> sender.sendMessage(Component.text("未知锚点类型: " + args[2], NamedTextColor.RED)));
  }

  private void removeAnchor(CommandSender sender, String[] args) {
    if (args.length < 4) {
      sender.sendMessage(
          Component.text(
              "用法: /craftmaid anchor remove <home|fishing_spot|chest|guard_post|redstone_watch> <name>",
              NamedTextColor.YELLOW));
      return;
    }

    AnchorType.fromInput(args[2])
        .ifPresentOrElse(
            type -> {
              AnchorOperationResult result = plugin.getAnchorService().removeAnchor(type, args[3]);
              sender.sendMessage(
                  Component.text(
                      result.message(),
                      result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            },
            () ->
                sender.sendMessage(Component.text("未知 anchor 类型: " + args[2], NamedTextColor.RED)));
  }

  private void sendAnchorList(CommandSender sender) {
    sender.sendMessage(Component.text("CraftMaid anchors：", NamedTextColor.LIGHT_PURPLE));
    for (String line : plugin.getAnchorService().anchorStatusLines()) {
      sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
    }
  }

  private void handleRegion(CommandSender sender, String[] args) {
    if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
      sendRegionList(sender);
      return;
    }

    switch (args[1].toLowerCase(Locale.ROOT)) {
      case "set" -> setRegion(sender, args);
      case "remove" -> removeRegion(sender, args);
      case "show" -> showRegion(sender, args);
      default ->
          sender.sendMessage(
              Component.text(
                  "用法: /craftmaid region <set|list|remove|show>", NamedTextColor.YELLOW));
    }
  }

  private void setRegion(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("只有玩家可以设置 region。", NamedTextColor.RED));
      return;
    }

    if (args.length < 5) {
      sender.sendMessage(
          Component.text(
              "用法: /craftmaid region set <farm|pond|redstone> <name> <pos1|pos2>",
              NamedTextColor.YELLOW));
      return;
    }

    OptionalRegionInput input = parseRegionInput(sender, args[2], args[4]);
    if (input == null) {
      return;
    }

    AnchorOperationResult result =
        plugin
            .getAnchorService()
            .setRegionCorner(input.type(), args[3], input.corner(), player.getLocation());
    sender.sendMessage(
        Component.text(
            result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
  }

  private void removeRegion(CommandSender sender, String[] args) {
    if (args.length < 4) {
      sender.sendMessage(
          Component.text(
              "用法: /craftmaid region remove <farm|pond|redstone> <name>", NamedTextColor.YELLOW));
      return;
    }

    RegionType.fromInput(args[2])
        .ifPresentOrElse(
            type -> {
              AnchorOperationResult result = plugin.getAnchorService().removeRegion(type, args[3]);
              sender.sendMessage(
                  Component.text(
                      result.message(),
                      result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            },
            () ->
                sender.sendMessage(Component.text("未知 region 类型: " + args[2], NamedTextColor.RED)));
  }

  private void showRegion(CommandSender sender, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage(Component.text("只有玩家可以显示 region。", NamedTextColor.RED));
      return;
    }

    if (args.length < 4) {
      sender.sendMessage(
          Component.text(
              "用法: /craftmaid region show <farm|pond|redstone> <name>", NamedTextColor.YELLOW));
      return;
    }

    RegionType.fromInput(args[2])
        .ifPresentOrElse(
            type -> {
              AnchorOperationResult result =
                  plugin.getAnchorService().showRegion(player, type, args[3]);
              sender.sendMessage(
                  Component.text(
                      result.message(),
                      result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
            },
            () ->
                sender.sendMessage(Component.text("未知 region 类型: " + args[2], NamedTextColor.RED)));
  }

  private void sendRegionList(CommandSender sender) {
    sender.sendMessage(Component.text("CraftMaid regions：", NamedTextColor.LIGHT_PURPLE));
    for (String line : plugin.getAnchorService().regionStatusLines()) {
      sender.sendMessage(Component.text(line, NamedTextColor.GRAY));
    }
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
    sender.sendMessage(
        Component.text("/" + label + " follow <start|stop>", NamedTextColor.YELLOW)
            .append(Component.text(" - 开始或停止女仆跟随", NamedTextColor.GRAY)));
    sender.sendMessage(
        Component.text("/" + label + " job <status|stop>", NamedTextColor.YELLOW)
            .append(Component.text(" - 查看或停止当前女仆 job", NamedTextColor.GRAY)));
    sender.sendMessage(
        Component.text("/" + label + " fishing <start|stop> [name]", NamedTextColor.YELLOW)
            .append(Component.text(" - 启动或停止模拟钓鱼", NamedTextColor.GRAY)));
    sender.sendMessage(
        Component.text("/" + label + " chunk <start|stop> [name]", NamedTextColor.YELLOW)
            .append(Component.text(" - 加载或停止 redstone_watch 附近 chunk", NamedTextColor.GRAY)));
    sender.sendMessage(
        Component.text("/" + label + " harvest <start|stop> [name]", NamedTextColor.YELLOW)
            .append(Component.text(" - 启动或停止农田收割", NamedTextColor.GRAY)));
    sender.sendMessage(
        Component.text("/" + label + " anchor <set|list|remove>", NamedTextColor.YELLOW)
            .append(Component.text(" - 管理命名单点 anchor", NamedTextColor.GRAY)));
    sender.sendMessage(
        Component.text("/" + label + " region <set|list|remove|show>", NamedTextColor.YELLOW)
            .append(Component.text(" - 管理命名长方体 region", NamedTextColor.GRAY)));
  }

  private OptionalRegionInput parseRegionInput(
      CommandSender sender, String typeInput, String cornerInput) {
    RegionType type = RegionType.fromInput(typeInput).orElse(null);
    if (type == null) {
      sender.sendMessage(Component.text("未知 region 类型: " + typeInput, NamedTextColor.RED));
      return null;
    }

    RegionCorner corner = RegionCorner.fromInput(cornerInput).orElse(null);
    if (corner == null) {
      sender.sendMessage(Component.text("未知 region 角点: " + cornerInput, NamedTextColor.RED));
      return null;
    }
    return new OptionalRegionInput(type, corner);
  }

  private List<String> filter(List<String> values, String prefix) {
    return values.stream().filter(value -> value.startsWith(prefix)).toList();
  }

  private record OptionalRegionInput(RegionType type, RegionCorner corner) {}
}
