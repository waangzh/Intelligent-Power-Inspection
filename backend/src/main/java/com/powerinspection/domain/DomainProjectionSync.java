package com.powerinspection.domain;

import com.powerinspection.alarm.AlarmEntity;
import com.powerinspection.data.DataCategory;
import com.powerinspection.notification.NotificationEntity;
import com.powerinspection.robot.RobotEntity;
import com.powerinspection.robot.RobotTelemetryEntity;
import com.powerinspection.route.RouteEntity;
import com.powerinspection.site.SiteEntity;
import com.powerinspection.task.InspectionTaskEntity;
import com.powerinspection.task.TaskEventEntity;
import com.powerinspection.workorder.WorkOrderEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DomainProjectionSync {
  @PersistenceContext
  private EntityManager entityManager;

  @Transactional
  public void upsert(String category, Map<String, Object> payload) {
    if (category == null || payload == null || payload.get("id") == null) {
      return;
    }
    String id = String.valueOf(payload.get("id"));
    switch (category) {
      case DataCategory.SITE -> replace(SiteEntity.class, id, SiteEntity.fromMap(payload));
      case DataCategory.ROUTE -> replace(RouteEntity.class, id, RouteEntity.fromMap(payload));
      case DataCategory.ROBOT -> {
        replace(RobotEntity.class, id, RobotEntity.fromMap(payload));
        Object telemetry = payload.get("telemetry");
        if (telemetry instanceof Map<?, ?> map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> telemetryMap = (Map<String, Object>) map;
          replace(RobotTelemetryEntity.class, id, RobotTelemetryEntity.fromMap(id, telemetryMap));
        }
      }
      case DataCategory.TASK -> replace(InspectionTaskEntity.class, id, InspectionTaskEntity.fromMap(payload));
      case DataCategory.EVENT -> replace(TaskEventEntity.class, id, TaskEventEntity.fromMap(payload));
      case DataCategory.ALARM -> {
        AlarmEntity entity = entityManager.find(AlarmEntity.class, id);
        if (entity == null) {
          entity = AlarmEntity.fromMap(payload);
          entityManager.persist(entity);
        } else {
          entity.apply(payload);
        }
      }
      case DataCategory.WORK_ORDER -> {
        WorkOrderEntity existing = entityManager.find(WorkOrderEntity.class, id);
        WorkOrderEntity next = WorkOrderEntity.fromMap(payload);
        if (existing == null) {
          entityManager.persist(next);
        } else {
          next.setVersion(existing.getVersion());
          entityManager.merge(next);
        }
      }
      case DataCategory.NOTIFICATION -> replace(NotificationEntity.class, id, NotificationEntity.fromMap(payload));
      default -> {
      }
    }
  }

  @Transactional
  public void delete(String category, String id) {
    if (category == null || id == null || id.isBlank()) {
      return;
    }
    Class<?> type = switch (category) {
      case DataCategory.SITE -> SiteEntity.class;
      case DataCategory.ROUTE -> RouteEntity.class;
      case DataCategory.ROBOT -> RobotEntity.class;
      case DataCategory.TASK -> InspectionTaskEntity.class;
      case DataCategory.EVENT -> TaskEventEntity.class;
      case DataCategory.ALARM -> AlarmEntity.class;
      case DataCategory.WORK_ORDER -> WorkOrderEntity.class;
      case DataCategory.NOTIFICATION -> NotificationEntity.class;
      default -> null;
    };
    if (DataCategory.WORK_ORDER.equals(category)) {
      entityManager.createQuery("delete from WorkOrderTransitionEntity t where t.workOrderId = :id")
        .setParameter("id", id)
        .executeUpdate();
    }
    if (type != null) {
      removeIfPresent(type, id);
    }
    if (DataCategory.ROBOT.equals(category)) {
      removeIfPresent(RobotTelemetryEntity.class, id);
    }
  }

  private <T> void replace(Class<T> type, String id, T next) {
    removeIfPresent(type, id);
    entityManager.persist(next);
  }

  private void removeIfPresent(Class<?> type, String id) {
    Object managed = entityManager.find(type, id);
    if (managed != null) {
      entityManager.remove(managed);
      entityManager.flush();
    }
  }
}
