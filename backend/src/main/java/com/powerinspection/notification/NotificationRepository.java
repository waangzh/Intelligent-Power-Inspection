package com.powerinspection.notification;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<NotificationEntity, String> {
  @Query("""
    select n from NotificationEntity n
    where (n.userId = :userId or n.userId = '*')
      and (:type is null or n.type = :type)
      and (:updatedAfter is null or n.updatedAt > :updatedAfter)
      and (:q is null
        or lower(n.title) like lower(concat('%', :q, '%'))
        or lower(n.content) like lower(concat('%', :q, '%')))
      and not exists (
        select r from NotificationRecipientEntity r
        where r.notificationId = n.id and r.userId = :userId and r.deletedAt is not null
      )
      and (
        :read is null
        or (:read = true and exists (
          select rr from NotificationRecipientEntity rr
          where rr.notificationId = n.id and rr.userId = :userId
            and rr.readAt is not null and rr.deletedAt is null
        ))
        or (:read = false and not exists (
          select rr from NotificationRecipientEntity rr
          where rr.notificationId = n.id and rr.userId = :userId and rr.readAt is not null
        ))
      )
    """)
  Page<NotificationEntity> findVisible(
    @Param("userId") String userId,
    @Param("type") String type,
    @Param("updatedAfter") String updatedAfter,
    @Param("q") String q,
    @Param("read") Boolean read,
    Pageable pageable
  );

  @Query("""
    select n from NotificationEntity n
    where (n.userId = :userId or n.userId = '*')
      and not exists (
        select r from NotificationRecipientEntity r
        where r.notificationId = n.id and r.userId = :userId and r.deletedAt is not null
      )
    """)
  List<NotificationEntity> findAllVisible(@Param("userId") String userId);
}
