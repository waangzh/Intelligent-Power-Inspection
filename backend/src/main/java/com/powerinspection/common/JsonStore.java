package com.powerinspection.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonStore {
  private final ObjectMapper objectMapper;

  public JsonStore(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String stringify(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw ApiException.badRequest("JSON 序列化失败");
    }
  }

  public Map<String, Object> parseObject(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<>() {
      });
    } catch (Exception ex) {
      throw ApiException.badRequest("JSON 解析失败");
    }
  }

  public Map<String, Object> toMap(Object value) {
    return objectMapper.convertValue(value, new TypeReference<>() {
    });
  }
}
