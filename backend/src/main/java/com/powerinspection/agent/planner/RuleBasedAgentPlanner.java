package com.powerinspection.agent.planner;

import com.powerinspection.agent.domain.AgentEnums;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Deterministic safety net used for local tests and every LLM degradation path. */
@Component
public class RuleBasedAgentPlanner implements AgentPlanner {
  @Override
  public PlannerDecision decide(AgentPlanningContext context) {
    String alarmId = text(context.input().get("alarmId"));
    String taskId = text(context.input().get("taskId"));
    if (hasText(taskId)) {
      if (!context.hasEvidence(AgentEnums.EvidenceSourceType.TASK)) {
        return PlannerDecision.callTool("读取关联任务上下文", "get_task", Map.of("taskId", taskId), context.evidenceIds());
      }
      if (!context.hasEvidence(AgentEnums.EvidenceSourceType.TASK_EVENT)) {
        return PlannerDecision.callTool("读取任务事件轨迹", "get_task_events", Map.of("taskId", taskId), context.evidenceIds());
      }
      Map<String, Object> task = taskDetails(context.firstEvidence(AgentEnums.EvidenceSourceType.TASK));
      String robotId = text(task.get("robotId"));
      if (hasText(robotId) && !context.hasEvidence(AgentEnums.EvidenceSourceType.ROBOT)) {
        return PlannerDecision.callTool("读取任务关联机器人状态", "get_robot", Map.of("robotId", robotId), context.evidenceIds());
      }
      String routeId = text(task.get("routeId"));
      if (hasText(routeId) && !context.hasEvidence(AgentEnums.EvidenceSourceType.ROUTE)) {
        return PlannerDecision.callTool("读取任务关联路线", "get_route", Map.of("routeId", routeId), context.evidenceIds());
      }
    }
    if (!hasText(alarmId)) {
      if (hasText(taskId) && !context.hasEvidence(AgentEnums.EvidenceSourceType.ALARM)) {
        return PlannerDecision.callTool("先读取任务关联告警", "list_task_alarms", Map.of("taskId", taskId), context.evidenceIds());
      }
      return PlannerDecision.askHuman("缺少可研判的告警", new PlannerQuestion("ALARM_ID_REQUIRED", "请提供需要复核的告警编号。", List.of()), context.evidenceIds());
    }
    if (!context.hasEvidence(AgentEnums.EvidenceSourceType.ALARM)) {
      return PlannerDecision.callTool("先读取告警详情", "get_alarm", Map.of("alarmId", alarmId), context.evidenceIds());
    }
    PlanningEvidence alarm = context.firstEvidence(AgentEnums.EvidenceSourceType.ALARM);
    if (hasImage(alarm) && !context.hasEvidence(AgentEnums.EvidenceSourceType.VISION_RESULT)) {
      return PlannerDecision.callTool("告警包含图像，进行视觉复核", "inspect_alarm_image", args(alarmId, taskId), context.evidenceIds());
    }
    if (!context.hasEvidence(AgentEnums.EvidenceSourceType.WORK_ORDER)) {
      return PlannerDecision.callTool("核对是否已有关联工单", "list_related_work_orders", args(alarmId, taskId), context.evidenceIds());
    }
    PlanningEvidence workOrders = context.firstEvidence(AgentEnums.EvidenceSourceType.WORK_ORDER);
    PlannerConclusion conclusion = new PlannerConclusion(risk(alarm), "已基于当前运行内的告警、视觉和工单证据完成研判。", List.of("建议由值班人员结合证据决定后续处置。"));
    if (!hasExistingWorkOrder(workOrders)) {
      Map<String, Object> payload = new java.util.LinkedHashMap<>();
      payload.put("alarmId", alarmId);
      if (hasText(taskId)) payload.put("taskId", taskId);
      payload.put("title", "Agent 建议处置：" + abbreviate(text(alarm.payload().get("message")), 24));
      payload.put("description", conclusion.cause());
      payload.put("priority", priority(risk(alarm)));
      ActionProposal proposal = new ActionProposal("CREATE_WORK_ORDER_DRAFT", "创建工单草稿", "当前 Run 未发现关联工单，建议建立可人工审批的处置草稿。", payload, context.evidenceIds(), 0.72);
      return PlannerDecision.finishWithProposal("证据已完成受限采集，并提出受控工单草稿", conclusion, proposal, context.evidenceIds(), 0.72);
    }
    return PlannerDecision.finish("证据已完成受限采集", conclusion, context.evidenceIds(), 0.72);
  }

  private Map<String, Object> args(String alarmId, String taskId) {
    return hasText(taskId) ? Map.of("alarmId", alarmId, "taskId", taskId) : Map.of("alarmId", alarmId);
  }

  private Map<String, Object> taskDetails(PlanningEvidence evidence) {
    if (evidence != null && evidence.payload().get("task") instanceof Map<?, ?> raw) {
      Map<String, Object> task = new java.util.LinkedHashMap<>();
      raw.forEach((key, value) -> task.put(String.valueOf(key), value));
      return task;
    }
    return Map.of();
  }

  private boolean hasImage(PlanningEvidence evidence) {
    return evidence != null && hasText(text(evidence.payload().get("imageUrl")));
  }

  private AgentEnums.RiskLevel risk(PlanningEvidence evidence) {
    String value = evidence == null ? null : text(evidence.payload().get("severity"));
    try { return value == null ? AgentEnums.RiskLevel.MEDIUM : AgentEnums.RiskLevel.valueOf(value); }
    catch (IllegalArgumentException ex) { return AgentEnums.RiskLevel.MEDIUM; }
  }

  private boolean hasExistingWorkOrder(PlanningEvidence evidence) { return evidence != null && evidence.payload().get("items") instanceof List<?> items && !items.isEmpty(); }
  private String priority(AgentEnums.RiskLevel level) { return switch (level) { case CRITICAL -> "URGENT"; case HIGH -> "HIGH"; case MEDIUM -> "MEDIUM"; case LOW -> "LOW"; }; }
  private String abbreviate(String value, int max) { if (!hasText(value)) return "巡检异常"; return value.length() <= max ? value : value.substring(0, max); }


  private boolean hasText(String value) { return value != null && !value.isBlank(); }
  private String text(Object value) { return value == null ? null : value.toString(); }
}
