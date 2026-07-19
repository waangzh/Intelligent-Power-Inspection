package com.powerinspection.agent.api;

import com.powerinspection.agent.AuditedAgentService;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/agent-actions")
public class AgentActionController {
  private final AuditedAgentService agentService;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public AgentActionController(
      AuditedAgentService agentService,
      PermissionService permissionService,
      CurrentUser currentUser) {
    this.agentService = agentService;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @GetMapping("/{actionId}")
  public ApiResponse<AgentDtos.ActionResponse> action(@PathVariable String actionId) {
    permissionService.require(currentUser.get(), Permission.AGENT_VIEW);
    return ApiResponse.ok(agentService.action(actionId));
  }

  @PostMapping("/{actionId}/approve")
  public ApiResponse<AgentDtos.ActionResponse> approve(
      @PathVariable String actionId, @Valid @RequestBody AgentDtos.ActionDecisionRequest request) {
    permissionService.require(currentUser.get(), Permission.AGENT_APPROVE);
    return ApiResponse.ok(agentService.approveAction(actionId, request, currentUser.get()));
  }

  @PostMapping("/{actionId}/reject")
  public ApiResponse<AgentDtos.ActionResponse> reject(
      @PathVariable String actionId, @Valid @RequestBody AgentDtos.ActionDecisionRequest request) {
    permissionService.require(currentUser.get(), Permission.AGENT_APPROVE);
    return ApiResponse.ok(agentService.rejectAction(actionId, request, currentUser.get()));
  }

  @PostMapping("/{actionId}/retry")
  public ApiResponse<AgentDtos.ActionResponse> retry(
      @PathVariable String actionId, @Valid @RequestBody AgentDtos.ActionDecisionRequest request) {
    permissionService.require(currentUser.get(), Permission.AGENT_APPROVE);
    return ApiResponse.ok(agentService.retryAction(actionId, request, currentUser.get()));
  }
}
