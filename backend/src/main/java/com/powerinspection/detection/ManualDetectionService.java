package com.powerinspection.detection;

import com.powerinspection.common.ApiException;
import com.powerinspection.model.LocateAnythingFinding;
import com.powerinspection.model.LocateAnythingGateway;
import com.powerinspection.model.LocateAnythingRequest;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Service;

@Service
public class ManualDetectionService {
  private static final String STATUS_RUNNING = "RUNNING";
  private static final String STATUS_SUCCEEDED = "SUCCEEDED";
  private static final String STATUS_FAILED = "FAILED";

  private final LocateAnythingGateway locateAnythingGateway;
  private final Map<String, ManualDetectionJob> jobs = new ConcurrentHashMap<>();
  private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
    Thread thread = new Thread(runnable, "manual-detection-worker");
    thread.setDaemon(true);
    return thread;
  });

  public ManualDetectionService(LocateAnythingGateway locateAnythingGateway) {
    this.locateAnythingGateway = locateAnythingGateway;
  }

  public ManualDetectionController.ManualDetectionResponse submit(String requestId, String inputImageUrl, List<Map<String, Object>> detections) {
    ManualDetectionJob job = new ManualDetectionJob(requestId, STATUS_RUNNING, inputImageUrl, null, List.of(), List.of(), null, Instant.now().toString(), null, null);
    jobs.put(requestId, job);
    executor.submit(() -> runDetection(requestId, inputImageUrl, detections));
    return job.toResponse();
  }

  public ManualDetectionController.ManualDetectionResponse get(String requestId) {
    ManualDetectionJob job = jobs.get(requestId);
    if (job == null) {
      throw ApiException.notFound("检测任务不存在");
    }
    return job.toResponse();
  }

  private void runDetection(String requestId, String inputImageUrl, List<Map<String, Object>> detections) {
    ManualDetectionJob current = jobs.get(requestId);
    String startedAt = Instant.now().toString();
    if (current != null) {
      jobs.put(requestId, current.withTiming(startedAt, null));
    }
    try {
      Map<String, Object> task = map("id", requestId, "name", "Manual LocateAnything Detection", "createdAt", Instant.now().toString());
      Map<String, Object> route = map("id", "manual_route", "name", "Manual Detection");
      Map<String, Object> checkpoint = map("id", "manual_checkpoint", "name", "Manual Upload");
      List<LocateAnythingFinding> findings = locateAnythingGateway.detectCheckpoint(
        new LocateAnythingRequest(task, route, checkpoint, inputImageUrl, detections)
      );
      String resultImageUrl = findings.stream()
        .map(LocateAnythingFinding::imageUrl)
        .filter(value -> value != null && !value.isBlank())
        .findFirst()
        .orElse(null);
      jobs.put(requestId, new ManualDetectionJob(
        requestId,
        STATUS_SUCCEEDED,
        inputImageUrl,
        resultImageUrl,
        findings,
        List.of(),
        null,
        current == null ? startedAt : current.createdAt(),
        startedAt,
        Instant.now().toString()
      ));
    } catch (Exception ex) {
      jobs.put(requestId, new ManualDetectionJob(
        requestId,
        STATUS_FAILED,
        inputImageUrl,
        null,
        List.of(),
        List.of(ex.getMessage() == null ? "模型检测失败" : ex.getMessage()),
        ex.getMessage() == null ? "模型检测失败" : ex.getMessage(),
        current == null ? startedAt : current.createdAt(),
        startedAt,
        Instant.now().toString()
      ));
    }
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> item = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) {
      item.put(values[i].toString(), values[i + 1]);
    }
    return item;
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }

  private record ManualDetectionJob(
    String requestId,
    String status,
    String inputImageUrl,
    String resultImageUrl,
    List<LocateAnythingFinding> findings,
    List<String> warnings,
    String errorMessage,
    String createdAt,
    String startedAt,
    String completedAt
  ) {
    ManualDetectionJob withTiming(String startedAt, String completedAt) {
      return new ManualDetectionJob(requestId, status, inputImageUrl, resultImageUrl, findings, warnings, errorMessage, createdAt, startedAt, completedAt);
    }

    ManualDetectionController.ManualDetectionResponse toResponse() {
      return new ManualDetectionController.ManualDetectionResponse(requestId, status, inputImageUrl, resultImageUrl, findings, warnings, errorMessage, createdAt, startedAt, completedAt);
    }
  }
}
