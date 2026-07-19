package com.powerinspection.detection;

import com.powerinspection.config.ModelFileWebConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DetectionRetentionWorker {
  private static final Path IMAGE_ROOT = ModelFileWebConfig.MODEL_FILE_ROOT.resolve("robot-inspection").normalize();
  private static final Path RESULT_ROOT = ModelFileWebConfig.MODEL_FILE_ROOT.resolve("detection-runs").normalize();
  private static final Path MANUAL_RESULT_ROOT = ModelFileWebConfig.MODEL_FILE_ROOT.resolve("locate-anything/results").normalize();

  private final RobotInspectionImageRepository imageRepository;
  private final DetectionRunRepository runRepository;
  private final int originalRetentionDays;
  private final int resultRetentionDays;

  public DetectionRetentionWorker(
      RobotInspectionImageRepository imageRepository,
      DetectionRunRepository runRepository,
      @Value("${app.robot-inspection.original-retention-days:30}") int originalRetentionDays,
      @Value("${app.robot-inspection.detection-retention-days:90}") int resultRetentionDays) {
    this.imageRepository = imageRepository;
    this.runRepository = runRepository;
    this.originalRetentionDays = originalRetentionDays;
    this.resultRetentionDays = resultRetentionDays;
  }

  @Scheduled(cron = "${app.robot-inspection.retention-cron:0 15 3 * * *}")
  public void runScheduled() {
    runOnce(Instant.now());
  }

  void runOnce(Instant now) {
    String imageCutoff = now.minus(originalRetentionDays, ChronoUnit.DAYS).toString();
    for (RobotInspectionImageEntity image : imageRepository.findByStorageKeyIsNotNullAndCapturedAtLessThan(imageCutoff)) {
      Path file = resolve(image.getStorageKey(), IMAGE_ROOT);
      if (file == null || !delete(file)) continue;
      image.setStorageKey(null);
      image.setStatus("ORIGINAL_PURGED");
      image.setOriginalPurgedAt(now.toString());
      image.setUpdatedAt(now.toString());
      imageRepository.save(image);
    }

    String runCutoff = now.minus(resultRetentionDays, ChronoUnit.DAYS).toString();
    for (DetectionRunEntity run : runRepository.findByCompletedAtIsNotNullAndCompletedAtLessThan(runCutoff)) {
      String storageKey = run.getResultStorageKey();
      if (storageKey != null) {
        Path file = resolveResult(storageKey);
        if (file == null || !delete(file)) continue;
      }
      runRepository.delete(run);
    }
  }

  private Path resolve(String storageKey, Path root) {
    if (storageKey == null || storageKey.isBlank()) return null;
    Path path = ModelFileWebConfig.MODEL_FILE_ROOT.resolve(storageKey).normalize();
    return path.startsWith(root) ? path : null;
  }

  private Path resolveResult(String storageKey) {
    Path path = ModelFileWebConfig.MODEL_FILE_ROOT.resolve(storageKey).normalize();
    return path.startsWith(RESULT_ROOT) || path.startsWith(MANUAL_RESULT_ROOT) ? path : null;
  }

  private boolean delete(Path file) {
    try {
      Files.deleteIfExists(file);
      return true;
    } catch (IOException ex) {
      return false;
    }
  }
}
