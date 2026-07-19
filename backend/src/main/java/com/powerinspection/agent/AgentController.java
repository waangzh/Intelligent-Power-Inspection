package com.powerinspection.agent;

import com.powerinspection.common.ApiResponse;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agents")
public class AgentController {
  private final AuditedAgentService agentService;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public AgentController(AuditedAgentService agentService, PermissionService permissionService, CurrentUser currentUser) {
    this.agentService = agentService;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @PostMapping("/sessions")
  public ApiResponse<Map<String, Object>> createSession(@RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.AGENT_RUN);
    return ApiResponse.ok(agentService.createLegacySession(body, currentUser.get()));
  }

  @GetMapping("/sessions")
  public ApiResponse<List<Map<String, Object>>> sessions() {
    permissionService.require(currentUser.get(), Permission.AGENT_VIEW);
    return ApiResponse.ok(agentService.legacySessions());
  }

  @GetMapping("/sessions/{id}")
  public ApiResponse<Map<String, Object>> session(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.AGENT_VIEW);
    return ApiResponse.ok(agentService.legacyDetail(id));
  }

  @PostMapping("/sessions/{id}/runs")
  public ApiResponse<Map<String, Object>> rerun(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.AGENT_RUN);
    return ApiResponse.ok(agentService.legacyRerun(id, currentUser.get()));
  }

  @PostMapping("/actions/{id}/confirm")
  public ApiResponse<Map<String, Object>> confirmAction(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.AGENT_APPROVE);
    return ApiResponse.ok(agentService.confirmLegacyAction(id, currentUser.get()));
  }

  @PostMapping("/actions/{id}/reject")
  public ApiResponse<Map<String, Object>> rejectAction(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.AGENT_APPROVE);
    return ApiResponse.ok(agentService.rejectLegacyAction(id, currentUser.get()));
  }
}
