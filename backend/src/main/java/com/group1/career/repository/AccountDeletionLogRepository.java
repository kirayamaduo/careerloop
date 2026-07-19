package com.group1.career.repository;

import com.group1.career.model.entity.AccountDeletionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountDeletionLogRepository extends JpaRepository<AccountDeletionLog, Long> {
    Optional<AccountDeletionLog> findTopByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId, String status);
}
