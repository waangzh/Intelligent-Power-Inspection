package com.powerinspection.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, String> {
  Optional<UserEntity> findByUsername(String username);

  boolean existsByUsername(String username);

  Optional<UserEntity> findByUsernameAndPhone(String username, String phone);

  List<UserEntity> findByPhone(String phone);

  List<UserEntity> findByRoleAndEnabledTrue(UserRole role);
}
