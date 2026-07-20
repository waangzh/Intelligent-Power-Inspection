package com.powerinspection.workorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkOrderPhotoRetentionWorkerTests {
  private Path orphanFile;

  @AfterEach
  void cleanFiles() throws Exception {
    if (orphanFile != null) {
      Files.deleteIfExists(orphanFile);
      Path parent = orphanFile.getParent();
      if (parent != null && Files.isDirectory(parent)) {
        try (var stream = Files.list(parent)) {
          if (stream.findAny().isEmpty()) {
            Files.deleteIfExists(parent);
          }
        }
      }
    }
  }

  @Test
  void runOncePurgesUnreferencedPhotosOlderThanRetentionWindow() throws Exception {
    Instant now = Instant.parse("2026-07-18T00:00:00Z");
    String orderId = "wo_orphan_" + UUID.randomUUID().toString().substring(0, 8);
    orphanFile = WorkOrderPhotoService.ROOT.resolve(orderId).resolve("orphan.jpg");
    Files.createDirectories(orphanFile.getParent());
    Files.write(orphanFile, new byte[] {1});
    Files.setLastModifiedTime(orphanFile, FileTime.from(now.minus(25, ChronoUnit.HOURS)));

    DataStoreService dataStore = mock(DataStoreService.class);
    when(dataStore.list(DataCategory.WORK_ORDER)).thenReturn(List.of());

    new WorkOrderPhotoRetentionWorker(dataStore, 24).runOnce(now);

    assertThat(Files.exists(orphanFile)).isFalse();
  }

  @Test
  void runOnceKeepsReferencedPendingAndResolutionPhotos() throws Exception {
    Instant now = Instant.parse("2026-07-18T00:00:00Z");
    String orderId = "wo_keep_" + UUID.randomUUID().toString().substring(0, 8);
    orphanFile = WorkOrderPhotoService.ROOT.resolve(orderId).resolve("keep.jpg");
    Files.createDirectories(orphanFile.getParent());
    Files.write(orphanFile, new byte[] {1});
    Files.setLastModifiedTime(orphanFile, FileTime.from(now.minus(25, ChronoUnit.HOURS)));
    String url = WorkOrderPhotoService.publicUrl(orderId, "keep.jpg");

    DataStoreService dataStore = mock(DataStoreService.class);
    when(dataStore.list(DataCategory.WORK_ORDER))
        .thenReturn(
            List.of(
                Map.of("pendingPhotos", List.of(url)),
                Map.of("resolutionForm", Map.of("photos", List.of(url)))));

    new WorkOrderPhotoRetentionWorker(dataStore, 24).runOnce(now);

    assertThat(Files.exists(orphanFile)).isTrue();
  }
}
