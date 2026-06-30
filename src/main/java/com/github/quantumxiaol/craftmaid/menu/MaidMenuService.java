package com.github.quantumxiaol.craftmaid.menu;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.anchor.AnchorType;
import com.github.quantumxiaol.craftmaid.anchor.MaidAnchorService;
import com.github.quantumxiaol.craftmaid.anchor.MaidAnchorService.AnchorOperationResult;
import com.github.quantumxiaol.craftmaid.anchor.RegionCorner;
import com.github.quantumxiaol.craftmaid.anchor.RegionType;
import com.github.quantumxiaol.craftmaid.job.MaidJobService.JobActionResult;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MaidMenuService implements Listener {
  private static final int MENU_SIZE = 45;

  private final CraftMaid plugin;

  public MaidMenuService(CraftMaid plugin) {
    this.plugin = plugin;
  }

  public void openFor(Player player) {
    MaidMenuHolder holder = new MaidMenuHolder();
    Inventory inventory =
        Bukkit.createInventory(holder, MENU_SIZE, Component.text(plugin.getMaidName() + "的菜单"));
    holder.setInventory(inventory);

    setActionItem(
        holder, inventory, 10, MaidMenuAction.STATUS, Material.BOOK, "查看状态", "查看当前能力和状态。");
    setActionItem(
        holder,
        inventory,
        11,
        MaidMenuAction.RECALL,
        Material.ENDER_PEARL,
        "召回到身边",
        "把女仆移动到你当前位置。");
    setActionItem(
        holder,
        inventory,
        12,
        MaidMenuAction.SET_HOME,
        Material.RED_BED,
        "设置 home",
        "把女仆当前位置记录为 home。");
    setActionItem(
        holder,
        inventory,
        13,
        MaidMenuAction.RETURN_HOME,
        Material.COMPASS,
        "回家",
        "把女仆送回已记录的 home。");
    setActionItem(
        holder, inventory, 14, MaidMenuAction.LOOK_AT_PLAYER, Material.SPYGLASS, "看向我", "让女仆转向你。");
    setActionItem(
        holder, inventory, 15, MaidMenuAction.OPEN_INVENTORY, Material.CHEST, "打开背包", "打开女仆背包。");
    setActionItem(
        holder,
        inventory,
        16,
        MaidMenuAction.OPEN_EQUIPMENT,
        Material.ARMOR_STAND,
        "配置装备",
        "设置女仆手持物和护甲。");
    setActionItem(
        holder,
        inventory,
        18,
        MaidMenuAction.REFRESH_SKIN,
        Material.PLAYER_HEAD,
        "刷新皮肤",
        "按当前 maid.skin 配置重新应用 NPC 皮肤。");
    setActionItem(
        holder,
        inventory,
        17,
        MaidMenuAction.LIST_ANCHORS,
        Material.MAP,
        "查看锚点",
        "查看 anchors 和 regions。");
    setActionItem(
        holder, inventory, 19, MaidMenuAction.FOLLOW_START, Material.LEAD, "跟随我", "让女仆跟随你。");
    setActionItem(
        holder, inventory, 20, MaidMenuAction.FOLLOW_STOP, Material.SLIME_BALL, "别跟了", "停止当前跟随。");
    setActionItem(
        holder,
        inventory,
        21,
        MaidMenuAction.JOB_STOP,
        Material.REDSTONE_BLOCK,
        "停止工作",
        "停止当前 job、跟随或护卫。");
    setActionItem(
        holder,
        inventory,
        27,
        MaidMenuAction.SET_FISHING_SPOT,
        Material.WATER_BUCKET,
        "设置钓鱼站位",
        "把你当前位置记录为 fishing_spot/default。");
    setActionItem(
        holder,
        inventory,
        28,
        MaidMenuAction.SET_FARM_REGION_POS1,
        Material.WOODEN_HOE,
        "设置农田点 1",
        "把你当前位置记录为 farm/default/pos1。");
    setActionItem(
        holder,
        inventory,
        29,
        MaidMenuAction.SET_FARM_REGION_POS2,
        Material.IRON_HOE,
        "设置农田点 2",
        "把你当前位置记录为 farm/default/pos2。");
    setActionItem(
        holder,
        inventory,
        30,
        MaidMenuAction.SET_POND_REGION_POS1,
        Material.KELP,
        "设置鱼塘点 1",
        "把你当前位置记录为 pond/default/pos1。");
    setActionItem(
        holder,
        inventory,
        31,
        MaidMenuAction.SET_POND_REGION_POS2,
        Material.SEAGRASS,
        "设置鱼塘点 2",
        "把你当前位置记录为 pond/default/pos2。");
    setActionItem(
        holder,
        inventory,
        32,
        MaidMenuAction.SET_REDSTONE_REGION_POS1,
        Material.REDSTONE,
        "设置红石区点 1",
        "把你当前位置记录为 redstone/default/pos1。");
    setActionItem(
        holder,
        inventory,
        33,
        MaidMenuAction.SET_REDSTONE_REGION_POS2,
        Material.REPEATER,
        "设置红石区点 2",
        "把你当前位置记录为 redstone/default/pos2。");
    setActionItem(
        holder,
        inventory,
        34,
        MaidMenuAction.SET_REDSTONE_WATCH,
        Material.OBSERVER,
        "设置红石观察点",
        "把你当前位置记录为 redstone_watch/default。");
    setActionItem(
        holder,
        inventory,
        23,
        MaidMenuAction.GUARD_START,
        Material.IRON_SWORD,
        "保护我",
        "需要 Sentinel。");
    setActionItem(
        holder,
        inventory,
        24,
        MaidMenuAction.GUARD_STOP,
        Material.SHIELD,
        "停止护卫",
        "停止 Sentinel 护卫状态。");
    setActionItem(
        holder,
        inventory,
        25,
        MaidMenuAction.GUARD_HERE,
        Material.BELL,
        "守在这里",
        "需要 Sentinel；让女仆守住当前位置。");
    setActionItem(
        holder,
        inventory,
        35,
        MaidMenuAction.FISHING_START,
        Material.FISHING_ROD,
        "去钓鱼",
        "使用 fishing_spot/default 和 pond/default。");
    setActionItem(
        holder,
        inventory,
        36,
        MaidMenuAction.CHUNK_KEEPER_START,
        Material.CLOCK,
        "看住机器",
        "使用 redstone_watch/default 加载机器区块。");
    setActionItem(
        holder,
        inventory,
        37,
        MaidMenuAction.HARVEST_START,
        Material.WHEAT,
        "收农田",
        "使用 farm/default 收割成熟作物。");
    setActionItem(holder, inventory, 40, MaidMenuAction.CLOSE, Material.BARRIER, "关闭", "关闭菜单。");

    player.openInventory(inventory);
  }

  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    Inventory topInventory = event.getView().getTopInventory();
    if (topInventory.getHolder() instanceof MaidEquipmentHolder) {
      handleEquipmentClick(event, topInventory);
      return;
    }

    if (!(topInventory.getHolder() instanceof MaidMenuHolder holder)) {
      return;
    }

    event.setCancelled(true);
    if (!topInventory.equals(event.getClickedInventory())
        || !(event.getWhoClicked() instanceof Player player)) {
      return;
    }

    MaidMenuAction action = holder.getAction(event.getSlot());
    if (action == null) {
      return;
    }

    handleAction(player, action);
  }

  @EventHandler
  public void onInventoryDrag(InventoryDragEvent event) {
    Inventory topInventory = event.getView().getTopInventory();
    if (topInventory.getHolder() instanceof MaidMenuHolder) {
      event.setCancelled(true);
      return;
    }

    if (!(topInventory.getHolder() instanceof MaidEquipmentHolder)) {
      return;
    }

    int topSize = topInventory.getSize();
    boolean touchesTopInventory = event.getRawSlots().stream().anyMatch(slot -> slot < topSize);
    if (!touchesTopInventory) {
      return;
    }

    boolean allTopSlotsAllowed =
        event.getRawSlots().stream()
            .filter(slot -> slot < topSize)
            .allMatch(MaidEquipmentHolder::isEquipmentSlot);
    if (!allTopSlotsAllowed) {
      event.setCancelled(true);
    }
  }

  @EventHandler
  public void onInventoryClose(InventoryCloseEvent event) {
    Inventory topInventory = event.getInventory();
    if (!(topInventory.getHolder() instanceof MaidEquipmentHolder holder)
        || !(event.getPlayer() instanceof Player player)) {
      return;
    }

    boolean saved = plugin.getMaidNpcService().setEquipment(holder.readEquipment());
    if (saved) {
      player.sendMessage(Component.text(plugin.getMaidName() + " 的装备已保存。", NamedTextColor.GREEN));
    } else {
      player.sendMessage(Component.text("保存女仆装备失败，请确认 Citizens 是否正常加载。", NamedTextColor.RED));
    }
  }

  private void handleAction(Player player, MaidMenuAction action) {
    switch (action) {
      case STATUS -> sendStatus(player);
      case RECALL -> recallMaid(player);
      case SET_HOME -> setHome(player);
      case RETURN_HOME -> returnHome(player);
      case LOOK_AT_PLAYER -> lookAtPlayer(player);
      case OPEN_INVENTORY -> openInventory(player);
      case OPEN_EQUIPMENT -> openEquipment(player);
      case REFRESH_SKIN -> refreshSkin(player);
      case LIST_ANCHORS -> sendAnchorStatus(player);
      case SET_FISHING_SPOT -> setAnchorAtPlayer(player, AnchorType.FISHING_SPOT);
      case SET_REDSTONE_WATCH -> setAnchorAtPlayer(player, AnchorType.REDSTONE_WATCH);
      case SET_FARM_REGION_POS1 ->
          setRegionCornerAtPlayer(player, RegionType.FARM, RegionCorner.POS1);
      case SET_FARM_REGION_POS2 ->
          setRegionCornerAtPlayer(player, RegionType.FARM, RegionCorner.POS2);
      case SET_POND_REGION_POS1 ->
          setRegionCornerAtPlayer(player, RegionType.POND, RegionCorner.POS1);
      case SET_POND_REGION_POS2 ->
          setRegionCornerAtPlayer(player, RegionType.POND, RegionCorner.POS2);
      case SET_REDSTONE_REGION_POS1 ->
          setRegionCornerAtPlayer(player, RegionType.REDSTONE, RegionCorner.POS1);
      case SET_REDSTONE_REGION_POS2 ->
          setRegionCornerAtPlayer(player, RegionType.REDSTONE, RegionCorner.POS2);
      case FOLLOW_START -> startFollowing(player);
      case FOLLOW_STOP -> stopFollowing(player);
      case JOB_STOP -> stopActiveJob(player);
      case GUARD_START -> startGuarding(player);
      case GUARD_STOP -> stopGuarding(player);
      case GUARD_HERE -> startGuardingHere(player);
      case FISHING_START -> startFishing(player);
      case CHUNK_KEEPER_START -> startChunkKeeper(player);
      case HARVEST_START -> startHarvest(player);
      case CLOSE -> player.closeInventory();
    }
  }

  private void handleEquipmentClick(InventoryClickEvent event, Inventory topInventory) {
    if (event.isShiftClick()) {
      event.setCancelled(true);
      return;
    }

    if (topInventory.equals(event.getClickedInventory())
        && !MaidEquipmentHolder.isEquipmentSlot(event.getSlot())) {
      event.setCancelled(true);
    }
  }

  private void sendStatus(Player player) {
    player.sendMessage(
        Component.text(plugin.getMaidName() + " 当前状态：", NamedTextColor.LIGHT_PURPLE));
    player.sendMessage(Component.text("- AI 对话：可用", NamedTextColor.GRAY));
    player.sendMessage(
        Component.text("- Citizens 实体：", NamedTextColor.GRAY)
            .append(
                Component.text(
                    plugin.getMaidNpcService().isAvailable() ? "可用" : "不可用",
                    NamedTextColor.WHITE)));
    player.sendMessage(Component.text("- 右键菜单：可用", NamedTextColor.GRAY));
    player.sendMessage(
        Component.text("- " + plugin.getAnchorService().homeStatusLine(), NamedTextColor.GRAY));
    player.sendMessage(
        Component.text(
            "- " + plugin.getAnchorService().defaultFarmStatusLine(), NamedTextColor.GRAY));
    player.sendMessage(
        Component.text(
            "- " + plugin.getAnchorService().defaultPondStatusLine(), NamedTextColor.GRAY));
    player.sendMessage(
        Component.text(
            "- " + plugin.getAnchorService().defaultRedstoneStatusLine(), NamedTextColor.GRAY));
    player.sendMessage(
        Component.text("- 跟随：", NamedTextColor.GRAY)
            .append(
                Component.text(
                    plugin.getMaidNpcService().isFollowing() ? "进行中" : "未进行",
                    NamedTextColor.WHITE)));
    player.sendMessage(
        Component.text("- Sentinel 护卫：", NamedTextColor.GRAY)
            .append(
                Component.text(
                    plugin.getMaidNpcService().isGuardAvailable() ? "可用" : "不可用",
                    NamedTextColor.WHITE)));
    player.sendMessage(
        Component.text("- " + plugin.getJobService().statusLine(), NamedTextColor.GRAY));
    player.sendMessage(Component.text("- 工作：钓鱼 / 看机器 / 收农田", NamedTextColor.GRAY));
  }

  private void sendAnchorStatus(Player player) {
    player.sendMessage(Component.text("CraftMaid anchors：", NamedTextColor.LIGHT_PURPLE));
    for (String line : plugin.getAnchorService().anchorStatusLines()) {
      player.sendMessage(Component.text(line, NamedTextColor.GRAY));
    }
    player.sendMessage(Component.text("CraftMaid regions：", NamedTextColor.LIGHT_PURPLE));
    for (String line : plugin.getAnchorService().regionStatusLines()) {
      player.sendMessage(Component.text(line, NamedTextColor.GRAY));
    }
  }

  private void setAnchorAtPlayer(Player player, AnchorType type) {
    if (!ensureControlAllowed(player)) {
      return;
    }

    AnchorOperationResult result =
        plugin
            .getAnchorService()
            .setAnchor(type, MaidAnchorService.DEFAULT_NAME, player.getLocation());
    player.sendMessage(
        Component.text(
            result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
    if (result.success()) {
      player.closeInventory();
    }
  }

  private void setRegionCornerAtPlayer(Player player, RegionType type, RegionCorner corner) {
    if (!ensureControlAllowed(player)) {
      return;
    }

    AnchorOperationResult result =
        plugin
            .getAnchorService()
            .setRegionCorner(type, MaidAnchorService.DEFAULT_NAME, corner, player.getLocation());
    player.sendMessage(
        Component.text(
            result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
    if (result.success()) {
      player.closeInventory();
    }
  }

  private void recallMaid(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }

    boolean moved = plugin.getMaidNpcService().spawnAt(player, plugin.getMaidName());
    if (!moved) {
      player.sendMessage(Component.text("召回女仆失败，请检查 Citizens 是否正常加载。", NamedTextColor.RED));
      return;
    }
    player.closeInventory();
    player.sendMessage(
        Component.text("已把 " + plugin.getMaidName() + " 召回到你身边。", NamedTextColor.GREEN));
  }

  private void setHome(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }
    boolean saved = plugin.getMaidNpcService().setHomeAtMaidLocation(player);
    if (!saved) {
      player.sendMessage(Component.text("设置 home 失败，请确认女仆实体存在。", NamedTextColor.RED));
      return;
    }
    player.closeInventory();
    player.sendMessage(
        Component.text(
            "已把 " + plugin.getMaidName() + " 的当前位置记录为 home/default。", NamedTextColor.GREEN));
  }

  private void returnHome(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }
    boolean returned = plugin.getMaidNpcService().returnHome();
    if (!returned) {
      player.sendMessage(
          Component.text("还没有设置 home/default，先在菜单里点“设置 home”。", NamedTextColor.YELLOW));
      return;
    }
    player.closeInventory();
    player.sendMessage(Component.text(plugin.getMaidName() + " 已回到 home。", NamedTextColor.GREEN));
  }

  private void lookAtPlayer(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }
    boolean looked = plugin.getMaidNpcService().lookAt(player);
    if (!looked) {
      player.sendMessage(Component.text("女仆当前不在世界里，无法看向你。", NamedTextColor.YELLOW));
      return;
    }
    player.sendMessage(Component.text(plugin.getMaidName() + " 看向了你。", NamedTextColor.GREEN));
  }

  private void openInventory(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }
    player.closeInventory();
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              boolean opened = plugin.getMaidNpcService().openInventory(player);
              if (!opened) {
                player.sendMessage(
                    Component.text("打开女仆背包失败，请检查 Citizens 是否正常加载。", NamedTextColor.RED));
              }
            });
  }

  private void openEquipment(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }

    player.closeInventory();
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              MaidEquipmentHolder holder = new MaidEquipmentHolder();
              Inventory inventory =
                  Bukkit.createInventory(
                      holder,
                      MaidEquipmentHolder.SIZE,
                      Component.text(plugin.getMaidName() + "的装备"));
              holder.setInventory(inventory);
              holder.populate(plugin.getMaidNpcService().getEquipment());
              player.openInventory(inventory);
              player.sendMessage(Component.text("把装备放入对应槽位，关闭窗口后保存。", NamedTextColor.GRAY));
            });
  }

  private void refreshSkin(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }

    boolean refreshed = plugin.getMaidNpcService().applyConfiguredSkin(player);
    if (refreshed) {
      player.sendMessage(Component.text(plugin.getMaidName() + " 的皮肤已刷新。", NamedTextColor.GREEN));
    } else {
      player.sendMessage(
          Component.text("未刷新皮肤：请确认女仆已生成，且 maid.skin 不是 none/default。", NamedTextColor.YELLOW));
    }
  }

  private void startFollowing(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }
    plugin.getJobService().stopActiveJobForExternalControl("当前工作停止：玩家开始跟随。");
    boolean started = plugin.getMaidNpcService().startFollowing(player);
    if (!started) {
      player.sendMessage(Component.text("启动跟随失败，请检查 Citizens 是否正常加载。", NamedTextColor.RED));
      return;
    }
    player.closeInventory();
    player.sendMessage(Component.text(plugin.getMaidName() + " 会跟着你。", NamedTextColor.GREEN));
  }

  private void stopFollowing(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }
    plugin.getMaidNpcService().stopFollowing();
    player.closeInventory();
    player.sendMessage(Component.text(plugin.getMaidName() + " 会留在这里。", NamedTextColor.GREEN));
  }

  private void stopActiveJob(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }
    JobActionResult result = plugin.getJobService().stopActiveJob("job 已手动停止。");
    player.closeInventory();
    player.sendMessage(
        Component.text(
            result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
  }

  private void startGuarding(Player player) {
    if (!ensureControlAllowed(player)
        || !ensureNpcAvailable(player)
        || !ensureSentinelAvailable(player)) {
      return;
    }
    plugin.getJobService().stopJobsForGuarding("当前工作停止：玩家开始护卫。");
    boolean started = plugin.getMaidNpcService().startGuarding(player);
    if (!started) {
      player.sendMessage(Component.text("启动护卫失败，请检查 Sentinel 是否正常加载。", NamedTextColor.RED));
      return;
    }
    player.closeInventory();
    player.sendMessage(Component.text(plugin.getMaidName() + " 会保护你。", NamedTextColor.GREEN));
  }

  private void stopGuarding(Player player) {
    if (!ensureControlAllowed(player)
        || !ensureNpcAvailable(player)
        || !ensureSentinelAvailable(player)) {
      return;
    }
    boolean stopped = plugin.getMaidNpcService().stopGuarding();
    if (!stopped) {
      player.sendMessage(Component.text("停止护卫失败，请检查 Sentinel 是否正常加载。", NamedTextColor.RED));
      return;
    }
    player.closeInventory();
    player.sendMessage(Component.text(plugin.getMaidName() + " 已停止护卫。", NamedTextColor.GREEN));
  }

  private void startGuardingHere(Player player) {
    if (!ensureControlAllowed(player)
        || !ensureNpcAvailable(player)
        || !ensureSentinelAvailable(player)) {
      return;
    }
    plugin.getJobService().stopJobsForGuarding("当前工作停止：玩家开始守卫。");
    boolean started = plugin.getMaidNpcService().startGuardingHere(player);
    if (!started) {
      player.sendMessage(Component.text("启动守卫失败，请检查 Sentinel 是否正常加载。", NamedTextColor.RED));
      return;
    }
    player.closeInventory();
    player.sendMessage(Component.text(plugin.getMaidName() + " 会守住这里。", NamedTextColor.GREEN));
  }

  private void startFishing(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }
    JobActionResult result =
        plugin.getJobService().startFishing(player, MaidAnchorService.DEFAULT_NAME);
    player.closeInventory();
    player.sendMessage(
        Component.text(
            result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
  }

  private void startChunkKeeper(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }
    JobActionResult result =
        plugin.getJobService().startChunkKeeper(player, MaidAnchorService.DEFAULT_NAME);
    player.closeInventory();
    player.sendMessage(
        Component.text(
            result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
  }

  private void startHarvest(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }
    JobActionResult result =
        plugin.getJobService().startHarvest(player, MaidAnchorService.DEFAULT_NAME);
    player.closeInventory();
    player.sendMessage(
        Component.text(
            result.message(), result.success() ? NamedTextColor.GREEN : NamedTextColor.RED));
  }

  private boolean ensureControlAllowed(Player player) {
    if (player.hasPermission("craftmaid.admin")
        || player.getName().equalsIgnoreCase(plugin.getMasterName())) {
      return true;
    }
    player.sendMessage(Component.text("只有主人或管理员可以控制女仆。", NamedTextColor.RED));
    return false;
  }

  private boolean ensureNpcAvailable(Player player) {
    if (plugin.getMaidNpcService().isAvailable()) {
      return true;
    }
    player.sendMessage(Component.text("未安装或未启用 Citizens，实体功能不可用。", NamedTextColor.RED));
    return false;
  }

  private boolean ensureSentinelAvailable(Player player) {
    if (plugin.getMaidNpcService().isGuardAvailable()) {
      return true;
    }
    player.sendMessage(Component.text("未安装或未启用 Sentinel，护卫功能不可用。", NamedTextColor.RED));
    return false;
  }

  private void setActionItem(
      MaidMenuHolder holder,
      Inventory inventory,
      int slot,
      MaidMenuAction action,
      Material material,
      String name,
      String description) {
    ItemStack item = new ItemStack(material);
    ItemMeta meta = item.getItemMeta();
    if (meta != null) {
      meta.displayName(Component.text(name, NamedTextColor.LIGHT_PURPLE));
      meta.lore(List.of(Component.text(description, NamedTextColor.GRAY)));
      item.setItemMeta(meta);
    }

    holder.setAction(slot, action);
    inventory.setItem(slot, item);
  }
}
