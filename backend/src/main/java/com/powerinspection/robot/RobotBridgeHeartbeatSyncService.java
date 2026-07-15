package com.powerinspection.robot;

import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 将 Bridge 的服务器时间心跳快照同步到平台持久化状态。 */
@Service
@ConditionalOnProperty(prefix = "app.robot", name = "mode", havingValue = "bridge")
public class RobotBridgeHeartbeatSyncService {
  private final RobotBridgeHeartbeatClient client;
  private final RobotHeartbeatService heartbeatService;
  private final DataStoreService dataStore;
  private final RobotBridgeIdMapper idMapper;

  public RobotBridgeHeartbeatSyncService(RobotBridgeHeartbeatClient client, RobotHeartbeatService heartbeatService, DataStoreService dataStore,
      RobotBridgeIdMapper idMapper) {
    this.client = client;
    this.heartbeatService = heartbeatService;
    this.dataStore = dataStore;
    this.idMapper = idMapper;
  }

  @Scheduled(fixedDelayString = "${app.robot.heartbeat-sync-interval-ms:3000}")
  public void synchronize() {
    Instant now = Instant.now();
    Set<String> configuredRobotIds;
    try {
      configuredRobotIds = Set.copyOf(client.configuredRobotIds());
    } catch (BridgeRobotClientException ex) {
      heartbeatService.markBridgeUnreachable(now);
      return;
    }
    List<Map<String, Object>> identities = dataStore.list(DataCategory.ROBOT);
    for (Map<String, Object> identity : identities) {
      String robotId = String.valueOf(identity.get("id"));
      String bridgeRobotId = idMapper.toBridgeId(robotId);
      if (!configuredRobotIds.contains(bridgeRobotId)) {
        heartbeatService.markBridgeUnconfigured(robotId, now);
        continue;
      }
      try {
        BridgeRobotSnapshot snapshot = client.robot(bridgeRobotId);
        heartbeatService.applyBridgeSnapshot(new BridgeRobotSnapshot(robotId, snapshot.lastHeartbeatAt(), snapshot.protocolVersion(),
          snapshot.bootId(), snapshot.state(), snapshot.softwareVersion(), snapshot.acceptedEventSequence(), snapshot.health()), now);
      } catch (BridgeRobotClientException ex) {
        heartbeatService.markBridgeUnreachable(robotId, now);
      }
    }
    heartbeatService.refreshTimeouts(now);
  }
}
