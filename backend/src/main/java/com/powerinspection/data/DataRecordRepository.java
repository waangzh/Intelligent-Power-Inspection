package com.powerinspection.data;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DataRecordRepository extends JpaRepository<DataRecord, Long>, JpaSpecificationExecutor<DataRecord> {
  @Query("select record.recordId from DataRecord record where record.category = :category order by record.recordId")
  List<String> findRecordIdsByCategory(@Param("category") String category);
  List<DataRecord> findByCategoryOrderByCreatedAtDesc(String category);

  Optional<DataRecord> findByCategoryAndRecordId(String category, String recordId);

  void deleteByCategoryAndRecordId(String category, String recordId);

  boolean existsByCategoryAndRecordId(String category, String recordId);
}
