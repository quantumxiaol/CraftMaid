package com.github.quantumxiaol.craftmaid.interaction;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.menu.MaidMenuService;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class CitizensMaidInteractionListener implements Listener {
  private final CraftMaid plugin;
  private final MaidNpcService maidNpcService;
  private final MaidMenuService menuService;

  public CitizensMaidInteractionListener(
      CraftMaid plugin, MaidNpcService maidNpcService, MaidMenuService menuService) {
    this.plugin = plugin;
    this.maidNpcService = maidNpcService;
    this.menuService = menuService;
  }

  @EventHandler
  public void onNpcRightClick(NPCRightClickEvent event) {
    if (!maidNpcService.isMaidNpc(event.getNPC().getId())) {
      return;
    }

    event.setCancelled(true);
    maidNpcService.syncConfiguredName();
    Bukkit.getScheduler().runTask(plugin, () -> menuService.openFor(event.getClicker()));
  }
}
