package com.powerinspection.sceneasset;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.robot.RobotBridgeIdMapper;
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

@RestController
@RequestMapping("/api/v1/internal/robot-scene-assets")
public class RobotSceneAssetController {
  private final SceneAssetService sceneAssetService;
  private final RobotBridgeIdMapper robotBridgeIdMapper;
  private final DataStoreService dataStore;

  public RobotSceneAssetController(SceneAssetService sceneAssetService,
      RobotBridgeIdMapper robotBridgeIdMapper, DataStoreService dataStore) {
    this.sceneAssetService = sceneAssetService;
    this.robotBridgeIdMapper = robotBridgeIdMapper;
    this.dataStore = dataStore;
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<ApiResponse<Map<String, Object>>> upload(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestHeader(value = "X-Bridge-Robot-Id", required = false) String bridgeRobotId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestPart("model") MultipartFile model,
      @RequestPart("metadata") MultipartFile metadata,
      @RequestParam String modelSha256,
      @RequestParam String assetKind,
      @RequestParam String format,
      @RequestParam String sourceSessionId,
      @RequestParam(required = false) String capturedAt,
      @RequestParam String reconstructedAt,
      @RequestParam String coordinateSystem,
      @RequestParam String unit,
      @RequestParam(required = false) Long pointCount) throws IOException {
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
    RobotSceneUploadResult result = sceneAssetService.createForRobot(siteId, robotId, normalizedBridgeId,
      idempotencyKey, model, metadata, modelSha256, assetKind, format, sourceSessionId,
      capturedAt, reconstructedAt, coordinateSystem, unit, pointCount);
    ResponseEntity.BodyBuilder response = result.created()
      ? ResponseEntity.created(URI.create("/api/v1/scene-assets/" + result.asset().get("id")))
      : ResponseEntity.ok();
    return response.body(ApiResponse.ok(result.asset()));
  }

  private String requiredHeader(String value, String message) {
    String normalized = text(value);
    if (normalized == null) throw ApiException.badRequest(message);
    if (normalized.length() > 160) throw ApiException.badRequest("请求头长度超过限制");
    return normalized;
  }

  private String text(Object value) {
    return value == null || value.toString().isBlank() ? null : value.toString().trim();
  }
}
