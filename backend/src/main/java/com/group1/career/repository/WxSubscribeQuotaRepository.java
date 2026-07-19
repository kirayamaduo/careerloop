package com.group1.career.repository;

import com.group1.career.model.entity.WxSubscribeQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WxSubscribeQuotaRepository extends JpaRepository<WxSubscribeQuota, Long> {
    Optional<WxSubscribeQuota> findByUserIdAndTemplateId(Long userId, String templateId);
    List<WxSubscribeQuota> findByUserId(Long userId);

    @Modifying
    @Query("UPDATE WxSubscribeQuota q SET q.remaining = q.remaining - 1, q.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE q.userId = :userId AND q.templateId = :templateId AND q.remaining > 0")
    int decrementRemaining(Long userId, String templateId);

    @Modifying
    @Query("UPDATE WxSubscribeQuota q SET q.remaining = q.remaining + 1, q.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE q.userId = :userId AND q.templateId = :templateId")
    int incrementRemaining(Long userId, String templateId);
}
