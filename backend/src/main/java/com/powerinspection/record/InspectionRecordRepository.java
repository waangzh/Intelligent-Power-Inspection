package com.powerinspection.record;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface InspectionRecordRepository extends JpaRepository<InspectionRecordEntity, String>, JpaSpecificationExecutor<InspectionRecordEntity> {
}
