package com.powerinspection.robot;

import com.powerinspection.business.CrudSupport;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.util.List;
import java.util.Map;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/robots")
public class RobotController extends CrudSupport {
  private final PermissionService permissionService;
  private final CurrentUser currentUser;
  private final SimpMessagingTemplate messagingTemplate;
  private final RobotProperties robotProperties;

  public RobotController(DataStoreService dataStore, PermissionService permissionService, CurrentUser currentUser, SimpMessagingTemplate messagingTemplate, RobotProperties robotProperties) {
    super(dataStore);
    this.permissionService = permissionService;
    this.currentUser = currentUser;
    this.messagingTemplate = messagingTemplate;
    this.robotProperties = robotProperties;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> listRobots() {
    return ApiResponse.ok(list(DataCategory.ROBOT));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> robot(@PathVariable String id) {
    return ApiResponse.ok(dataStore.get(DataCategory.ROBOT, id));
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> createRobot(@RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.ROBOT_MANAGE);
    if (!robotProperties.isAllowRegistration()) {
      throw ApiException.badRequest("当前为单机器人实机模式，不支持注册新机器人");
    }
    validateSite(body.get("siteId"));
    body.putIfAbsent("status", "OFFLINE");
    Map<String, Object> robot = create(DataCategory.ROBOT, "robot", body);
    publishRobot(robot);
    return ApiResponse.ok(robot);
  }

  @PatchMapping("/{id}")
  public ApiResponse<Map<String, Object>> updateRobot(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.ROBOT_MANAGE);
    if (body.containsKey("siteId")) {
      validateSite(body.get("siteId"));
    }
    Map<String, Object> robot = update(DataCategory.ROBOT, id, body);
    publishRobot(robot);
    return ApiResponse.ok(robot);
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> deleteRobot(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.ROBOT_MANAGE);
    if (!robotProperties.isAllowRegistration()) {
      throw ApiException.badRequest("当前为单机器人实机模式，不支持删除机器人");
    }
    ensureRobotNotBusy(id);
    delete(DataCategory.ROBOT, id);
    return ApiResponse.ok();
  }

  @GetMapping("/{id}/telemetry")
  public ApiResponse<Map<String, Object>> telemetry(@PathVariable String id) {
    return ApiResponse.ok(dataStore.get(DataCategory.ROBOT, id));
  }

  private void validateSite(Object siteId) {
    if (siteId == null || String.valueOf(siteId).isBlank()) {
      return;
    }
    if (dataStore.find(DataCategory.SITE, String.valueOf(siteId)) == null) {
      throw ApiException.badRequest("站点不存在");
    }
  }

  private void ensureRobotNotBusy(String robotId) {
    boolean active = dataStore.list(DataCategory.TASK).stream()
      .anyMatch(task -> robotId.equals(String.valueOf(task.get("robotId"))) && isActiveStatus(String.valueOf(task.get("status"))));
    if (active) {
      throw ApiException.badRequest("机器人正在执行任务，不能删除");
    }
  }

  private boolean isActiveStatus(String status) {
    return List.of("DISPATCHED", "RUNNING", "PAUSED", "MANUAL_TAKEOVER").contains(status);
  }

  private void publishRobot(Map<String, Object> robot) {
    messagingTemplate.convertAndSend("/topic/robots/" + robot.get("id"), robot);
    messagingTemplate.convertAndSend("/topic/robots", robot);
  }
}
