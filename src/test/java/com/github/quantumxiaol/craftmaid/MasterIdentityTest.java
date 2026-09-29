package com.github.quantumxiaol.craftmaid;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class MasterIdentityTest {
  @Test
  void configuredUuidSurvivesRenameAndRejectsSameNameWithDifferentUuid() throws Exception {
    CraftMaid plugin = mock(CraftMaid.class, CALLS_REAL_METHODS);
    CraftMaidConfig config = mock(CraftMaidConfig.class, RETURNS_DEEP_STUBS);
    var field = CraftMaid.class.getDeclaredField("config");
    field.setAccessible(true);
    field.set(plugin, config);
    UUID master = UUID.randomUUID();
    when(config.maid().master()).thenReturn("OldName");
    when(config.maid().masterUuid()).thenReturn(master);
    var player = mock(Player.class);
    when(player.getName()).thenReturn("NewName");
    when(player.getUniqueId()).thenReturn(master);
    assertTrue(plugin.isMaster(player));
    when(player.getName()).thenReturn("OldName");
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    assertFalse(plugin.isMaster(player));
    when(config.maid().masterUuid()).thenReturn(null);
    assertTrue(plugin.isMaster(player), "Legacy configs retain name matching until a UUID is set");
  }

  @Test
  void malformedUuidDoesNotSilentlyFallBackToNameMatching() {
    CraftMaid plugin = mock(CraftMaid.class);
    var yaml = new YamlConfiguration();
    yaml.set("maid.master_uuid", "not-a-uuid");
    when(plugin.getConfig()).thenReturn(yaml);
    when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    assertEquals(new UUID(0L, 0L), CraftMaidConfig.load(plugin).maid().masterUuid());
  }
}
