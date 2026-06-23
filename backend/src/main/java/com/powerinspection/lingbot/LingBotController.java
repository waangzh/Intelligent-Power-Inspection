package com.powerinspection.lingbot;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.Ids;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.model.LingBotMapGateway;
import com.powerinspection.model.ModelServiceException;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lingbot")
public class LingBotController {
  private final DataStoreService dataStore;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;
  private final SimpMessagingTemplate messagingTemplate;
  private final LingBotMapGateway lingBotMapGateway;

  public LingBotController(DataStoreService dataStore, PermissionService permissionService, CurrentUser currentUser, SimpMessagingTemplate messagingTemplate, LingBotMapGateway lingBotMapGateway) {
    this.dataStore = dataStore;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
    this.messagingTemplate = messagingTemplate;
    this.lingBotMapGateway = lingBotMapGateway;
  }

  @GetMapping("/jobs")
  public ApiResponse<List<Map<String, Object>>> jobs() {
    return ApiResponse.ok(dataStore.list(DataCategory.LINGBOT_JOB));
  }

  @PostMapping("/jobs")
  public ApiResponse<Map<String, Object>> createJob(@RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.LINGBOT_MANAGE);
    body.putIfAbsent("id", Ids.next("lingbot"));
    body.putIfAbsent("createdAt", Instant.now().toString());
    Map<String, Object> job;
    try {
      job = lingBotMapGateway.createJob(body);
    } catch (ModelServiceException ex) {
      job = failedJob(body, ex.getMessage());
    }
    job = dataStore.upsert(DataCategory.LINGBOT_JOB, job);
    publishJob(job);
    return ApiResponse.ok(job);
  }

  @GetMapping("/jobs/{id}")
  public ApiResponse<Map<String, Object>> job(@PathVariable String id) {
    return ApiResponse.ok(dataStore.get(DataCategory.LINGBOT_JOB, id));
  }

  @PostMapping("/jobs/{id}/simulate")
  public ApiResponse<Map<String, Object>> simulate(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.LINGBOT_MANAGE);
    Map<String, Object> job = dataStore.get(DataCategory.LINGBOT_JOB, id);
    Map<String, Object> updated;
    try {
      updated = lingBotMapGateway.advanceJob(job);
    } catch (ModelServiceException ex) {
      updated = failedJob(job, ex.getMessage());
    }
    dataStore.upsert(DataCategory.LINGBOT_JOB, updated);
    publishJob(updated);
    return ApiResponse.ok(updated);
  }

  @GetMapping("/maps/{id}/pointcloud")
  public ApiResponse<Map<String, Object>> pointCloud(@PathVariable String id) {
    Map<String, Object> job = dataStore.get(DataCategory.LINGBOT_JOB, id);
    return ApiResponse.ok(lingBotMapGateway.pointCloud(job));
  }

  private Map<String, Object> failedJob(Map<String, Object> source, String message) {
    Map<String, Object> job = new LinkedHashMap<>(source);
    job.put("status", "FAILED");
    job.put("progress", 0);
    job.put("errorMessage", message);
    job.putIfAbsent("pointCount", 0);
    job.putIfAbsent("videoCount", 0);
    job.putIfAbsent("modelProvider", "http");
    return job;
  }

  private void publishJob(Map<String, Object> job) {
    messagingTemplate.convertAndSend("/topic/lingbot/jobs/" + job.get("id"), job);
    messagingTemplate.convertAndSend("/topic/lingbot/jobs", job);
  }
}
