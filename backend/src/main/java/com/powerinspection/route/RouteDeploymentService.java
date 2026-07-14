package com.powerinspection.route;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.robot.RobotConnectionStatus;
import com.powerinspection.robot.RobotHeartbeatService;
import com.powerinspection.robot.RobotHeartbeatStatusView;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RouteDeploymentService {
  private final RouteDeploymentRepository repository;
  private final RouteRevisionService routeRevisionService;
  private final DataStoreService dataStore;
  private final ObjectMapper objectMapper;
  private final RobotHeartbeatService heartbeatService;

  public RouteDeploymentService(
      RouteDeploymentRepository repository,
      RouteRevisionService routeRevisionService,
      DataStoreService dataStore,
      ObjectMapper objectMapper,
      RobotHeartbeatService heartbeatService) {
    this.repository = repository;
    this.routeRevisionService = routeRevisionService;
    this.dataStore = dataStore;
    this.objectMapper = objectMapper;
    this.heartbeatService = heartbeatService;
  }

  @Transactional
  public Map<String, Object> request(String revisionId, String robotId, String requestId) {
    if (requestId == null || requestId.isBlank() || requestId.length() > 160) {
      throw ApiException.badRequest("请提供长度不超过 160 的 Idempotency-Key");
    }
    RouteDeploymentEntity existing = repository.findByRequestId(requestId).orElse(null);
    if (existing != null) {
      if (!revisionId.equals(existing.getRouteRevisionId()) || !robotId.equals(existing.getRobotId())) {
        throw ApiException.conflict("Idempotency-Key 已用于不同的路线部署请求");
      }
      return toDto(existing);
    }

    RouteRevisionEntity revision = routeRevisionService.require(revisionId);
    Map<String, Object> robot = dataStore.find(DataCategory.ROBOT, robotId);
    if (robot == null) throw ApiException.badRequest("机器人不存在");
    Map<String, Object> route = dataStore.get(DataCategory.ROUTE, revision.getRouteId());
    String routeSiteId = text(route.get("siteId"));
    String robotSiteId = text(robot.get("siteId"));
    if (routeSiteId == null || robotSiteId == null || !routeSiteId.equals(robotSiteId)) {
      throw ApiException.badRequest("机器人与路线修订不属于同一站点");
    }
    requireDeployableRobot(robotId);
    RouteDeploymentEntity active = repository.findFirstByRobotIdAndStateInOrderByCreatedAtDesc(robotId,
      List.of(RouteDeploymentState.PENDING.name(), RouteDeploymentState.INSTALLING.name(), RouteDeploymentState.UNKNOWN.name())).orElse(null);
    if (active != null) {
      throw ApiException.conflict("机器人已有正在同步或等待对账的部署，请等待现有部署完成");
    }

    String now = Instant.now().toString();
    RouteDeploymentEntity deployment = new RouteDeploymentEntity();
    deployment.setId(Ids.next("deploy"));
    deployment.setRouteRevisionId(revisionId);
    deployment.setRobotId(robotId);
    deployment.setRequestId(requestId);
    deployment.setState(RouteDeploymentState.PENDING.name());
    deployment.setAttemptNo(0);
    deployment.setCreatedAt(now);
    deployment.setUpdatedAt(now);
    return toDto(repository.save(deployment));
  }

  public Map<String, Object> get(String deploymentId) {
    RouteDeploymentEntity deployment = repository.findById(deploymentId)
      .orElseThrow(() -> ApiException.notFound("路线部署记录不存在"));
    return toDto(deployment);
  }

  public List<Map<String, Object>> listByRevision(String revisionId) {
    routeRevisionService.require(revisionId);
    return repository.findByRouteRevisionIdOrderByCreatedAtDesc(revisionId).stream().map(this::toDto).toList();
  }

  private Map<String, Object> toDto(RouteDeploymentEntity deployment) {
    try {
      Map<String, Object> dto = new LinkedHashMap<>();
      dto.put("id", deployment.getId());
      dto.put("routeRevisionId", deployment.getRouteRevisionId());
      dto.put("robotId", deployment.getRobotId());
      dto.put("requestId", deployment.getRequestId());
      dto.put("state", deployment.getState());
      dto.put("attemptNo", deployment.getAttemptNo());
      dto.put("attemptCount", deployment.getAttemptNo());
      dto.put("lastAttemptAt", deployment.getLastAttemptAt());
      dto.put("nextReconcileAt", deployment.getNextReconcileAt());
      dto.put("remoteSummary", deployment.getRemoteSummaryJson() == null ? null : objectMapper.readValue(deployment.getRemoteSummaryJson(), new TypeReference<Map<String, Object>>() {}));
      dto.put("errorCode", deployment.getErrorCode());
      dto.put("errorMessage", deployment.getErrorMessage());
      dto.put("createdAt", deployment.getCreatedAt());
      dto.put("updatedAt", deployment.getUpdatedAt());
      dto.put("stateVersion", deployment.getVersion());
      RouteRevisionEntity revision = routeRevisionService.require(deployment.getRouteRevisionId());
      dto.put("routeContentSha256", revision.getContentSha256());
      dto.put("mapAssetId", revision.getMapAssetId());
      dto.put("mapImageSha256", revision.getMapImageSha256());
      return dto;
    } catch (Exception ex) {
      throw new IllegalStateException("路线部署数据损坏", ex);
    }
  }

  private String text(Object value) {
    if (value == null || value.toString().isBlank() || "null".equals(value.toString())) return null;
    return value.toString();
  }

  private void requireDeployableRobot(String robotId) {
    RobotHeartbeatStatusView status = heartbeatService.detail(robotId);
    if (!status.source().bridgeConfigured()) {
      throw ApiException.conflict("机器人尚未在 Bridge 中配置，无法部署路线");
    }
    if (RobotConnectionStatus.BRIDGE_UNREACHABLE.name().equals(status.connectionStatus())) {
      throw ApiException.conflict("Bridge 当前不可达，无法确认机器人部署条件");
    }
    if (!status.online()) {
      throw ApiException.conflict("机器人当前离线，等待心跳恢复后再部署");
    }
    if (!RobotConnectionStatus.CONNECTED.name().equals(status.connectionStatus())) {
      throw ApiException.conflict("机器人 Bridge 状态异常，无法部署路线");
    }
  }
}
