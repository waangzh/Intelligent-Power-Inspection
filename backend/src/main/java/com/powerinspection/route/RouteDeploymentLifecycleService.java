package com.powerinspection.route;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.robot.RobotBridgeDeploymentResult;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
class RouteDeploymentLifecycleService {
  private final RouteDeploymentRepository repository;
  private final ObjectMapper objectMapper;
  private final RouteDeploymentProperties properties;

  RouteDeploymentLifecycleService(RouteDeploymentRepository repository, ObjectMapper objectMapper, RouteDeploymentProperties properties) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  List<String> eligibleIds(Instant now) {
    return repository.findEligibleIds(RouteDeploymentState.PENDING.name(), RouteDeploymentState.UNKNOWN.name(), now.toString(),
      PageRequest.of(0, Math.max(1, properties.getWorkerBatchSize())));
  }

  @Transactional
  RouteDeploymentEntity claim(String deploymentId, Instant now) {
    int changed = repository.claimForInstall(deploymentId, RouteDeploymentState.PENDING.name(), RouteDeploymentState.UNKNOWN.name(),
      RouteDeploymentState.INSTALLING.name(), now.toString());
    return changed == 1 ? repository.findById(deploymentId).orElse(null) : null;
  }

  @Transactional
  void ready(String deploymentId, RobotBridgeDeploymentResult result, Instant now) {
    RouteDeploymentEntity deployment = installing(deploymentId);
    if (deployment == null) return;
    deployment.setState(RouteDeploymentState.READY_FOR_ROBOT.name());
    deployment.setRemoteSummaryJson(write(result.auditSummary()));
    deployment.setErrorCode(null);
    deployment.setErrorMessage(null);
    deployment.setNextReconcileAt(null);
    deployment.setUpdatedAt(now.toString());
    repository.save(deployment);
  }

  @Transactional
  void failed(String deploymentId, String code, String message, Instant now) {
    RouteDeploymentEntity deployment = installing(deploymentId);
    if (deployment == null) return;
    deployment.setState(RouteDeploymentState.FAILED.name());
    deployment.setErrorCode(safeCode(code));
    deployment.setErrorMessage(safeMessage(message));
    deployment.setNextReconcileAt(null);
    deployment.setUpdatedAt(now.toString());
    repository.save(deployment);
  }

  @Transactional
  void unknown(String deploymentId, String code, String message, Instant now) {
    RouteDeploymentEntity deployment = installing(deploymentId);
    if (deployment == null) return;
    deployment.setState(RouteDeploymentState.UNKNOWN.name());
    deployment.setErrorCode(safeCode(code));
    boolean exhausted = deployment.getAttemptNo() >= Math.max(1, properties.getMaxAttempts());
    deployment.setErrorMessage(safeMessage(exhausted ? message + "；自动对账次数已达上限，等待人工处理" : message));
    deployment.setNextReconcileAt(exhausted ? null : now.plusSeconds(backoffSeconds(deployment.getAttemptNo())).toString());
    deployment.setUpdatedAt(now.toString());
    repository.save(deployment);
  }

  @Transactional
  int recoverInterruptedInstalls(Instant now) {
    int count = 0;
    for (RouteDeploymentEntity deployment : repository.findByState(RouteDeploymentState.INSTALLING.name())) {
      deployment.setState(RouteDeploymentState.UNKNOWN.name());
      deployment.setErrorCode("RECOVERY_REQUIRED");
      deployment.setErrorMessage("服务重启导致同步结果不明，正在进行保守对账");
      deployment.setNextReconcileAt(now.toString());
      deployment.setUpdatedAt(now.toString());
      repository.save(deployment);
      count++;
    }
    return count;
  }

  private RouteDeploymentEntity installing(String deploymentId) {
    RouteDeploymentEntity deployment = repository.findById(deploymentId).orElse(null);
    return deployment != null && RouteDeploymentState.INSTALLING.name().equals(deployment.getState()) ? deployment : null;
  }

  private long backoffSeconds(int attemptCount) {
    long initial = Math.max(1, properties.getInitialBackoffSeconds());
    int exponent = Math.min(20, Math.max(0, attemptCount - 1));
    long delay = initial * (1L << exponent);
    return Math.min(Math.max(initial, properties.getMaxBackoffSeconds()), delay);
  }

  private String write(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new IllegalStateException("部署审计摘要序列化失败", ex);
    }
  }

  private String safeCode(String value) {
    String normalized = value == null ? "BRIDGE_ERROR" : value.replaceAll("[^A-Za-z0-9_]", "_").toUpperCase();
    return normalized.substring(0, Math.min(64, normalized.length()));
  }

  private String safeMessage(String value) {
    String normalized = value == null ? "Bridge 同步结果未知" : value.replaceAll("[\\r\\n]+", " ").trim();
    normalized = normalized.replaceAll("(?i)(bearer\\s+)[^\\s,;]+", "$1[REDACTED]");
    normalized = normalized.replaceAll("(?i)(authorization|token|password)=[^\\s,;]+", "$1=[REDACTED]");
    return normalized.substring(0, Math.min(500, normalized.length()));
  }
}
