package com.powerinspection.task;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
  private final TaskService taskService;
  private final TaskExecutionLifecycleService executionLifecycleService;
  private final TaskExecutionControlService executionControlService;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public TaskController(TaskService taskService, TaskExecutionLifecycleService executionLifecycleService, TaskExecutionControlService executionControlService,
      PermissionService permissionService, CurrentUser currentUser) {
    this.taskService = taskService;
    this.executionLifecycleService = executionLifecycleService;
    this.executionControlService = executionControlService;
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

  @GetMapping("/{id}/execution")
  public ApiResponse<Map<String, Object>> execution(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    return ApiResponse.ok(executionLifecycleService.detail(id));
  }

  @GetMapping("/{id}/start-eligibility")
  public ApiResponse<Map<String, Object>> startEligibility(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    return ApiResponse.ok(executionLifecycleService.eligibility(id));
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

  @PostMapping("/{id}/start")
  public ResponseEntity<ApiResponse<Map<String, Object>>> start(@PathVariable String id,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    permissionService.require(currentUser.get(), Permission.TASK_DISPATCH);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(executionLifecycleService.start(id, idempotencyKey)));
  }

  @PostMapping("/{id}/pause")
  public ResponseEntity<ApiResponse<Map<String, Object>>> pause(@PathVariable String id,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    permissionService.require(currentUser.get(), Permission.TASK_CONTROL);
    if (!executionLifecycleService.hasExecution(id)) return ResponseEntity.ok(ApiResponse.ok(taskService.pause(id)));
    return accepted(executionControlService.request(id, TaskExecutionControlAction.PAUSE, idempotencyKey, null, currentUser.get()));
  }

  @PostMapping("/{id}/resume")
  public ResponseEntity<ApiResponse<Map<String, Object>>> resume(@PathVariable String id,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    permissionService.require(currentUser.get(), Permission.TASK_CONTROL);
    if (!executionLifecycleService.hasExecution(id)) return ResponseEntity.ok(ApiResponse.ok(taskService.resume(id)));
    return accepted(executionControlService.request(id, TaskExecutionControlAction.RESUME, idempotencyKey, null, currentUser.get()));
  }

  @PostMapping("/{id}/takeover")
  public ResponseEntity<ApiResponse<Map<String, Object>>> takeover(@PathVariable String id,
      @RequestHeader("Idempotency-Key") String idempotencyKey, @RequestBody(required = false) Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.TASK_TAKEOVER);
    if (!executionLifecycleService.hasExecution(id)) return ResponseEntity.ok(ApiResponse.ok(taskService.takeover(id)));
    String reason = body == null || body.get("reason") == null ? null : String.valueOf(body.get("reason"));
    return accepted(executionControlService.request(id, TaskExecutionControlAction.TAKEOVER, idempotencyKey, reason, currentUser.get()));
  }

  @PostMapping("/{id}/cancel")
  public ResponseEntity<ApiResponse<Map<String, Object>>> cancel(@PathVariable String id,
      @RequestHeader("Idempotency-Key") String idempotencyKey) {
    permissionService.require(currentUser.get(), Permission.TASK_CONTROL);
    if (!executionLifecycleService.hasExecution(id)) return ResponseEntity.ok(ApiResponse.ok(taskService.cancel(id)));
    return accepted(executionControlService.request(id, TaskExecutionControlAction.CANCEL, idempotencyKey, null, currentUser.get()));
  }

  @GetMapping("/{id}/events")
  public ApiResponse<List<Map<String, Object>>> events(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.TASK_VIEW);
    return ApiResponse.ok(taskService.events(id));
  }

  private ResponseEntity<ApiResponse<Map<String, Object>>> accepted(Map<String, Object> result) {
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(result));
  }
}
