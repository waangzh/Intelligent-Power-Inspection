package com.powerinspection.route;

import com.powerinspection.robot.RobotBridgeDeploymentClient;
import com.powerinspection.robot.RobotBridgeDeploymentException;
import com.powerinspection.robot.RobotBridgeDeploymentResult;
import java.time.Instant;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 领取本地作业后才同步 Bridge；每次 UNKNOWN 都以同一 deploymentId 进行幂等对账。 */
@Component
@ConditionalOnProperty(prefix = "app.robot", name = "mode", havingValue = "bridge")
public class RouteDeploymentWorker {
  private final RouteDeploymentLifecycleService lifecycle;
  private final RouteDeploymentRepository repository;
  private final RouteRevisionService routeRevisionService;
  private final RobotBridgeDeploymentClient bridgeClient;

  public RouteDeploymentWorker(RouteDeploymentLifecycleService lifecycle, RouteDeploymentRepository repository,
      RouteRevisionService routeRevisionService, RobotBridgeDeploymentClient bridgeClient) {
    this.lifecycle = lifecycle;
    this.repository = repository;
    this.routeRevisionService = routeRevisionService;
    this.bridgeClient = bridgeClient;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recoverAfterRestart() {
    lifecycle.recoverInterruptedInstalls(Instant.now());
    runOnce();
  }

  @Scheduled(fixedDelayString = "${app.route-deployment.worker-delay-ms:3000}", initialDelayString = "${app.route-deployment.worker-initial-delay-ms:5000}")
  public void scheduledRun() {
    runOnce();
  }

  public void runOnce() {
    Instant now = Instant.now();
    for (String deploymentId : lifecycle.eligibleIds(now)) {
      RouteDeploymentEntity deployment = lifecycle.claim(deploymentId, now);
      if (deployment != null) synchronize(deployment);
    }
  }

  private void synchronize(RouteDeploymentEntity deployment) {
    Instant finishedAt = Instant.now();
    try {
      RobotBridgeDeploymentResult result = bridgeClient.sync(deployment.getId());
      RouteRevisionEntity revision = routeRevisionService.require(deployment.getRouteRevisionId());
      if (!matches(deployment, revision, result)) {
        lifecycle.failed(deployment.getId(), "INVALID_BRIDGE_PAYLOAD", "Bridge 返回的部署身份或哈希与本地不可变路线修订不一致", finishedAt);
        return;
      }
      lifecycle.ready(deployment.getId(), result, finishedAt);
    } catch (RobotBridgeDeploymentException ex) {
      if (ex.getDisposition() == RobotBridgeDeploymentException.Disposition.FAILED) {
        lifecycle.failed(deployment.getId(), ex.getErrorCode(), ex.getMessage(), finishedAt);
      } else {
        lifecycle.unknown(deployment.getId(), ex.getErrorCode(), ex.getMessage(), finishedAt);
      }
    } catch (RuntimeException ex) {
      lifecycle.unknown(deployment.getId(), "BRIDGE_SYNC_EXCEPTION", "Bridge 同步过程异常，部署结果待对账", finishedAt);
    }
  }

  private boolean matches(RouteDeploymentEntity deployment, RouteRevisionEntity revision, RobotBridgeDeploymentResult result) {
    return RouteDeploymentState.READY_FOR_ROBOT.name().equals(result.state())
      && "1.0".equals(result.schemaVersion())
      && Objects.equals(deployment.getId(), result.deploymentId())
      && Objects.equals(deployment.getRobotId(), result.robotId())
      && Objects.equals(revision.getId(), result.routeRevisionId())
      && Objects.equals(revision.getContentSha256(), result.routeRevisionContentSha256())
      && Objects.equals(revision.getContentSha256(), result.routePayloadSha256())
      && Objects.equals(revision.getContentSha256(), result.routeContentSha256())
      && Objects.equals(revision.getMapAssetId(), result.mapAssetId())
      && Objects.equals(revision.getMapImageSha256(), result.mapImageSha256());
  }
}
