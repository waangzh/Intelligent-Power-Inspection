package com.powerinspection.user;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserActivityRepository extends JpaRepository<UserActivityEntity, String> {
  List<UserActivityEntity> findTop200ByUserIdOrderByCreatedAtDesc(String userId);
}
