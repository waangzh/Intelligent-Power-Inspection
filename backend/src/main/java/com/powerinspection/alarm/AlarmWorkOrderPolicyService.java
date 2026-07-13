package com.powerinspection.alarm;

import com.powerinspection.common.ApiException;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.user.UserEntity;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AlarmWorkOrderPolicyService {
  private static final String POLICY_ID = "default";
  private static final List<String> SEVERITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
  private static final List<String> MODES = List.of("AUTO", "MANUAL");

  private final DataStoreService dataStore;

  public AlarmWorkOrderPolicyService(DataStoreService dataStore) {
    this.dataStore = dataStore;
  }

  public Map<String, Object> policy() {
    Map<String, Object> stored = dataStore.find(DataCategory.ALARM_WORK_ORDER_POLICY, POLICY_ID);
    return stored == null ? defaultPolicy() : stored;
  }

  public Map<String, Object> update(Map<String, Object> body, UserEntity user) {
    Map<String, Object> rules = rules(body == null ? null : body.get("rules"));
    Map<String, Object> policy = new LinkedHashMap<>();
    policy.put("id", POLICY_ID);
    policy.put("rules", rules);
    policy.put("updatedBy", user.getId());
    policy.put("updatedAt", Instant.now().toString());
    return dataStore.upsert(DataCategory.ALARM_WORK_ORDER_POLICY, policy);
  }

  public String modeFor(String severity) {
    Map<String, Object> rules = rules(policy().get("rules"));
    return String.valueOf(rules.getOrDefault(severity, "MANUAL"));
  }

  private Map<String, Object> defaultPolicy() {
    Map<String, Object> policy = new LinkedHashMap<>();
    policy.put("id", POLICY_ID);
    policy.put("rules", defaultRules());
    return policy;
  }

  private Map<String, Object> rules(Object raw) {
    if (!(raw instanceof Map<?, ?> input)) {
      throw ApiException.badRequest("rules is required");
    }
    Map<String, Object> rules = new LinkedHashMap<>();
    for (String severity : SEVERITIES) {
      Object value = input.get(severity);
      String mode = value == null ? null : value.toString();
      if (!MODES.contains(mode)) {
        throw ApiException.badRequest(severity + " must be AUTO or MANUAL");
      }
      rules.put(severity, mode);
    }
    return rules;
  }

  private Map<String, Object> defaultRules() {
    Map<String, Object> rules = new LinkedHashMap<>();
    rules.put("LOW", "MANUAL");
    rules.put("MEDIUM", "MANUAL");
    rules.put("HIGH", "MANUAL");
    rules.put("CRITICAL", "AUTO");
    return rules;
  }
}
