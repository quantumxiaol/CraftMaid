package com.github.quantumxiaol.craftmaid.npc;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public final class MaidNpcService {
  private final CraftMaid plugin;

  public MaidNpcService(CraftMaid plugin) {
    this.plugin = plugin;
  }

  public boolean isCitizensEnabled() {
    return plugin.getServer().getPluginManager().isPluginEnabled("Citizens");
  }

  public boolean spawnAt(Player player, String maidName) {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, maidName);
      plugin.getConfig().set("maid.npc_id", npc.getId());
      plugin.saveConfig();
    }

    if (npc.isSpawned()) {
      npc.despawn();
    }
    npc.spawn(player.getLocation());
    return true;
  }

  public boolean despawnStored() {
    NPC npc = getStoredNpcOrNull();
    if (npc == null) {
      return false;
    }

    if (npc.isSpawned()) {
      npc.despawn();
    }
    npc.destroy();
    plugin.getConfig().set("maid.npc_id", -1);
    plugin.saveConfig();
    return true;
  }

  public NPC getStoredNpcOrNull() {
    int npcId = plugin.getConfig().getInt("maid.npc_id", -1);
    if (npcId < 0) {
      return null;
    }
    return CitizensAPI.getNPCRegistry().getById(npcId);
  }
}
