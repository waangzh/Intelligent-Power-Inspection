package com.powerinspection.agent.action;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.agent.domain.AgentActionEntity;
import com.powerinspection.agent.domain.AgentCaseEntity;
import com.powerinspection.agent.domain.AgentEnums;
import com.powerinspection.agent.persistence.AgentCaseRepository;
import com.powerinspection.common.ApiException;
import com.powerinspection.task.TaskExecutionControlAction;
import com.powerinspection.task.TaskExecutionControlService;
import com.powerinspection.task.TaskExecutionLifecycleService;
import com.powerinspection.task.TaskService;
import com.powerinspection.user.UserEntity;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RequestTaskPauseActionHandler implements AgentActionHandler {
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private final AgentCaseRepository caseRepository;
  private final TaskExecutionLifecycleService executionLifecycleService;
  private final TaskExecutionControlService executionControlService;
  private final TaskService taskService;
  private final ObjectMapper objectMapper;

  public RequestTaskPauseActionHandler(
      AgentCaseRepository caseRepository,
      TaskExecutionLifecycleService executionLifecycleService,
      TaskExecutionControlService executionControlService,
      TaskService taskService,
      ObjectMapper objectMapper) {
    this.caseRepository = caseRepository;
    this.executionLifecycleService = executionLifecycleService;
    this.executionControlService = executionControlService;
    this.taskService = taskService;
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean supports(AgentEnums.ActionType type) {
    return type == AgentEnums.ActionType.REQUEST_TASK_PAUSE;
  }

  @Override
  public Map<String, Object> execute(AgentActionEntity action, UserEntity user) {
    AgentCaseEntity agentCase = caseRepository.findById(action.getCaseId())
      .orElseThrow(() -> ApiException.notFound("处置案件不存在"));
    String taskId = text(payload(action.getPayloadJson()).get("taskId"));
    if (taskId == null || !taskId.equals(agentCase.getTaskId())) {
      throw ApiException.badRequest("任务暂停对象不属于当前案件");
    }
    boolean controlledExecution = executionLifecycleService.hasExecution(taskId);
    if (controlledExecution) {
      executionControlService.request(taskId, TaskExecutionControlAction.PAUSE, action.getIdempotencyKey(), null, user);
    } else {
      taskService.pause(taskId);
    }
    String summary = controlledExecution ? "任务暂停请求已进入受控执行队列" : "模拟任务已暂停";
    return Map.of("resourceType", "TASK", "resourceId", taskId, "summary", summary);
  }

  private Map<String, Object> payload(String json) {
    try {
      return objectMapper.readValue(json, MAP_TYPE);
    } catch (Exception ex) {
      throw ApiException.badRequest("任务暂停动作参数损坏");
    }
  }

  private String text(Object value) {
    return value == null || value.toString().isBlank() ? null : value.toString();
  }
}
