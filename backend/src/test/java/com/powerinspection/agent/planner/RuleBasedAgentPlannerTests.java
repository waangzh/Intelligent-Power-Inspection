package com.powerinspection.agent.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.powerinspection.agent.AgentToolService;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.tool.AgentToolRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleBasedAgentPlannerTests {
  private final RuleBasedAgentPlanner planner = new RuleBasedAgentPlanner();

  @Test
  void choosesGetAlarmBeforeAnyAlarmEvidence() {
    PlannerDecision decision = planner.decide(context(List.of()));
    assertThat(decision.type()).isEqualTo(PlannerDecisionType.CALL_TOOL);
    assertThat(decision.toolName()).isEqualTo("get_alarm");
  }

  @Test
  void doesNotRepeatGetAlarmAfterAlarmEvidence() {
    PlannerDecision decision = planner.decide(context(List.of(alarm(false))));
    assertThat(decision.toolName()).isEqualTo("list_related_work_orders");
  }

  @Test
  void choosesVisionWhenAlarmHasImageAndNoVisionEvidence() {
    PlannerDecision decision = planner.decide(context(List.of(alarm(true))));
    assertThat(decision.toolName()).isEqualTo("inspect_alarm_image");
  }

  @Test
  void doesNotRepeatVisionAfterVisionEvidence() {
    PlannerDecision decision = planner.decide(context(List.of(alarm(true), evidence("vision", AgentEnums.EvidenceSourceType.VISION_RESULT, Map.of()))));
    assertThat(decision.toolName()).isEqualTo("list_related_work_orders");
  }

  @Test
  void finishesOnlyAfterWorkOrderObservation() {
    PlannerDecision decision = planner.decide(context(List.of(alarm(false), evidence("orders", AgentEnums.EvidenceSourceType.WORK_ORDER, Map.of("items", List.of())))));
    assertThat(decision.type()).isEqualTo(PlannerDecisionType.FINISH);
    assertThat(decision.evidenceIds()).containsExactly("alarm", "orders");
  }

  @Test
  void collectsTaskEventsRobotAndRouteOnFallbackPath() {
    Map<String, Object> input = Map.of("alarmId", "alarm_1", "taskId", "task_1");
    PlanningEvidence task = evidence("task", AgentEnums.EvidenceSourceType.TASK,
      Map.of("task", Map.of("id", "task_1", "robotId", "robot_1", "routeId", "route_1")));
    PlanningEvidence events = evidence("events", AgentEnums.EvidenceSourceType.TASK_EVENT, Map.of("items", List.of()));
    PlanningEvidence robot = evidence("robot", AgentEnums.EvidenceSourceType.ROBOT, Map.of("id", "robot_1"));
    PlanningEvidence route = evidence("route", AgentEnums.EvidenceSourceType.ROUTE, Map.of("id", "route_1"));

    assertThat(planner.decide(context(input, List.of())).toolName()).isEqualTo("get_task");
    assertThat(planner.decide(context(input, List.of(task))).toolName()).isEqualTo("get_task_events");
    assertThat(planner.decide(context(input, List.of(task, events))).toolName()).isEqualTo("get_robot");
    assertThat(planner.decide(context(input, List.of(task, events, robot))).toolName()).isEqualTo("get_route");
    assertThat(planner.decide(context(input, List.of(task, events, robot, route))).toolName()).isEqualTo("get_alarm");
  }

  @Test
  void rejectsUnknownToolAndArguments() {
    PlannerDecisionValidator validator = validator();
    assertThatThrownBy(() -> validator.validate(PlannerDecision.callTool("bad", "unknown", Map.of("alarmId", "alarm_1"), List.of()), context(List.of())))
      .isInstanceOf(PlannerValidationException.class);
    assertThatThrownBy(() -> validator.validate(PlannerDecision.callTool("bad", "get_alarm", Map.of("path", "C:/temp"), List.of()), context(List.of())))
      .isInstanceOf(PlannerValidationException.class);
  }

  @Test
  void rejectsEvidenceFromAnotherRun() {
    PlannerDecisionValidator validator = validator();
    assertThatThrownBy(() -> validator.validate(PlannerDecision.finish("done", new PlannerConclusion(AgentEnums.RiskLevel.LOW, "ok", List.of()), List.of("other-run"), 0.5), context(List.of(alarm(false)))))
      .isInstanceOf(PlannerValidationException.class)
      .hasFieldOrPropertyWithValue("code", "EVIDENCE_NOT_IN_RUN");
  }

  @Test
  void validatesAskHumanAndFinishConstraints() {
    PlannerDecisionValidator validator = validator();
    assertThatThrownBy(() -> validator.validate(PlannerDecision.askHuman("need input", null, List.of()), context(List.of())))
      .isInstanceOf(PlannerValidationException.class);
    assertThatThrownBy(() -> validator.validate(PlannerDecision.finish("done", null, List.of(), 1.2), context(List.of())))
      .isInstanceOf(PlannerValidationException.class);
  }

  private PlannerDecisionValidator validator() {
    return new PlannerDecisionValidator(new AgentToolRegistry(mock(AgentToolService.class)));
  }

  private AgentPlanningContext context(List<PlanningEvidence> evidence) {
    return context(Map.of("alarmId", "alarm_1"), evidence);
  }

  private AgentPlanningContext context(Map<String, Object> input, List<PlanningEvidence> evidence) {
    return new AgentPlanningContext("case_1", "run_1", "user_1", "review alarm", input, evidence, List.of(), Instant.now());
  }

  private PlanningEvidence alarm(boolean image) {
    return evidence("alarm", AgentEnums.EvidenceSourceType.ALARM, image ? Map.of("severity", "HIGH", "imageUrl", "https://example.test/a.jpg") : Map.of("severity", "HIGH"));
  }

  private PlanningEvidence evidence(String id, AgentEnums.EvidenceSourceType type, Map<String, Object> payload) {
    return new PlanningEvidence(id, type, "source_1", type.name(), "test", payload);
  }
}
