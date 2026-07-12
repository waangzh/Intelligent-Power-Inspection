package com.powerinspection.agent.planner;

import com.powerinspection.agent.tool.AgentToolRegistry;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Server-side validation; prompts and model output never authorize execution by themselves. */
@Component
public class PlannerDecisionValidator {
  private final AgentToolRegistry toolRegistry;

  public PlannerDecisionValidator(AgentToolRegistry toolRegistry) {
    this.toolRegistry = toolRegistry;
  }

  public void validate(PlannerDecision decision, AgentPlanningContext context) {
    if (decision == null || decision.type() == null) {
      fail("INVALID_PLANNER_DECISION", "Planner 未返回有效决策");
    }
    if (decision.summary() == null || decision.summary().isBlank() || decision.summary().length() > 500) {
      fail("INVALID_PLANNER_DECISION", "决策说明不合法");
    }
    if (!Double.isFinite(decision.confidence()) || decision.confidence() < 0 || decision.confidence() > 1) {
      fail("INVALID_PLANNER_DECISION", "决策置信度必须在 0 到 1 之间");
    }
    Set<String> runEvidence = new HashSet<>(context.evidenceIds());
    if (!runEvidence.containsAll(decision.evidenceIds())) {
      fail("EVIDENCE_NOT_IN_RUN", "Planner 引用了当前 Run 之外的证据");
    }
    switch (decision.type()) {
      case CALL_TOOL -> validateTool(decision);
      case ASK_HUMAN -> {
        if (decision.question() == null || blank(decision.question().type()) || blank(decision.question().prompt())) {
          fail("INVALID_HUMAN_QUESTION", "ASK_HUMAN 必须提供类型化问题");
        }
      }
      case FINISH -> {
        if (decision.conclusion() == null || decision.conclusion().defectLevel() == null || blank(decision.conclusion().cause()) || decision.evidenceIds().isEmpty()) {
          fail("INVALID_CONCLUSION", "FINISH 必须提供结论和当前 Run 的证据引用");
        }
      }
      case PROPOSE_ACTION -> fail("UNSUPPORTED_PLANNER_DECISION", "当前阶段不允许 Planner 创建或执行动作");
    }
  }

  private void validateTool(PlannerDecision decision) {
    if (blank(decision.toolName())) { fail("INVALID_TOOL_NAME", "CALL_TOOL 缺少工具名"); }
    var descriptor = toolRegistry.find(decision.toolName()).map(item -> item.descriptor()).orElseThrow(() -> new PlannerValidationException("TOOL_NOT_REGISTERED", "Planner 请求了未注册工具"));
    if (!descriptor.readOnly()) { fail("TOOL_NOT_READ_ONLY", "Planner 不能调用写工具"); }
    if (!descriptor.inputFields().containsAll(decision.toolArguments().keySet())) { fail("INVALID_TOOL_ARGUMENT", "工具参数包含未允许字段"); }
    if (decision.toolArguments().isEmpty()) { fail("INVALID_TOOL_ARGUMENT", "CALL_TOOL 必须提供参数"); }
  }

  private boolean blank(String value) { return value == null || value.isBlank(); }
  private void fail(String code, String message) { throw new PlannerValidationException(code, message); }
}
