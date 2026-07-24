package com.powerinspection.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.notification.NotificationService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RobotHeartbeatNotificationTests {
  @Mock RobotHeartbeatStatusRepository repository;
  @Mock DataStoreService dataStore;
  @Mock NotificationService notificationService;

  @Test
  void offlineTransitionPublishesSystemNotification() {
    RobotProperties properties = new RobotProperties();
    properties.setMode("bridge");
    RobotHeartbeatService service = new RobotHeartbeatService(repository, dataStore, properties, notificationService);
    when(dataStore.find(DataCategory.ROBOT, "robot-1")).thenReturn(new HashMap<>(java.util.Map.of("id", "robot-1", "status", "ONLINE")));
    when(repository.findById("robot-1")).thenReturn(Optional.empty());
    when(repository.save(any(RobotHeartbeatStatusEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.markBridgeUnreachable("robot-1", Instant.parse("2026-07-23T00:00:00Z"));

    verify(notificationService).pushEvent(
        eq("*"), eq("SYSTEM"), eq("ROBOT_UNREACHABLE"), eq("ROBOT"), eq("robot-1"),
        eq("机器人状态异常"), any(String.class), eq("/robots/status"), isNull(String.class));
  }
  @Test
  void staleUnchangedHeartbeatDoesNotRestoreOfflineRobot() {
    RobotProperties properties = new RobotProperties();
    properties.setMode("bridge");
    RobotHeartbeatService service = new RobotHeartbeatService(repository, dataStore, properties, notificationService);
    Instant heartbeatAt = Instant.parse("2026-07-24T00:00:00Z");
    Instant timedOutAt = heartbeatAt.plusSeconds(13);
    RobotHeartbeatStatusEntity status = new RobotHeartbeatStatusEntity();
    status.setRobotId("robot-1");
    status.setConnectionStatus(RobotConnectionStatus.CONNECTED.name());
    status.setLastHeartbeatAt(heartbeatAt);

    when(dataStore.find(DataCategory.ROBOT, "robot-1"))
        .thenReturn(new HashMap<>(Map.of("id", "robot-1", "status", "ONLINE")));
    when(repository.findAll()).thenReturn(List.of(status));
    when(repository.findById("robot-1")).thenReturn(Optional.of(status));
    when(repository.save(any(RobotHeartbeatStatusEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.refreshTimeouts(timedOutAt);
    service.applyBridgeSnapshot(snapshot("robot-1", heartbeatAt), timedOutAt.plusSeconds(3));

    assertEquals(RobotConnectionStatus.OFFLINE.name(), status.getConnectionStatus());
    verify(notificationService, never()).pushEvent(
        eq("*"), eq("SYSTEM"), eq("HEARTBEAT_CONNECTED"), eq("ROBOT"), eq("robot-1"),
        any(String.class), any(String.class), eq("/robots/status"), isNull(String.class));

    Instant recoveredAt = timedOutAt.plusSeconds(4);
    service.applyBridgeSnapshot(snapshot("robot-1", recoveredAt), recoveredAt);

    assertEquals(RobotConnectionStatus.CONNECTED.name(), status.getConnectionStatus());
    verify(notificationService).pushEvent(
        eq("*"), eq("SYSTEM"), eq("HEARTBEAT_CONNECTED"), eq("ROBOT"), eq("robot-1"),
        any(String.class), any(String.class), eq("/robots/status"), isNull(String.class));
  }

  private BridgeRobotSnapshot snapshot(String robotId, Instant heartbeatAt) {
    return new BridgeRobotSnapshot(robotId, heartbeatAt, "1.0", "boot-1", "idle", "build-1", 1,
        Map.of(), null, true, false, null, false, null, heartbeatAt);
  }
}
