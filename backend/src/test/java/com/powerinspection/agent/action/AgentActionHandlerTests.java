package com.powerinspection.agent.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.agent.domain.AgentActionEntity;
import com.powerinspection.agent.domain.AgentCaseEntity;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.persistence.AgentCaseRepository;
import com.powerinspection.alarm.AlarmService;
import com.powerinspection.common.ApiException;
import com.powerinspection.task.TaskExecutionControlAction;
import com.powerinspection.task.TaskExecutionControlService;
import com.powerinspection.task.TaskExecutionLifecycleService;
import com.powerinspection.task.TaskService;
import com.powerinspection.user.UserEntity;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AgentActionHandlerTests {
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void acknowledgesOnlyTheAlarmBoundToTheCase() {
    AgentCaseRepository cases = mock(AgentCaseRepository.class);
    AlarmService alarms = mock(AlarmService.class);
    AgentCaseEntity agentCase = agentCase("case_1", "alarm_1", null);
    when(cases.findById("case_1")).thenReturn(Optional.of(agentCase));
    AcknowledgeAlarmActionHandler handler = new AcknowledgeAlarmActionHandler(cases, alarms, objectMapper);

    Map<String, Object> result = handler.execute(action("case_1", AgentEnums.ActionType.ACKNOWLEDGE_ALARM,
      "{\"alarmId\":\"alarm_1\"}"), new UserEntity());

    assertThat(result).containsEntry("resourceType", "ALARM").containsEntry("resourceId", "alarm_1");
    verify(alarms).acknowledge("alarm_1");
  }

  @Test
  void rejectsAlarmFromAnotherCase() {
    AgentCaseRepository cases = mock(AgentCaseRepository.class);
    AlarmService alarms = mock(AlarmService.class);
    when(cases.findById("case_1")).thenReturn(Optional.of(agentCase("case_1", "alarm_1", null)));
    AcknowledgeAlarmActionHandler handler = new AcknowledgeAlarmActionHandler(cases, alarms, objectMapper);

    assertThatThrownBy(() -> handler.execute(action("case_1", AgentEnums.ActionType.ACKNOWLEDGE_ALARM,
      "{\"alarmId\":\"alarm_2\"}"), new UserEntity())).isInstanceOf(ApiException.class);
    verify(alarms, never()).acknowledge("alarm_2");
  }

  @Test
  void queuesPauseForARealTaskExecution() {
    AgentCaseRepository cases = mock(AgentCaseRepository.class);
    TaskExecutionLifecycleService lifecycle = mock(TaskExecutionLifecycleService.class);
    TaskExecutionControlService controls = mock(TaskExecutionControlService.class);
    TaskService tasks = mock(TaskService.class);
    UserEntity user = new UserEntity();
    when(cases.findById("case_1")).thenReturn(Optional.of(agentCase("case_1", null, "task_1")));
    when(lifecycle.hasExecution("task_1")).thenReturn(true);
    RequestTaskPauseActionHandler handler = new RequestTaskPauseActionHandler(cases, lifecycle, controls, tasks, objectMapper);
    AgentActionEntity action = action("case_1", AgentEnums.ActionType.REQUEST_TASK_PAUSE, "{\"taskId\":\"task_1\"}");
    action.setIdempotencyKey("agent-pause-1");

    Map<String, Object> result = handler.execute(action, user);

    assertThat(result.get("summary")).isEqualTo("任务暂停请求已进入受控执行队列");
    verify(controls).request("task_1", TaskExecutionControlAction.PAUSE, "agent-pause-1", null, user);
    verify(tasks, never()).pause("task_1");
  }

  @Test
  void pausesSimulationTaskWithoutCreatingAControlCommand() {
    AgentCaseRepository cases = mock(AgentCaseRepository.class);
    TaskExecutionLifecycleService lifecycle = mock(TaskExecutionLifecycleService.class);
    TaskExecutionControlService controls = mock(TaskExecutionControlService.class);
    TaskService tasks = mock(TaskService.class);
    when(cases.findById("case_1")).thenReturn(Optional.of(agentCase("case_1", null, "task_1")));
    when(lifecycle.hasExecution("task_1")).thenReturn(false);
    RequestTaskPauseActionHandler handler = new RequestTaskPauseActionHandler(cases, lifecycle, controls, tasks, objectMapper);

    handler.execute(action("case_1", AgentEnums.ActionType.REQUEST_TASK_PAUSE,
      "{\"taskId\":\"task_1\"}"), new UserEntity());

    verify(tasks).pause("task_1");
    verifyNoInteractions(controls);
  }

  private AgentCaseEntity agentCase(String id, String alarmId, String taskId) {
    AgentCaseEntity value = new AgentCaseEntity();
    value.setId(id);
    value.setAlarmId(alarmId);
    value.setTaskId(taskId);
    return value;
  }

  private AgentActionEntity action(String caseId, AgentEnums.ActionType type, String payload) {
    AgentActionEntity value = new AgentActionEntity();
    value.setCaseId(caseId);
    value.setType(type);
    value.setPayloadJson(payload);
    return value;
  }
}
