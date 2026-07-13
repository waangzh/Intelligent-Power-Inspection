package com.powerinspection.route;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RouteDeploymentService {
  private final RouteDeploymentRepository repository;
  private final RouteRevisionService routeRevisionService;
  private final DataStoreService dataStore;
  private final ObjectMapper objectMapper;

  public RouteDeploymentService(
      RouteDeploymentRepository repository,
      RouteRevisionService routeRevisionService,
      DataStoreService dataStore,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.routeRevisionService = routeRevisionService;
    this.dataStore = dataStore;
    this.objectMapper = objectMapper;
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
    if (routeSiteId != null && robotSiteId != null && !routeSiteId.equals(robotSiteId)) {
      throw ApiException.badRequest("机器人与路线修订不属于同一站点");
    }

    String now = Instant.now().toString();
    RouteDeploymentEntity deployment = new RouteDeploymentEntity();
    deployment.setId(Ids.next("deploy"));
    deployment.setRouteRevisionId(revisionId);
    deployment.setRobotId(robotId);
    deployment.setRequestId(requestId);
    deployment.setState("PENDING");
    deployment.setAttemptNo(1);
    deployment.setCreatedAt(now);
    deployment.setUpdatedAt(now);
    return toDto(repository.save(deployment));
  }

  public Map<String, Object> get(String deploymentId) {
    RouteDeploymentEntity deployment = repository.findById(deploymentId)
      .orElseThrow(() -> ApiException.notFound("路线部署记录不存在"));
    return toDto(deployment);
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
      dto.put("remoteSummary", deployment.getRemoteSummaryJson() == null ? null : objectMapper.readValue(deployment.getRemoteSummaryJson(), new TypeReference<Map<String, Object>>() {}));
      dto.put("errorCode", deployment.getErrorCode());
      dto.put("errorMessage", deployment.getErrorMessage());
      dto.put("createdAt", deployment.getCreatedAt());
      dto.put("updatedAt", deployment.getUpdatedAt());
      return dto;
    } catch (Exception ex) {
      throw new IllegalStateException("路线部署数据损坏", ex);
    }
  }

  private String text(Object value) {
    if (value == null || value.toString().isBlank() || "null".equals(value.toString())) return null;
    return value.toString();
  }
}
