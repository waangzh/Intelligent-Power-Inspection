package com.powerinspection.robot;

import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RobotTrackRetentionService {
  private final RobotLocationService locationService;

  public RobotTrackRetentionService(RobotLocationService locationService) {
    this.locationService = locationService;
  }

  @Scheduled(cron = "${app.robot.track-retention-cron:0 30 3 * * *}")
  public void cleanup() {
    locationService.purgeExpiredTrackPoints(Instant.now());
  }
}
