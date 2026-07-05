package com.powerinspection.lingbot;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.Ids;
import com.powerinspection.config.ModelFileWebConfig;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.model.LingBotMapGateway;
import com.powerinspection.model.ModelServiceException;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/lingbot")
public class LingBotController {
  private static final Path VIDEO_UPLOAD_DIR = ModelFileWebConfig.MODEL_FILE_ROOT.resolve("lingbot").resolve("uploads");
  private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of("video/mp4", "video/quicktime", "video/x-msvideo", "video/x-matroska", "video/webm");
  private static final Set<String> INPUT_KINDS = Set.of("video", "image_sequence");
  private static final Set<String> OUTPUT_PROFILES = Set.of("preview", "viewer-ready", "rendered-video", "predictions");
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
    normalizeJobPayload(body);
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

  @PostMapping(value = "/uploads/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<UploadResponse> uploadVideo(@RequestPart("video") MultipartFile video, HttpServletRequest request) throws IOException {
    permissionService.require(currentUser.get(), Permission.LINGBOT_MANAGE);
    validateVideo(video);

    String filename = Ids.next("lingbot_video") + extension(video);
    Files.createDirectories(VIDEO_UPLOAD_DIR);
    Path output = VIDEO_UPLOAD_DIR.resolve(filename).normalize();
    if (!output.startsWith(VIDEO_UPLOAD_DIR)) {
      throw ApiException.badRequest("上传文件名非法");
    }
    try (InputStream input = video.getInputStream()) {
      Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
    }

    return ApiResponse.ok(new UploadResponse(publicVideoUrl(request, filename), filename, video.getSize()));
  }

  @PostMapping("/jobs/{id}/simulate")
  public ApiResponse<Map<String, Object>> simulate(@PathVariable String id) {
    return refreshJob(id);
  }

  @PostMapping("/jobs/{id}/refresh")
  public ApiResponse<Map<String, Object>> refresh(@PathVariable String id) {
    return refreshJob(id);
  }

  private ApiResponse<Map<String, Object>> refreshJob(String id) {
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

  private void normalizeJobPayload(Map<String, Object> body) {
    String inputKind = text(body.getOrDefault("inputKind", "video"));
    if (!INPUT_KINDS.contains(inputKind)) {
      throw ApiException.badRequest("建图输入类型仅支持 video 或 image_sequence");
    }
    String outputProfile = text(body.getOrDefault("outputProfile", "preview"));
    if (!OUTPUT_PROFILES.contains(outputProfile)) {
      throw ApiException.badRequest("建图输出规格不支持");
    }
    body.put("inputKind", inputKind);
    body.put("outputProfile", outputProfile);
    validateInputSource(inputKind, body);
    body.put("mode", text(body.getOrDefault("mode", "windowed")));
    normalizePositiveInt(body, "fps", 10, 1, 120);
    normalizePositiveInt(body, "stride", 1, 1, 1000);
    normalizePositiveInt(body, "keyframeInterval", 5, 1, 1000);
    normalizePositiveInt(body, "windowSize", 16, 1, 1000);
    body.put("maskSky", bool(body.get("maskSky")));
  }

  private void validateInputSource(String inputKind, Map<String, Object> body) {
    String videoUrl = text(body.get("videoUrl"));
    String imageFolderUrl = text(body.get("imageFolderUrl"));
    if ("video".equals(inputKind) && (videoUrl == null || videoUrl.isBlank())) {
      throw ApiException.badRequest("视频建图任务必须提供 videoUrl");
    }
    if ("image_sequence".equals(inputKind) && (imageFolderUrl == null || imageFolderUrl.isBlank())) {
      throw ApiException.badRequest("图片序列建图任务必须提供 imageFolderUrl");
    }
  }

  private void normalizePositiveInt(Map<String, Object> body, String key, int fallback, int min, int max) {
    int value = number(body.get(key), fallback);
    if (value < min || value > max) {
      throw ApiException.badRequest(key + " 参数范围应为 " + min + "-" + max);
    }
    body.put(key, value);
  }

  private void validateVideo(MultipartFile video) {
    if (video == null || video.isEmpty()) {
      throw ApiException.badRequest("请上传建图视频");
    }
    String contentType = video.getContentType() == null ? "" : video.getContentType().toLowerCase();
    if (!ALLOWED_VIDEO_TYPES.contains(contentType)) {
      throw ApiException.badRequest("仅支持 MP4、MOV、AVI、MKV、WEBM 视频");
    }
  }

  private String publicVideoUrl(HttpServletRequest request, String filename) {
    return ServletUriComponentsBuilder.fromRequestUri(request)
      .replacePath("/model-files/lingbot/uploads/" + filename)
      .replaceQuery(null)
      .build()
      .toUriString();
  }

  private String extension(MultipartFile video) {
    String contentType = video.getContentType() == null ? "" : video.getContentType().toLowerCase();
    return switch (contentType) {
      case "video/quicktime" -> ".mov";
      case "video/x-msvideo" -> ".avi";
      case "video/x-matroska" -> ".mkv";
      case "video/webm" -> ".webm";
      default -> ".mp4";
    };
  }

  private int number(Object value, int fallback) {
    if (value == null) {
      return fallback;
    }
    if (value instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(value.toString());
    } catch (NumberFormatException ex) {
      throw ApiException.badRequest("数字参数格式错误");
    }
  }

  private boolean bool(Object value) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    return value != null && Boolean.parseBoolean(value.toString());
  }

  private String text(Object value) {
    return value == null ? null : value.toString();
  }

  public record UploadResponse(String videoUrl, String filename, long size) {
  }
}
