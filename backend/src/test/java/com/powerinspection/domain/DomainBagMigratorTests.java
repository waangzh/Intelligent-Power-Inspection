package com.powerinspection.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataRecord;
import com.powerinspection.data.DataRecordRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class DomainBagMigratorTests {
  @Autowired private DomainBagMigrator migrator;

  @Autowired private DataRecordRepository dataRecordRepository;

  @Autowired private DomainStoreService domainStore;

  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void preservesLegacyTaskWhenReferencedRouteIsMissing() throws Exception {
    String suffix = Long.toUnsignedString(System.nanoTime());
    String siteId = "legacy-site-" + suffix;
    String taskId = "legacy-task-" + suffix;
    String now = Instant.now().toString();
    try {
      dataRecordRepository.save(
          legacyRecord(
              DataCategory.SITE,
              siteId,
              "{\"id\":\"" + siteId + "\",\"name\":\"Legacy Site\"}",
              now));
      dataRecordRepository.save(
          legacyRecord(
              DataCategory.TASK,
              taskId,
              "{\"id\":\""
                  + taskId
                  + "\",\"name\":\"Legacy Task\","
                  + "\"routeId\":\"missing-route\",\"robotId\":\"missing-robot\"}",
              now));

      migrator.run(new DefaultApplicationArguments());

      assertNotNull(domainStore.find(DataCategory.SITE, siteId));
      assertFalse(dataRecordRepository.existsByCategoryAndRecordId(DataCategory.SITE, siteId));
      assertTrue(dataRecordRepository.existsByCategoryAndRecordId(DataCategory.TASK, taskId));
      assertTrue(domainStore.find(DataCategory.TASK, taskId) == null);
    } finally {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                dataRecordRepository.deleteByCategoryAndRecordId(DataCategory.TASK, taskId);
                dataRecordRepository.deleteByCategoryAndRecordId(DataCategory.SITE, siteId);
                domainStore.delete(DataCategory.SITE, siteId);
              });
    }
  }

  private DataRecord legacyRecord(String category, String recordId, String payload, String now) {
    DataRecord record = new DataRecord();
    record.setCategory(category);
    record.setRecordId(recordId);
    record.setPayload(payload);
    record.setCreatedAt(now);
    record.setUpdatedAt(now);
    return record;
  }
}
