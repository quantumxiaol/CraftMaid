package com.github.quantumxiaol.craftmaid.job;

import com.github.quantumxiaol.craftmaid.job.MaidJobService.JobActionResult;
import java.util.UUID;

public interface MaidJob {
  MaidJobType type();

  String name();

  UUID ownerId();

  JobPhase phase();

  /** Validate and reserve resources without changing the current body controller. */
  JobActionResult prepare();

  /** Release resources reserved by prepare(), without cancelling another controller. */
  void discardPreparation();

  JobActionResult start();

  void stop(String reason);

  void cancelWithoutNotification();

  boolean isRunning();

  String statusLine();
}
