package com.powerinspection.robot;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.user.UserEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RobotLocalConfirmPolicyService {
  public static final Set<String> SUPPORTED_PROTOCOL_VERSIONS = Set.of("1");

  private final DataStoreService dataStore;
  private final RobotHeartbeatStatusRepository heartbeatRepository;
  private final RobotLocalConfirmPolicyAuditRepository auditRepository;

  public RobotLocalConfirmPolicyService(
      DataStoreService dataStore,
      RobotHeartbeatStatusRepository heartbeatRepository,
      RobotLocalConfirmPolicyAuditRepository auditRepository) {
    this.dataStore = dataStore;
    this.heartbeatRepository = heartbeatRepository;
    this.auditRepository = auditRepository;
  }

  public Map<String, Object> decorate(Map<String, Object> source) {
    Map<String, Object> robot = new LinkedHashMap<>(source);
    String robotId = String.valueOf(robot.get("id"));
    RobotHeartbeatStatusEntity heartbeat = heartbeatRepository.findById(robotId).orElse(null);
    boolean reportedRemote = heartbeat == null || heartbeat.isReportedRemoteImmediateStart();
    boolean reportedLocal = heartbeat != null && heartbeat.isReportedLocalConfirmStart();
    String protocolVersion = heartbeat == null ? null : heartbeat.getLocalConfirmProtocolVersion();
    boolean protocolCompatible = isProtocolSupported(protocolVersion);
    boolean enabled = Boolean.TRUE.equals(robot.get("localConfirmStartEnabled"));
    boolean effective = reportedLocal && enabled && protocolCompatible;
    robot.put("reportedSupportsRemoteImmediateStart", reportedRemote);
    robot.put("reportedSupportsLocalConfirmStart", reportedLocal);
    robot.put("localConfirmProtocolVersion", protocolVersion);
    robot.put("localConfirmProtocolCompatible", protocolCompatible);
    robot.put("localConfirmStartReady", heartbeat != null && heartbeat.isLocalConfirmStartReady());
    robot.put("localConfirmStartError", heartbeat == null ? null : heartbeat.getLocalConfirmStartError());
    robot.put("capabilityReportedAt", heartbeat == null ? null : heartbeat.getCapabilityReportedAt());
    robot.put("localConfirmStartEnabled", enabled);
    robot.put("supportsRemoteImmediateStart", reportedRemote);
    robot.put("supportsLocalConfirmStart", effective);
    return robot;
  }

  @Transactional
  public Map<String, Object> update(String robotId, boolean enabled, UserEntity operator) {
    Map<String, Object> current = dataStore.get(DataCategory.ROBOT, robotId);
    boolean previous = Boolean.TRUE.equals(current.get("localConfirmStartEnabled"));
    if (enabled) validateEnable(robotId);
    if (previous == enabled) return decorate(current);

    Map<String, Object> updated = dataStore.patch(
        DataCategory.ROBOT, robotId, Map.of("localConfirmStartEnabled", enabled));
    RobotLocalConfirmPolicyAuditEntity audit = new RobotLocalConfirmPolicyAuditEntity();
    audit.setId(Ids.next("rlcp"));
    audit.setRobotId(robotId);
    audit.setOperatorId(operator.getId());
    audit.setOperatorName(displayName(operator));
    audit.setPreviousEnabled(previous);
    audit.setNewEnabled(enabled);
    audit.setChangedAt(Instant.now());
    auditRepository.save(audit);
    return decorate(updated);
  }

  public static boolean isProtocolSupported(String version) {
    return version != null && SUPPORTED_PROTOCOL_VERSIONS.contains(version);
  }

  private void validateEnable(String robotId) {
    RobotHeartbeatStatusEntity heartbeat = heartbeatRepository.findById(robotId).orElse(null);
    if (heartbeat == null || heartbeat.getLastHeartbeatAt() == null) {
      throw ApiException.conflict("机器人尚未完成首次有效 heartbeat，不能启用本地确认启动");
    }
    if (!heartbeat.isReportedLocalConfirmStart()) {
      throw ApiException.conflict("设备尚未声明支持本地确认启动，不能启用管理员审批");
    }
    if (!isProtocolSupported(heartbeat.getLocalConfirmProtocolVersion())) {
      throw ApiException.conflict("本地确认协议版本不兼容，平台当前支持版本：1");
    }
  }

  private static String displayName(UserEntity operator) {
    String name = operator.getDisplayName();
    return name == null || name.isBlank() ? operator.getUsername() : name;
  }
}
