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
  private static final int MENU_SIZE = 27;

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
        holder, inventory, 10, MaidMenuAction.STATUS, Material.BOOK, "查看状态", "查看当前已接入的能力。");
    setActionItem(
        holder,
        inventory,
        12,
        MaidMenuAction.RECALL,
        Material.ENDER_PEARL,
        "召回到身边",
        "把女仆移动到你当前位置。");
    setActionItem(
        holder,
        inventory,
        14,
        MaidMenuAction.FOLLOW_PLACEHOLDER,
        Material.LEAD,
        "跟随我",
        "占位按钮：下一步接 Citizens Navigator。");
    setActionItem(
        holder,
        inventory,
        15,
        MaidMenuAction.GUARD_PLACEHOLDER,
        Material.IRON_SWORD,
        "保护我",
        "占位按钮：后续接 Sentinel。");
    setActionItem(
        holder,
        inventory,
        16,
        MaidMenuAction.FISHING_PLACEHOLDER,
        Material.FISHING_ROD,
        "去钓鱼",
        "占位按钮：后续接 Denizen 或 CraftMaid job。");
    setActionItem(holder, inventory, 22, MaidMenuAction.CLOSE, Material.BARRIER, "关闭", "关闭菜单。");

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
      case FOLLOW_PLACEHOLDER ->
          player.sendMessage(
              Component.text("跟随功能还没有接入；下一步会接 Citizens Navigator。", NamedTextColor.YELLOW));
      case GUARD_PLACEHOLDER ->
          player.sendMessage(Component.text("护卫功能还没有接入；后续会接 Sentinel。", NamedTextColor.YELLOW));
      case FISHING_PLACEHOLDER ->
          player.sendMessage(Component.text("钓鱼功能还没有接入；后续会先接 Denizen 原型。", NamedTextColor.YELLOW));
      case CLOSE -> player.closeInventory();
    }
  }

  private void sendStatus(Player player) {
    player.sendMessage(
        Component.text(plugin.getMaidName() + " 当前状态：", NamedTextColor.LIGHT_PURPLE));
    player.sendMessage(Component.text("- AI 对话：可用", NamedTextColor.GRAY));
    player.sendMessage(Component.text("- Citizens 实体：可用", NamedTextColor.GRAY));
    player.sendMessage(Component.text("- 右键菜单：可用", NamedTextColor.GRAY));
    player.sendMessage(Component.text("- 跟随 / 护卫 / 钓鱼 / 家务：尚未接入", NamedTextColor.YELLOW));
  }

  private void recallMaid(Player player) {
    if (!plugin.getMaidNpcService().isAvailable()) {
      player.sendMessage(Component.text("未安装或未启用 Citizens，无法召回实体女仆。", NamedTextColor.RED));
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
