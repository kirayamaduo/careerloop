package com.group1.career.repository;

import com.group1.career.model.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    List<User> findByOrgId(Long orgId);

    Page<User> findAllByDeletedAtIsNullOrderByCreatedAtDesc(Pageable pageable);

    /** F16: search by nickname (case-insensitive LIKE). */
    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL AND LOWER(u.nickname) LIKE LOWER(CONCAT('%', :q, '%')) ORDER BY u.createdAt DESC")
    Page<User> searchByNickname(String q, Pageable pageable);

    /** F25: IDs of users whose 30-day grace period has elapsed. */
    @Query("SELECT u.userId FROM User u WHERE u.deletedAt IS NOT NULL AND u.deletedAt <= :cutoff")
    List<Long> findExpiredDeletionIds(LocalDateTime cutoff);

    /** Atomic generation bump so concurrent security events cannot lose a revocation. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.authVersion = COALESCE(u.authVersion, 0) + 1 WHERE u.userId = :userId")
    int incrementAuthVersion(@Param("userId") Long userId);
}
