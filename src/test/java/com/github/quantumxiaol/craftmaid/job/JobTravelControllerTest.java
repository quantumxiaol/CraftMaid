package com.github.quantumxiaol.craftmaid.job;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.github.quantumxiaol.craftmaid.CraftMaid;
import com.github.quantumxiaol.craftmaid.config.CraftMaidConfig.JobNavigationSettings;
import com.github.quantumxiaol.craftmaid.npc.MaidNpcService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JobTravelControllerTest {
  @ParameterizedTest
  @CsvSource({"50,60", "75,80", "199,200", "100,100"})
  void retriesOnElapsedTimeInsteadOfWaitingForACommonMultiple(int retry, int expectedPeriod) {
    CraftMaid plugin = mock(CraftMaid.class);
    MaidNpcService npc = mock(MaidNpcService.class);
    when(plugin.getMaidNpcService()).thenReturn(npc);
    when(plugin.getJobNavigationSettings())
        .thenReturn(new JobNavigationSettings(1.5, 20, 3, 180, retry, 0));
    List<Integer> checks = new ArrayList<>();
    AtomicInteger tick = new AtomicInteger();
    when(npc.distanceSquaredTo(any()))
        .thenAnswer(
            call -> {
              checks.add(tick.get());
              return 100.0;
            });
    JobTravelController travel = new JobTravelController(plugin, new Location(null, 0, 0, 0));
    for (int t = 20; t <= expectedPeriod * 3; t += 20) {
      tick.set(t);
      assertTrue(travel.tickTravelling(20));
    }
    assertEquals(List.of(expectedPeriod, expectedPeriod * 2, expectedPeriod * 3), checks);
    verify(npc, atLeastOnce()).moveTo(any());
  }
}
