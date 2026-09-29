package com.github.quantumxiaol.craftmaid.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.npc.MaidEquipment;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MaidEquipmentEditorTest {
  MaidNpcService npc;
  MaidEquipmentEditor editor;
  AtomicReference<MaidEquipment> stored;

  @BeforeEach
  void setup() {
    npc = mock(MaidNpcService.class);
    when(npc.getStoredNpcUniqueId()).thenReturn(UUID.randomUUID());
    ItemStack sword = mock(ItemStack.class);
    when(sword.getType()).thenReturn(Material.DIAMOND_SWORD);
    when(sword.clone()).thenReturn(sword);
    stored = new AtomicReference<>(new MaidEquipment(sword, null, null, null, null, null));
    when(npc.getEquipment()).thenAnswer(call -> stored.get());
    when(npc.setEquipment(any()))
        .thenAnswer(
            call -> {
              stored.set(call.getArgument(0));
              return true;
            });
    editor = new MaidEquipmentEditor(npc);
  }

  @Test
  void onlyOnePlayerCanTakeCustodyOfTheEquipment() {
    var first = editor.begin(UUID.randomUUID());
    assertNotNull(first);
    assertNotNull(first.equipment().mainHand());
    assertNull(stored.get().mainHand(), "NPC must not retain a second copy");
    assertNull(editor.begin(UUID.randomUUID()));
    assertTrue(editor.finish(first, MaidEquipment.empty()));
    var second = editor.begin(UUID.randomUUID());
    assertNull(second.equipment().mainHand(), "Taking the sword must not restore it on close");
  }

  @Test
  void closingTwiceCannotWriteBackTheSameItemsTwice() {
    var session = editor.begin(UUID.randomUUID());
    assertTrue(editor.finish(session, session.equipment()));
    assertThrows(IllegalStateException.class, () -> editor.finish(session, session.equipment()));
  }

  @Test
  void anOldWindowCannotWriteToAReplacementNpc() {
    var session = editor.begin(UUID.randomUUID());
    when(npc.getStoredNpcUniqueId()).thenReturn(UUID.randomUUID());
    clearInvocations(npc);
    assertFalse(editor.finish(session, session.equipment()));
    verify(npc, never()).setEquipment(any());
  }

  @Test
  void failedCommitReleasesTheSessionForRecovery() {
    var session = editor.begin(UUID.randomUUID());
    doReturn(false).when(npc).setEquipment(any());
    assertFalse(editor.finish(session, session.equipment()));
    assertFalse(editor.owns(session));
  }

  @Test
  void newlyEquippedItemsAreNotOverwrittenOnClose() {
    var session = editor.begin(UUID.randomUUID());
    stored.set(session.equipment());
    clearInvocations(npc);
    assertFalse(editor.finish(session, session.equipment()));
    verify(npc, never()).setEquipment(any());
  }
}
