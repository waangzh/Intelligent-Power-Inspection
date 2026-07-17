package com.powerinspection.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.powerinspection.common.ApiException;
import com.powerinspection.data.DataCategory;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class DomainStoreServiceTests {
  @Autowired
  private DomainStoreService dataStore;

  @Test
  void rejectsStaleExplicitVersion() {
    String siteId = "site-version-" + suffix();
    try {
      Map<String, Object> created = dataStore.upsert(
        DataCategory.SITE, map("id", siteId, "name", "Version Site")
      );
      Object version = created.get("version");

      dataStore.patch(DataCategory.SITE, siteId, map("name", "Updated Site", "version", version));
      ApiException conflict = assertThrows(
        ApiException.class,
        () -> dataStore.patch(DataCategory.SITE, siteId, map("name", "Stale Update", "version", version))
      );

      assertEquals(HttpStatus.CONFLICT, conflict.status());
    } finally {
      dataStore.delete(DataCategory.SITE, siteId);
    }
  }

  @Test
  void mapsSecondActiveTaskForRobotToConflict() {
    String token = suffix();
    String siteId = "site-active-" + token;
    String routeId = "route-active-" + token;
    String robotId = "robot-active-" + token;
    String firstTaskId = "task-active-a-" + token;
    String secondTaskId = "task-active-b-" + token;
    try {
      dataStore.upsert(DataCategory.SITE, map("id", siteId, "name", "Active Site"));
      dataStore.upsert(DataCategory.ROUTE, map(
        "id", routeId, "siteId", siteId, "name", "Active Route"
      ));
      dataStore.upsert(DataCategory.ROBOT, map(
        "id", robotId, "siteId", siteId, "name", "Active Robot", "status", "ONLINE"
      ));
      dataStore.upsert(DataCategory.TASK, task(firstTaskId, siteId, routeId, robotId));
      dataStore.upsert(DataCategory.TASK, task(secondTaskId, siteId, routeId, robotId));
      dataStore.patch(DataCategory.TASK, firstTaskId, map("status", "RUNNING"));

      ApiException conflict = assertThrows(
        ApiException.class,
        () -> dataStore.patch(DataCategory.TASK, secondTaskId, map("status", "PAUSED"))
      );

      assertEquals(HttpStatus.CONFLICT, conflict.status());
    } finally {
      dataStore.delete(DataCategory.TASK, secondTaskId);
      dataStore.delete(DataCategory.TASK, firstTaskId);
      dataStore.delete(DataCategory.ROBOT, robotId);
      dataStore.delete(DataCategory.ROUTE, routeId);
      dataStore.delete(DataCategory.SITE, siteId);
    }
  }

  private Map<String, Object> task(String id, String siteId, String routeId, String robotId) {
    return map(
      "id", id,
      "name", id,
      "siteId", siteId,
      "routeId", routeId,
      "robotId", robotId,
      "status", "CREATED"
    );
  }

  private Map<String, Object> map(Object... values) {
    Map<String, Object> result = new LinkedHashMap<>();
    for (int index = 0; index < values.length; index += 2) {
      result.put(String.valueOf(values[index]), values[index + 1]);
    }
    return result;
  }

  private String suffix() {
    return UUID.randomUUID().toString().replace("-", "");
  }
}
