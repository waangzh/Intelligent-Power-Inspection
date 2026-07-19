package com.powerinspection.domain;

import com.powerinspection.common.ApiException;
import com.powerinspection.common.JsonStore;
import com.powerinspection.data.DataCategory;
import com.powerinspection.data.DataRecord;
import com.powerinspection.data.DataRecordRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One-time/compatibility import: copy legacy data_records payloads into formal domain tables,
 * then remove those bag rows so formal tables become the only source of truth.
 */
@Component
@Order(1)
public class DomainBagMigrator implements ApplicationRunner {
  private static final Logger log = LoggerFactory.getLogger(DomainBagMigrator.class);

  private static final List<String> CATEGORIES = List.of(
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

  private final DataRecordRepository dataRecordRepository;
  private final DomainStoreService domainStore;
  private final JsonStore jsonStore;
  private final TransactionTemplate transactionTemplate;

  public DomainBagMigrator(
      DataRecordRepository dataRecordRepository,
      DomainStoreService domainStore,
      JsonStore jsonStore,
      PlatformTransactionManager transactionManager) {
    this.dataRecordRepository = dataRecordRepository;
    this.domainStore = domainStore;
    this.jsonStore = jsonStore;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public void run(ApplicationArguments args) {
    int imported = 0;
    int removed = 0;
    int failed = 0;
    for (String category : CATEGORIES) {
      List<DataRecord> records = dataRecordRepository.findByCategoryOrderByCreatedAtDesc(category);
      if (records.isEmpty()) {
        continue;
      }
      for (DataRecord record : records) {
        try {
          transactionTemplate.executeWithoutResult(status -> {
            Map<String, Object> payload = jsonStore.parseObject(record.getPayload());
            if (payload.get("id") == null || String.valueOf(payload.get("id")).isBlank()) {
              payload.put("id", record.getRecordId());
            }
            domainStore.upsert(category, payload);
            dataRecordRepository.deleteByCategoryAndRecordId(category, record.getRecordId());
          });
          imported++;
          removed++;
        } catch (ApiException ex) {
          failed++;
          log.warn(
              "Skipped legacy domain record during migration: category={}, recordId={}, reason={}",
              category,
              record.getRecordId(),
              ex.getMessage());
        }
      }
    }
    if (imported > 0 || removed > 0 || failed > 0) {
      log.info(
          "Domain bag migration complete: imported={}, removedBagRows={}, skippedInvalidRows={}",
          imported,
          removed,
          failed);
    }
  }
}
