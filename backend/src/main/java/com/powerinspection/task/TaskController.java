package com.powerinspection.task;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
  private final TaskService taskService;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public TaskController(TaskService taskService, PermissionService permissionService, CurrentUser currentUser) {
    this.taskService = taskService;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> tasks() {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    return ApiResponse.ok(taskService.tasks());
  }

  @GetMapping("/active")
  public ApiResponse<Map<String, Object>> active() {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    return ApiResponse.ok(taskService.activeTask());
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> task(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    return ApiResponse.ok(taskService.task(id));
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> createTask(@RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.TASK_CREATE);
    return ApiResponse.ok(taskService.createTask(body));
  }

  @PatchMapping("/{id}")
  public ApiResponse<Map<String, Object>> updateTask(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.TASK_CREATE);
    return ApiResponse.ok(taskService.updateTask(id, body));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> deleteTask(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.TASK_CREATE);
    taskService.deleteTask(id);
    return ApiResponse.ok();
  }

  @PostMapping("/{id}/dispatch")
  public ApiResponse<Map<String, Object>> dispatch(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.TASK_DISPATCH);
    return ApiResponse.ok(taskService.dispatch(id));
  }

  @PostMapping("/{id}/pause")
  public ApiResponse<Map<String, Object>> pause(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.TASK_CONTROL);
    return ApiResponse.ok(taskService.pause(id));
  }

  @PostMapping("/{id}/resume")
  public ApiResponse<Map<String, Object>> resume(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.TASK_CONTROL);
    return ApiResponse.ok(taskService.resume(id));
  }

  @PostMapping("/{id}/takeover")
  public ApiResponse<Map<String, Object>> takeover(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.TASK_CONTROL);
    return ApiResponse.ok(taskService.takeover(id));
  }

  @PostMapping("/{id}/cancel")
  public ApiResponse<Map<String, Object>> cancel(@PathVariable String id) {
    permissionService.requireAny(currentUser.get(), Permission.TASK_CONTROL, Permission.TASK_ESTOP);
    return ApiResponse.ok(taskService.cancel(id));
  }

  @GetMapping("/{id}/events")
  public ApiResponse<List<Map<String, Object>>> events(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    return ApiResponse.ok(taskService.events(id));
  }
}
