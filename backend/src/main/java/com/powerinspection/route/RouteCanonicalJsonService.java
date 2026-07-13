package com.powerinspection.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.powerinspection.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

@Service
public class RouteCanonicalJsonService {
  private final ObjectMapper objectMapper;

  public RouteCanonicalJsonService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String canonicalJson(JsonNode source) {
    try {
      return objectMapper.writeValueAsString(canonicalize(source));
    } catch (Exception ex) {
      throw ApiException.badRequest("路线 JSON 序列化失败");
    }
  }

  public String sha256(JsonNode source) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(canonicalJson(source).getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 不可用", ex);
    }
  }

  private JsonNode canonicalize(JsonNode source) {
    if (source == null || source.isNull() || source.isValueNode()) {
      return source == null ? NullNode.getInstance() : source.deepCopy();
    }
    if (source.isArray()) {
      ArrayNode target = objectMapper.createArrayNode();
      for (JsonNode item : source) {
        target.add(canonicalize(item));
      }
      return target;
    }
    ObjectNode target = objectMapper.createObjectNode();
    Map<String, JsonNode> sorted = new TreeMap<>();
    Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      sorted.put(field.getKey(), field.getValue());
    }
    sorted.forEach((key, value) -> target.set(key, canonicalize(value)));
    return target;
  }
}
