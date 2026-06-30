package com.github.quantumxiaol.craftmaid.job;

import com.github.quantumxiaol.craftmaid.job.MaidJobService.JobActionResult;
import java.util.UUID;

public interface MaidJob {
  MaidJobType type();

  String name();

  UUID ownerId();

  JobPhase phase();

  JobActionResult start();

  void stop(String reason);

  void cancelWithoutNotification();

  boolean isRunning();

  String statusLine();
}
