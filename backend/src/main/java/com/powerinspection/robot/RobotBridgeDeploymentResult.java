package com.powerinspection.robot;

import java.util.LinkedHashMap;
import java.util.Map;

public record RobotBridgeDeploymentResult(
  String deploymentId,
  String state,
  String schemaVersion,
  String robotId,
  String routeRevisionId,
  String routeRevisionContentSha256,
  String routePayloadSha256,
  String routeContentSha256,
  String mapAssetId,
  String mapImageSha256,
  String yamlName,
  String pgmName
) {
  public Map<String, Object> auditSummary() {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("state", state);
    summary.put("schemaVersion", schemaVersion);
    summary.put("deploymentId", deploymentId);
    summary.put("robotId", robotId);
    summary.put("routeRevisionId", routeRevisionId);
    summary.put("routeRevisionContentSha256", routeRevisionContentSha256);
    summary.put("routePayloadSha256", routePayloadSha256);
    summary.put("routeContentSha256", routeContentSha256);
    summary.put("mapAssetId", mapAssetId);
    summary.put("mapImageSha256", mapImageSha256);
    summary.put("yamlName", yamlName);
    summary.put("pgmName", pgmName);
    return summary;
  }
}
