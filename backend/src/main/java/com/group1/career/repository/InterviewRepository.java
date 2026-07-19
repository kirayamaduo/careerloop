package com.group1.career.repository;

import com.group1.career.model.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, Long> {
    List<Interview> findByUserIdOrderByStartedAtDesc(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Interview i where i.interviewId = :interviewId")
    Optional<Interview> findByIdForUpdate(@Param("interviewId") Long interviewId);

    /**
     * Distinct user ids that have at least one COMPLETED interview started
     * in the given window. Used by the weekly report job to skip users who
     * never showed up in the relevant period.
     */
    @Query("select distinct i.userId from Interview i " +
            "where i.status = 'COMPLETED' " +
            "and i.startedAt between :from and :to")
    List<Long> findActiveUserIdsBetween(@Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to);

    /** Completed interviews for a given user within [from, to]. */
    List<Interview> findByUserIdAndStatusAndStartedAtBetween(Long userId, String status,
                                                              LocalDateTime from, LocalDateTime to);
}
