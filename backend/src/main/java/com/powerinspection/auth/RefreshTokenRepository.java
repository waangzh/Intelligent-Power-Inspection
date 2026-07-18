package com.powerinspection.auth;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {
  Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from RefreshTokenEntity t where t.tokenHash = :tokenHash")
  Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  List<RefreshTokenEntity> findByUserIdAndRevokedAtIsNull(String userId);

  @Modifying(clearAutomatically = true)
  @Query("update RefreshTokenEntity t set t.revokedAt = :revokedAt where t.userId = :userId and t.revokedAt is null")
  int revokeAllActiveForUser(@Param("userId") String userId, @Param("revokedAt") String revokedAt);

  @Modifying(clearAutomatically = true)
  @Query("update RefreshTokenEntity t set t.revokedAt = :revokedAt where t.familyId = :familyId and t.revokedAt is null")
  int revokeFamily(@Param("familyId") String familyId, @Param("revokedAt") String revokedAt);
}
