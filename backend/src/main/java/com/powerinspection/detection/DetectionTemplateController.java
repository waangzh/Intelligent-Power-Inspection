package com.powerinspection.detection;

import com.powerinspection.business.CrudSupport;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.ApiResponse;
import com.powerinspection.common.ListQuery;
import com.powerinspection.common.PageResult;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataStoreService;
import com.powerinspection.model.DetectionItems;
import com.powerinspection.security.CurrentUser;
import com.powerinspection.user.Permission;
import com.powerinspection.user.PermissionService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/detection-templates")
public class DetectionTemplateController extends CrudSupport {
  private static final Map<String, Set<String>> SCOPE_TYPES = Map.of(
    "ROUTE", Set.of("PERSON", "HELMET", "OBSTACLE", "FIRE"),
    "CHECKPOINT", Set.of("SWITCH", "METER", "OIL_LEAK", "FIRE", "FOREIGN_OBJECT")
  );
  private static final Map<String, String> DEFAULT_PROMPTS = Map.of(
    "PERSON", "巡检区域内的人员",
    "HELMET", "人员头部佩戴的安全帽",
    "OBSTACLE", "机器人行进路线上的障碍物",
    "FIRE", "图像中清晰可见的火焰、火光或明显烟雾区域",
    "SWITCH", "变电设备上的刀闸开关操作手柄、连杆及触头区域",
    "METER", "圆形机械压力表的完整表盘和指针区域",
    "OIL_LEAK", "变压器或电气设备表面、法兰、阀门、接口及底部可见的油渍、油迹或积油区域",
    "FOREIGN_OBJECT", "设备操作区域内不属于设备本体的遗留物，例如工具、纸箱、塑料袋、布料或其他杂物"
  );
  private final PermissionService permissionService;
  private final CurrentUser currentUser;

  public DetectionTemplateController(DataStoreService dataStore, PermissionService permissionService, CurrentUser currentUser) {
    super(dataStore);
    this.permissionService = permissionService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ApiResponse<PageResult<Map<String, Object>>> templates(ListQuery query) {
    permissionService.require(currentUser.get(), Permission.DETECTION_MANAGE);
    PageResult<Map<String, Object>> result = page(DataCategory.DETECTION_TEMPLATE, query, "type", "enabled");
    return ApiResponse.ok(new PageResult<>(
        result.items().stream().map(this::normalizeStored).toList(),
        result.total(), result.page(), result.size(), result.hasMore(), result.nextCursor()));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> template(@PathVariable String id) {
    return ApiResponse.ok(normalizeStored(dataStore.get(DataCategory.DETECTION_TEMPLATE, id)));
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> addTemplate(@RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.DETECTION_MANAGE);
    return ApiResponse.ok(create(DataCategory.DETECTION_TEMPLATE, "tpl", normalizeForSave(upgradeInputShape(body))));
  }

  @PatchMapping("/{id}")
  public ApiResponse<Map<String, Object>> updateTemplate(@PathVariable String id, @RequestBody Map<String, Object> body) {
    permissionService.require(currentUser.get(), Permission.DETECTION_MANAGE);
    Map<String, Object> merged = new LinkedHashMap<>(normalizeStored(dataStore.get(DataCategory.DETECTION_TEMPLATE, id)));
    merged.putAll(body);
    return ApiResponse.ok(update(DataCategory.DETECTION_TEMPLATE, id, normalizeForSave(merged)));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Void> removeTemplate(@PathVariable String id) {
    permissionService.require(currentUser.get(), Permission.DETECTION_MANAGE);
    delete(DataCategory.DETECTION_TEMPLATE, id);
    return ApiResponse.ok();
  }

  /** 创建/更新请求体：仅把 legacy `types` 升级为 `items`，不替客户端补默认提示词。 */
  private Map<String, Object> upgradeInputShape(Map<String, Object> source) {
    if (source.get("items") instanceof List<?> storedItems && !storedItems.isEmpty()) {
      return source;
    }
    Map<String, Object> upgraded = new LinkedHashMap<>(source);
    Map<?, ?> prompts = source.get("prompts") instanceof Map<?, ?> map ? map : Map.of();
    List<Map<String, Object>> items = new ArrayList<>();
    if (source.get("types") instanceof List<?> types) {
      for (Object rawType : types) {
        String type = text(rawType);
        Object configuredPrompt = prompts.get(type);
        items.add(item(type, true, configuredPrompt == null ? DEFAULT_PROMPTS.get(type) : text(configuredPrompt), null));
      }
    }
    upgraded.put("items", items);
    return upgraded;
  }

  private Map<String, Object> normalizeStored(Map<String, Object> source) {
    Map<String, Object> upgraded = new LinkedHashMap<>(source);
    Map<?, ?> prompts = source.get("prompts") instanceof Map<?, ?> map ? map : Map.of();
    List<Map<String, Object>> items = new ArrayList<>();
    if (source.get("items") instanceof List<?> storedItems) {
      for (Object value : storedItems) {
        if (!(value instanceof Map<?, ?> raw)) {
          continue;
        }
        String type = text(raw.get("type"));
        boolean enabled = !Boolean.FALSE.equals(raw.get("enabled"));
        String prompt = text(raw.get("prompt"));
        String displayLabel = text(raw.get("displayLabel"));
        if (enabled && (prompt == null || prompt.isBlank())) {
          prompt = DEFAULT_PROMPTS.get(type);
        }
        items.add(item(type, enabled, prompt, displayLabel));
      }
    } else if (source.get("types") instanceof List<?> types) {
      for (Object rawType : types) {
        String type = text(rawType);
        Object configuredPrompt = prompts.get(type);
        items.add(item(type, true, configuredPrompt == null ? DEFAULT_PROMPTS.get(type) : text(configuredPrompt), null));
      }
    }
    upgraded.put("items", items);
    return normalizeForSave(upgraded);
  }

  private Map<String, Object> normalizeForSave(Map<String, Object> source) {
    String name = text(source.get("name"));
    String scope = text(source.get("scope"));
    if (name == null || name.isBlank()) {
      throw ApiException.badRequest("检测模板名称不能为空");
    }
    if (!SCOPE_TYPES.containsKey(scope)) {
      throw ApiException.badRequest("检测模板范围必须是 ROUTE 或 CHECKPOINT");
    }
    if (!(source.get("items") instanceof List<?> rawItems) || rawItems.isEmpty()) {
      throw ApiException.badRequest("检测模板至少需要一个检测项");
    }

    List<Map<String, Object>> items = new ArrayList<>();
    Set<String> types = new LinkedHashSet<>();
    Map<String, String> prompts = new LinkedHashMap<>();
    for (Object value : rawItems) {
      if (!(value instanceof Map<?, ?> raw)) {
        throw ApiException.badRequest("检测项格式错误");
      }
      String type = text(raw.get("type"));
      if (!SCOPE_TYPES.get(scope).contains(type)) {
        throw ApiException.badRequest("检测类型 " + type + " 不适用于 " + scope + " 模板");
      }
      if (!types.add(type)) {
        throw ApiException.badRequest("检测类型不能重复：" + type);
      }
      boolean enabled = !Boolean.FALSE.equals(raw.get("enabled"));
      String prompt = text(raw.get("prompt"));
      if (enabled && (prompt == null || prompt.isBlank())) {
        throw ApiException.badRequest("已启用检测项 " + type + " 必须填写提示词");
      }
      String normalizedPrompt = prompt == null ? "" : prompt.trim();
      String displayLabel = text(raw.get("displayLabel"));
      String normalizedDisplayLabel = displayLabel == null || displayLabel.isBlank()
        ? DetectionItems.displayLabel(type)
        : displayLabel.trim();
      items.add(item(type, enabled, normalizedPrompt, normalizedDisplayLabel));
      if (!normalizedPrompt.isBlank()) {
        prompts.put(type, normalizedPrompt);
      }
    }

    Map<String, Object> normalized = new LinkedHashMap<>();
    copyIfPresent(source, normalized, "id");
    normalized.put("name", name.trim());
    normalized.put("scope", scope);
    normalized.put("description", text(source.get("description")) == null ? "" : text(source.get("description")).trim());
    normalized.put("items", items);
    normalized.put("types", List.copyOf(types));
    normalized.put("prompts", prompts);
    copyIfPresent(source, normalized, "createdAt");
    return normalized;
  }

  private Map<String, Object> item(String type, boolean enabled, String prompt, String displayLabel) {
    Map<String, Object> item = new LinkedHashMap<>();
    item.put("type", type);
    item.put("enabled", enabled);
    item.put("displayLabel", displayLabel == null || displayLabel.isBlank() ? DetectionItems.displayLabel(type) : displayLabel.trim());
    item.put("prompt", prompt == null ? "" : prompt);
    item.put("threshold", 0.75);
    return item;
  }

  private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
    if (source.get(key) != null) {
      target.put(key, source.get(key));
    }
  }

  private String text(Object value) {
    return value == null ? null : value.toString();
  }
}
