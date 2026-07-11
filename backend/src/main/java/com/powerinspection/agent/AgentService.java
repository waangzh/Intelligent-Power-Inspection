package com.powerinspection.agent;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.model.ModelServiceException;
import com.powerinspection.user.UserEntity;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AgentService {
  private static final String STATUS_RUNNING = "RUNNING";
  private static final String STATUS_SUCCEEDED = "SUCCEEDED";
  private static final String STATUS_FAILED = "FAILED";
  private static final String STATUS_PENDING = "PENDING";

  private final DataStoreService dataStore;
  private final AgentToolService toolService;
  private final AgentLlmGateway llmGateway;
  private final SimpMessagingTemplate messagingTemplate;
  private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
    Thread thread = new Thread(runnable, "agent-run-worker");
    thread.setDaemon(true);
    return thread;
  });

  public AgentService(DataStoreService dataStore, AgentToolService toolService, AgentLlmGateway llmGateway, SimpMessagingTemplate messagingTemplate) {
    this.dataStore = dataStore;
    this.toolService = toolService;
    this.llmGateway = llmGateway;
    this.messagingTemplate = messagingTemplate;
  }

  public Map<String, Object> createSession(Map<String, Object> body, UserEntity user) {
    Map<String, Object> input = normalizeInput(body);
    String now = Instant.now().toString();
    Map<String, Object> session = new LinkedHashMap<>();
    session.put("id", Ids.next("agent_session"));
    session.put("title", title(input));
    session.put("inputType", inputType(input));
    putIfText(session, "taskId", input.get("taskId"));
    putIfText(session, "alarmId", input.get("alarmId"));
    putIfText(session, "prompt", input.get("prompt"));
    session.put("status", STATUS_RUNNING);
    session.put("createdById", user.getId());
    session.put("createdAt", now);
    session.put("updatedAt", now);
    dataStore.upsert(DataCategory.AGENT_SESSION, session);
    Map<String, Object> run = createRun(session);
    executor.submit(() -> runSession(new LinkedHashMap<>(session), user, run));
    return detail(text(session.get("id")));
  }

  private Map<String, Object> normalizeInput(Map<String, Object> body) {
    Map<String, Object> input = new LinkedHashMap<>(body == null ? map() : body);
    String taskId = text(input.get("taskId"));
    String alarmId = text(input.get("alarmId"));
    String prompt = text(input.get("prompt"));
    boolean explicitTaskId = hasText(taskId);
    if (!hasText(taskId) && !hasText(alarmId) && !hasText(prompt)) {
      throw ApiException.badRequest("请至少选择任务、告警或填写补充说明");
    }

    if (hasText(alarmId)) {
      Map<String, Object> alarm = dataStore.find(DataCategory.ALARM, alarmId);
      if (alarm == null) {
        throw ApiException.badRequest("告警不存在");
      }
      String alarmTaskId = text(alarm.get("taskId"));
      if (hasText(taskId) && hasText(alarmTaskId) && !taskId.equals(alarmTaskId)) {
        throw ApiException.badRequest("告警不属于所选任务");
      }
      if (!hasText(taskId) && hasText(alarmTaskId)) {
        input.put("taskId", alarmTaskId);
        taskId = alarmTaskId;
      }
    }

    if (explicitTaskId && dataStore.find(DataCategory.TASK, taskId) == null) {
      throw ApiException.badRequest("任务不存在");
    }
    return input;
  }

  public List<Map<String, Object>> sessions() {
    return dataStore.list(DataCategory.AGENT_SESSION);
  }

  public Map<String, Object> detail(String sessionId) {
    Map<String, Object> session = new LinkedHashMap<>(dataStore.get(DataCategory.AGENT_SESSION, sessionId));
    List<Map<String, Object>> runs = filterBySession(DataCategory.AGENT_RUN, sessionId);
    List<Map<String, Object>> evidence = filterBySession(DataCategory.AGENT_EVIDENCE, sessionId);
    List<Map<String, Object>> actions = filterBySession(DataCategory.AGENT_ACTION, sessionId);
    session.put("runs", runs);
    session.put("evidence", evidence);
    session.put("actions", actions);
    runs.stream().max(Comparator.comparing(item -> text(item.get("startedAt")))).ifPresent(run -> {
      session.put("latestRun", run);
      session.put("analysis", run.get("summary"));
    });
    return session;
  }

  public Map<String, Object> rerun(String sessionId, UserEntity user) {
    Map<String, Object> session = dataStore.get(DataCategory.AGENT_SESSION, sessionId);
    session.put("status", STATUS_RUNNING);
    session.put("updatedAt", Instant.now().toString());
    dataStore.upsert(DataCategory.AGENT_SESSION, session);
    Map<String, Object> run = createRun(session);
    executor.submit(() -> runSession(new LinkedHashMap<>(session), user, run));
    return detail(sessionId);
  }

  public Map<String, Object> confirmAction(String actionId, UserEntity user) {
    Map<String, Object> action = dataStore.get(DataCategory.AGENT_ACTION, actionId);
    requirePending(action);
    String type = text(action.get("type"));
    Map<String, Object> payload = payload(action);
    Map<String, Object> result;
    if ("CREATE_WORK_ORDER_DRAFT".equals(type)) {
      result = toolService.createWorkOrderDraft(payload, user);
    } else if ("PUSH_NOTIFICATION".equals(type)) {
      result = toolService.pushNotification(payload);
    } else {
      throw ApiException.badRequest("不支持的 Agent 动作类型");
    }
    action.put("status", "CONFIRMED");
    action.put("resultRef", map("type", type, "id", result.get("id"), "payload", result));
    action.put("updatedAt", Instant.now().toString());
    Map<String, Object> saved = dataStore.upsert(DataCategory.AGENT_ACTION, action);
    emit(text(saved.get("sessionId")), "ACTION_CONFIRMED", saved);
    return saved;
  }

  public Map<String, Object> rejectAction(String actionId) {
    Map<String, Object> action = dataStore.get(DataCategory.AGENT_ACTION, actionId);
    requirePending(action);
    action.put("status", "REJECTED");
    action.put("updatedAt", Instant.now().toString());
    Map<String, Object> saved = dataStore.upsert(DataCategory.AGENT_ACTION, action);
    emit(text(saved.get("sessionId")), "ACTION_REJECTED", saved);
    return saved;
  }

  private Map<String, Object> createRun(Map<String, Object> session) {
    String sessionId = text(session.get("id"));
    String now = Instant.now().toString();
    Map<String, Object> run = map(
      "id", Ids.next("agent_run"),
      "sessionId", sessionId,
      "status", STATUS_RUNNING,
      "steps", new ArrayList<Map<String, Object>>(),
      "startedAt", now
    );
    dataStore.upsert(DataCategory.AGENT_RUN, run);
    addStep(run, "RUN_STARTED", "Agent 开始巡检处置分析", null);
    return run;
  }

  private void runSession(Map<String, Object> session, UserEntity user, Map<String, Object> run) {
    try {
      List<Map<String, Object>> evidence = collectEvidence(session, run);
      AgentLlmAnalysis analysis = analyze(session, run, evidence);
      Map<String, Object> summary = summary(analysis);
      run.put("summary", summary);
      addStep(run, "LLM_ANALYZED", "Agent 已生成缺陷研判", summary);
      proposeActions(session, run, evidence, analysis, user);
      run.put("status", STATUS_SUCCEEDED);
      run.put("completedAt", Instant.now().toString());
      dataStore.upsert(DataCategory.AGENT_RUN, run);
      session.put("status", STATUS_SUCCEEDED);
      session.put("updatedAt", Instant.now().toString());
      dataStore.upsert(DataCategory.AGENT_SESSION, session);
      addStep(run, "RUN_SUCCEEDED", "Agent 分析完成", summary);
    } catch (Exception ex) {
      run.put("status", STATUS_FAILED);
      run.put("errorMessage", ex.getMessage());
      run.put("completedAt", Instant.now().toString());
      dataStore.upsert(DataCategory.AGENT_RUN, run);
      session.put("status", STATUS_FAILED);
      session.put("updatedAt", Instant.now().toString());
      dataStore.upsert(DataCategory.AGENT_SESSION, session);
      addStep(run, "RUN_FAILED", firstText(ex.getMessage(), "Agent 分析失败"), null);
    }
  }

  @PreDestroy
  void shutdown() {
    executor.shutdownNow();
  }

  private List<Map<String, Object>> collectEvidence(Map<String, Object> session, Map<String, Object> run) {
    List<Map<String, Object>> evidence = new ArrayList<>();
    String taskId = text(session.get("taskId"));
    String alarmId = text(session.get("alarmId"));

    addStep(run, "TOOL_STARTED", "查询任务上下文", map("tool", "queryTask"));
    Map<String, Object> taskContext = toolService.queryTask(taskId);
    if (taskContext != null) {
      evidence.add(saveEvidence(session, run, "TASK", taskId, "任务上下文", describeTask(taskContext), null, taskContext));
    }
    addStep(run, "TOOL_COMPLETED", "任务上下文查询完成", map("tool", "queryTask"));

    addStep(run, "TOOL_STARTED", "查询告警", map("tool", "queryAlarms"));
    List<Map<String, Object>> alarms = toolService.queryAlarms(alarmId, taskId);
    for (Map<String, Object> alarm : alarms) {
      evidence.add(saveEvidence(session, run, "ALARM", firstText(alarm.get("id"), alarmId), "告警证据", describeAlarm(alarm), text(alarm.get("imageUrl")), alarm));
    }
    addStep(run, "TOOL_COMPLETED", "告警查询完成", map("tool", "queryAlarms", "count", alarms.size()));

    addStep(run, "TOOL_STARTED", "查询关联工单", map("tool", "queryWorkOrders"));
    List<Map<String, Object>> workOrders = toolService.queryWorkOrders(alarmId, taskId);
    if (!workOrders.isEmpty()) {
      evidence.add(saveEvidence(session, run, "WORK_ORDER", null, "关联工单", "已有关联工单 " + workOrders.size() + " 个。", null, map("items", workOrders)));
    }
    addStep(run, "TOOL_COMPLETED", "关联工单查询完成", map("tool", "queryWorkOrders", "count", workOrders.size()));

    addStep(run, "TOOL_STARTED", "调用 LocateAnything 复核", map("tool", "locateAnything"));
    for (Map<String, Object> item : toolService.locateAnythingEvidence(taskContext, alarms)) {
      evidence.add(saveEvidence(
        session,
        run,
        "LOCATE_ANYTHING",
        text(item.get("sourceId")),
        text(item.get("title")),
        text(item.get("content")),
        text(item.get("imageUrl")),
        payload(item)
      ));
    }
    addStep(run, "TOOL_COMPLETED", "LocateAnything 复核完成", map("tool", "locateAnything"));

    if (hasText(text(session.get("prompt")))) {
      evidence.add(saveEvidence(session, run, "OPERATOR_PROMPT", null, "人工补充说明", text(session.get("prompt")), null, map("prompt", session.get("prompt"))));
    }
    return evidence;
  }

  private AgentLlmAnalysis analyze(Map<String, Object> session, Map<String, Object> run, List<Map<String, Object>> evidence) {
    try {
      return llmGateway.analyze(session, evidence);
    } catch (ModelServiceException ex) {
      saveEvidence(session, run, "LLM_FALLBACK", null, "LLM 降级", "LLM 不可用，已使用规则生成研判：" + ex.getMessage(), null, map("errorMessage", ex.getMessage()));
      return fallbackAnalysis(evidence);
    }
  }

  private void proposeActions(Map<String, Object> session, Map<String, Object> run, List<Map<String, Object>> evidence, AgentLlmAnalysis analysis, UserEntity user) {
    Map<String, Object> alarm = firstPayload(evidence, "ALARM");
    boolean hasWorkOrder = evidence.stream().anyMatch(item -> "WORK_ORDER".equals(item.get("type")));
    String priority = priority(analysis.defectLevel());
    if (alarm != null && !Boolean.TRUE.equals(alarm.get("missing")) && !hasWorkOrder) {
      Map<String, Object> payload = map(
        "alarmId", alarm.get("id"),
        "title", "Agent 建议处置：" + abbreviate(firstText(alarm.get("message"), "巡检异常"), 24),
        "description", firstText(analysis.cause(), alarm.get("message")),
        "priority", priority,
        "assigneeName", user.getDisplayName()
      );
      saveAction(session, run, "CREATE_WORK_ORDER_DRAFT", "创建工单草稿", "根据 Agent 研判创建 PENDING 工单，等待人工处理。", payload);
    }
    Map<String, Object> notificationPayload = map(
      "userId", user.getId(),
      "type", "AGENT",
      "title", "Agent 处置建议",
      "content", "缺陷等级：" + analysis.defectLevel() + "；" + abbreviate(analysis.cause(), 60),
      "link", "/agents"
    );
    saveAction(session, run, "PUSH_NOTIFICATION", "推送处置通知", "向当前调度员推送 Agent 研判摘要。", notificationPayload);
  }

  private Map<String, Object> saveEvidence(Map<String, Object> session, Map<String, Object> run, String type, String sourceId, String title, String content, String imageUrl, Map<String, Object> payload) {
    Map<String, Object> item = map(
      "id", Ids.next("agent_ev"),
      "sessionId", session.get("id"),
      "runId", run.get("id"),
      "type", type,
      "title", title,
      "content", content,
      "payload", payload == null ? map() : payload,
      "createdAt", Instant.now().toString()
    );
    putIfText(item, "sourceId", sourceId);
    putIfText(item, "imageUrl", imageUrl);
    Map<String, Object> saved = dataStore.upsert(DataCategory.AGENT_EVIDENCE, item);
    addStep(run, "EVIDENCE_ADDED", title, map("evidenceId", saved.get("id"), "type", type));
    return saved;
  }

  private Map<String, Object> saveAction(Map<String, Object> session, Map<String, Object> run, String type, String title, String description, Map<String, Object> payload) {
    String now = Instant.now().toString();
    Map<String, Object> action = map(
      "id", Ids.next("agent_act"),
      "sessionId", session.get("id"),
      "runId", run.get("id"),
      "type", type,
      "status", STATUS_PENDING,
      "title", title,
      "description", description,
      "payload", payload,
      "createdAt", now,
      "updatedAt", now
    );
    Map<String, Object> saved = dataStore.upsert(DataCategory.AGENT_ACTION, action);
    addStep(run, "ACTION_PROPOSED", title, map("actionId", saved.get("id"), "type", type));
    return saved;
  }

  @SuppressWarnings("unchecked")
  private void addStep(Map<String, Object> run, String type, String message, Map<String, Object> payload) {
    List<Map<String, Object>> steps = run.get("steps") instanceof List<?> list ? (List<Map<String, Object>>) list : new ArrayList<>();
    Map<String, Object> step = map(
      "id", Ids.next("agent_step"),
      "runId", run.get("id"),
      "sessionId", run.get("sessionId"),
      "type", type,
      "message", message,
      "payload", payload == null ? map() : payload,
      "createdAt", Instant.now().toString()
    );
    steps.add(step);
    run.put("steps", steps);
    dataStore.upsert(DataCategory.AGENT_RUN, run);
    emit(text(run.get("sessionId")), type, step);
  }

  private void emit(String sessionId, String type, Map<String, Object> payload) {
    if (!hasText(sessionId)) {
      return;
    }
    Map<String, Object> event = map("type", type, "sessionId", sessionId, "payload", payload, "createdAt", Instant.now().toString());
    messagingTemplate.convertAndSend("/topic/agents/" + sessionId, event);
  }

  private AgentLlmAnalysis fallbackAnalysis(List<Map<String, Object>> evidence) {
    String level = "LOW";
    String cause = "未发现明确异常证据。";
    for (Map<String, Object> item : evidence) {
      if (!"ALARM".equals(item.get("type"))) {
        continue;
      }
      Map<String, Object> alarm = payload(item);
      String severity = text(alarm.get("severity"));
      if (severityRank(severity) > severityRank(level)) {
        level = severity;
        cause = firstText(alarm.get("message"), cause);
      }
    }
    return new AgentLlmAnalysis(level, cause, List.of("复核告警截图和模型结果", "必要时创建工单并安排现场确认"), List.of("告警证据", "LocateAnything 复核结果"), 0.55);
  }

  private Map<String, Object> summary(AgentLlmAnalysis analysis) {
    return map(
      "defectLevel", analysis.defectLevel(),
      "cause", analysis.cause(),
      "recommendedActions", analysis.recommendedActions(),
      "citations", analysis.evidenceIds(),
      "confidence", analysis.confidence()
    );
  }

  private String describeTask(Map<String, Object> context) {
    if (Boolean.TRUE.equals(context.get("missing"))) {
      return "任务不存在：" + context.get("taskId");
    }
    Map<String, Object> task = payloadValue(context.get("task"));
    return "任务 " + firstText(task.get("name"), task.get("id")) + "，状态 " + firstText(task.get("status"), "-") + "。";
  }

  private String describeAlarm(Map<String, Object> alarm) {
    if (Boolean.TRUE.equals(alarm.get("missing"))) {
      return "告警不存在：" + alarm.get("alarmId");
    }
    return "告警级别 " + firstText(alarm.get("severity"), "-") + "，内容：" + firstText(alarm.get("message"), "-");
  }

  private List<Map<String, Object>> filterBySession(String category, String sessionId) {
    return dataStore.list(category).stream()
      .filter(item -> sessionId.equals(text(item.get("sessionId"))))
      .toList();
  }

  private Map<String, Object> firstPayload(List<Map<String, Object>> evidence, String type) {
    return evidence.stream()
      .filter(item -> type.equals(item.get("type")))
      .map(this::payload)
      .findFirst()
      .orElse(null);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> payload(Map<String, Object> item) {
    Object payload = item.get("payload");
    return payload instanceof Map<?, ?> map ? (Map<String, Object>) map : map();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> payloadValue(Object value) {
    return value instanceof Map<?, ?> map ? (Map<String, Object>) map : map();
  }

  private void requirePending(Map<String, Object> action) {
    if (!STATUS_PENDING.equals(text(action.get("status")))) {
      throw ApiException.badRequest("该 Agent 动作不是待确认状态");
    }
  }

  private String inputType(Map<String, Object> body) {
    if (hasText(text(body.get("alarmId")))) {
      return "ALARM";
    }
    if (hasText(text(body.get("taskId")))) {
      return "TASK";
    }
    return "FREE_TEXT";
  }

  private String title(Map<String, Object> body) {
    String alarmId = text(body.get("alarmId"));
    if (hasText(alarmId)) {
      Map<String, Object> alarm = dataStore.find(DataCategory.ALARM, alarmId);
      return "告警处置：" + abbreviate(firstText(alarm == null ? null : alarm.get("message"), "巡检异常"), 24);
    }
    String taskId = text(body.get("taskId"));
    if (hasText(taskId)) {
      Map<String, Object> task = dataStore.find(DataCategory.TASK, taskId);
      return "任务处置：" + abbreviate(firstText(task == null ? null : task.get("name"), "巡检任务"), 24);
    }
    return "巡检处置 Agent";
  }

  private String priority(String level) {
    return switch (firstText(level, "MEDIUM")) {
      case "CRITICAL" -> "URGENT";
      case "HIGH" -> "HIGH";
      case "LOW" -> "LOW";
      default -> "MEDIUM";
    };
  }

  private int severityRank(String level) {
    return switch (firstText(level, "LOW")) {
      case "CRITICAL" -> 4;
      case "HIGH" -> 3;
      case "MEDIUM" -> 2;
      default -> 1;
    };
  }

  private String abbreviate(String value, int max) {
    if (value == null) {
      return "";
    }
    return value.length() <= max ? value : value.substring(0, max);
  }

  private void putIfText(Map<String, Object> target, String key, Object value) {
    String text = text(value);
    if (hasText(text)) {
      target.put(key, text);
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private String firstText(Object... values) {
    for (Object value : values) {
      String text = text(value);
      if (hasText(text)) {
        return text;
      }
    }
    return "";
  }

  private String text(Object value) {
    return value == null ? null : value.toString();
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> item = new LinkedHashMap<>();
    for (int i = 0; i + 1 < values.length; i += 2) {
      item.put(values[i].toString(), values[i + 1]);
    }
    return item;
  }
}
