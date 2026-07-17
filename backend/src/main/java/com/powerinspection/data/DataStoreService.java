package com.powerinspection.data;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.JsonStore;
import com.powerinspection.common.PageResult;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

  public PageResult<Map<String, Object>> page(
      String category, int page, int size, String sort, String direction,
      String updatedAfter, String search, Map<String, String> filters) {
    int safePage = Math.max(0, page);
    int safeSize = Math.min(Math.max(size, 1), 200);
    String sortProperty = switch (sort == null ? "" : sort) {
      case "id" -> "recordId";
      case "createdAt" -> "createdAt";
      default -> "updatedAt";
    };
    Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
    Page<DataRecord> records = repository.findAll(
      specification(category, updatedAfter, null, search, filters),
      PageRequest.of(safePage, safeSize, Sort.by(sortDirection, sortProperty).and(Sort.by(sortDirection, "id")))
    );
    List<Map<String, Object>> items = records.getContent().stream().map(record -> {
      Map<String, Object> item = new LinkedHashMap<>(jsonStore.parseObject(record.getPayload()));
      item.put("updatedAt", record.getUpdatedAt());
      return item;
    }).toList();
    String nextCursor = records.hasNext() && !records.getContent().isEmpty()
      ? records.getContent().get(records.getContent().size() - 1).getUpdatedAt()
      : null;
    return new PageResult<>(items, records.getTotalElements(), safePage, safeSize, records.hasNext(), nextCursor);
  }

  public long count(String category, String createdAfter, String createdBefore, Map<String, String> filters) {
    return repository.count((root, query, builder) -> {
      List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
      predicates.add(builder.equal(root.get("category"), category));
      if (createdAfter != null && !createdAfter.isBlank()) {
        predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
      }
      if (createdBefore != null && !createdBefore.isBlank()) {
        predicates.add(builder.lessThan(root.get("createdAt"), createdBefore));
      }
      appendFilters(root, builder, predicates, filters);
      return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    });
  }

  private Specification<DataRecord> specification(
      String category, String after, String before, String search, Map<String, String> filters) {
    return (root, query, builder) -> {
      List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
      predicates.add(builder.equal(root.get("category"), category));
      if (after != null && !after.isBlank()) predicates.add(builder.greaterThanOrEqualTo(root.get("updatedAt"), after));
      if (before != null && !before.isBlank()) predicates.add(builder.lessThan(root.get("createdAt"), before));
      if (search != null && !search.isBlank()) {
        predicates.add(builder.like(builder.lower(root.get("payload")), "%" + escapeLike(search.toLowerCase()) + "%", '\\'));
      }
      appendFilters(root, builder, predicates, filters);
      return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
    };
  }

  private void appendFilters(
      jakarta.persistence.criteria.Root<DataRecord> root,
      jakarta.persistence.criteria.CriteriaBuilder builder,
      List<jakarta.persistence.criteria.Predicate> predicates,
      Map<String, String> filters) {
    if (filters == null) return;
    filters.forEach((field, value) -> {
      if (field != null && field.matches("[A-Za-z][A-Za-z0-9]*") && value != null && !value.isBlank()) {
        List<jakarta.persistence.criteria.Predicate> alternatives = new ArrayList<>();
        for (String candidate : value.split(",")) {
          String escaped = escapeLike(candidate.trim());
          alternatives.add(builder.like(root.get("payload"), "%\"" + field + "\":\"" + escaped + "\"%", '\\'));
          alternatives.add(builder.like(root.get("payload"), "%\"" + field + "\":" + escaped + "%", '\\'));
        }
        predicates.add(builder.or(alternatives.toArray(jakarta.persistence.criteria.Predicate[]::new)));
      }
    });
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

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
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
