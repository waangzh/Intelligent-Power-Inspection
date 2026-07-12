package com.powerinspection.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powerinspection.user.PermissionService;
import jakarta.annotation.PreDestroy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Service;

@Service
public class AgentToolExecutor {
  private static final String ID_PATTERN = "[A-Za-z0-9_-]{1,64}";
  private final AgentToolRegistry registry;
  private final PermissionService permissionService;
  private final ObjectMapper objectMapper;
  private final ExecutorService workers = Executors.newCachedThreadPool(runnable -> {
    Thread thread = new Thread(runnable, "agent-read-tool-worker");
    thread.setDaemon(true);
    return thread;
  });

  public AgentToolExecutor(AgentToolRegistry registry, PermissionService permissionService, ObjectMapper objectMapper) {
    this.registry = registry;
    this.permissionService = permissionService;
    this.objectMapper = objectMapper;
  }

  public AgentToolExecution execute(String toolName, Map<String, Object> arguments, AgentToolExecutionContext context) {
    AgentTool<?, ?> rawTool = registry.find(toolName).orElseThrow(() -> new AgentToolExecutionException("TOOL_NOT_REGISTERED", "工具未注册"));
    AgentToolDescriptor descriptor = rawTool.descriptor();
    if (!descriptor.readOnly()) {
      throw new AgentToolExecutionException("TOOL_NOT_READ_ONLY", "当前阶段只允许只读工具");
    }
    Map<String, Object> normalized = normalizeArguments(arguments, descriptor);
    permissionService.require(context.user(), descriptor.requiredPermission());
    Object input = convertInput(normalized, descriptor);
    AgentToolResult<Object> result = run(rawTool, input, context, descriptor.timeout());
    String argumentsJson = json(normalized);
    return new AgentToolExecution(descriptor, normalized, argumentsJson, sha256(argumentsJson), result);
  }

  private Map<String, Object> normalizeArguments(Map<String, Object> input, AgentToolDescriptor descriptor) {
    Map<String, Object> source = input == null ? Map.of() : input;
    if (!descriptor.inputFields().containsAll(source.keySet())) {
      throw new AgentToolExecutionException("INVALID_TOOL_ARGUMENT", "工具参数包含未允许字段");
    }
    Map<String, Object> normalized = new LinkedHashMap<>();
    for (String field : descriptor.inputFields()) {
      Object value = source.get(field);
      if (value == null) {
        continue;
      }
      if (!(value instanceof String text) || !text.matches(ID_PATTERN)) {
        throw new AgentToolExecutionException("INVALID_TOOL_ARGUMENT", "工具对象 ID 不合法");
      }
      normalized.put(field, text);
    }
    if (normalized.isEmpty() || ("inspect_alarm_image".equals(descriptor.name()) && !normalized.containsKey("alarmId"))) {
      throw new AgentToolExecutionException("INVALID_TOOL_ARGUMENT", "缺少工具对象 ID");
    }
    return normalized;
  }

  private Object convertInput(Map<String, Object> arguments, AgentToolDescriptor descriptor) {
    try {
      return objectMapper.convertValue(arguments, descriptor.inputType());
    } catch (IllegalArgumentException ex) {
      throw new AgentToolExecutionException("INVALID_TOOL_ARGUMENT", "工具参数无法转换为受限输入类型");
    }
  }

  @SuppressWarnings("unchecked")
  private AgentToolResult<Object> run(AgentTool<?, ?> rawTool, Object input, AgentToolExecutionContext context, Duration timeout) {
    AgentTool<Object, Object> tool = (AgentTool<Object, Object>) rawTool;
    Future<AgentToolResult<Object>> task = workers.submit(() -> tool.execute(input, context));
    try {
      return task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      task.cancel(true);
      throw new AgentToolExecutionException("TOOL_TIMEOUT", "工具调用超时");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AgentToolExecutionException("TOOL_INTERRUPTED", "工具调用被中断");
    } catch (ExecutionException ex) {
      throw new AgentToolExecutionException("TOOL_FAILED", "工具调用失败");
    }
  }

  private String json(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(new TreeMap<>(value));
    } catch (JsonProcessingException ex) {
      throw new AgentToolExecutionException("INVALID_TOOL_ARGUMENT", "工具参数无法序列化");
    }
  }

  private String sha256(String value) {
    try {
      byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder();
      for (byte item : hash) { result.append(String.format("%02x", item)); }
      return result.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }

  @PreDestroy
  void shutdown() { workers.shutdownNow(); }
}
