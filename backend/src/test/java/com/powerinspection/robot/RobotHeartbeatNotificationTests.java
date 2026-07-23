package com.powerinspection.robot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.notification.NotificationService;
import java.time.Instant;
import java.util.HashMap;
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
}
