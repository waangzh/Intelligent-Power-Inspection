package com.powerinspection.agent.api;

import com.powerinspection.agent.AuditedAgentService;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AgentCaseController {
  private final AuditedAgentService agentService;
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public AgentCaseController(AuditedAgentService agentService, PermissionService permissionService, CurrentUser currentUser) {
    this.agentService = agentService;
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @PostMapping("/agent-cases")
  public ApiResponse<AgentDtos.CaseSummary> create(@Valid @RequestBody AgentDtos.CreateCaseRequest request) {
    permissionService.require(currentUser.get(), Permission.AGENT_RUN);
    return ApiResponse.ok(agentService.createCase(request, currentUser.get()));
  }

  @GetMapping("/agent-cases")
  public ApiResponse<List<AgentDtos.CaseSummary>> list() {
    permissionService.require(currentUser.get(), Permission.AGENT_VIEW);
    return ApiResponse.ok(agentService.cases());
  }

  @GetMapping("/agent-cases/{caseId}")
  public ApiResponse<AgentDtos.CaseDetail> detail(@PathVariable String caseId) {
    permissionService.require(currentUser.get(), Permission.AGENT_VIEW);
    return ApiResponse.ok(agentService.caseDetail(caseId));
  }

  @PostMapping("/agent-cases/{caseId}/runs")
  public ApiResponse<AgentDtos.RunSummary> startRun(@PathVariable String caseId, @Valid @RequestBody AgentDtos.StartRunRequest request) {
    permissionService.require(currentUser.get(), Permission.AGENT_RUN);
    return ApiResponse.ok(agentService.startRun(caseId, request, currentUser.get()));
  }

  @GetMapping("/agent-cases/{caseId}/runs")
  public ApiResponse<List<AgentDtos.RunSummary>> runs(@PathVariable String caseId) {
    permissionService.require(currentUser.get(), Permission.AGENT_VIEW);
    return ApiResponse.ok(agentService.caseDetail(caseId).runs());
  }

  @GetMapping("/agent-runs/{runId}")
  public ApiResponse<AgentDtos.RunDetail> run(@PathVariable String runId) {
    permissionService.require(currentUser.get(), Permission.AGENT_VIEW);
    return ApiResponse.ok(agentService.runDetail(runId));
  }

  @GetMapping("/agent-runs/{runId}/evidence")
  public ApiResponse<List<AgentDtos.EvidenceResponse>> evidence(@PathVariable String runId) {
    permissionService.require(currentUser.get(), Permission.AGENT_VIEW);
    return ApiResponse.ok(agentService.evidence(runId));
  }

  @GetMapping("/agent-runs/{runId}/trace")
  public ApiResponse<AgentDtos.RunDetail> trace(@PathVariable String runId) {
    permissionService.require(currentUser.get(), Permission.AGENT_VIEW);
    return ApiResponse.ok(agentService.runDetail(runId));
  }

  @GetMapping("/agent-runs/{runId}/tool-calls")
  public ApiResponse<List<AgentDtos.ToolCallResponse>> toolCalls(@PathVariable String runId) {
    permissionService.require(currentUser.get(), Permission.AGENT_VIEW);
    return ApiResponse.ok(agentService.toolCalls(runId));
  }

  @GetMapping("/agent-runs/{runId}/question")
  public ApiResponse<AgentDtos.RunQuestionResponse> question(@PathVariable String runId) {
    permissionService.require(currentUser.get(), Permission.AGENT_VIEW);
    return ApiResponse.ok(agentService.question(runId));
  }

  @PostMapping("/agent-runs/{runId}/cancel")
  public ApiResponse<AgentDtos.RunSummary> cancel(@PathVariable String runId) {
    permissionService.require(currentUser.get(), Permission.AGENT_RUN);
    return ApiResponse.ok(agentService.cancelRun(runId));
  }
}
