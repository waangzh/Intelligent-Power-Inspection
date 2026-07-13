package com.powerinspection.agent.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.powerinspection.agent.domain.AgentEnums;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class AgentDtos {
  private AgentDtos() {
  }

  public record CreateCaseRequest(
    @NotBlank(message = "处置目标不能为空") @Size(max = 1000, message = "处置目标不能超过 1000 字") String goal,
    @Size(max = 64) String taskId,
    @Size(max = 64) String alarmId,
    @Size(max = 16) String priority,
    @Size(max = 1000) String operatorNote
  ) {
  }

  public record StartRunRequest(@NotBlank(message = "重新分析原因不能为空") @Size(max = 64) String reason) {
  }

  public record ActionDecisionRequest(
    @JsonProperty("expectedVersion") @JsonAlias("version") @Min(value = 0, message = "动作版本不合法") long expectedVersion,
    @NotBlank(message = "审批意见不能为空") @Size(max = 1000) String comment,
    Map<String, Object> payload
  ) {
    public ActionDecisionRequest(long expectedVersion, String comment) {
      this(expectedVersion, comment, null);
    }
  }

  public record HumanInputRequest(
    @NotBlank(message = "问题编号不能为空") @Size(max = 64) String questionId,
    AgentEnums.HumanInputMode mode,
    @Size(max = 2000, message = "回答不能超过 2000 字") String text,
    @Size(max = 10, message = "附件引用不能超过 10 个") List<@Size(max = 64) String> attachmentIds
  ) {
  }

  public record HumanInputResponse(String answerId, String questionId, AgentEnums.HumanInputMode mode, boolean resumed) {
  }

  public record CaseSummary(
    String id, String title, String goal, String taskId, String alarmId,
    AgentEnums.CaseStatus status, String priority, Instant createdAt, Instant updatedAt, long version,
    RunSummary latestRun
  ) {
  }

  public record CaseDetail(CaseSummary item, List<RunSummary> runs) {
  }

  public record RunSummary(
    String id, int runNumber, AgentEnums.RunStatus status, String reanalysisReason,
    Instant startedAt, Instant completedAt, String errorCode, String errorMessage, long version,
    String plannerType, boolean degraded, String degradationReason
  ) {
  }

  public record RunDetail(
    RunSummary run, AgentConclusion conclusion, List<StepResponse> steps,
    List<ToolCallResponse> toolCalls, List<EvidenceResponse> evidence, List<ActionResponse> actions,
    RunQuestionResponse question
  ) {
  }

  public record StepResponse(
    String id, int sequenceNo, AgentEnums.StepType type, String summary, JsonNode detail, Instant createdAt
  ) {
  }

  public record ToolCallResponse(
    String id, int stepNo, String toolName, JsonNode arguments, AgentEnums.ToolCallStatus status,
    String reason, Instant startedAt, Instant completedAt, Long durationMs, String resultSummary,
    String errorCode, String errorMessage, Integer sequenceNo, String argumentsHash
  ) {
  }

  public record EvidenceResponse(
    String id, String toolCallId, AgentEnums.EvidenceSourceType sourceType, String sourceId,
    String title, String summary, String contentType, JsonNode payload, String contentHash, Instant collectedAt
  ) {
  }

  public record EvidenceReference(
    String evidenceId, String role, @Size(max = 500) String statement
  ) {
  }

  public record RunQuestionResponse(String runId, JsonNode question, boolean degraded, String degradationReason) {
  }

  public record AgentConclusion(
    AgentEnums.RiskLevel defectLevel, String cause, List<String> recommendedActions,
    List<EvidenceReference> evidenceReferences, @Min(0) @Max(1) double confidence
  ) {
  }

  public record ActionResponse(
    String id, AgentEnums.ActionType type, String title, String reason, AgentEnums.RiskLevel riskLevel, double confidence,
    AgentEnums.ActionStatus status, JsonNode payload, List<String> evidenceIds, JsonNode payloadAudit,
    AgentEnums.PolicyDecisionType policyDecision, String policyCode, String policyReason, boolean requiresApproval, String idempotencyKey, String approvedById,
    Instant approvedAt, String approvalComment, String rejectedById, Instant rejectedAt,
    String rejectionComment, Instant executionStartedAt, Instant executionCompletedAt,
    JsonNode result, String errorCode, String errorMessage, Instant createdAt, Instant updatedAt, long version
  ) {
  }

  public record AgentEvent(
    String eventId, String caseId, String runId, AgentEnums.StepType type,
    int sequenceNo, String summary, Instant occurredAt
  ) {
  }
}
