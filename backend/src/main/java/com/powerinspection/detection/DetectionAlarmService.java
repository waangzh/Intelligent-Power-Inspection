package com.powerinspection.detection;

import com.powerinspection.alarm.AlarmRepository;
import com.powerinspection.alarm.AlarmService;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.Ids;
import com.powerinspection.model.LocateAnythingFinding;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DetectionAlarmService {
  private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

  private final AlarmService alarmService;
  private final AlarmRepository alarmRepository;

  public DetectionAlarmService(AlarmService alarmService, AlarmRepository alarmRepository) {
    this.alarmService = alarmService;
    this.alarmRepository = alarmRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<Map<String, Object>> createAlarms(
      String detectionRunId,
      String sourceType,
      Map<String, Object> context,
      List<Map<String, Object>> detections,
      List<LocateAnythingFinding> findings) {
    List<Map<String, Object>> created = new ArrayList<>();
    if (!StringUtils.hasText(detectionRunId) || findings == null) return created;
    for (LocateAnythingFinding finding : findings) {
      Map<String, Object> item = matchingItem(detections, finding);
      if (item == null || !Boolean.TRUE.equals(item.get("alarmEnabled"))
          || !Boolean.TRUE.equals(item.get("alarmOnFinding"))) continue;
      String itemId = text(item.get("itemId"));
      String findingKey = findingKey(detectionRunId, itemId, finding.bbox());
      if (alarmRepository.existsByFindingKey(findingKey)) continue;
      Map<String, Object> alarm = new LinkedHashMap<>();
      alarm.put("id", Ids.next("alarm"));
      copy(context, alarm, "siteId", "routeId", "robotId", "taskId", "routeName", "checkpointName",
          "checkpointId", "imageId");
      alarm.put("type", text(item.get("type")) == null ? finding.type() : text(item.get("type")));
      alarm.put("severity", severity(item.get("alarmSeverity")));
      alarm.put("message", message(item, context, finding));
      alarm.put("imageUrl", finding.imageUrl());
      alarm.put("acknowledged", false);
      alarm.put("sourceType", sourceType);
      alarm.put("detectionRunId", detectionRunId);
      alarm.put("itemId", itemId);
      alarm.put("findingKey", findingKey);
      alarm.put("finding", findingToMap(finding));
      alarm.put("createdAt", Instant.now().toString());
      Map<String, Object> saved;
      try {
        saved = alarmService.create(alarm);
      } catch (ApiException ex) {
        if (ex.code() == 409 && alarmRepository.existsByFindingKey(findingKey)) {
          continue;
        }
        throw ex;
      }
      created.add(saved);
    }
    return created;
  }

  public long countForRun(String detectionRunId) {
    if (!StringUtils.hasText(detectionRunId)) return 0;
    return alarmRepository.countByDetectionRunId(detectionRunId);
  }

  private Map<String, Object> matchingItem(List<Map<String, Object>> detections, LocateAnythingFinding finding) {
    if (detections == null) return null;
    return detections.stream()
        .filter(item -> !Boolean.FALSE.equals(item.get("enabled")))
        .filter(item -> text(item.get("type")) != null && text(item.get("type")).equals(finding.type()))
        .filter(item -> !StringUtils.hasText(text(item.get("prompt")))
            || text(item.get("prompt")).equals(finding.prompt()))
        .findFirst().orElse(null);
  }

  private String message(Map<String, Object> item, Map<String, Object> context, LocateAnythingFinding finding) {
    String template = text(item.get("alarmMessage"));
    if (!StringUtils.hasText(template)) {
      template = "检查点「{checkpointName}」检测到{label}风险";
    }
    return template.replace("{checkpointName}", text(context.get("checkpointName")) == null ? "" : text(context.get("checkpointName")))
        .replace("{label}", finding.label() == null ? text(item.get("displayLabel")) : finding.label())
        .replace("{itemName}", text(item.get("name")) == null ? "检测项" : text(item.get("name")));
  }

  private Map<String, Object> findingToMap(LocateAnythingFinding finding) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("type", finding.type());
    result.put("prompt", finding.prompt());
    result.put("score", finding.score());
    result.put("bbox", finding.bbox());
    result.put("label", finding.label());
    result.put("imageUrl", finding.imageUrl());
    if (finding.rawResult() != null && !finding.rawResult().isEmpty()) result.put("rawResult", finding.rawResult());
    return result;
  }

  private String findingKey(String runId, String itemId, List<Integer> bbox) {
    return runId + ":" + itemId + ":" + (bbox == null ? "" : bbox.toString());
  }

  private String severity(Object value) {
    String result = text(value);
    return SEVERITIES.contains(result) ? result : "MEDIUM";
  }

  private void copy(Map<String, Object> source, Map<String, Object> target, String... keys) {
    if (source == null) return;
    for (String key : keys) if (source.get(key) != null) target.put(key, source.get(key));
  }

  private String text(Object value) { return value == null ? null : value.toString(); }
}
