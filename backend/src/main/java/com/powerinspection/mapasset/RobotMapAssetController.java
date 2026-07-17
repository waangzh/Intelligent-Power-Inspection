package com.powerinspection.mapasset;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.robot.RobotBridgeIdMapper;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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

@RestController
@RequestMapping("/api/v1/internal/robot-map-assets")
public class RobotMapAssetController {
  private final MapAssetService mapAssetService;
  private final RobotBridgeIdMapper robotBridgeIdMapper;
  private final DataStoreService dataStore;

  public RobotMapAssetController(MapAssetService mapAssetService, RobotBridgeIdMapper robotBridgeIdMapper,
      DataStoreService dataStore) {
    this.mapAssetService = mapAssetService;
    this.robotBridgeIdMapper = robotBridgeIdMapper;
    this.dataStore = dataStore;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Bridge-Robot-Id", required = false) String bridgeRobotId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestPart("yaml") MultipartFile yaml,
      @RequestPart("pgm") MultipartFile pgm,
      @RequestParam(value = "capturedAt", required = false) String capturedAt,
      @RequestParam(value = "contentIdentitySha256", required = false) String contentIdentitySha256) throws IOException {
    if (!robotBridgeIdMapper.isBridgePlatformRequest(authorization)) {
      throw ApiException.unauthorized("Bridge 服务凭据无效");
    }
    String normalizedBridgeId = requiredHeader(bridgeRobotId, "缺少 X-Bridge-Robot-Id");
    String robotId = robotBridgeIdMapper.findPlatformId(normalizedBridgeId)
      .orElseThrow(() -> ApiException.notFound("Bridge 机器人 ID 未配置映射"));
    Map<String, Object> robot = dataStore.find(DataCategory.ROBOT, robotId);
    if (robot == null) throw ApiException.notFound("机器人不存在");
    String siteId = text(robot.get("siteId"));
    if (siteId == null || dataStore.find(DataCategory.SITE, siteId) == null) {
      throw ApiException.forbidden("机器人未绑定有效站点");
    }

    RobotMapUploadResult result = mapAssetService.createForRobot(
      siteId, robotId, normalizedBridgeId, idempotencyKey, contentIdentitySha256,
      parseCapturedAt(capturedAt), yaml, pgm);
    ResponseEntity.BodyBuilder response = result.created()
      ? ResponseEntity.created(URI.create("/api/v1/map-assets/" + result.asset().get("id")))
      : ResponseEntity.ok();
    return response.body(ApiResponse.ok(result.asset()));
  }

  private String requiredHeader(String value, String message) {
    String normalized = text(value);
    if (normalized == null) throw ApiException.badRequest(message);
    if (normalized.length() > 160) throw ApiException.badRequest("请求头长度超过限制");
    return normalized;
  }

  private Instant parseCapturedAt(String value) {
    String normalized = text(value);
    if (normalized == null) return null;
    try {
      return Instant.parse(normalized);
    } catch (DateTimeParseException ex) {
      throw ApiException.badRequest("capturedAt 必须是带时区的 ISO-8601 时间");
    }
  }

  private String text(Object value) {
    if (value == null || value.toString().isBlank() || "null".equals(value.toString())) return null;
    return value.toString().trim();
  }
}
