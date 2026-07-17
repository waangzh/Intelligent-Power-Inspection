package com.powerinspection.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.powerinspection.common.ApiException;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.robot.BridgeRobotSnapshot;
import com.powerinspection.robot.RobotBridgeDeploymentClient;
import com.powerinspection.robot.RobotBridgeDeploymentException;
import com.powerinspection.robot.RobotBridgeDeploymentResult;
import com.powerinspection.robot.RobotHeartbeatService;
import com.powerinspection.robot.RobotHeartbeatStatusEntity;
import com.powerinspection.robot.RobotHeartbeatStatusRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(properties = {
  "app.robot.mode=bridge",
  "app.robot.bridge-admin-token=test-bridge-admin-token",
  "app.robot.heartbeat-bridge-base-url=http://127.0.0.1:1",
  "app.robot.heartbeat-sync-interval-ms=600000",
  "app.route-deployment.worker-delay-ms=600000",
  "app.route-deployment.worker-initial-delay-ms=600000",
  "app.route-deployment.max-attempts=3",
  "app.route-deployment.initial-backoff-seconds=1",
  "app.route-deployment.max-backoff-seconds=4"
})
class RouteDeploymentServiceTests {
  @Autowired private RouteDeploymentService deploymentService;
  @Autowired private RouteDeploymentRepository deploymentRepository;
  @Autowired private RouteRevisionRepository revisionRepository;
  @Autowired private RouteDeploymentWorker worker;
  @Autowired private RouteDeploymentLifecycleService lifecycle;
  @Autowired private DataStoreService dataStore;
  @Autowired private RobotHeartbeatService heartbeatService;
  @Autowired private RobotHeartbeatStatusRepository heartbeatStatusRepository;
  @MockBean private RobotBridgeDeploymentClient bridgeClient;

  @BeforeEach
  void setUp() {
    deploymentRepository.deleteAll();
  }

  @AfterEach
  void cleanUp() {
    deploymentRepository.deleteAll();
  }

  @Test
  void onlineConfiguredRobotCreatesPendingDeploymentAndIdempotencyDoesNotDuplicate() {
    Fixture fixture = fixture();
    Map<String, Object> first = deploymentService.request(fixture.revisionId(), fixture.robotId(), "deploy-key-" + fixture.revisionId());
    Map<String, Object> second = deploymentService.request(fixture.revisionId(), fixture.robotId(), "deploy-key-" + fixture.revisionId());

    assertEquals(RouteDeploymentState.PENDING.name(), first.get("state"));
    assertEquals(first.get("id"), second.get("id"));
    assertEquals(1, deploymentRepository.count());
  }

  @Test
  void offlineUnconfiguredAndUnreachableRobotsAreRejected() {
    Fixture offline = fixture();
    Instant old = Instant.now().minusSeconds(30);
    RobotHeartbeatStatusEntity offlineStatus = heartbeatStatusRepository.findById(offline.robotId()).orElseThrow();
    offlineStatus.setLastHeartbeatAt(old);
    heartbeatStatusRepository.save(offlineStatus);
    heartbeatService.refreshTimeouts(Instant.now());
    assertThrows(ApiException.class, () -> deploymentService.request(offline.revisionId(), offline.robotId(), "offline-" + offline.revisionId()));

    Fixture unconfigured = fixture();
    heartbeatService.markBridgeUnconfigured(unconfigured.robotId(), Instant.now());
    assertThrows(ApiException.class, () -> deploymentService.request(unconfigured.revisionId(), unconfigured.robotId(), "unconfigured-" + unconfigured.revisionId()));

    Fixture unreachable = fixture();
    heartbeatService.markBridgeUnreachable(unreachable.robotId(), Instant.now());
    assertThrows(ApiException.class, () -> deploymentService.request(unreachable.revisionId(), unreachable.robotId(), "unreachable-" + unreachable.revisionId()));
  }

  @Test
  void robotWithoutSiteBindingIsRejected() {
    Fixture fixture = fixture();
    Map<String, Object> robot = dataStore.get(DataCategory.ROBOT, fixture.robotId());
    robot.remove("siteId");
    dataStore.upsert(DataCategory.ROBOT, robot);

    assertThrows(ApiException.class, () -> deploymentService.request(fixture.revisionId(), fixture.robotId(), "unbound-" + fixture.revisionId()));
  }

  @Test
  void workerMarksReadyOnlyAfterBridgeIdentityAndHashesMatch() {
    Fixture fixture = fixture();
    String deploymentId = create(fixture);
    when(bridgeClient.sync(deploymentId)).thenReturn(ready(fixture, deploymentId));

    worker.runOnce();

    RouteDeploymentEntity stored = deploymentRepository.findById(deploymentId).orElseThrow();
    assertEquals(RouteDeploymentState.READY_FOR_ROBOT.name(), stored.getState());
    assertNotNull(stored.getRemoteSummaryJson());
  }

  @Test
  void mismatchedBridgeIdentityNeverBecomesReady() {
    Fixture fixture = fixture();
    String deploymentId = create(fixture);
    RobotBridgeDeploymentResult mismatch = new RobotBridgeDeploymentResult(deploymentId, "READY_FOR_ROBOT", "1.0", fixture.robotId(),
      fixture.revisionId(), "f".repeat(64), fixture.routeSha(), fixture.routeSha(), fixture.mapAssetId(), fixture.mapSha(), "floor.yaml", "floor.pgm");
    when(bridgeClient.sync(deploymentId)).thenReturn(mismatch);

    worker.runOnce();

    RouteDeploymentEntity stored = deploymentRepository.findById(deploymentId).orElseThrow();
    assertEquals(RouteDeploymentState.FAILED.name(), stored.getState());
    assertEquals("INVALID_BRIDGE_PAYLOAD", stored.getErrorCode());
  }

  @Test
  void timeoutAndServerFailureRemainUnknownButExplicitClientFailureIsFailed() {
    Fixture timeout = fixture();
    String timeoutId = create(timeout);
    when(bridgeClient.sync(timeoutId)).thenThrow(RobotBridgeDeploymentException.unknown("BRIDGE_HTTP_503", "Bridge 服务暂不可用"));
    worker.runOnce();
    RouteDeploymentEntity unknown = deploymentRepository.findById(timeoutId).orElseThrow();
    assertEquals(RouteDeploymentState.UNKNOWN.name(), unknown.getState());
    assertNotNull(unknown.getNextReconcileAt());

    Fixture rejected = fixture();
    String rejectedId = create(rejected);
    when(bridgeClient.sync(rejectedId)).thenThrow(RobotBridgeDeploymentException.failed("ROUTE_HASH_MISMATCH", "路线哈希不一致"));
    worker.runOnce();
    RouteDeploymentEntity failed = deploymentRepository.findById(rejectedId).orElseThrow();
    assertEquals(RouteDeploymentState.FAILED.name(), failed.getState());
    assertEquals("ROUTE_HASH_MISMATCH", failed.getErrorCode());
  }

  @Test
  void manualReconcileSchedulesAnExhaustedUnknownDeployment() {
    Fixture fixture = fixture();
    String deploymentId = create(fixture);
    RouteDeploymentEntity deployment = deploymentRepository.findById(deploymentId).orElseThrow();
    deployment.setState(RouteDeploymentState.UNKNOWN.name());
    deployment.setAttemptNo(5);
    deployment.setNextReconcileAt(null);
    deploymentRepository.save(deployment);

    Map<String, Object> response = deploymentService.reconcile(deploymentId);
    RouteDeploymentEntity reconciled = deploymentRepository.findById(deploymentId).orElseThrow();

    assertEquals(RouteDeploymentState.UNKNOWN.name(), response.get("state"));
    assertEquals(5, response.get("attemptCount"));
    assertNotNull(reconciled.getNextReconcileAt());
  }

  @Test
  void manualReconcileRejectsDeploymentThatIsAlreadyScheduled() {
    Fixture fixture = fixture();
    String deploymentId = create(fixture);
    RouteDeploymentEntity deployment = deploymentRepository.findById(deploymentId).orElseThrow();
    deployment.setState(RouteDeploymentState.UNKNOWN.name());
    deployment.setNextReconcileAt(Instant.now().toString());
    deploymentRepository.save(deployment);

    assertThrows(ApiException.class, () -> deploymentService.reconcile(deploymentId));
  }

  @Test
  void interruptedInstallRecoversThroughUnknownReconciliation() {
    Fixture fixture = fixture();
    String deploymentId = create(fixture);
    RouteDeploymentEntity installing = deploymentRepository.findById(deploymentId).orElseThrow();
    installing.setState(RouteDeploymentState.INSTALLING.name());
    installing.setAttemptNo(1);
    deploymentRepository.save(installing);

    lifecycle.recoverInterruptedInstalls(Instant.now());
    assertEquals(RouteDeploymentState.UNKNOWN.name(), deploymentRepository.findById(deploymentId).orElseThrow().getState());

    when(bridgeClient.sync(deploymentId)).thenReturn(ready(fixture, deploymentId));
    worker.runOnce();
    assertEquals(RouteDeploymentState.READY_FOR_ROBOT.name(), deploymentRepository.findById(deploymentId).orElseThrow().getState());
  }

  @Test
  void concurrentWorkersClaimOneDeploymentOnlyOnce() throws Exception {
    Fixture fixture = fixture();
    String deploymentId = create(fixture);
    CountDownLatch remoteEntered = new CountDownLatch(1);
    CountDownLatch allowRemoteReturn = new CountDownLatch(1);
    AtomicInteger calls = new AtomicInteger();
    when(bridgeClient.sync(deploymentId)).thenAnswer(invocation -> {
      calls.incrementAndGet();
      remoteEntered.countDown();
      allowRemoteReturn.await(3, TimeUnit.SECONDS);
      return ready(fixture, deploymentId);
    });

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      var first = executor.submit(worker::runOnce);
      org.junit.jupiter.api.Assertions.assertTrue(remoteEntered.await(3, TimeUnit.SECONDS));
      var second = executor.submit(worker::runOnce);
      second.get(3, TimeUnit.SECONDS);
      assertEquals(1, calls.get());
      allowRemoteReturn.countDown();
      first.get(3, TimeUnit.SECONDS);
    } finally {
      allowRemoteReturn.countDown();
      executor.shutdownNow();
    }
    assertEquals(RouteDeploymentState.READY_FOR_ROBOT.name(), deploymentRepository.findById(deploymentId).orElseThrow().getState());
  }

  private Fixture fixture() {
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String siteId = "site-deploy-" + suffix;
    String routeId = "route-deploy-" + suffix;
    String revisionId = "route-rev-deploy-" + suffix;
    String robotId = "robot-deploy-" + suffix;
    String routeSha = "a".repeat(64);
    String mapSha = "b".repeat(64);
    String mapAssetId = "map-deploy-" + suffix;
    dataStore.upsert(DataCategory.SITE, map("id", siteId, "name", "部署测试站点"));
    dataStore.upsert(DataCategory.ROUTE, map("id", routeId, "siteId", siteId, "name", "部署测试路线"));
    dataStore.upsert(DataCategory.ROBOT, map("id", robotId, "siteId", siteId, "name", "部署测试机器人", "serialNo", robotId, "status", "ONLINE"));
    RouteRevisionEntity revision = new RouteRevisionEntity();
    revision.setId(revisionId);
    revision.setRouteId(routeId);
    revision.setRevisionNo(1);
    revision.setExecutorJson("{}");
    revision.setContentSha256(routeSha);
    revision.setMapAssetId(mapAssetId);
    revision.setMapImageSha256(mapSha);
    revision.setValidationReportJson("{}");
    revision.setCreatedAt(Instant.now().toString());
    revisionRepository.save(revision);
    Instant now = Instant.now();
    heartbeatService.applyBridgeSnapshot(snapshot(robotId, now), now);
    return new Fixture(revisionId, robotId, routeSha, mapAssetId, mapSha);
  }

  private String create(Fixture fixture) {
    return String.valueOf(deploymentService.request(fixture.revisionId(), fixture.robotId(), "request-" + UUID.randomUUID()).get("id"));
  }

  private BridgeRobotSnapshot snapshot(String robotId, Instant at) {
    return new BridgeRobotSnapshot(robotId, at, "1.0", "boot-test", "idle", "test", 0, Map.of());
  }

  private RobotBridgeDeploymentResult ready(Fixture fixture, String deploymentId) {
    return new RobotBridgeDeploymentResult(deploymentId, "READY_FOR_ROBOT", "1.0", fixture.robotId(), fixture.revisionId(),
      fixture.routeSha(), fixture.routeSha(), fixture.routeSha(), fixture.mapAssetId(), fixture.mapSha(), "floor.yaml", "floor.pgm");
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index < values.length; index += 2) result.put(String.valueOf(values[index]), values[index + 1]);
    return result;
  }

  private record Fixture(String revisionId, String robotId, String routeSha, String mapAssetId, String mapSha) { }
}
