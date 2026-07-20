package com.powerinspection.route;

import com.powerinspection.common.ApiException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class RouteExecutorSupport {
  private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

  private RouteExecutorSupport() {
  }

  public static Map<String, Object> normalizeRoute(Map<String, Object> route) {
    Map<String, Object> executor = executor(route);
    if (executor == null) {
      return route;
    }
    validate(executor);
    route.put("executorJson", executor);
    route.put("rosRoute", executor);
    route.put("mapMode", "ros2d");
    List<Map<String, Object>> checkpoints = checkpointsFromExecutor(String.valueOf(route.get("id")), executor);
    route.put("checkpoints", checkpoints);
    route.put("path", pathFromCheckpoints(checkpoints));
    return route;
  }

  public static Map<String, Object> attachRosAlias(Map<String, Object> route) {
    route.putIfAbsent("path", List.of());
    route.putIfAbsent("routeDetections", List.of());
    route.putIfAbsent("checkpoints", List.of());
    route.putIfAbsent("mapMode", "2d");
    Map<String, Object> executor = executor(route);
    if (executor != null) {
      route.put("executorJson", executor);
      route.put("rosRoute", executor);
    }
    return route;
  }

  public static boolean hasExecutorTargets(Map<String, Object> route) {
    Map<String, Object> executor = executor(route);
    return executor != null && !orderedTargets(executor).isEmpty();
  }

  public static List<Map<String, Object>> compatibleCheckpoints(Map<String, Object> route) {
    Map<String, Object> executor = executor(route);
    if (executor != null) {
      List<Map<String, Object>> checkpoints = checkpointsFromExecutor(String.valueOf(route.get("id")), executor);
      if (!checkpoints.isEmpty()) {
        return checkpoints;
      }
    }
    Object raw = route.get("checkpoints");
    if (raw instanceof List<?>) {
      return castMapList((List<?>) raw);
    }
    return List.of();
  }

  public static List<Map<String, Object>> compatiblePath(Map<String, Object> route) {
    Map<String, Object> executor = executor(route);
    if (executor != null) {
      List<Map<String, Object>> checkpoints = checkpointsFromExecutor(String.valueOf(route.get("id")), executor);
      if (!checkpoints.isEmpty()) {
        return pathFromCheckpoints(checkpoints);
      }
    }
    Object raw = route.get("path");
    if (raw instanceof List<?>) {
      return castMapList((List<?>) raw);
    }
    return List.of();
  }

  public static int checkpointCount(Map<String, Object> route) {
    return compatibleCheckpoints(route).size();
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> executor(Map<String, Object> route) {
    Object raw = route.get("executorJson");
    if (raw == null) {
      raw = route.get("rosRoute");
    }
    if (raw == null) {
      return null;
    }
    if (raw instanceof Map<?, ?>) {
      return (Map<String, Object>) raw;
    }
    throw ApiException.badRequest("executorJson must be an object");
  }

  public static void validate(Map<String, Object> executor) {
    Integer version = number(executor.get("version"));
    if (version == null || (version != 2 && version != 3)) {
      throw ApiException.badRequest("executorJson.version must be 2 or 3");
    }
    if (!"map".equals(String.valueOf(executor.get("frame_id")))) {
      throw ApiException.badRequest("executorJson.frame_id must be map");
    }
    if (version == 3) {
      validateMapIdentity(executor.get("map"));
    }
    String activeRouteId = requiredText(executor.get("active_route_id"), "executorJson.active_route_id is required");
    Map<String, Object> startPose = requireMap(executor.get("start_pose"), "executorJson.start_pose must be an object");
    Map<String, Object> normalizedStartPose = requirePose(startPose.get("pose"), "executorJson.start_pose.pose");
    if (version == 3) {
      if (!"map".equals(String.valueOf(startPose.get("frame_id")))) {
        throw ApiException.badRequest("executorJson.start_pose.frame_id must be map");
      }
      requireMatchingLocation(normalizedStartPose, startPose.get("location"), "executorJson.start_pose");
    }
    if (startPose.containsKey("covariance")) {
      Map<String, Object> covariance = requireMap(startPose.get("covariance"), "executorJson.start_pose.covariance must be an object");
      requireFiniteNumber(covariance.get("x"), "executorJson.start_pose.covariance.x must be a number");
      requireFiniteNumber(covariance.get("y"), "executorJson.start_pose.covariance.y must be a number");
      requireFiniteNumber(covariance.get("yaw"), "executorJson.start_pose.covariance.yaw must be a number");
    }

    Object rawTargets = executor.get("targets");
    if (!(rawTargets instanceof List<?>)) {
      throw ApiException.badRequest("executorJson.targets must be a list");
    }
    List<Map<String, Object>> targets = castMapList((List<?>) rawTargets);
    Map<String, Map<String, Object>> targetById = new LinkedHashMap<>();
    for (int i = 0; i < targets.size(); i++) {
      Map<String, Object> target = targets.get(i);
      String id = requiredText(target.get("id"), "executorJson.targets[" + i + "].id is required");
      if (targetById.put(id, target) != null) {
        throw ApiException.badRequest("executorJson.targets contains duplicate id: " + id);
      }
      Map<String, Object> pose = requirePose(target.get("pose"), "executorJson.targets[" + i + "].pose");
      if (version == 3) {
        requireMatchingLocation(pose, target.get("location"), "executorJson.targets[" + i + "]");
      }
      if (target.containsKey("task_duration_sec")) {
        requireNonNegativeNumber(target.get("task_duration_sec"), "executorJson.targets[" + i + "].task_duration_sec must be a non-negative number");
      }
    }

    Object rawRoutes = executor.get("routes");
    if (!(rawRoutes instanceof List<?>) || ((List<?>) rawRoutes).isEmpty()) {
      throw ApiException.badRequest("executorJson.routes must contain one route");
    }
    boolean activeRouteFound = false;
    Set<String> routeIds = new HashSet<>();
    List<Map<String, Object>> routes = castMapList((List<?>) rawRoutes);
    for (int i = 0; i < routes.size(); i++) {
      Map<String, Object> route = routes.get(i);
      String id = requiredText(route.get("id"), "executorJson.routes[" + i + "].id is required");
      if (!routeIds.add(id)) {
        throw ApiException.badRequest("executorJson.routes contains duplicate id: " + id);
      }
      activeRouteFound = activeRouteFound || activeRouteId.equals(id);
      Object rawIds = route.get("target_ids");
      if (rawIds != null) {
        if (!(rawIds instanceof List<?>)) {
          throw ApiException.badRequest("executorJson.routes[" + i + "].target_ids must be a list");
        }
        for (Object rawId : (List<?>) rawIds) {
          String targetId = requiredText(rawId, "executorJson.routes[" + i + "].target_ids contains blank id");
          if (!targetById.containsKey(targetId)) {
            throw ApiException.badRequest("executorJson target_ids references missing target: " + targetId);
          }
        }
      }
      if (route.containsKey("goal_timeout_sec")) {
        requireNonNegativeNumber(route.get("goal_timeout_sec"), "executorJson.routes[" + i + "].goal_timeout_sec must be a non-negative number");
      }
      if (route.containsKey("max_retries_per_checkpoint")) {
        requireNonNegativeNumber(route.get("max_retries_per_checkpoint"), "executorJson.routes[" + i + "].max_retries_per_checkpoint must be a non-negative number");
      }
      Object failurePolicy = route.get("failure_policy");
      if (failurePolicy != null && !List.of("abort_and_return_home", "abort").contains(String.valueOf(failurePolicy))) {
        throw ApiException.badRequest("executorJson.routes[" + i + "].failure_policy is invalid");
      }
      Object loop = route.get("loop");
      if (loop != null) {
        Map<String, Object> loopMap = requireMap(loop, "executorJson.routes[" + i + "].loop must be an object");
        if (loopMap.containsKey("wait_sec")) {
          requireNonNegativeNumber(loopMap.get("wait_sec"), "executorJson.routes[" + i + "].loop.wait_sec must be a non-negative number");
        }
        if (loopMap.containsKey("max_cycles")) {
          requireNonNegativeNumber(loopMap.get("max_cycles"), "executorJson.routes[" + i + "].loop.max_cycles must be a non-negative number");
        }
      }
    }
    if (!activeRouteFound) {
      throw ApiException.badRequest("executorJson.active_route_id must reference a route id");
    }

    Object rawSchedules = executor.get("schedules");
    if (!(rawSchedules instanceof List<?>)) {
      throw ApiException.badRequest("executorJson.schedules must be a list");
    }
    if (version == 3) {
      Map<String, Object> map = requireMap(executor.get("map"), "executorJson.map must be an object");
      validateKeepoutZones(executor.get("keepout_zones"), requirePositiveNumber(map.get("resolution"), "executorJson.map.resolution must be a positive number"));
    } else if (executor.containsKey("keepout_zones")) {
      validateKeepoutZones(executor.get("keepout_zones"), null);
    }
  }

  public static List<Map<String, Object>> orderedTargets(Map<String, Object> executor) {
    Object rawTargets = executor.get("targets");
    if (!(rawTargets instanceof List<?>) || ((List<?>) rawTargets).isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> targets = castMapList((List<?>) rawTargets);
    Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
    for (Map<String, Object> target : targets) {
      byId.put(String.valueOf(target.get("id")), target);
    }

    Object rawRoutes = executor.get("routes");
    if (rawRoutes instanceof List<?>) {
      List<?> routeList = (List<?>) rawRoutes;
      if (!routeList.isEmpty() && routeList.get(0) instanceof Map<?, ?>) {
        Map<?, ?> routeDef = (Map<?, ?>) routeList.get(0);
        Object rawIds = routeDef.get("target_ids");
        if (rawIds instanceof List<?> && !((List<?>) rawIds).isEmpty()) {
          List<Map<String, Object>> ordered = new ArrayList<>();
          for (Object id : (List<?>) rawIds) {
            Map<String, Object> target = byId.get(String.valueOf(id));
            if (target == null) {
              throw ApiException.badRequest("executorJson target_ids references missing target: " + id);
            }
            ordered.add(target);
          }
          return ordered;
        }
      }
    }
    return targets;
  }

  private static List<Map<String, Object>> checkpointsFromExecutor(String routeId, Map<String, Object> executor) {
    List<Map<String, Object>> checkpoints = new ArrayList<>();
    List<Map<String, Object>> targets = orderedTargets(executor);
    for (int i = 0; i < targets.size(); i++) {
      Map<String, Object> target = targets.get(i);
      Map<String, Object> pose = mapCopy(target.get("pose"));
      Map<String, Object> checkpoint = new LinkedHashMap<>();
      checkpoint.put("id", target.getOrDefault("id", "target_" + (i + 1)));
      checkpoint.put("routeId", routeId);
      checkpoint.put("name", target.getOrDefault("name", checkpoint.get("id")));
      checkpoint.put("seq", i + 1);
      checkpoint.put("position", positionFromPose(pose));
      checkpoint.put("pan", 0);
      checkpoint.put("tilt", 0);
      checkpoint.put("dwellSeconds", integerOr(target.get("task_duration_sec"), 5));
      checkpoints.add(checkpoint);
    }
    return checkpoints;
  }

  private static List<Map<String, Object>> pathFromCheckpoints(List<Map<String, Object>> checkpoints) {
    List<Map<String, Object>> path = new ArrayList<>();
    for (Map<String, Object> checkpoint : checkpoints) {
      path.add(mapCopy(checkpoint.get("position")));
    }
    return path;
  }

  private static Map<String, Object> positionFromPose(Map<String, Object> pose) {
    Map<String, Object> position = new LinkedHashMap<>();
    double x = decimalOr(pose.get("x"), 0);
    double y = decimalOr(pose.get("y"), 0);
    position.put("lat", y);
    position.put("lng", x);
    position.put("x", x);
    position.put("y", y);
    return position;
  }

  private static Map<String, Object> requirePose(Object value, String field) {
    Map<String, Object> pose = requireMap(value, field + " must be an object");
    requireFiniteNumber(pose.get("x"), field + ".x must be a number");
    requireFiniteNumber(pose.get("y"), field + ".y must be a number");
    requireFiniteNumber(pose.get("yaw"), field + ".yaw must be a number");
    return pose;
  }

  private static void validateMapIdentity(Object value) {
    Map<String, Object> map = requireMap(value, "executorJson.map must be an object");
    requiredText(map.get("yaml"), "executorJson.map.yaml is required");
    requiredText(map.get("image"), "executorJson.map.image is required");
    requirePositiveNumber(map.get("resolution"), "executorJson.map.resolution must be a positive number");
    Object rawOrigin = map.get("origin");
    if (!(rawOrigin instanceof List<?> origin) || origin.size() != 3) {
      throw ApiException.badRequest("executorJson.map.origin must contain 3 numbers");
    }
    for (int i = 0; i < origin.size(); i++) {
      requireFiniteNumber(origin.get(i), "executorJson.map.origin[" + i + "] must be a number");
    }
    requirePositiveInteger(map.get("width"), "executorJson.map.width must be a positive integer");
    requirePositiveInteger(map.get("height"), "executorJson.map.height must be a positive integer");
    String hash = requiredText(map.get("image_sha256"), "executorJson.map.image_sha256 is required");
    if (!SHA256_PATTERN.matcher(hash).matches()) {
      throw ApiException.badRequest("executorJson.map.image_sha256 must be a lowercase SHA-256 hex string");
    }
  }

  private static void requireMatchingLocation(Map<String, Object> pose, Object value, String field) {
    Map<String, Object> location = requireMap(value, field + ".location must be an object");
    if (!"map_pose".equals(String.valueOf(location.get("type")))) {
      throw ApiException.badRequest(field + ".location.type must be map_pose");
    }
    if (!"map".equals(String.valueOf(location.get("frame_id")))) {
      throw ApiException.badRequest(field + ".location.frame_id must be map");
    }
    for (String axis : List.of("x", "y", "yaw")) {
      double locationValue = requireFiniteNumber(location.get(axis), field + ".location." + axis + " must be a number");
      double poseValue = requireFiniteNumber(pose.get(axis), field + ".pose." + axis + " must be a number");
      if (Math.abs(locationValue - poseValue) > 1e-6) {
        throw ApiException.badRequest(field + ".pose and location disagree on " + axis);
      }
    }
  }

  private static void validateKeepoutZones(Object value, Double maxMaskPadding) {
    if (!(value instanceof List<?> zones)) {
      throw ApiException.badRequest("executorJson.keepout_zones must be a list");
    }
    Set<String> zoneIds = new HashSet<>();
    for (int i = 0; i < zones.size(); i++) {
      Map<String, Object> zone = requireMap(zones.get(i), "executorJson.keepout_zones[" + i + "] must be an object");
      String id = requiredText(zone.get("id"), "executorJson.keepout_zones[" + i + "].id is required");
      if (!zoneIds.add(id)) {
        throw ApiException.badRequest("executorJson.keepout_zones contains duplicate id: " + id);
      }
      if (!"hard_keepout".equals(String.valueOf(zone.get("type")))) {
        throw ApiException.badRequest("executorJson.keepout_zones[" + i + "].type must be hard_keepout");
      }
      if (!(zone.get("enabled") instanceof Boolean)) {
        throw ApiException.badRequest("executorJson.keepout_zones[" + i + "].enabled must be a boolean");
      }
      Object rawPolygon = zone.get("polygon");
      if (!(rawPolygon instanceof List<?> polygon) || polygon.size() < 3) {
        throw ApiException.badRequest("executorJson.keepout_zones[" + i + "].polygon must contain at least 3 points");
      }
      List<Map<String, Object>> points = new ArrayList<>();
      for (int pointIndex = 0; pointIndex < polygon.size(); pointIndex++) {
        Map<String, Object> point = requireMap(polygon.get(pointIndex), "executorJson.keepout_zones[" + i + "].polygon[" + pointIndex + "] must be an object");
        requireFiniteNumber(point.get("x"), "executorJson.keepout_zones[" + i + "].polygon[" + pointIndex + "].x must be a number");
        requireFiniteNumber(point.get("y"), "executorJson.keepout_zones[" + i + "].polygon[" + pointIndex + "].y must be a number");
        points.add(point);
      }
      if (polygonSelfIntersects(points)) {
        throw ApiException.badRequest("executorJson.keepout_zones[" + i + "].polygon self-intersects");
      }
      if (Math.abs(polygonArea(points)) <= 1e-9) {
        throw ApiException.badRequest("executorJson.keepout_zones[" + i + "].polygon area must not be zero");
      }
      if (zone.containsKey("mask_padding_m")) {
        double padding = requireNonNegativeNumber(zone.get("mask_padding_m"), "executorJson.keepout_zones[" + i + "].mask_padding_m must be a non-negative number");
        if (maxMaskPadding != null && padding > maxMaskPadding + 1e-12) {
          throw ApiException.badRequest("executorJson.keepout_zones[" + i + "].mask_padding_m must not exceed executorJson.map.resolution");
        }
      }
    }
  }

  private static boolean polygonSelfIntersects(List<Map<String, Object>> points) {
    for (int i = 0; i < points.size(); i++) {
      for (int j = i + 1; j < points.size(); j++) {
        if (j == i || j == (i + 1) % points.size() || (i == 0 && j == points.size() - 1)) continue;
        if (segmentsIntersect(points.get(i), points.get((i + 1) % points.size()), points.get(j), points.get((j + 1) % points.size()))) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean segmentsIntersect(Map<String, Object> a, Map<String, Object> b, Map<String, Object> c, Map<String, Object> d) {
    double abC = orientation(a, b, c);
    double abD = orientation(a, b, d);
    double cdA = orientation(c, d, a);
    double cdB = orientation(c, d, b);
    if (abC * abD < -1e-9 && cdA * cdB < -1e-9) return true;
    return (Math.abs(abC) <= 1e-9 && onSegment(a, b, c))
      || (Math.abs(abD) <= 1e-9 && onSegment(a, b, d))
      || (Math.abs(cdA) <= 1e-9 && onSegment(c, d, a))
      || (Math.abs(cdB) <= 1e-9 && onSegment(c, d, b));
  }

  private static boolean onSegment(Map<String, Object> a, Map<String, Object> b, Map<String, Object> point) {
    double x = decimalOr(point.get("x"), 0);
    double y = decimalOr(point.get("y"), 0);
    return x >= Math.min(decimalOr(a.get("x"), 0), decimalOr(b.get("x"), 0)) - 1e-9
      && x <= Math.max(decimalOr(a.get("x"), 0), decimalOr(b.get("x"), 0)) + 1e-9
      && y >= Math.min(decimalOr(a.get("y"), 0), decimalOr(b.get("y"), 0)) - 1e-9
      && y <= Math.max(decimalOr(a.get("y"), 0), decimalOr(b.get("y"), 0)) + 1e-9;
  }

  private static double orientation(Map<String, Object> a, Map<String, Object> b, Map<String, Object> c) {
    return (decimalOr(b.get("x"), 0) - decimalOr(a.get("x"), 0))
      * (decimalOr(c.get("y"), 0) - decimalOr(a.get("y"), 0))
      - (decimalOr(b.get("y"), 0) - decimalOr(a.get("y"), 0))
      * (decimalOr(c.get("x"), 0) - decimalOr(a.get("x"), 0));
  }

  private static double polygonArea(List<Map<String, Object>> points) {
    double area = 0;
    for (int i = 0; i < points.size(); i++) {
      Map<String, Object> current = points.get(i);
      Map<String, Object> next = points.get((i + 1) % points.size());
      area += decimalOr(current.get("x"), 0) * decimalOr(next.get("y"), 0)
        - decimalOr(current.get("y"), 0) * decimalOr(next.get("x"), 0);
    }
    return area / 2;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> castMapList(List<?> list) {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?>)) {
        throw ApiException.badRequest("executorJson list entries must be objects");
      }
      result.add(new LinkedHashMap<>((Map<String, Object>) item));
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapCopy(Object value) {
    if (value instanceof Map<?, ?>) {
      return new LinkedHashMap<>((Map<String, Object>) value);
    }
    return new LinkedHashMap<>();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> requireMap(Object value, String message) {
    if (value instanceof Map<?, ?>) {
      return new LinkedHashMap<>((Map<String, Object>) value);
    }
    throw ApiException.badRequest(message);
  }

  private static String requiredText(Object value, String message) {
    if (value == null || value.toString().isBlank()) {
      throw ApiException.badRequest(message);
    }
    return value.toString();
  }

  private static Integer number(Object value) {
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    if (value == null) {
      return null;
    }
    try {
      return Integer.parseInt(value.toString());
    } catch (NumberFormatException ex) {
      return null;
    }
  }

  private static int integerOr(Object value, int fallback) {
    Integer n = number(value);
    return n == null ? fallback : n;
  }

  private static double decimalOr(Object value, double fallback) {
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    if (value == null) {
      return fallback;
    }
    try {
      return Double.parseDouble(value.toString());
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static double requireFiniteNumber(Object value, String message) {
    Double n = decimal(value);
    if (n == null || !Double.isFinite(n)) {
      throw ApiException.badRequest(message);
    }
    return n;
  }

  private static double requireNonNegativeNumber(Object value, String message) {
    double n = requireFiniteNumber(value, message);
    if (n < 0) {
      throw ApiException.badRequest(message);
    }
    return n;
  }

  private static double requirePositiveNumber(Object value, String message) {
    double n = requireFiniteNumber(value, message);
    if (n <= 0) {
      throw ApiException.badRequest(message);
    }
    return n;
  }

  private static void requirePositiveInteger(Object value, String message) {
    Integer n = number(value);
    if (n == null || n <= 0 || (value instanceof Number number && number.doubleValue() != n.doubleValue())) {
      throw ApiException.badRequest(message);
    }
  }

  private static Double decimal(Object value) {
    if (value instanceof Number) {
      return ((Number) value).doubleValue();
    }
    if (value == null) {
      return null;
    }
    try {
      return Double.parseDouble(value.toString());
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
