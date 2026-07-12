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
    return PlannerDecision.finish("证据已完成受限采集", new PlannerConclusion(risk(alarm), "已基于当前运行内的告警、视觉和工单证据完成研判。", List.of("建议由值班人员结合证据决定后续处置。")), context.evidenceIds(), 0.72);
  }

  private Map<String, Object> args(String alarmId, String taskId) {
    return hasText(taskId) ? Map.of("alarmId", alarmId, "taskId", taskId) : Map.of("alarmId", alarmId);
  }

  private boolean hasImage(PlanningEvidence evidence) {
    return evidence != null && hasText(text(evidence.payload().get("imageUrl")));
  }

  private AgentEnums.RiskLevel risk(PlanningEvidence evidence) {
    String value = evidence == null ? null : text(evidence.payload().get("severity"));
    try { return value == null ? AgentEnums.RiskLevel.MEDIUM : AgentEnums.RiskLevel.valueOf(value); }
    catch (IllegalArgumentException ex) { return AgentEnums.RiskLevel.MEDIUM; }
  }

  private boolean hasText(String value) { return value != null && !value.isBlank(); }
  private String text(Object value) { return value == null ? null : value.toString(); }
}
