package com.powerinspection.robot;

import com.powerinspection.common.ApiException;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.notification.NotificationService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RobotHeartbeatService {
  private static final String SOURCE_NAME = "robot-bridge";
  private static final Duration MAX_FUTURE_SKEW = Duration.ofSeconds(30);

  private final RobotHeartbeatStatusRepository repository;
  private final DataStoreService dataStore;
  private final RobotProperties properties;
  private final Duration timeout;
  private final NotificationService notificationService;

  public RobotHeartbeatService(
      RobotHeartbeatStatusRepository repository,
      DataStoreService dataStore,
      RobotProperties properties) {
    this(repository, dataStore, properties, null);
  }

  @Autowired
  public RobotHeartbeatService(
      RobotHeartbeatStatusRepository repository,
      DataStoreService dataStore,
      RobotProperties properties,
      NotificationService notificationService) {
    this.repository = repository;
    this.dataStore = dataStore;
    this.properties = properties;
    this.timeout = Duration.ofSeconds(Math.max(1, properties.getHeartbeatTimeoutSeconds()));
    this.notificationService = notificationService;
  }

  @Transactional
  public void applyBridgeSnapshot(BridgeRobotSnapshot snapshot, Instant observedAt) {
    requireIdentity(snapshot.robotId());
    if (snapshot.lastHeartbeatAt() == null) {
      markNoHeartbeat(snapshot.robotId(), observedAt);
      return;
    }
    if (snapshot.lastHeartbeatAt().isAfter(observedAt.plus(MAX_FUTURE_SKEW))) {
      markInvalidSnapshot(snapshot.robotId(), observedAt);
      return;
    }
    RobotHeartbeatStatusEntity status = status(snapshot.robotId(), observedAt);
    String previous = status.getConnectionStatus();
    Instant current = status.getLastHeartbeatAt();
    if (current != null && snapshot.lastHeartbeatAt().isBefore(current)) return;
    if (current != null && snapshot.lastHeartbeatAt().equals(current)) {
      status.setConnectionStatus(RobotConnectionStatus.CONNECTED.name());
      status.setOfflineReason(null);
      status.setBridgeConfigured(true);
      status.setStatusUpdatedAt(observedAt);
      repository.save(status);
      syncInventoryPresence(snapshot.robotId(), isOnline(status, observedAt));
      notifyRobotTransition(snapshot.robotId(), previous, status.getConnectionStatus(), "HEARTBEAT_CONNECTED");
      return;
    }
    status.setLastHeartbeatAt(snapshot.lastHeartbeatAt());
    status.setLastOnlineAt(snapshot.lastHeartbeatAt());
    status.setConnectionStatus(RobotConnectionStatus.CONNECTED.name());
    status.setOfflineReason(null);
    status.setSourceName(SOURCE_NAME);
    status.setBridgeConfigured(true);
    status.setProtocolVersion(blankToNull(snapshot.protocolVersion()));
    status.setBootId(blankToNull(snapshot.bootId()));
    status.setSoftwareVersion(blankToNull(snapshot.softwareVersion()));
    status.setRobotState(blankToNull(snapshot.state()));
    status.setAcceptedEventSequence(snapshot.acceptedEventSequence());
    status.setReportedRemoteImmediateStart(snapshot.reportedSupportsRemoteImmediateStart());
    status.setReportedLocalConfirmStart(snapshot.reportedSupportsLocalConfirmStart());
    status.setLocalConfirmProtocolVersion(blankToNull(snapshot.localConfirmProtocolVersion()));
    status.setLocalConfirmStartReady(snapshot.localConfirmStartReady());
    status.setLocalConfirmStartError(blankToNull(snapshot.localConfirmStartError()));
    status.setCapabilityReportedAt(snapshot.capabilityReportedAt());
    status.setDiagnosticSummary(diagnosticSummary(snapshot));
    status.setStatusUpdatedAt(observedAt);
    repository.save(status);
    syncInventoryPresence(snapshot.robotId(), isOnline(status, observedAt));
    notifyRobotTransition(snapshot.robotId(), previous, status.getConnectionStatus(), "HEARTBEAT_CONNECTED");
  }

  @Transactional
  public void markBridgeUnreachable(Instant now) {
    dataStore
        .list(DataCategory.ROBOT)
        .forEach(identity -> markBridgeUnreachable(robotId(identity), now));
  }

  @Transactional
  public void markBridgeUnreachable(String robotId, Instant now) {
    if (dataStore.find(DataCategory.ROBOT, robotId) == null) return;
    RobotHeartbeatStatusEntity status = status(robotId, now);
    String previous = status.getConnectionStatus();
    status.setConnectionStatus(RobotConnectionStatus.BRIDGE_UNREACHABLE.name());
    status.setOfflineReason("BRIDGE_UNREACHABLE");
    status.setSourceName(SOURCE_NAME);
    status.setStatusUpdatedAt(now);
    repository.save(status);
    syncInventoryPresence(robotId, false);
    notifyRobotTransition(robotId, previous, status.getConnectionStatus(), "ROBOT_UNREACHABLE");
  }

  @Transactional
  public void markBridgeUnconfigured(String robotId, Instant now) {
    if (dataStore.find(DataCategory.ROBOT, robotId) == null) return;
    RobotHeartbeatStatusEntity status = status(robotId, now);
    String previous = status.getConnectionStatus();
    status.setConnectionStatus(RobotConnectionStatus.BRIDGE_UNCONFIGURED.name());
    status.setOfflineReason("BRIDGE_UNCONFIGURED");
    status.setSourceName(SOURCE_NAME);
    status.setBridgeConfigured(false);
    status.setStatusUpdatedAt(now);
    repository.save(status);
    syncInventoryPresence(robotId, false);
    notifyRobotTransition(robotId, previous, status.getConnectionStatus(), "ROBOT_UNCONFIGURED");
  }

  @Transactional
  public void markInvalidSnapshot(String robotId, Instant now) {
    RobotHeartbeatStatusEntity status = status(robotId, now);
    String previous = status.getConnectionStatus();
    status.setConnectionStatus(RobotConnectionStatus.UNKNOWN.name());
    status.setOfflineReason("INVALID_BRIDGE_SNAPSHOT");
    status.setSourceName(SOURCE_NAME);
    status.setBridgeConfigured(true);
    status.setStatusUpdatedAt(now);
    repository.save(status);
    syncInventoryPresence(robotId, false);
    notifyRobotTransition(robotId, previous, status.getConnectionStatus(), "ROBOT_INVALID_HEARTBEAT");
  }

  @Transactional
  public void refreshTimeouts(Instant now) {
    for (RobotHeartbeatStatusEntity status : repository.findAll()) {
      if (status.getLastHeartbeatAt() == null
          || !RobotConnectionStatus.CONNECTED.name().equals(status.getConnectionStatus())) continue;
      if (status.getLastHeartbeatAt().plus(timeout).isAfter(now)) continue;
      String previous = status.getConnectionStatus();
      status.setConnectionStatus(RobotConnectionStatus.OFFLINE.name());
      status.setOfflineReason("HEARTBEAT_TIMEOUT");
      status.setStatusUpdatedAt(now);
      repository.save(status);
      syncInventoryPresence(status.getRobotId(), false);
      notifyRobotTransition(status.getRobotId(), previous, status.getConnectionStatus(), "ROBOT_OFFLINE");
    }
  }

  @Transactional
  public long countOnline() {
    Instant now = Instant.now();
    refreshTimeouts(now);
    long online = 0;
    for (Map<String, Object> identity : dataStore.list(DataCategory.ROBOT)) {
      RobotHeartbeatStatusEntity status = repository.findById(robotId(identity)).orElse(null);
      if (view(identity, status, now).online()) online++;
    }
    return online;
  }

  @Transactional
  public RobotHeartbeatStatusPage list(
      Boolean online, String connectionStatus, String sort, String direction, int page, int size) {
    Instant now = Instant.now();
    refreshTimeouts(now);
    List<RobotHeartbeatStatusView> items = new ArrayList<>();
    for (Map<String, Object> identity : dataStore.list(DataCategory.ROBOT)) {
      RobotHeartbeatStatusEntity status = repository.findById(robotId(identity)).orElse(null);
      items.add(view(identity, status, now));
    }
    items =
        items.stream()
            .filter(item -> online == null || item.online() == online)
            .filter(
                item ->
                    connectionStatus == null
                        || connectionStatus.isBlank()
                        || item.connectionStatus().equalsIgnoreCase(connectionStatus))
            .sorted(comparator(sort, direction))
            .toList();
    int safeSize = Math.min(100, Math.max(1, size));
    int safePage = Math.max(0, page);
    int from = Math.min(items.size(), safePage * safeSize);
    int to = Math.min(items.size(), from + safeSize);
    return new RobotHeartbeatStatusPage(items.subList(from, to), safePage, safeSize, items.size());
  }

  @Transactional
  public RobotHeartbeatStatusView detail(String robotId) {
    Instant now = Instant.now();
    refreshTimeouts(now);
    return view(requireIdentity(robotId), repository.findById(robotId).orElse(null), now);
  }

  private void markNoHeartbeat(String robotId, Instant now) {
    RobotHeartbeatStatusEntity status = status(robotId, now);
    status.setConnectionStatus(RobotConnectionStatus.UNKNOWN.name());
    status.setOfflineReason("NO_HEARTBEAT");
    status.setSourceName(SOURCE_NAME);
    status.setBridgeConfigured(true);
    status.setStatusUpdatedAt(now);
    repository.save(status);
    syncInventoryPresence(robotId, false);
  }

  /**
   * Keep inventory status aligned with heartbeat presence in bridge mode only. Simulation keeps
   * seed/task-driven ONLINE so dispatch demos and tests still work; dashboards should use heartbeat
   * counts instead of inventory status.
   */
  private void syncInventoryPresence(String robotId, boolean online) {
    if (!properties.isBridgeMode()) return;
    Map<String, Object> robot = dataStore.find(DataCategory.ROBOT, robotId);
    if (robot == null) return;
    String current = text(robot.get("status"));
    if (current == null || current.isBlank()) current = "OFFLINE";
    String next;
    if (online) {
      next = "BUSY".equals(current) ? "BUSY" : "ONLINE";
    } else {
      next = "OFFLINE";
    }
    if (next.equals(current)) return;
    robot.put("status", next);
    if (online) robot.put("lastOnlineAt", Instant.now().toString());
    dataStore.upsert(DataCategory.ROBOT, robot);
  }

  private boolean isOnline(RobotHeartbeatStatusEntity status, Instant now) {
    return RobotConnectionStatus.CONNECTED.name().equals(status.getConnectionStatus())
        && status.getLastHeartbeatAt() != null
        && status.getLastHeartbeatAt().plus(timeout).isAfter(now);
  }

  private RobotHeartbeatStatusEntity status(String robotId, Instant now) {
    return repository
        .findById(robotId)
        .orElseGet(
            () -> {
              RobotHeartbeatStatusEntity item = new RobotHeartbeatStatusEntity();
              item.setRobotId(robotId);
              item.setConnectionStatus(RobotConnectionStatus.UNKNOWN.name());
              item.setOfflineReason("NO_HEARTBEAT");
              item.setSourceName(SOURCE_NAME);
              item.setReportedRemoteImmediateStart(true);
              item.setStatusUpdatedAt(now);
              return item;
            });
  }

  private Map<String, Object> requireIdentity(String robotId) {
    Map<String, Object> identity = dataStore.find(DataCategory.ROBOT, robotId);
    if (identity == null) throw ApiException.notFound("机器人身份未注册");
    return identity;
  }

  private RobotHeartbeatStatusView view(
      Map<String, Object> identity, RobotHeartbeatStatusEntity status, Instant now) {
    if (status == null) {
      return new RobotHeartbeatStatusView(
          robotId(identity),
          text(identity.get("serialNo")),
          text(identity.get("name")),
          RobotConnectionStatus.UNKNOWN.name(),
          false,
          null,
          null,
          null,
          "NO_HEARTBEAT",
          new RobotHeartbeatStatusView.Source(SOURCE_NAME, false),
          null,
          null,
          null,
          null,
          0,
          null,
          true,
          false,
          null,
          false,
          false,
          null,
          null);
    }
    boolean online = isOnline(status, now);
    return new RobotHeartbeatStatusView(
        robotId(identity),
        text(identity.get("serialNo")),
        text(identity.get("name")),
        status.getConnectionStatus(),
        online,
        status.getLastHeartbeatAt(),
        status.getLastOnlineAt(),
        status.getStatusUpdatedAt(),
        status.getOfflineReason(),
        new RobotHeartbeatStatusView.Source(
            status.getSourceName(), Boolean.TRUE.equals(status.getBridgeConfigured())),
        status.getProtocolVersion(),
        status.getBootId(),
        status.getSoftwareVersion(),
        status.getRobotState(),
        status.getAcceptedEventSequence(),
        status.getDiagnosticSummary(),
        status.isReportedRemoteImmediateStart(),
        status.isReportedLocalConfirmStart(),
        status.getLocalConfirmProtocolVersion(),
        RobotLocalConfirmPolicyService.isProtocolSupported(status.getLocalConfirmProtocolVersion()),
        status.isLocalConfirmStartReady(),
        status.getLocalConfirmStartError(),
        status.getCapabilityReportedAt());
  }

  private Comparator<RobotHeartbeatStatusView> comparator(String sort, String direction) {
    Comparator<RobotHeartbeatStatusView> comparator =
        switch (sort == null ? "" : sort) {
          case "robotId" ->
              Comparator.comparing(
                  RobotHeartbeatStatusView::robotId, Comparator.nullsLast(String::compareTo));
          case "displayName" ->
              Comparator.comparing(
                  RobotHeartbeatStatusView::displayName, Comparator.nullsLast(String::compareTo));
          case "statusUpdatedAt" ->
              Comparator.comparing(
                  RobotHeartbeatStatusView::statusUpdatedAt,
                  Comparator.nullsLast(Instant::compareTo));
          default ->
              Comparator.comparing(
                  RobotHeartbeatStatusView::lastHeartbeatAt,
                  Comparator.nullsLast(Instant::compareTo));
        };
    return "asc".equalsIgnoreCase(direction) ? comparator : comparator.reversed();
  }

  private String diagnosticSummary(BridgeRobotSnapshot snapshot) {
    List<String> values = new ArrayList<>();
    addDiagnostic(values, "state", snapshot.state());
    addDiagnostic(values, "systemMode", snapshot.health().get("systemMode"));
    addDiagnostic(values, "nav2", snapshot.health().get("nav2"));
    addDiagnostic(values, "odomAgeSec", snapshot.health().get("odomAgeSec"));
    addDiagnostic(values, "scanAgeSec", snapshot.health().get("scanAgeSec"));
    addDiagnostic(values, "imuAgeSec", snapshot.health().get("imuAgeSec"));
    return values.isEmpty() ? null : String.join("; ", values);
  }

  private void addDiagnostic(List<String> values, String key, Object value) {
    if (value == null || !(value instanceof Number || value instanceof String)) return;
    String normalized = String.valueOf(value).replaceAll("[\\r\\n]", " ").trim();
    if (!normalized.isBlank())
      values.add(key + "=" + normalized.substring(0, Math.min(80, normalized.length())));
  }

  private static String robotId(Map<String, Object> identity) {
    return text(identity.get("id"));
  }

  private static String text(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private void notifyRobotTransition(String robotId, String previous, String current, String eventCode) {
    if (notificationService == null || current == null || current.equals(previous)) return;
    String title = "CONNECTED".equals(current) ? "机器人已恢复在线" : "机器人状态异常";
    String content = "机器人 " + robotId + " 状态由 " + previous + " 变更为 " + current + "。";
    notificationService.pushEvent("*", "SYSTEM", eventCode, "ROBOT", robotId, title, content,
        "/robots/status", null);
  }
}
