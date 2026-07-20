package com.powerinspection.workorder;

import com.powerinspection.config.ModelFileWebConfig;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkOrderPhotoRetentionWorker {
  private final DataStoreService dataStore;
  private final int orphanRetentionHours;

  public WorkOrderPhotoRetentionWorker(
      DataStoreService dataStore,
      @Value("${app.work-order.orphan-photo-retention-hours:24}") int orphanRetentionHours) {
    this.dataStore = dataStore;
    this.orphanRetentionHours = orphanRetentionHours;
  }

  @Scheduled(cron = "${app.work-order.orphan-photo-retention-cron:0 25 3 * * *}")
  public void runScheduled() {
    runOnce(Instant.now());
  }

  void runOnce(Instant now) {
    Set<String> referenced = referencedPhotoUrls();
    long cutoffMs = now.minus(orphanRetentionHours, ChronoUnit.HOURS).toEpochMilli();
    try {
      if (!Files.isDirectory(WorkOrderPhotoService.ROOT)) {
        return;
      }
      Files.walk(WorkOrderPhotoService.ROOT)
        .filter(Files::isRegularFile)
        .forEach(file -> {
          String url = toPublicUrl(file);
          if (url == null || referenced.contains(url)) {
            return;
          }
          try {
            if (Files.getLastModifiedTime(file).toMillis() > cutoffMs) {
              return;
            }
            Files.deleteIfExists(file);
          } catch (IOException ignored) {
            // best effort
          }
        });
    } catch (IOException ignored) {
      // best effort
    }
  }

  private Set<String> referencedPhotoUrls() {
    Set<String> urls = new HashSet<>();
    for (Map<String, Object> order : dataStore.list(DataCategory.WORK_ORDER)) {
      collectUrls(urls, order.get("pendingPhotos"));
      Object resolutionForm = order.get("resolutionForm");
      if (resolutionForm instanceof Map<?, ?> form) {
        collectUrls(urls, form.get("photos"));
      }
    }
    return urls;
  }

  private void collectUrls(Set<String> urls, Object raw) {
    if (!(raw instanceof List<?> list)) {
      return;
    }
    for (Object item : list) {
      if (item != null) {
        String url = String.valueOf(item).trim();
        if (!url.isBlank()) {
          urls.add(url);
        }
      }
    }
  }

  private String toPublicUrl(Path file) {
    Path root = ModelFileWebConfig.MODEL_FILE_ROOT.normalize();
    Path normalized = file.normalize();
    if (!normalized.startsWith(WorkOrderPhotoService.ROOT)) {
      return null;
    }
    Path relative = root.relativize(normalized);
    return "/model-files/" + relative.toString().replace('\\', '/');
  }
}
