package com.github.quantumxiaol.craftmaid.menu;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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
        holder, inventory, 19, MaidMenuAction.FOLLOW_START, Material.LEAD, "跟随我", "让女仆跟随你。");
    setActionItem(
        holder, inventory, 20, MaidMenuAction.FOLLOW_STOP, Material.SLIME_BALL, "别跟了", "停止当前跟随。");
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
        31,
        MaidMenuAction.FISHING_PLACEHOLDER,
        Material.FISHING_ROD,
        "去钓鱼",
        "占位按钮：后续接 Denizen 或 CraftMaid job。");
    setActionItem(holder, inventory, 40, MaidMenuAction.CLOSE, Material.BARRIER, "关闭", "关闭菜单。");

    player.openInventory(inventory);
  }

  @EventHandler
  public void onInventoryClick(InventoryClickEvent event) {
    Inventory topInventory = event.getView().getTopInventory();
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

  private void handleAction(Player player, MaidMenuAction action) {
    switch (action) {
      case STATUS -> sendStatus(player);
      case RECALL -> recallMaid(player);
      case SET_HOME -> setHome(player);
      case RETURN_HOME -> returnHome(player);
      case LOOK_AT_PLAYER -> lookAtPlayer(player);
      case FOLLOW_START -> startFollowing(player);
      case FOLLOW_STOP -> stopFollowing(player);
      case GUARD_START -> startGuarding(player);
      case GUARD_STOP -> stopGuarding(player);
      case GUARD_HERE -> startGuardingHere(player);
      case FISHING_PLACEHOLDER ->
          player.sendMessage(Component.text("钓鱼功能还没有接入；后续会先接 Denizen 原型。", NamedTextColor.YELLOW));
      case CLOSE -> player.closeInventory();
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
        Component.text("- home：", NamedTextColor.GRAY)
            .append(
                Component.text(
                    plugin.getMaidNpcService().getHomeLocation() == null ? "未设置" : "已设置",
                    NamedTextColor.WHITE)));
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
    player.sendMessage(Component.text("- 钓鱼 / 家务：尚未接入", NamedTextColor.YELLOW));
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
        Component.text("已把当前位置记录为 " + plugin.getMaidName() + " 的 home。", NamedTextColor.GREEN));
  }

  private void returnHome(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }
    boolean returned = plugin.getMaidNpcService().returnHome();
    if (!returned) {
      player.sendMessage(Component.text("还没有设置 home，先在菜单里点“设置 home”。", NamedTextColor.YELLOW));
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

  private void startFollowing(Player player) {
    if (!ensureControlAllowed(player) || !ensureNpcAvailable(player)) {
      return;
    }
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

  private void startGuarding(Player player) {
    if (!ensureControlAllowed(player)
        || !ensureNpcAvailable(player)
        || !ensureSentinelAvailable(player)) {
      return;
    }
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
    boolean started = plugin.getMaidNpcService().startGuardingHere(player);
    if (!started) {
      player.sendMessage(Component.text("启动守卫失败，请检查 Sentinel 是否正常加载。", NamedTextColor.RED));
      return;
    }
    player.closeInventory();
    player.sendMessage(Component.text(plugin.getMaidName() + " 会守住这里。", NamedTextColor.GREEN));
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
