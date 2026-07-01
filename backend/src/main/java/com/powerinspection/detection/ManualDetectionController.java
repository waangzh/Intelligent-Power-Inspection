package com.powerinspection.detection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.Ids;
import com.powerinspection.config.ModelFileWebConfig;
import com.powerinspection.model.LocateAnythingFinding;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/detections")
public class ManualDetectionController {
  private static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp", "image/bmp");
  private static final TypeReference<List<Map<String, Object>>> DETECTION_LIST_TYPE = new TypeReference<>() {
  };
  private static final Path UPLOAD_DIR = ModelFileWebConfig.MODEL_FILE_ROOT.resolve("locate-anything").resolve("uploads");

  private final ManualDetectionService manualDetectionService;
  private final ObjectMapper objectMapper;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public ManualDetectionController(ManualDetectionService manualDetectionService, ObjectMapper objectMapper, PermissionService permissionService, CurrentUser currentUser) {
    this.manualDetectionService = manualDetectionService;
    this.objectMapper = objectMapper;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @PostMapping(value = "/manual", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<ManualDetectionResponse> detect(
      @RequestPart("image") MultipartFile image,
      @RequestPart("detections") String detections,
      HttpServletRequest request) throws IOException {
    permissionService.require(currentUser.get(), Permission.DETECTION_MANAGE);
    validateImage(image);
    List<Map<String, Object>> enabledDetections = enabledDetections(detections);
    if (enabledDetections.isEmpty()) {
      throw ApiException.badRequest("请至少启用一个检测项");
    }

    String requestId = Ids.next("manual_det");
    String filename = requestId + extension(image);
    Files.createDirectories(UPLOAD_DIR);
    Path output = UPLOAD_DIR.resolve(filename).normalize();
    if (!output.startsWith(UPLOAD_DIR)) {
      throw ApiException.badRequest("上传文件名非法");
    }
    try (InputStream input = image.getInputStream()) {
      Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
    }

    String inputImageUrl = publicImageUrl(request, filename);
    return ApiResponse.ok(manualDetectionService.submit(requestId, inputImageUrl, enabledDetections));
  }

  @GetMapping("/manual/{requestId}")
  public ApiResponse<ManualDetectionResponse> get(@PathVariable String requestId) {
    permissionService.require(currentUser.get(), Permission.DETECTION_MANAGE);
    return ApiResponse.ok(manualDetectionService.get(requestId));
  }

  private void validateImage(MultipartFile image) {
    if (image == null || image.isEmpty()) {
      throw ApiException.badRequest("请上传检测图片");
    }
    String contentType = image.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
      throw ApiException.badRequest("仅支持 JPG、PNG、WEBP、BMP 图片");
    }
  }

  private List<Map<String, Object>> enabledDetections(String detections) {
    try {
      return objectMapper.readValue(detections, DETECTION_LIST_TYPE).stream()
        .filter(item -> Boolean.TRUE.equals(item.get("enabled")))
        .map(this::normalizeDetection)
        .toList();
    } catch (JsonProcessingException ex) {
      throw ApiException.badRequest("检测项 JSON 格式错误");
    }
  }

  private Map<String, Object> normalizeDetection(Map<String, Object> raw) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("type", text(raw.get("type")));
    item.put("prompt", text(raw.get("prompt")));
    item.put("threshold", raw.getOrDefault("threshold", 0.75));
    item.put("enabled", true);
    return item;
  }

  private String publicImageUrl(HttpServletRequest request, String filename) {
    return ServletUriComponentsBuilder.fromRequestUri(request)
      .replacePath("/model-files/locate-anything/uploads/" + filename)
      .replaceQuery(null)
      .build()
      .toUriString();
  }

  private String extension(MultipartFile image) {
    String contentType = image.getContentType() == null ? "" : image.getContentType().toLowerCase(Locale.ROOT);
    return switch (contentType) {
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      case "image/bmp" -> ".bmp";
      default -> ".jpg";
    };
  }

  private String text(Object value) {
    return value == null ? null : value.toString();
  }

  public record ManualDetectionResponse(
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
  }
}
