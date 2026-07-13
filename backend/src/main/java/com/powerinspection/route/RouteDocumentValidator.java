package com.powerinspection.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 发布前的 v3 JSON 契约校验器；只给出结构和几何基础结论，不替代 Nav2 碰撞验证。 */
@Component
public class RouteDocumentValidator {
  private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

  public List<ValidationIssue> validate(JsonNode document) {
    List<ValidationIssue> issues = new ArrayList<>();
    if (!document.isObject()) { error(issues, "ROUTE_NOT_OBJECT", "", "路线文档必须是对象"); return issues; }
    if (document.path("version").asInt(-1) != 3) error(issues, "INVALID_VERSION", "/version", "version 必须为 3");
    if (!"map".equals(document.path("frame_id").asText())) error(issues, "INVALID_FRAME_ID", "/frame_id", "frame_id 必须为 map");
    validateMap(document.path("map"), issues);
    ArrayNode routes = array(document.path("routes"));
    if (routes == null || routes.size() != 1) error(issues, "MULTIPLE_ROUTES", "/routes", "平台路线必须且只能包含一条 route");
    ArrayNode schedules = array(document.path("schedules"));
    if (schedules == null || !schedules.isEmpty()) error(issues, "NON_EMPTY_SCHEDULES", "/schedules", "schedules 必须为空数组");
    JsonNode route = routes == null || routes.isEmpty() ? null : routes.get(0);
    if (route == null || !document.path("active_route_id").asText().equals(route.path("id").asText())) error(issues, "INVALID_ACTIVE_ROUTE", "/active_route_id", "active_route_id 必须指向唯一 route");
    validatePoseLocation(document.path("start_pose"), "/start_pose", issues);
    ArrayNode targets = array(document.path("targets"));
    Set<String> ids = new HashSet<>();
    if (targets == null) error(issues, "INVALID_TARGETS", "/targets", "targets 必须为数组");
    else for (int i = 0; i < targets.size(); i++) {
      JsonNode target = targets.get(i); String id = target.path("id").asText();
      if (id.isBlank() || !ids.add(id)) error(issues, "DUPLICATE_TARGET_ID", "/targets/" + i + "/id", "目标 id 必须非空且唯一");
      validatePoseLocation(target, "/targets/" + i, issues);
    }
    validateRoute(route, ids, targets, issues);
    ArrayNode zones = array(document.path("keepout_zones"));
    if (zones == null) error(issues, "INVALID_KEEP_OUTS", "/keepout_zones", "keepout_zones 必须为数组");
    else {
      Set<String> zoneIds = new HashSet<>();
      for (int i = 0; i < zones.size(); i++) {
        String id = zones.get(i).path("id").asText();
        if (id.isBlank() || !zoneIds.add(id)) error(issues, "DUPLICATE_KEEP_OUT_ID", "/keepout_zones/" + i + "/id", "禁行区 id 必须非空且唯一");
        validateZone(zones.get(i), i, document.path("map").path("resolution").asDouble(Double.NaN), issues);
      }
    }
    return issues;
  }

  private void validateMap(JsonNode map, List<ValidationIssue> issues) {
    if (!map.isObject()) { error(issues, "INVALID_MAP", "/map", "map 必须为对象"); return; }
    if (map.path("yaml").asText().isBlank() || map.path("image").asText().isBlank()) error(issues, "INVALID_MAP", "/map", "map 必须包含 yaml 和 image");
    if (!finitePositive(map.path("resolution"))) error(issues, "INVALID_MAP_RESOLUTION", "/map/resolution", "resolution 必须为有限正数");
    JsonNode origin = map.path("origin");
    boolean validOrigin = origin.isArray() && origin.size() == 3;
    if (validOrigin) for (JsonNode value : origin) validOrigin = validOrigin && finite(value);
    if (!validOrigin) error(issues, "INVALID_MAP_ORIGIN", "/map/origin", "origin 必须为三个有限数");
    for (String key : List.of("width", "height")) if (!map.path(key).canConvertToInt() || map.path(key).asInt() <= 0) error(issues, "INVALID_MAP_SIZE", "/map/" + key, key + " 必须为正整数");
    if (!SHA256.matcher(map.path("image_sha256").asText()).matches()) error(issues, "INVALID_MAP_SHA256", "/map/image_sha256", "image_sha256 必须为 64 位小写十六进制");
  }

  private void validatePoseLocation(JsonNode node, String pointer, List<ValidationIssue> issues) {
    JsonNode pose = node.path("pose"), location = node.path("location");
    for (String key : List.of("x", "y", "yaw")) {
      if (!finite(pose.path(key))) error(issues, "INVALID_POSE", pointer + "/pose/" + key, "pose 坐标必须为有限数");
      if (!finite(location.path(key)) || (finite(pose.path(key)) && Math.abs(pose.path(key).asDouble() - location.path(key).asDouble()) > 1e-6)) error(issues, "INVALID_LOCATION", pointer + "/location/" + key, "location 必须与 pose 一致");
    }
    if (!"map_pose".equals(location.path("type").asText())) error(issues, "INVALID_LOCATION_TYPE", pointer + "/location/type", "location.type 必须为 map_pose");
    if (!"map".equals(location.path("frame_id").asText())) error(issues, "INVALID_LOCATION_FRAME", pointer + "/location/frame_id", "location.frame_id 必须为 map");
  }

  private void validateRoute(JsonNode route, Set<String> targetIds, ArrayNode targets, List<ValidationIssue> issues) {
    if (route == null) return;
    ArrayNode ids = array(route.path("target_ids"));
    if (ids == null) { error(issues, "INVALID_TARGET_ORDER", "/routes/0/target_ids", "target_ids 必须为数组"); return; }
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < ids.size(); i++) {
      String id = ids.get(i).asText();
      if (!targetIds.contains(id)) error(issues, "UNKNOWN_TARGET_REFERENCE", "/routes/0/target_ids/" + i, "引用了不存在的目标");
      if (!seen.add(id)) error(issues, "DUPLICATE_TARGET_REFERENCE", "/routes/0/target_ids/" + i, "target_ids 不得重复");
      if (targets != null && (i >= targets.size() || !id.equals(targets.get(i).path("id").asText()))) error(issues, "TARGET_ORDER_MISMATCH", "/routes/0/target_ids", "target_ids 必须与目标数组顺序一致");
    }
    if (targets != null && ids.size() != targets.size()) error(issues, "TARGET_ORDER_MISMATCH", "/routes/0/target_ids", "target_ids 必须与目标数组顺序一致");
    if (!finitePositive(route.path("goal_timeout_sec"))) error(issues, "INVALID_GOAL_TIMEOUT", "/routes/0/goal_timeout_sec", "goal_timeout_sec 必须为正数");
    if (!nonNegativeInteger(route.path("max_retries_per_checkpoint"))) error(issues, "INVALID_RETRIES", "/routes/0/max_retries_per_checkpoint", "max_retries_per_checkpoint 必须为非负整数");
    JsonNode loop = route.path("loop");
    if (!finite(loop.path("wait_sec")) || loop.path("wait_sec").asDouble() < 0) error(issues, "INVALID_LOOP_WAIT", "/routes/0/loop/wait_sec", "loop.wait_sec 必须为非负数");
    if (!nonNegativeInteger(loop.path("max_cycles"))) error(issues, "INVALID_LOOP_CYCLES", "/routes/0/loop/max_cycles", "loop.max_cycles 必须为非负整数");
    if (!List.of("abort", "abort_and_return_home").contains(route.path("failure_policy").asText())) error(issues, "INVALID_FAILURE_POLICY", "/routes/0/failure_policy", "failure_policy 不合法");
  }

  private void validateZone(JsonNode zone, int index, double resolution, List<ValidationIssue> issues) {
    String base = "/keepout_zones/" + index; ArrayNode points = array(zone.path("polygon"));
    if (zone.path("id").asText().isBlank()) error(issues, "INVALID_KEEP_OUT_ID", base + "/id", "禁行区 id 不得为空");
    if (!"hard_keepout".equals(zone.path("type").asText())) error(issues, "INVALID_KEEP_OUT_TYPE", base + "/type", "type 必须为 hard_keepout");
    if (!zone.path("enabled").isBoolean()) error(issues, "INVALID_KEEP_OUT_ENABLED", base + "/enabled", "enabled 必须为布尔值");
    if (points == null || points.size() < 3) error(issues, "INVALID_POLYGON", base + "/polygon", "polygon 至少包含三个点");
    else {
      for (int i = 0; i < points.size(); i++) if (!finite(points.get(i).path("x")) || !finite(points.get(i).path("y"))) error(issues, "INVALID_POLYGON_POINT", base + "/polygon/" + i, "坐标必须为有限数");
      if (selfIntersects(points)) error(issues, "SELF_INTERSECTING_POLYGON", base + "/polygon", "polygon 不得自交");
      if (Math.abs(area(points)) <= 1e-9) error(issues, "ZERO_AREA_POLYGON", base + "/polygon", "polygon 面积必须大于零");
    }
    double padding = zone.path("mask_padding_m").asDouble(Double.NaN); if (!Double.isFinite(padding) || padding < 0 || padding > resolution) error(issues, "INVALID_MASK_PADDING", base + "/mask_padding_m", "mask_padding_m 必须在 0 到 map.resolution 之间");
  }

  private ArrayNode array(JsonNode node) { return node instanceof ArrayNode result ? result : null; }
  private boolean finite(JsonNode node) { return node.isNumber() && Double.isFinite(node.asDouble()); }
  private boolean finitePositive(JsonNode node) { return finite(node) && node.asDouble() > 0; }
  private boolean nonNegativeInteger(JsonNode node) { return node.isIntegralNumber() && node.asLong() >= 0; }
  private boolean selfIntersects(ArrayNode points) {
    for (int i = 0; i < points.size(); i++) for (int j = i + 1; j < points.size(); j++) {
      if (j == i + 1 || (i == 0 && j == points.size() - 1)) continue;
      if (intersects(points.get(i), points.get((i + 1) % points.size()), points.get(j), points.get((j + 1) % points.size()))) return true;
    }
    return false;
  }
  private boolean intersects(JsonNode a, JsonNode b, JsonNode c, JsonNode d) {
    double abC = cross(a, b, c), abD = cross(a, b, d), cdA = cross(c, d, a), cdB = cross(c, d, b);
    return abC * abD < -1e-9 && cdA * cdB < -1e-9;
  }
  private double cross(JsonNode a, JsonNode b, JsonNode c) { return (b.path("x").asDouble() - a.path("x").asDouble()) * (c.path("y").asDouble() - a.path("y").asDouble()) - (b.path("y").asDouble() - a.path("y").asDouble()) * (c.path("x").asDouble() - a.path("x").asDouble()); }
  private double area(ArrayNode points) { double area = 0; for (int i = 0; i < points.size(); i++) { JsonNode a = points.get(i), b = points.get((i + 1) % points.size()); area += a.path("x").asDouble() * b.path("y").asDouble() - a.path("y").asDouble() * b.path("x").asDouble(); } return area / 2; }
  private void error(List<ValidationIssue> issues, String code, String pointer, String message) { issues.add(new ValidationIssue(code, pointer, message, "ERROR")); }
  public record ValidationIssue(String code, String jsonPointer, String message, String severity) { }
}
