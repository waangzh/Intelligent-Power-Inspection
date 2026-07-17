package com.powerinspection.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class EntityPayloadCodec {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private EntityPayloadCodec() {
  }

  public static String extraJson(Map<String, Object> map, Set<String> knownKeys) {
    Map<String, Object> extra = new LinkedHashMap<>();
    map.forEach((key, value) -> {
      if (!knownKeys.contains(key) && value != null) {
        extra.put(key, value);
      }
    });
    return extra.isEmpty() ? null : write(extra);
  }

  public static Map<String, Object> readMap(String json) {
    if (json == null || json.isBlank()) {
      return new LinkedHashMap<>();
    }
    try {
      return MAPPER.readValue(json, new TypeReference<>() {
      });
    } catch (Exception ex) {
      return new LinkedHashMap<>();
    }
  }

  public static Object readValue(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return MAPPER.readValue(json, Object.class);
    } catch (Exception ex) {
      return null;
    }
  }

  public static String write(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String text) {
      return text;
    }
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception ex) {
      return null;
    }
  }

  public static void mergeExtra(Map<String, Object> target, String extraJson) {
    Map<String, Object> extra = readMap(extraJson);
    extra.forEach(target::putIfAbsent);
  }
}
