package com.powerinspection.detection;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.Ids;
import com.powerinspection.config.ModelFileWebConfig;
import com.powerinspection.model.LocateAnythingFinding;
import com.powerinspection.model.ModelProperties;
import com.powerinspection.model.DetectionItems;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import jakarta.servlet.http.HttpServletRequest;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;
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
  private final ModelProperties modelProperties;

  public ManualDetectionController(ManualDetectionService manualDetectionService, ObjectMapper objectMapper, PermissionService permissionService, CurrentUser currentUser, ModelProperties modelProperties) {
    this.manualDetectionService = manualDetectionService;
    this.objectMapper = objectMapper;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
    this.modelProperties = modelProperties;
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

    ImageDimensions dimensions = imageDimensions(output);
    String publicInputImageUrl = publicFileUrl(request, "uploads/" + filename);
    String modelInputImageUrl = modelInputImageUrl(publicInputImageUrl, filename);
    String publicResultBaseUrl = publicFileUrl(request, "results/");
    return ApiResponse.ok(manualDetectionService.submit(
      requestId,
      publicInputImageUrl,
      modelInputImageUrl,
      publicResultBaseUrl,
      dimensions.width(),
      dimensions.height(),
      enabledDetections
    ));
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
    String type = text(raw.get("type"));
    String prompt = text(raw.get("prompt"));
    if (!StringUtils.hasText(type) || !StringUtils.hasText(prompt)) {
      throw ApiException.badRequest("已启用检测项必须填写类型和提示词");
    }
    type = type.trim();
    prompt = prompt.trim();
    String itemId = text(raw.get("itemId"));
    String name = text(raw.get("name"));
    item.put("itemId", StringUtils.hasText(itemId) ? itemId.trim() : type);
    item.put("type", type);
    item.put("name", StringUtils.hasText(name) ? name.trim() : type);
    String displayLabel = text(raw.get("displayLabel"));
    item.put("displayLabel", displayLabel == null || displayLabel.isBlank()
      ? DetectionItems.displayLabel(type)
      : displayLabel.trim());
    item.put("prompt", prompt);
    item.put("threshold", raw.getOrDefault("threshold", 0.75));
    item.put("enabled", true);
    DetectionRiskRules.normalize(raw, item);
    return item;
  }

  private String publicFileUrl(HttpServletRequest request, String relativePath) {
    return ServletUriComponentsBuilder.fromRequestUri(request)
      .replacePath("/model-files/locate-anything/" + relativePath)
      .replaceQuery(null)
      .build()
      .toUriString();
  }

  private String modelInputImageUrl(String publicInputImageUrl, String filename) {
    String baseUrl = modelProperties.getLocateAnything().getInputFileBaseUrl();
    if (!StringUtils.hasText(baseUrl)) {
      return publicInputImageUrl;
    }
    String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    return normalizedBaseUrl + "/locate-anything/uploads/" + filename;
  }

  private ImageDimensions imageDimensions(Path image) {
    try (ImageInputStream input = ImageIO.createImageInputStream(image.toFile())) {
      if (input == null) {
        return ImageDimensions.unknown();
      }
      Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) {
        return ImageDimensions.unknown();
      }
      ImageReader reader = readers.next();
      try {
        reader.setInput(input, true, true);
        return new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
      } finally {
        reader.dispose();
      }
    } catch (IOException ex) {
      return ImageDimensions.unknown();
    }
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

  private record ImageDimensions(Integer width, Integer height) {
    private static ImageDimensions unknown() {
      return new ImageDimensions(null, null);
    }
  }

  public record ManualDetectionResponse(
    String requestId,
    String status,
    String inputImageUrl,
    String resultImageUrl,
    List<Map<String, Object>> detections,
    List<LocateAnythingFinding> findings,
    List<String> warnings,
    String errorMessage,
    String createdAt,
    String startedAt,
    String completedAt,
    long alarmCount
  ) {
  }
}
