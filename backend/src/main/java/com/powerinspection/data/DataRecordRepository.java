package com.powerinspection.data;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataRecordRepository extends JpaRepository<DataRecord, Long> {
  List<DataRecord> findByCategoryOrderByCreatedAtDesc(String category);

  Optional<DataRecord> findByCategoryAndRecordId(String category, String recordId);

  void deleteByCategoryAndRecordId(String category, String recordId);

  boolean existsByCategoryAndRecordId(String category, String recordId);
}
