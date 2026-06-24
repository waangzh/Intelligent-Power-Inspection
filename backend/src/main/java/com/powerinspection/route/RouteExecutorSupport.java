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
    Object rawRoutes = executor.get("routes");
    if (!(rawRoutes instanceof List<?>) || ((List<?>) rawRoutes).isEmpty()) {
      throw ApiException.badRequest("executorJson.routes must contain one route");
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

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> castMapList(List<?> list) {
    return new ArrayList<>((List<Map<String, Object>>) list);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> mapCopy(Object value) {
    if (value instanceof Map<?, ?>) {
      return new LinkedHashMap<>((Map<String, Object>) value);
    }
    return new LinkedHashMap<>();
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
}