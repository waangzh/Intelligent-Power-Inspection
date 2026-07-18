package com.powerinspection.detection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerinspection.config.ModelFileWebConfig;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DetectionRetentionWorkerTests {
  private Path originalFile;
  private Path resultFile;
  private Path manualResultFile;

  @AfterEach
  void cleanFiles() throws Exception {
    if (originalFile != null) Files.deleteIfExists(originalFile);
    if (resultFile != null) Files.deleteIfExists(resultFile);
    if (manualResultFile != null) Files.deleteIfExists(manualResultFile);
  }

  @Test
  void runOncePurgesExpiredOriginalsAndDetectionRunsAtConfiguredBoundaries() throws Exception {
    Instant now = Instant.parse("2026-07-18T00:00:00Z");
    String suffix = UUID.randomUUID().toString();
    String originalKey = "robot-inspection/retention-" + suffix + ".jpg";
    String resultKey = "detection-runs/retention-" + suffix + ".jpg";
    originalFile = ModelFileWebConfig.MODEL_FILE_ROOT.resolve(originalKey);
    resultFile = ModelFileWebConfig.MODEL_FILE_ROOT.resolve(resultKey);
    String manualResultKey = "locate-anything/results/retention-" + suffix + ".jpg";
    manualResultFile = ModelFileWebConfig.MODEL_FILE_ROOT.resolve(manualResultKey);
    Files.createDirectories(originalFile.getParent());
    Files.createDirectories(resultFile.getParent());
    Files.write(originalFile, new byte[] {1});
    Files.write(resultFile, new byte[] {2});
    Files.createDirectories(manualResultFile.getParent());
    Files.write(manualResultFile, new byte[] {3});

    RobotInspectionImageEntity image = new RobotInspectionImageEntity();
    image.setId("rimg-retention");
    image.setStorageKey(originalKey);
    image.setStatus("AVAILABLE");
    DetectionRunEntity run = new DetectionRunEntity();
    run.setId("det-retention");
    run.setResultStorageKey(resultKey);
    DetectionRunEntity manualRun = new DetectionRunEntity();
    manualRun.setId("manual-retention");
    manualRun.setResultStorageKey(manualResultKey);

    RobotInspectionImageRepository imageRepository = mock(RobotInspectionImageRepository.class);
    DetectionRunRepository runRepository = mock(DetectionRunRepository.class);
    String imageCutoff = now.minus(30, ChronoUnit.DAYS).toString();
    String runCutoff = now.minus(90, ChronoUnit.DAYS).toString();
    when(imageRepository.findByStorageKeyIsNotNullAndCapturedAtLessThan(imageCutoff)).thenReturn(List.of(image));
    when(runRepository.findByCompletedAtIsNotNullAndCompletedAtLessThan(runCutoff)).thenReturn(List.of(run, manualRun));

    new DetectionRetentionWorker(imageRepository, runRepository, 30, 90).runOnce(now);

    assertThat(Files.exists(originalFile)).isFalse();
    assertThat(image.getStorageKey()).isNull();
    assertThat(image.getStatus()).isEqualTo("ORIGINAL_PURGED");
    assertThat(image.getOriginalPurgedAt()).isEqualTo(now.toString());
    verify(imageRepository).save(image);
    assertThat(Files.exists(resultFile)).isFalse();
    verify(runRepository).delete(run);
    assertThat(Files.exists(manualResultFile)).isFalse();
    verify(runRepository).delete(manualRun);
  }
}
