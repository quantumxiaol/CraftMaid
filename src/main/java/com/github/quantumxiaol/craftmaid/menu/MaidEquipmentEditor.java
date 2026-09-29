package com.github.quantumxiaol.craftmaid.menu;

import com.github.quantumxiaol.craftmaid.npc.MaidEquipment;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import java.util.UUID;

/** A single custody transfer: editable items live in the window, not on the NPC as well. */
final class MaidEquipmentEditor {
  private final MaidNpcService npc;
  private Session active;

  MaidEquipmentEditor(MaidNpcService npc) {
    this.npc = npc;
  }

  Session begin(UUID playerId) {
    if (active != null) {
      return null;
    }
    UUID npcId = npc.getStoredNpcUniqueId();
    if (npcId == null) {
      return null;
    }
    MaidEquipment equipment = npc.getEquipment();
    if (!npc.setEquipment(MaidEquipment.empty())) {
      return null;
    }
    active = new Session(playerId, npcId, equipment);
    return active;
  }

  boolean owns(Session session) {
    return session != null && active == session;
  }

  boolean isEditing() {
    return active != null;
  }

  boolean finish(Session session, MaidEquipment equipment) {
    if (!owns(session)) {
      throw new IllegalStateException("Equipment session is no longer active");
    }
    active = null;
    // Combat/loot or another plugin may have equipped new items during the edit.
    // In that case keep those items on the NPC and return this window's items to its owner.
    return session.npcId().equals(npc.getStoredNpcUniqueId())
        && npc.getEquipment().equals(MaidEquipment.empty())
        && npc.setEquipment(equipment);
  }

  record Session(UUID playerId, UUID npcId, MaidEquipment equipment) {}
}
