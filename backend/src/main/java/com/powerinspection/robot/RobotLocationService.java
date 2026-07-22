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
import org.springframework.scheduling.annotation.Scheduled;
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
    BridgeGnssFix fix = normalizeFreshness(snapshot.gnssFix(), receivedAt);
    if (fix == null) {
      telemetryRepository.findById(snapshot.robotId()).ifPresent(telemetry -> {
        telemetry.applyContext(snapshot);
        BridgeGnssFix previous = telemetry.toGnssFix();
        if (previous != null) {
          BridgeGnssFix stale = new BridgeGnssFix(
              false, true, previous.frame(), previous.latitude(), previous.longitude(),
              previous.altitude(), previous.quality(), previous.fixType(), previous.satellites(),
              previous.hdop(), previous.differentialAge(), previous.baseStationId(),
              previous.ageSec(), previous.observedAt());
          telemetry.applyGnssFix(stale, receivedAt, true);
        }
        telemetry.setUpdatedAt(receivedAt.toString());
        telemetryRepository.save(telemetry);
      });
      return;
    }

    RobotTelemetryEntity telemetry = telemetryRepository.findById(snapshot.robotId()).orElseGet(() -> {
      RobotTelemetryEntity created = new RobotTelemetryEntity();
      created.setRobotId(snapshot.robotId());
      created.setUpdatedAt(receivedAt.toString());
      return created;
    });
    telemetry.applyContext(snapshot);

    if (fix.valid() && GnssFixParser.coordinateValid(fix.latitude(), fix.longitude())) {
      RobotLocationHistoryEntity lastPoint = historyRepository.findTopByRobotIdOrderByObservedAtDesc(snapshot.robotId());
      Instant observedAt = fix.observedAt();
      if (lastPoint != null && (observedAt == null || !observedAt.isAfter(lastPoint.getObservedAt()))) {
        telemetry.applyGnssFix(unavailableFix(fix), receivedAt, telemetry.hasStoredCoordinates());
        telemetry.setUpdatedAt(receivedAt.toString());
        telemetryRepository.save(telemetry);
        return;
      }
      if (lastPoint != null && shouldRejectJump(lastPoint, fix, observedAt)) {
        log.warn("robot_location_jump_rejected robotId={} fixType={}", snapshot.robotId(), fix.fixType());
        telemetry.applyGnssFix(unavailableFix(fix), receivedAt, telemetry.hasStoredCoordinates());
        telemetry.setUpdatedAt(receivedAt.toString());
        telemetryRepository.save(telemetry);
        return;
      }
      telemetry.applyGnssFix(fix, receivedAt);
      telemetry.setUpdatedAt(receivedAt.toString());
      telemetryRepository.save(telemetry);
      maybeSaveTrackPoint(snapshot, fix, receivedAt, lastPoint);
      return;
    }

    telemetry.applyGnssFix(fix, receivedAt, telemetry.hasStoredCoordinates());
    telemetry.setUpdatedAt(receivedAt.toString());
    telemetryRepository.save(telemetry);
  }

  public RobotLocationView getLocation(String robotId) {
    requireRobotExists(robotId);
    RobotHeartbeatStatusView heartbeat = heartbeatService.detail(robotId);
    RobotTelemetryEntity telemetry = telemetryRepository.findById(robotId).orElse(null);
    return toView(robotId, heartbeat.online(), telemetry);
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
      views.add(toView(robotId, heartbeat.online(), telemetry));
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
    List<RobotTrackPointView> points = rows.stream()
        .map(row -> new RobotTrackPointView(
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
            row.getObservedAt()))
        .toList();
    return new RobotTrackView(
        robotId,
        StringUtils.hasText(executionId) ? executionId : null,
        resolvedStart,
        resolvedEnd,
        points);
  }

  private RobotLocationView toView(String robotId, boolean online, RobotTelemetryEntity telemetry) {
    if (telemetry == null || !telemetry.hasStoredCoordinates()) {
      return new RobotLocationView(robotId, online, false, false, null, null, null);
    }
    BridgeGnssFix fix = telemetry.toGnssFix();
    boolean realtime = online && fix != null && fix.valid() && !fix.stale();
    return new RobotLocationView(
        robotId,
        online,
        true,
        realtime,
        telemetry.getGpsRobotState(),
        telemetry.getGpsExecutionId(),
        fix);
  }

  private void maybeSaveTrackPoint(
      BridgeRobotSnapshot snapshot,
      BridgeGnssFix fix,
      Instant receivedAt,
      RobotLocationHistoryEntity lastPoint) {
    Instant observedAt = fix.observedAt() != null ? fix.observedAt() : receivedAt;
    if (lastPoint != null && !observedAt.isAfter(lastPoint.getObservedAt())) return;
    if (historyRepository.existsByRobotIdAndObservedAt(snapshot.robotId(), observedAt)) return;
    String executionId = StringUtils.hasText(snapshot.executionId()) ? snapshot.executionId() : null;
    if (TERMINAL_STATES.contains(snapshot.state())) {
      if (executionId == null
          || historyRepository.existsByRobotIdAndExecutionIdAndRobotStateIn(
              snapshot.robotId(), executionId, TERMINAL_STATES)) return;
    } else if (lastPoint != null && !shouldSample(snapshot.state(), lastPoint, fix, observedAt)) {
      return;
    }

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
    bindExecution(point, snapshot);
    historyRepository.save(point);
  }

  private void bindExecution(RobotLocationHistoryEntity point, BridgeRobotSnapshot snapshot) {
    point.setRobotState(snapshot.state());
    String executionId = StringUtils.hasText(snapshot.executionId()) ? snapshot.executionId() : null;
    point.setExecutionId(executionId);
    if (executionId != null) {
      executionRepository.findByExecutionId(executionId)
          .filter(execution -> snapshot.robotId().equals(execution.getRobotId()))
          .ifPresent(execution -> point.setTaskId(execution.getTaskId()));
    }
    BridgePatrolSnapshot patrol = snapshot.patrol();
    if (patrol != null) {
      point.setRouteId(patrol.routeId());
      point.setTargetId(patrol.targetId());
      point.setCycleIndex(patrol.cycleIndex());
      point.setNavigationPhase(patrol.navigationPhase());
    }
  }

  private boolean shouldSample(String state, RobotLocationHistoryEntity lastPoint, BridgeGnssFix fix, Instant observedAt) {
    double distanceMeters = haversineMeters(
        lastPoint.getLatitude(), lastPoint.getLongitude(), fix.latitude(), fix.longitude());
    double elapsedSec = Math.max(0.001, Duration.between(lastPoint.getObservedAt(), observedAt).toMillis() / 1000.0);
    if (ACTIVE_STATES.contains(state)) {
      return distanceMeters >= 1.0 || elapsedSec >= 5.0;
    }
    return distanceMeters >= 5.0 || elapsedSec >= 30.0;
  }

  private boolean shouldRejectJump(
      RobotLocationHistoryEntity lastPoint, BridgeGnssFix fix, Instant observedAt) {
    double distanceMeters = haversineMeters(
        lastPoint.getLatitude(), lastPoint.getLongitude(), fix.latitude(), fix.longitude());
    double elapsedSec = Math.max(0.001, Duration.between(lastPoint.getObservedAt(), observedAt).toMillis() / 1000.0);
    double speedMps = distanceMeters / elapsedSec;
    return speedMps > Math.max(0.1, properties.getMaxGpsSpeedMps());
  }

  private BridgeGnssFix normalizeFreshness(BridgeGnssFix fix, Instant receivedAt) {
    if (fix == null) return null;
    Instant observedAt = fix.observedAt();
    long timeoutSeconds = Math.max(1, properties.getGpsStaleTimeoutSeconds());
    boolean stale = fix.stale()
        || observedAt == null
        || observedAt.isBefore(receivedAt.minusSeconds(timeoutSeconds))
        || observedAt.isAfter(receivedAt.plusSeconds(30));
    Double ageSec = observedAt == null
        ? null
        : Math.max(0.0, Duration.between(observedAt, receivedAt).toMillis() / 1000.0);
    boolean valid = fix.valid()
        && GnssFixParser.coordinateValid(fix.latitude(), fix.longitude())
        && fix.quality() != null
        && fix.quality() > 0
        && !stale;
    return new BridgeGnssFix(
        valid, stale, fix.frame(), fix.latitude(), fix.longitude(), fix.altitude(),
        fix.quality(), fix.fixType(), fix.satellites(), fix.hdop(), fix.differentialAge(),
        fix.baseStationId(), ageSec, observedAt);
  }

  private BridgeGnssFix unavailableFix(BridgeGnssFix fix) {
    return new BridgeGnssFix(
        false, fix.stale(), fix.frame(), fix.latitude(), fix.longitude(), fix.altitude(),
        fix.quality(), fix.fixType(), fix.satellites(), fix.hdop(), fix.differentialAge(),
        fix.baseStationId(), fix.ageSec(), fix.observedAt());
  }

  @Scheduled(cron = "${app.robot.track-retention-cleanup-cron:0 15 3 * * *}")
  @Transactional
  public void cleanupExpiredTrackPoints() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(Math.max(1, properties.getTrackRetentionDays())));
    long deletedCount = historyRepository.deleteByObservedAtBefore(cutoff);
    if (deletedCount > 0) {
      log.info("robot_track_retention_cleanup deletedCount={} cutoffTime={}", deletedCount, cutoff);
    }
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
