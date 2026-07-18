package com.powerinspection.detection;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.robot.RobotBridgeIdMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/internal/robot-inspection-images")
public class RobotInspectionImageIngestController {
  private final RobotInspectionImageService service;
  private final RobotBridgeIdMapper robotBridgeIdMapper;

  public RobotInspectionImageIngestController(
      RobotInspectionImageService service, RobotBridgeIdMapper robotBridgeIdMapper) {
    this.service = service;
    this.robotBridgeIdMapper = robotBridgeIdMapper;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Bridge-Robot-Id", required = false) String bridgeRobotId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestPart("image") MultipartFile image,
      @RequestParam String executionId,
      @RequestParam String taskId,
      @RequestParam String checkpointId,
      @RequestParam(required = false) String capturedAt,
      @RequestParam(required = false) String imageSha256,
      HttpServletRequest request) throws IOException {
    if (!robotBridgeIdMapper.isBridgePlatformRequest(authorization)) {
      throw ApiException.unauthorized("Bridge 服务凭据无效");
    }
    String robotId = robotBridgeIdMapper.findPlatformId(required(bridgeRobotId, "缺少 X-Bridge-Robot-Id"))
      .orElseThrow(() -> ApiException.notFound("Bridge 机器人 ID 未配置映射"));
    RobotInspectionImageService.UploadResult result = service.importFromBridge(
      robotId, executionId, taskId, checkpointId, capturedAt, imageSha256, idempotencyKey, image);
    String publicBase = ServletUriComponentsBuilder.fromRequestUri(request)
      .replacePath("/model-files/").replaceQuery(null).build().toUriString();
    ApiResponse<Map<String, Object>> body = ApiResponse.ok(service.view(result.image(), publicBase));
    return result.created()
      ? ResponseEntity.created(URI.create("/api/v1/robot-inspection-images/" + result.image().getId())).body(body)
      : ResponseEntity.ok(body);
  }

  private String required(String value, String message) {
    if (value == null || value.isBlank()) throw ApiException.badRequest(message);
    String normalized = value.trim();
    if (normalized.length() > 160) throw ApiException.badRequest("请求头长度超过限制");
    return normalized;
  }
}
