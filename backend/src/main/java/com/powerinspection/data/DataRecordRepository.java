package com.powerinspection.data;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DataRecordRepository extends JpaRepository<DataRecord, Long>, JpaSpecificationExecutor<DataRecord> {
  List<DataRecord> findByCategoryOrderByCreatedAtDesc(String category);

  Optional<DataRecord> findByCategoryAndRecordId(String category, String recordId);

  void deleteByCategoryAndRecordId(String category, String recordId);

  boolean existsByCategoryAndRecordId(String category, String recordId);
}
