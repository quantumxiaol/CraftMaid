package com.github.quantumxiaol.craftmaid.combat;

import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MaidDamagePolicyListenerTest {
  @ParameterizedTest
  @CsvSource({"true,5", "false,0"})
  void blockedOrZeroDamageNeverStartsRetaliation(boolean cancelled, double damage) {
    CraftMaid plugin = mock(CraftMaid.class);
    MaidNpcService npc = mock(MaidNpcService.class);
    MaidSelfDefenseService defense = mock(MaidSelfDefenseService.class);
    when(plugin.getMaidNpcService()).thenReturn(npc);
    when(plugin.getMaidSelfDefenseService()).thenReturn(defense);
    Entity maid = mock(Entity.class);
    Player attacker = mock(Player.class);
    EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
    when(event.getEntity()).thenReturn(maid);
    when(event.getDamager()).thenReturn(attacker);
    when(event.isCancelled()).thenReturn(cancelled);
    when(event.getFinalDamage()).thenReturn(damage);
    when(npc.isMaidEntity(maid)).thenReturn(true);
    new MaidDamagePolicyListener(plugin).onEntityDamage(event);
    verifyNoInteractions(defense);
  }
}
