package com.powerinspection.robot;

import com.powerinspection.common.ApiException;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.task.TaskExecutionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RobotLocationService {
  private static final Logger log = LoggerFactory.getLogger(RobotLocationService.class);
  private static final Duration DEFAULT_TRACK_RANGE = Duration.ofHours(1);
  private static final Duration MAX_TRACK_RANGE = Duration.ofDays(7);
  private static final int DEFAULT_TRACK_LIMIT = 2000;
  private static final int MAX_TRACK_LIMIT = 10000;
  private static final Set<String> ACTIVE_STATES = Set.of(
      "starting", "running", "paused", "manual_takeover", "returning_home", "waiting_loop");
  private static final Set<String> TERMINAL_STATES = Set.of("succeeded", "failed", "canceled");

  private final RobotTelemetryRepository telemetryRepository;
  private final RobotLocationHistoryRepository historyRepository;
  private final RobotHeartbeatService heartbeatService;
  private final DataStoreService dataStore;
  private final TaskExecutionRepository executionRepository;
  private final RobotProperties properties;

  public RobotLocationService(
      RobotTelemetryRepository telemetryRepository,
      RobotLocationHistoryRepository historyRepository,
      RobotHeartbeatService heartbeatService,
      DataStoreService dataStore,
      TaskExecutionRepository executionRepository,
      RobotProperties properties) {
    this.telemetryRepository = telemetryRepository;
    this.historyRepository = historyRepository;
    this.heartbeatService = heartbeatService;
    this.dataStore = dataStore;
    this.executionRepository = executionRepository;
    this.properties = properties;
  }

  @Transactional
  public void applySnapshot(BridgeRobotSnapshot snapshot, Instant receivedAt) {
    if (snapshot == null || !StringUtils.hasText(snapshot.robotId())) return;
    BridgeGnssFix fix = snapshot.gnssFix() == null
        ? null
        : GnssFixParser.withServerStale(
            snapshot.gnssFix(), receivedAt, properties.getGpsStaleTimeoutSeconds());
    if (fix == null) return;

    RobotTelemetryEntity telemetry = telemetryRepository.findById(snapshot.robotId()).orElseGet(() -> {
      RobotTelemetryEntity created = new RobotTelemetryEntity();
      created.setRobotId(snapshot.robotId());
      created.setUpdatedAt(receivedAt.toString());
      return created;
    });
    if (StringUtils.hasText(snapshot.executionId())) {
      telemetry.setLastExecutionId(snapshot.executionId());
    }

    if (Boolean.TRUE.equals(fix.valid()) && GnssFixParser.coordinateValid(fix.latitude(), fix.longitude())) {
      RobotLocationHistoryEntity lastPoint = historyRepository.findTopByRobotIdOrderByObservedAtDesc(snapshot.robotId());
      if (lastPoint != null && shouldRejectJump(lastPoint, fix)) {
        log.warn("robot_location_jump_rejected robotId={} fixType={}", snapshot.robotId(), fix.fixType());
        return;
      }
      telemetry.applyGnssFix(fix, receivedAt);
      telemetry.setUpdatedAt(receivedAt.toString());
      telemetryRepository.save(telemetry);
      maybeSaveTrackPoint(snapshot, fix, receivedAt, lastPoint);
      return;
    }

    if (telemetry.hasStoredCoordinates()) {
      BridgeGnssFix staleFix = new BridgeGnssFix(
          false,
          true,
          fix.frame(),
          telemetry.getGpsLatitude(),
          telemetry.getGpsLongitude(),
          telemetry.getGpsAltitude(),
          telemetry.getGpsQuality(),
          telemetry.getGpsFixType() == null ? "UNKNOWN" : telemetry.getGpsFixType(),
          telemetry.getGpsSatellites(),
          telemetry.getGpsHdop(),
          telemetry.getGpsDifferentialAge(),
          telemetry.getGpsBaseStationId(),
          fix.ageSec(),
          telemetry.getGpsObservedAt());
      telemetry.applyGnssFix(staleFix, receivedAt);
    } else {
      telemetry.applyGnssFix(fix, receivedAt);
    }
    telemetry.setUpdatedAt(receivedAt.toString());
    telemetryRepository.save(telemetry);
  }

  public RobotLocationView getLocation(String robotId) {
    requireRobotExists(robotId);
    RobotHeartbeatStatusView heartbeat = heartbeatService.detail(robotId);
    RobotTelemetryEntity telemetry = telemetryRepository.findById(robotId).orElse(null);
    return toView(robotId, heartbeat, telemetry);
  }

  public List<RobotLocationView> listLocations(String siteId, Boolean online) {
    List<Map<String, Object>> robots = dataStore.list(DataCategory.ROBOT);
    List<RobotLocationView> views = new ArrayList<>();
    for (Map<String, Object> robot : robots) {
      String robotId = String.valueOf(robot.get("id"));
      if (StringUtils.hasText(siteId) && !siteId.equals(String.valueOf(robot.get("siteId")))) continue;
      RobotHeartbeatStatusView heartbeat = heartbeatService.detail(robotId);
      if (online != null && heartbeat.online() != online) continue;
      RobotTelemetryEntity telemetry = telemetryRepository.findById(robotId).orElse(null);
      views.add(toView(robotId, heartbeat, telemetry));
    }
    return views;
  }

  public RobotTrackView getTrack(String robotId, Instant start, Instant end, String executionId, Integer limit) {
    requireRobotExists(robotId);
    Instant resolvedEnd = end == null ? Instant.now() : end;
    Instant resolvedStart = start == null ? resolvedEnd.minus(DEFAULT_TRACK_RANGE) : start;
    if (resolvedStart.isAfter(resolvedEnd)) {
      throw ApiException.badRequest("INVALID_TRACK_TIME_RANGE: start must be before end");
    }
    if (Duration.between(resolvedStart, resolvedEnd).compareTo(MAX_TRACK_RANGE) > 0) {
      throw ApiException.badRequest("TRACK_QUERY_RANGE_TOO_LARGE: maximum range is 7 days");
    }
    int resolvedLimit = limit == null ? DEFAULT_TRACK_LIMIT : limit;
    if (resolvedLimit < 1) {
      throw ApiException.badRequest("INVALID_TRACK_POINT_LIMIT: limit must be at least 1");
    }
    if (resolvedLimit > MAX_TRACK_LIMIT) {
      throw ApiException.badRequest("TRACK_POINT_LIMIT_EXCEEDED: maximum limit is 10000");
    }
    List<RobotLocationHistoryEntity> rows = historyRepository.findTrack(
        robotId, resolvedStart, resolvedEnd, StringUtils.hasText(executionId) ? executionId : null,
        PageRequest.of(0, resolvedLimit));
    List<RobotTrackPointView> points = rows.stream().map(this::toTrackPoint).toList();
    return new RobotTrackView(robotId, executionId, resolvedStart, resolvedEnd, points);
  }

  @Transactional
  public long purgeExpiredTrackPoints(Instant now) {
    int retentionDays = properties.getTrackRetentionDays();
    if (retentionDays <= 0) return 0;
    Instant cutoff = now.minus(Duration.ofDays(retentionDays));
    long deleted = historyRepository.deleteByObservedAtBefore(cutoff);
    if (deleted > 0) {
      log.info("robot_track_retention_cleanup deletedCount={} cutoffTime={}", deleted, cutoff);
    }
    return deleted;
  }

  private RobotLocationView toView(String robotId, RobotHeartbeatStatusView heartbeat, RobotTelemetryEntity telemetry) {
    boolean online = heartbeat.online();
    String state = heartbeat.robotState();
    String executionId = telemetry == null ? null : telemetry.getLastExecutionId();
    if (telemetry == null || !telemetry.hasStoredCoordinates()) {
      return new RobotLocationView(robotId, online, false, false, state, executionId, null);
    }
    BridgeGnssFix fix = telemetry.toGnssFix();
    boolean realtime = online && fix != null && fix.valid() && !fix.stale();
    return new RobotLocationView(robotId, online, true, realtime, state, executionId, fix);
  }

  private RobotTrackPointView toTrackPoint(RobotLocationHistoryEntity row) {
    return new RobotTrackPointView(
        row.getLatitude(),
        row.getLongitude(),
        row.getAltitude(),
        row.getFixType(),
        row.getSatellites(),
        row.getHdop(),
        row.getRobotState(),
        row.getNavigationPhase(),
        row.getTargetId(),
        row.getCycleIndex(),
        row.getObservedAt());
  }

  private void maybeSaveTrackPoint(
      BridgeRobotSnapshot snapshot,
      BridgeGnssFix fix,
      Instant receivedAt,
      RobotLocationHistoryEntity lastPoint) {
    if (!fix.valid() || fix.stale()) return;
    Instant observedAt = fix.observedAt() != null ? fix.observedAt() : receivedAt;
    if (lastPoint != null && !observedAt.isAfter(lastPoint.getObservedAt())) return;
    if (TERMINAL_STATES.contains(snapshot.state())) {
      String executionId = snapshot.executionId();
      if (StringUtils.hasText(executionId)
          && historyRepository.existsByRobotIdAndExecutionIdAndRobotStateIn(
              snapshot.robotId(), executionId, List.copyOf(TERMINAL_STATES))) {
        return;
      }
    } else if (lastPoint != null && !shouldSample(snapshot.state(), lastPoint, fix, observedAt)) {
      return;
    }
    if (historyRepository.existsByRobotIdAndObservedAt(snapshot.robotId(), observedAt)) return;

    RobotLocationHistoryEntity point = new RobotLocationHistoryEntity();
    point.setRobotId(snapshot.robotId());
    point.setObservedAt(observedAt);
    point.setReceivedAt(receivedAt);
    point.setLatitude(fix.latitude());
    point.setLongitude(fix.longitude());
    point.setAltitude(fix.altitude());
    point.setQuality(fix.quality());
    point.setFixType(fix.fixType());
    point.setSatellites(fix.satellites());
    point.setHdop(fix.hdop());
    point.setRobotState(snapshot.state());
    applyPatrolContext(point, snapshot);
    bindExecution(point, snapshot);
    historyRepository.save(point);
    log.debug("robot_track_point_saved robotId={} executionId={} state={}",
        snapshot.robotId(), point.getExecutionId(), point.getRobotState());
  }

  private void applyPatrolContext(RobotLocationHistoryEntity point, BridgeRobotSnapshot snapshot) {
    BridgePatrolSnapshot patrol = snapshot.patrol();
    if (patrol == null) return;
    point.setRouteId(patrol.routeId());
    point.setTargetId(patrol.targetId());
    point.setCycleIndex(patrol.cycleIndex());
    point.setNavigationPhase(patrol.navigationPhase());
  }

  private void bindExecution(RobotLocationHistoryEntity point, BridgeRobotSnapshot snapshot) {
    if (StringUtils.hasText(snapshot.executionId())) {
      point.setExecutionId(snapshot.executionId());
      executionRepository.findByExecutionId(snapshot.executionId()).ifPresent(execution -> {
        point.setTaskId(execution.getTaskId());
      });
      return;
    }
    executionRepository.findFirstByRobotIdAndStatusInOrderByUpdatedAtDesc(
        snapshot.robotId(), List.of("RUNNING", "PAUSED", "MANUAL_TAKEOVER", "STARTING", "WAITING_LOCAL_CONFIRM"))
        .ifPresent(execution -> {
          point.setExecutionId(execution.getExecutionId());
          point.setTaskId(execution.getTaskId());
        });
  }

  private boolean shouldSample(String state, RobotLocationHistoryEntity lastPoint, BridgeGnssFix fix, Instant observedAt) {
    if (TERMINAL_STATES.contains(state)) return true;
    double distanceMeters = haversineMeters(
        lastPoint.getLatitude(), lastPoint.getLongitude(), fix.latitude(), fix.longitude());
    double elapsedSec = Math.max(0.001, Duration.between(lastPoint.getObservedAt(), observedAt).toMillis() / 1000.0);
    if (ACTIVE_STATES.contains(state)) {
      return distanceMeters >= 1.0 || elapsedSec >= 5.0;
    }
    return distanceMeters >= 5.0 || elapsedSec >= 30.0;
  }

  private boolean shouldRejectJump(RobotLocationHistoryEntity lastPoint, BridgeGnssFix fix) {
    double distanceMeters = haversineMeters(
        lastPoint.getLatitude(), lastPoint.getLongitude(), fix.latitude(), fix.longitude());
    Instant observedAt = fix.observedAt() != null ? fix.observedAt() : Instant.now();
    double elapsedSec = Math.max(0.001, Duration.between(lastPoint.getObservedAt(), observedAt).toMillis() / 1000.0);
    double speedMps = distanceMeters / elapsedSec;
    return speedMps > Math.max(0.1, properties.getMaxGpsSpeedMps());
  }

  private void requireRobotExists(String robotId) {
    if (dataStore.get(DataCategory.ROBOT, robotId) == null) {
      throw ApiException.notFound("ROBOT_LOCATION_NOT_FOUND: robot not found");
    }
  }

  static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
    double radius = 6371000.0;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLon = Math.toRadians(lon2 - lon1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    return radius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  }
}
