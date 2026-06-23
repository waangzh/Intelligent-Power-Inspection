package com.powerinspection.data;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.JsonStore;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DataStoreService {
  private final DataRecordRepository repository;
  private final JsonStore jsonStore;

  public DataStoreService(DataRecordRepository repository, JsonStore jsonStore) {
    this.repository = repository;
    this.jsonStore = jsonStore;
  }

  public List<Map<String, Object>> list(String category) {
    return repository.findByCategoryOrderByCreatedAtDesc(category).stream()
      .map(record -> jsonStore.parseObject(record.getPayload()))
      .sorted(new CreatedAtComparator())
      .toList();
  }

  public Map<String, Object> get(String category, String id) {
    return repository.findByCategoryAndRecordId(category, id)
      .map(record -> jsonStore.parseObject(record.getPayload()))
      .orElseThrow(() -> ApiException.notFound("数据不存在"));
  }

  public Map<String, Object> find(String category, String id) {
    return repository.findByCategoryAndRecordId(category, id)
      .map(record -> jsonStore.parseObject(record.getPayload()))
      .orElse(null);
  }

  @Transactional
  public Map<String, Object> upsert(String category, Map<String, Object> payload) {
    String id = string(payload.get("id"));
    if (id == null || id.isBlank()) {
      throw ApiException.badRequest("缺少 id");
    }
    String now = Instant.now().toString();
    payload.putIfAbsent("createdAt", now);
    DataRecord record = repository.findByCategoryAndRecordId(category, id).orElseGet(() -> {
      DataRecord item = new DataRecord();
      item.setCategory(category);
      item.setRecordId(id);
      item.setCreatedAt(string(payload.get("createdAt")));
      return item;
    });
    record.setPayload(jsonStore.stringify(payload));
    record.setUpdatedAt(now);
    repository.save(record);
    return payload;
  }

  @Transactional
  public Map<String, Object> patch(String category, String id, Map<String, Object> patch) {
    Map<String, Object> current = get(category, id);
    current.putAll(patch);
    current.put("id", id);
    return upsert(category, current);
  }

  @Transactional
  public void delete(String category, String id) {
    repository.deleteByCategoryAndRecordId(category, id);
  }

  @Transactional
  public void deleteWhere(String category, String field, String value) {
    List<Map<String, Object>> all = new ArrayList<>(list(category));
    all.stream()
      .filter(item -> value.equals(string(item.get(field))))
      .map(item -> string(item.get("id")))
      .filter(id -> id != null && !id.isBlank())
      .forEach(id -> repository.deleteByCategoryAndRecordId(category, id));
  }

  public boolean exists(String category, String id) {
    return repository.existsByCategoryAndRecordId(category, id);
  }

  private String string(Object value) {
    return value == null ? null : value.toString();
  }

  private static class CreatedAtComparator implements Comparator<Map<String, Object>> {
    @Override
    public int compare(Map<String, Object> left, Map<String, Object> right) {
      String a = String.valueOf(left.getOrDefault("createdAt", ""));
      String b = String.valueOf(right.getOrDefault("createdAt", ""));
      return b.compareTo(a);
    }
  }
}
