package com.github.quantumxiaol.craftmaid.npc;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.combat.MaidCombatPolicy;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.CombatSettings;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

class SentinelCompatibilityTest {
  @Test
  void unknownTargetsCannotEraseSupportedTargetsInInstalledSentinel() throws Throwable {
    String jar = System.getProperty("craftmaid.sentinel.jar", "");
    assumeTrue(!jar.isBlank(), "Pass -Dcraftmaid.sentinel.jar=/path/to/Sentinel.jar");
    try (var loader =
            new URLClassLoader(
                new URL[] {Path.of(jar).toUri().toURL()}, getClass().getClassLoader());
        var bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getBukkitVersion).thenReturn("26.1.2-R0.1-SNAPSHOT");
      // Register the version-specific pillager label without initializing server registries.
      Class.forName("org.mcmonkey.sentinel.targeting.SentinelTarget", true, loader)
          .getConstructor(EntityType[].class, String[].class)
          .newInstance(new EntityType[] {EntityType.PILLAGER}, new String[] {"PILLAGER"});
      Class<?> sentinelPlugin = loader.loadClass("org.mcmonkey.sentinel.SentinelPlugin");
      @SuppressWarnings("unchecked")
      var integrations =
          (Map<String, Object>)
              java.lang.invoke.MethodHandles.lookup()
                  .findStaticGetter(sentinelPlugin, "integrationPrefixMap", java.util.HashMap.class)
                  .invoke();
      integrations.put(
          "uuid",
          loader
              .loadClass("org.mcmonkey.sentinel.integration.SentinelUUID")
              .getConstructor()
              .newInstance());
      Class<?> traitType = loader.loadClass("org.mcmonkey.sentinel.SentinelTrait");
      Class<?> listType = loader.loadClass("org.mcmonkey.sentinel.targeting.SentinelTargetList");
      // Avoid NPC/weapon initialization: this test runs the real target APIs without a server.
      Object trait = new ObjenesisStd().newInstance(traitType);
      for (String field : List.of("allTargets", "allIgnores", "allAvoids")) {
        traitType.getField(field).set(trait, listType.getConstructor().newInstance());
      }
      String unsupported = "craftmaid_unknown_mob";
      traitType.getMethod("addTarget", String.class).invoke(trait, "zombies");
      assertThrows(
          InvocationTargetException.class,
          () -> traitType.getMethod("addTarget", String.class).invoke(trait, unsupported));

      CraftMaid plugin = mock(CraftMaid.class);
      when(plugin.getLogger()).thenReturn(Logger.getLogger("SentinelCompatibilityTest"));
      when(plugin.getMaidCombatPolicy())
          .thenReturn(
              policy(
                  List.of("zombies", "skeletons", "pillagers", "creaking", unsupported),
                  List.of("creepers", "wolves", unsupported)));
      var service = new CitizensMaidNpcService(plugin);
      var configure =
          CitizensMaidNpcService.class.getDeclaredMethod("configureSentinelCombat", Object.class);
      configure.setAccessible(true);
      configure.invoke(service, trait);
      for (String required : List.of("ZOMBIE", "SKELETON", "PILLAGER")) {
        assertTrue(labels(trait, "allTargets").contains(required), required);
      }
      assertFalse(labels(trait, "allTargets").contains(unsupported));
      assertTrue(labels(trait, "allAvoids").contains("CREEPER"));
      assertTrue(labels(trait, "allIgnores").contains("WOLF"));
      Object targets = traitType.getField("allTargets").get(trait);
      assertFalse(
          ((Collection<?>) listType.getField("targetsProcessed").get(targets)).contains(null));
      // Re-entering guard must remain stable, including after an old corrupted list.
      configure.invoke(service, trait);
      assertTrue(labels(trait, "allTargets").contains("PILLAGER"));

      // UUID self-defense labels must still work after adding validation.
      var add =
          CitizensMaidNpcService.class.getDeclaredMethod(
              "addSentinelLabel", Object.class, String.class, String.class);
      add.setAccessible(true);
      add.invoke(service, trait, "addTarget", "uuid:00000000-0000-0000-0000-000000000001");
      assertFalse(((Collection<?>) listType.getField("byOther").get(targets)).isEmpty());

      when(plugin.getMaidCombatPolicy()).thenReturn(policy(List.of(unsupported), List.of()));
      var failure =
          assertThrows(InvocationTargetException.class, () -> configure.invoke(service, trait));
      assertTrue(failure.getCause().getMessage().contains("没有成功添加任何"));
    }
  }

  @Test
  void missingValidationApiDoesNotWriteAnyTargetsOrReportGuardReady() throws Exception {
    CraftMaid plugin = mock(CraftMaid.class);
    when(plugin.getLogger()).thenReturn(Logger.getLogger("SentinelCompatibilityTest"));
    when(plugin.getMaidCombatPolicy()).thenReturn(policy(List.of("zombies"), List.of()));
    var service = new CitizensMaidNpcService(plugin);
    var trait = new UnsupportedSentinel();
    var configure =
        CitizensMaidNpcService.class.getDeclaredMethod("configureSentinelCombat", Object.class);
    configure.setAccessible(true);
    assertThrows(InvocationTargetException.class, () -> configure.invoke(service, trait));
    assertEquals(0, trait.addCalls);
  }

  private static Collection<?> labels(Object trait, String field) throws Exception {
    Object list = trait.getClass().getField(field).get(trait);
    return (Collection<?>) list.getClass().getField("targets").get(list);
  }

  private static MaidCombatPolicy policy(List<String> hostile, List<String> avoid) {
    return MaidCombatPolicy.from(
        new CombatSettings(
            true, false, 0, hostile, List.of(), avoid, "ignore", false, null, null, null));
  }

  public static class UnsupportedSentinel {
    int addCalls;

    public void addTarget(String key) {
      addCalls++;
    }
  }
}
