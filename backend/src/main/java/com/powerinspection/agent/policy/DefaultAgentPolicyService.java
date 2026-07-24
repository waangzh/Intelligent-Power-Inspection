package com.powerinspection.agent.policy;

import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.domain.AgentEvidenceEntity;
import com.powerinspection.agent.planner.ActionProposal;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import com.powerinspection.user.UserEntity;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DefaultAgentPolicyService implements AgentPolicyService {
  private final PermissionService permissionService;
  private final DataStoreService dataStore;

  @Autowired
  public DefaultAgentPolicyService(
      PermissionService permissionService, DataStoreService dataStore) {
    this.permissionService = permissionService;
    this.dataStore = dataStore;
  }

  @Override
  public AgentPolicyDecision evaluate(AgentPolicyContext context) {
    ActionProposal proposal = context.proposal();
    AgentEnums.ActionType type;
    try {
      type = AgentEnums.ActionType.valueOf(proposal.actionType());
    } catch (Exception ex) {
      return deny(AgentEnums.RiskLevel.CRITICAL, "AGENT_ACTION_UNKNOWN", "动作类型不在受控白名单中。");
    }
    UserEntity user = context.user();
    if (user == null
        || !Boolean.TRUE.equals(user.getEnabled())
        || !(permissionService.has(user.getRole(), Permission.AGENT_RUN)
            || permissionService.has(user.getRole(), Permission.AGENT_APPROVE))) {
      return deny(
          AgentEnums.RiskLevel.HIGH, "AGENT_RUN_PERMISSION_REQUIRED", "当前用户无权提出、审批或执行 Agent 动作。");
    }
    return switch (type) {
      case PUSH_NOTIFICATION_TO_SELF -> notification(context);
      case CREATE_WORK_ORDER_DRAFT -> workOrder(context);
      case ACKNOWLEDGE_ALARM ->
          approval(AgentEnums.RiskLevel.MEDIUM, "AGENT_ALARM_ACK_APPROVAL", "告警确认必须经过人工审批。");
      case REQUEST_TASK_PAUSE ->
          approval(
              AgentEnums.RiskLevel.HIGH,
              "AGENT_TASK_PAUSE_APPROVAL",
              "任务暂停请求必须经过人工审批；审批后仅进入受控执行队列，最终状态以设备事件为准。");
      case PUSH_NOTIFICATION ->
          approval(
              AgentEnums.RiskLevel.LOW,
              "LEGACY_NOTIFICATION_APPROVAL",
              "旧通知动作保留兼容，但必须经过阶段 3 审批和受控执行器。");
      default -> deny(AgentEnums.RiskLevel.CRITICAL, "AGENT_ACTION_DENIED", "该动作在本阶段被明确禁止。");
    };
  }

  @Override
  public AgentPolicyDecision evaluate(AgentEnums.ActionType type, UserEntity user) {
    if (user == null) {
      return switch (type) {
        case CREATE_WORK_ORDER_DRAFT, PUSH_NOTIFICATION ->
            approval(AgentEnums.RiskLevel.LOW, "LEGACY_COMPATIBILITY", "兼容阶段 1 的只读策略查询。");
        default -> deny(AgentEnums.RiskLevel.CRITICAL, "AGENT_ACTION_DENIED", "该动作在本阶段被明确禁止。");
      };
    }
    return AgentPolicyService.super.evaluate(type, user);
  }

  private AgentPolicyDecision notification(AgentPolicyContext context) {
    if (!context.autoExecutionEnabled())
      return approval(AgentEnums.RiskLevel.LOW, "AGENT_AUTO_EXECUTION_DISABLED", "当前环境未启用通知自动执行。");
    if (context.proposal().confidence() < 0.60)
      return approval(
          AgentEnums.RiskLevel.LOW, "AGENT_NOTIFICATION_LOW_CONFIDENCE", "通知提案置信度不足，需人工确认。");
    return new AgentPolicyDecision(
        AgentEnums.PolicyDecisionType.AUTO_EXECUTE,
        AgentEnums.RiskLevel.LOW,
        "AGENT_NOTIFICATION_AUTO",
        "仅向当前运行创建者推送受限通知，可自动执行。");
  }

  private AgentPolicyDecision workOrder(AgentPolicyContext context) {
    if (context.agentCase() == null)
      return approval(
          AgentEnums.RiskLevel.LOW, "AGENT_WORK_ORDER_APPROVAL", "创建工单草稿必须由具备审批权限的用户确认。");
    String alarmId = string(context.proposal().payload().get("alarmId"));
    String taskId = string(context.proposal().payload().get("taskId"));
    boolean sufficient =
        context.evidence().stream()
            .anyMatch(
                item ->
                    item.getSourceType() == AgentEnums.EvidenceSourceType.ALARM
                        || item.getSourceType() == AgentEnums.EvidenceSourceType.TASK);
    if (!sufficient)
      return deny(
          AgentEnums.RiskLevel.MEDIUM, "AGENT_EVIDENCE_INSUFFICIENT", "创建工单前必须具有当前 Run 的告警或任务证据。");
    boolean existing =
        dataStore != null
            && dataStore.list(DataCategory.WORK_ORDER).stream()
                .anyMatch(
                    order ->
                        (alarmId != null && alarmId.equals(string(order.get("alarmId"))))
                            || (taskId != null && taskId.equals(string(order.get("taskId")))));
    if (existing || hasWorkOrderEvidence(context.evidence()))
      return deny(
          AgentEnums.RiskLevel.MEDIUM, "AGENT_RELATED_WORK_ORDER_EXISTS", "关联对象已存在工单，禁止创建重复草稿。");
    return approval(
        context.proposal().confidence() < 0.60
            ? AgentEnums.RiskLevel.MEDIUM
            : AgentEnums.RiskLevel.LOW,
        "AGENT_WORK_ORDER_APPROVAL",
        "创建工单草稿必须由具备审批权限的用户确认。");
  }

  private boolean hasWorkOrderEvidence(List<AgentEvidenceEntity> evidence) {
    return evidence.stream()
        .anyMatch(
            item ->
                item.getSourceType() == AgentEnums.EvidenceSourceType.WORK_ORDER
                    && payloadItemsExist(item.getPayloadJson()));
  }

  private boolean payloadItemsExist(String value) {
    return value != null && !value.contains("\"items\":[]");
  }

  private AgentPolicyDecision approval(AgentEnums.RiskLevel risk, String code, String reason) {
    return new AgentPolicyDecision(
        AgentEnums.PolicyDecisionType.REQUIRE_APPROVAL, risk, code, reason);
  }

  private AgentPolicyDecision deny(AgentEnums.RiskLevel risk, String code, String reason) {
    return new AgentPolicyDecision(AgentEnums.PolicyDecisionType.DENY, risk, code, reason);
  }

  private String string(Object value) {
    return value == null || value.toString().isBlank() ? null : value.toString();
  }
}
