package com.powerinspection.route;

import com.powerinspection.common.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RouteExecutorSupport {
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
    if (!Integer.valueOf(2).equals(number(executor.get("version")))) {
      throw ApiException.badRequest("executorJson.version must be 2");
    }
    if (!"map".equals(String.valueOf(executor.get("frame_id")))) {
      throw ApiException.badRequest("executorJson.frame_id must be map");
    }
    String activeRouteId = requiredText(executor.get("active_route_id"), "executorJson.active_route_id is required");
    Map<String, Object> startPose = requireMap(executor.get("start_pose"), "executorJson.start_pose must be an object");
    requirePose(startPose.get("pose"), "executorJson.start_pose.pose");
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
      requirePose(target.get("pose"), "executorJson.targets[" + i + "].pose");
      if (target.containsKey("task_duration_sec")) {
        requireNonNegativeNumber(target.get("task_duration_sec"), "executorJson.targets[" + i + "].task_duration_sec must be a non-negative number");
      }
    }

    Object rawRoutes = executor.get("routes");
    if (!(rawRoutes instanceof List<?>) || ((List<?>) rawRoutes).isEmpty()) {
      throw ApiException.badRequest("executorJson.routes must contain one route");
    }
    boolean activeRouteFound = false;
    List<Map<String, Object>> routes = castMapList((List<?>) rawRoutes);
    for (int i = 0; i < routes.size(); i++) {
      Map<String, Object> route = routes.get(i);
      String id = requiredText(route.get("id"), "executorJson.routes[" + i + "].id is required");
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

  private static void requirePose(Object value, String field) {
    Map<String, Object> pose = requireMap(value, field + " must be an object");
    requireFiniteNumber(pose.get("x"), field + ".x must be a number");
    requireFiniteNumber(pose.get("y"), field + ".y must be a number");
    requireFiniteNumber(pose.get("yaw"), field + ".yaw must be a number");
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
