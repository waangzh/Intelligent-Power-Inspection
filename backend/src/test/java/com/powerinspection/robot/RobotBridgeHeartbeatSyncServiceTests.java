package com.powerinspection.robot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RobotBridgeHeartbeatSyncServiceTests {
  @Test
  void writesBridgeHeartbeatUnderPlatformRobotId() {
    RobotBridgeHeartbeatClient client = Mockito.mock(RobotBridgeHeartbeatClient.class);
    RobotHeartbeatService heartbeats = Mockito.mock(RobotHeartbeatService.class);
    DataStoreService dataStore = Mockito.mock(DataStoreService.class);
    RobotProperties properties = new RobotProperties();
    properties.setBridgeRobotIdMappings(Map.of("robot_001", "robot-001"));
    RobotLocationService locations = Mockito.mock(RobotLocationService.class);
    RobotBridgeHeartbeatSyncService service = new RobotBridgeHeartbeatSyncService(client, heartbeats, locations, dataStore,
      new RobotBridgeIdMapper(properties));
    BridgeRobotSnapshot bridgeSnapshot = new BridgeRobotSnapshot("robot-001", Instant.parse("2026-07-15T00:00:00Z"),
      "1.0", "boot-1", "idle", "build-1", 3, Map.of(), null);

    when(client.configuredRobotIds()).thenReturn(List.of("robot-001"));
    when(dataStore.list(DataCategory.ROBOT)).thenReturn(List.of(Map.of("id", "robot_001")));
    when(client.robot("robot-001")).thenReturn(bridgeSnapshot);

    service.synchronize();

    ArgumentCaptor<BridgeRobotSnapshot> captured = ArgumentCaptor.forClass(BridgeRobotSnapshot.class);
    verify(heartbeats).applyBridgeSnapshot(captured.capture(), any(Instant.class));
    verify(locations).applySnapshot(captured.capture(), any(Instant.class));
    verify(heartbeats).refreshTimeouts(any(Instant.class));
    assertEquals("robot_001", captured.getValue().robotId());
    assertEquals(bridgeSnapshot.lastHeartbeatAt(), captured.getValue().lastHeartbeatAt());
  }
}
