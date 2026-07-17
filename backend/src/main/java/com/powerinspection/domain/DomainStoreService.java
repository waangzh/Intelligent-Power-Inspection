package com.powerinspection.domain;

import com.powerinspection.alarm.AlarmEntity;
import com.powerinspection.common.ApiException;
import com.powerinspection.common.PageResult;
import com.powerinspection.data.DataCategory;
import com.powerinspection.notification.NotificationEntity;
import com.powerinspection.record.InspectionRecordEntity;
import com.powerinspection.robot.RobotEntity;
import com.powerinspection.robot.RobotTelemetryEntity;
import com.powerinspection.route.RouteEntity;
import com.powerinspection.site.SiteEntity;
import com.powerinspection.task.InspectionTaskEntity;
import com.powerinspection.task.TaskEventEntity;
import com.powerinspection.workorder.WorkOrderEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DomainStoreService {
  private static final Set<String> DOMAIN = Set.of(
    DataCategory.SITE,
    DataCategory.ROUTE,
    DataCategory.ROBOT,
    DataCategory.TASK,
    DataCategory.RECORD,
    DataCategory.EVENT,
    DataCategory.ALARM,
    DataCategory.WORK_ORDER,
    DataCategory.NOTIFICATION
  );

  @PersistenceContext
  private EntityManager entityManager;

  public static boolean supports(String category) {
    return category != null && DOMAIN.contains(category);
  }

  public List<Map<String, Object>> list(String category) {
    return page(category, 0, Integer.MAX_VALUE, "createdAt", "desc", null, null, null).items();
  }

  public List<String> ids(String category) {
    Class<?> type = entityType(category);
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<String> query = cb.createQuery(String.class);
    Root<?> root = query.from(type);
    query.select(root.get("id"));
    query.orderBy(cb.desc(root.get("createdAt")));
    return entityManager.createQuery(query).getResultList();
  }

  public PageResult<Map<String, Object>> page(
      String category, int page, int size, String sort, String direction,
      String updatedAfter, String search, Map<String, String> filters) {
    @SuppressWarnings("unchecked")
    Class<Object> type = (Class<Object>) entityType(category);
    int safePage = Math.max(0, page);
    int safeSize = size == Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.min(Math.max(size, 1), 200);
    String sortProperty = resolveSort(category, sort);
    boolean asc = "asc".equalsIgnoreCase(direction);

    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Object> dataQuery = cb.createQuery(type);
    Root<Object> root = dataQuery.from(type);
    List<Predicate> predicates = buildPredicates(cb, root, category, updatedAfter, null, null, search, filters);
    dataQuery.select(root);
    dataQuery.where(predicates.toArray(Predicate[]::new));
    Path<?> sortPath = root.get(sortProperty);
    dataQuery.orderBy(
      asc ? cb.asc(sortPath) : cb.desc(sortPath),
      asc ? cb.asc(root.get("id")) : cb.desc(root.get("id"))
    );

    TypedQuery<Object> typed = entityManager.createQuery(dataQuery);
    if (safeSize != Integer.MAX_VALUE) {
      typed.setFirstResult(safePage * safeSize);
      typed.setMaxResults(safeSize);
    }
    List<Map<String, Object>> items = typed.getResultList().stream()
      .map(entity -> toMap(category, entity))
      .toList();

    CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
    Root<?> countRoot = countQuery.from(type);
    countQuery.select(cb.count(countRoot));
    countQuery.where(buildPredicates(cb, countRoot, category, updatedAfter, null, null, search, filters).toArray(Predicate[]::new));
    long total = entityManager.createQuery(countQuery).getSingleResult();
    boolean hasNext = safeSize != Integer.MAX_VALUE && (long) (safePage + 1) * safeSize < total;
    String nextCursor = hasNext && !items.isEmpty()
      ? String.valueOf(items.get(items.size() - 1).getOrDefault("updatedAt", ""))
      : null;
    return new PageResult<>(items, total, safePage, safeSize == Integer.MAX_VALUE ? items.size() : safeSize, hasNext, nextCursor);
  }

  public long count(String category, String createdAfter, String createdBefore, Map<String, String> filters) {
    Class<?> type = entityType(category);
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    CriteriaQuery<Long> query = cb.createQuery(Long.class);
    Root<?> root = query.from(type);
    query.select(cb.count(root));
    query.where(buildPredicates(cb, root, category, null, createdAfter, createdBefore, null, filters).toArray(Predicate[]::new));
    return entityManager.createQuery(query).getSingleResult();
  }

  public Map<String, Object> get(String category, String id) {
    Map<String, Object> found = find(category, id);
    if (found == null) {
      throw ApiException.notFound("数据不存在");
    }
    return found;
  }

  public Map<String, Object> find(String category, String id) {
    Object entity = entityManager.find(entityType(category), id);
    return entity == null ? null : toMap(category, entity);
  }

  public boolean exists(String category, String id) {
    return entityManager.find(entityType(category), id) != null;
  }

  @Transactional
  public Map<String, Object> upsert(String category, Map<String, Object> payload) {
    String id = text(payload.get("id"));
    if (id == null || id.isBlank()) {
      throw ApiException.badRequest("缺少 id");
    }
    String now = Instant.now().toString();
    payload.putIfAbsent("createdAt", now);
    payload.put("updatedAt", now);

    try {
      Object existing = entityManager.find(entityType(category), id);
      if (existing == null) {
        entityManager.persist(fromMap(category, payload));
      } else {
        assertExpectedVersion(existing, payload.get("version"));
        applyExisting(category, existing, payload);
      }
      if (DataCategory.ROBOT.equals(category)) {
        syncRobotTelemetry(id, payload.get("telemetry"));
      }
      entityManager.flush();
      return toMap(category, entityManager.find(entityType(category), id));
    } catch (ObjectOptimisticLockingFailureException | OptimisticLockException ex) {
      throw ApiException.conflict("数据已被其他人修改，请刷新后重试");
    } catch (DataIntegrityViolationException | org.hibernate.exception.ConstraintViolationException ex) {
      String message = mostSpecificMessage(ex);
      if (message != null && message.toLowerCase().contains("active_robot")) {
        throw ApiException.conflict("机器人已有执行中的任务");
      }
      if (message != null && message.toLowerCase().contains("uq_work_orders_alarm")) {
        throw ApiException.conflict("该告警已存在工单");
      }
      throw ApiException.badRequest("数据约束冲突，请检查关联对象是否存在或状态是否合法");
    }
  }

  @Transactional
  public Map<String, Object> patch(String category, String id, Map<String, Object> patch) {
    Map<String, Object> current = get(category, id);
    Map<String, Object> merged = new LinkedHashMap<>(current);
    Object patchVersion = patch == null ? null : patch.get("version");
    if (patch != null) {
      merged.putAll(patch);
    }
    merged.put("id", id);
    if (patchVersion != null) {
      merged.put("version", patchVersion);
    } else {
      merged.put("version", current.get("version"));
    }
    return upsert(category, merged);
  }

  @Transactional
  public void delete(String category, String id) {
    if (DataCategory.WORK_ORDER.equals(category)) {
      entityManager.createQuery("delete from WorkOrderTransitionEntity t where t.workOrderId = :id")
        .setParameter("id", id)
        .executeUpdate();
    }
    if (DataCategory.ROBOT.equals(category)) {
      RobotTelemetryEntity telemetry = entityManager.find(RobotTelemetryEntity.class, id);
      if (telemetry != null) {
        entityManager.remove(telemetry);
        entityManager.flush();
      }
    }
    Object managed = entityManager.find(entityType(category), id);
    if (managed != null) {
      entityManager.remove(managed);
    }
  }

  @Transactional
  public void deleteWhere(String category, String field, String value) {
    list(category).stream()
      .filter(item -> value.equals(text(item.get(field))))
      .map(item -> text(item.get("id")))
      .filter(id -> id != null && !id.isBlank())
      .forEach(id -> delete(category, id));
  }

  private void syncRobotTelemetry(String robotId, Object telemetry) {
    if (!(telemetry instanceof Map<?, ?> raw)) {
      return;
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) raw;
    RobotTelemetryEntity existing = entityManager.find(RobotTelemetryEntity.class, robotId);
    RobotTelemetryEntity next = RobotTelemetryEntity.fromMap(robotId, map);
    if (existing == null) {
      entityManager.persist(next);
    } else {
      next.setVersion(existing.getVersion());
      entityManager.merge(next);
    }
  }

  private List<Predicate> buildPredicates(
      CriteriaBuilder cb,
      Root<?> root,
      String category,
      String updatedAfter,
      String createdAfter,
      String createdBefore,
      String search,
      Map<String, String> filters) {
    List<Predicate> predicates = new ArrayList<>();
    if (updatedAfter != null && !updatedAfter.isBlank()) {
      predicates.add(cb.greaterThanOrEqualTo(root.get("updatedAt"), updatedAfter));
    }
    if (createdAfter != null && !createdAfter.isBlank()) {
      predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
    }
    if (createdBefore != null && !createdBefore.isBlank()) {
      predicates.add(cb.lessThan(root.get("createdAt"), createdBefore));
    }
    if (search != null && !search.isBlank()) {
      Predicate searchPredicate = searchPredicate(cb, root, category, search.trim().toLowerCase());
      if (searchPredicate != null) {
        predicates.add(searchPredicate);
      }
    }
    if (DataCategory.WORK_ORDER.equals(category) && filters != null) {
      String viewerId = filters.get("_viewerId");
      String viewerName = filters.get("_viewerName");
      if (viewerId != null && !viewerId.isBlank()) {
        Path<String> assigneeId = root.get("assigneeId");
        Path<String> assigneeName = root.get("assigneeName");
        Path<String> createdByName = root.get("createdByName");
        Predicate noAssigneeId = cb.or(cb.isNull(assigneeId), cb.equal(assigneeId, ""));
        Predicate unassignedName = cb.or(
          cb.isNull(assigneeName), cb.equal(assigneeName, ""), cb.equal(assigneeName, createdByName));
        Predicate ownLegacy = viewerName == null || viewerName.isBlank()
          ? cb.disjunction()
          : cb.and(noAssigneeId, cb.equal(assigneeName, viewerName));
        predicates.add(cb.or(
          cb.equal(assigneeId, viewerId),
          ownLegacy,
          cb.and(noAssigneeId, unassignedName)
        ));
      }
    }
    if (filters != null) {
      filters.forEach((field, value) -> {
        if (field == null || value == null || value.isBlank()) {
          return;
        }
        String attribute = mapFilterAttribute(category, field);
        if (attribute == null || !hasAttribute(root, attribute)) {
          return;
        }
        String[] parts = value.split(",");
        List<Predicate> alternatives = new ArrayList<>();
        for (String part : parts) {
          String token = part.trim();
          if (token.isEmpty()) continue;
          Path<?> path = root.get(attribute);
          Class<?> javaType = path.getJavaType();
          if (javaType == Boolean.class || javaType == boolean.class) {
            alternatives.add(cb.equal(path, Boolean.parseBoolean(token)));
          } else if (Number.class.isAssignableFrom(javaType)) {
            alternatives.add(cb.equal(path, Long.parseLong(token)));
          } else {
            alternatives.add(cb.equal(path, token));
          }
        }
        if (!alternatives.isEmpty()) {
          predicates.add(cb.or(alternatives.toArray(Predicate[]::new)));
        }
      });
    }
    return predicates;
  }

  private Predicate searchPredicate(CriteriaBuilder cb, Root<?> root, String category, String q) {
    String pattern = "%" + escapeLike(q) + "%";
    List<Expression<String>> fields = searchFields(root, category);
    if (fields.isEmpty()) {
      return null;
    }
    List<Predicate> likes = new ArrayList<>();
    for (Expression<String> field : fields) {
      likes.add(cb.like(cb.lower(field), pattern, '\\'));
    }
    return cb.or(likes.toArray(Predicate[]::new));
  }

  private List<Expression<String>> searchFields(Root<?> root, String category) {
    List<Expression<String>> fields = new ArrayList<>();
    switch (category) {
      case DataCategory.SITE, DataCategory.ROUTE, DataCategory.ROBOT, DataCategory.TASK -> addString(fields, root, "name");
      case DataCategory.ALARM -> {
        addString(fields, root, "message");
        addString(fields, root, "type");
        addString(fields, root, "routeName");
        addString(fields, root, "checkpointName");
      }
      case DataCategory.WORK_ORDER -> {
        addString(fields, root, "title");
        addString(fields, root, "description");
        addString(fields, root, "locationDescription");
      }
      case DataCategory.NOTIFICATION -> {
        addString(fields, root, "title");
        addString(fields, root, "content");
      }
      case DataCategory.EVENT -> {
        addString(fields, root, "message");
        addString(fields, root, "type");
      }
      case DataCategory.RECORD -> {
        addString(fields, root, "taskName");
        addString(fields, root, "routeName");
        addString(fields, root, "summary");
      }
      default -> {
      }
    }
    return fields;
  }

  private void addString(List<Expression<String>> fields, Root<?> root, String attribute) {
    if (hasAttribute(root, attribute)) {
      fields.add(root.<String>get(attribute));
    }
  }

  private boolean hasAttribute(Root<?> root, String attribute) {
    try {
      root.get(attribute);
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }

  private String mapFilterAttribute(String category, String field) {
    if ("read".equals(field) && DataCategory.NOTIFICATION.equals(category)) {
      return "readFlag";
    }
    return field;
  }

  private String resolveSort(String category, String sort) {
    if (sort == null || sort.isBlank()) {
      return "updatedAt";
    }
    return switch (sort) {
      case "id", "createdAt", "updatedAt", "status", "severity", "name", "priority" -> sort;
      case "acknowledged" -> DataCategory.ALARM.equals(category) ? "acknowledged" : "updatedAt";
      default -> "updatedAt";
    };
  }

  private Class<?> entityType(String category) {
    return switch (category) {
      case DataCategory.SITE -> SiteEntity.class;
      case DataCategory.ROUTE -> RouteEntity.class;
      case DataCategory.ROBOT -> RobotEntity.class;
      case DataCategory.TASK -> InspectionTaskEntity.class;
      case DataCategory.RECORD -> InspectionRecordEntity.class;
      case DataCategory.EVENT -> TaskEventEntity.class;
      case DataCategory.ALARM -> AlarmEntity.class;
      case DataCategory.WORK_ORDER -> WorkOrderEntity.class;
      case DataCategory.NOTIFICATION -> NotificationEntity.class;
      default -> throw ApiException.badRequest("不支持的领域类别: " + category);
    };
  }

  private Object fromMap(String category, Map<String, Object> payload) {
    return switch (category) {
      case DataCategory.SITE -> SiteEntity.fromMap(payload);
      case DataCategory.ROUTE -> RouteEntity.fromMap(payload);
      case DataCategory.ROBOT -> RobotEntity.fromMap(payload);
      case DataCategory.TASK -> InspectionTaskEntity.fromMap(payload);
      case DataCategory.RECORD -> InspectionRecordEntity.fromMap(payload);
      case DataCategory.EVENT -> TaskEventEntity.fromMap(payload);
      case DataCategory.ALARM -> AlarmEntity.fromMap(payload);
      case DataCategory.WORK_ORDER -> WorkOrderEntity.fromMap(payload);
      case DataCategory.NOTIFICATION -> NotificationEntity.fromMap(payload);
      default -> throw ApiException.badRequest("不支持的领域类别: " + category);
    };
  }

  private void applyExisting(String category, Object existing, Map<String, Object> payload) {
    switch (category) {
      case DataCategory.SITE -> ((SiteEntity) existing).apply(payload);
      case DataCategory.ROUTE -> ((RouteEntity) existing).apply(payload);
      case DataCategory.ROBOT -> ((RobotEntity) existing).apply(payload);
      case DataCategory.TASK -> ((InspectionTaskEntity) existing).apply(payload);
      case DataCategory.RECORD -> ((InspectionRecordEntity) existing).apply(payload);
      case DataCategory.EVENT -> ((TaskEventEntity) existing).apply(payload);
      case DataCategory.ALARM -> ((AlarmEntity) existing).apply(payload);
      case DataCategory.WORK_ORDER -> ((WorkOrderEntity) existing).apply(payload);
      case DataCategory.NOTIFICATION -> ((NotificationEntity) existing).apply(payload);
      default -> throw ApiException.badRequest("不支持的领域类别: " + category);
    }
  }

  private Map<String, Object> toMap(String category, Object entity) {
    Map<String, Object> map = switch (category) {
      case DataCategory.SITE -> ((SiteEntity) entity).toMap();
      case DataCategory.ROUTE -> ((RouteEntity) entity).toMap();
      case DataCategory.ROBOT -> ((RobotEntity) entity).toMap();
      case DataCategory.TASK -> ((InspectionTaskEntity) entity).toMap();
      case DataCategory.RECORD -> ((InspectionRecordEntity) entity).toMap();
      case DataCategory.EVENT -> ((TaskEventEntity) entity).toMap();
      case DataCategory.ALARM -> ((AlarmEntity) entity).toMap();
      case DataCategory.WORK_ORDER -> ((WorkOrderEntity) entity).toMap();
      case DataCategory.NOTIFICATION -> ((NotificationEntity) entity).toMap();
      default -> throw ApiException.badRequest("不支持的领域类别: " + category);
    };
    Long version = readVersion(entity);
    if (version != null) {
      map.put("version", version);
    }
    if (DataCategory.ROBOT.equals(category)) {
      RobotTelemetryEntity telemetry = entityManager.find(RobotTelemetryEntity.class, map.get("id"));
      if (telemetry != null) {
        map.put("telemetry", telemetry.toMap());
      }
    }
    return map;
  }

  private void assertExpectedVersion(Object existing, Object expectedVersion) {
    if (expectedVersion == null) {
      return;
    }
    Long current = readVersion(existing);
    Long expected = toLong(expectedVersion);
    if (current != null && expected != null && !current.equals(expected)) {
      throw ApiException.conflict("数据已被其他人修改，请刷新后重试");
    }
  }

  private Long readVersion(Object entity) {
    try {
      Object value = entity.getClass().getMethod("getVersion").invoke(entity);
      return toLong(value);
    } catch (Exception ex) {
      return null;
    }
  }

  private static Long toLong(Object value) {
    if (value == null) return null;
    if (value instanceof Number number) return number.longValue();
    try {
      return Long.parseLong(value.toString());
    } catch (Exception ex) {
      return null;
    }
  }

  private static String mostSpecificMessage(Throwable throwable) {
    Throwable current = throwable;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    String message = current.getMessage();
    return message == null ? "" : message;
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
