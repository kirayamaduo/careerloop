package com.group1.career.repository;

import com.group1.career.model.entity.AgentTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentTaskRepository extends JpaRepository<AgentTask, Long> {
    List<AgentTask> findByUserIdAndDueDateOrderByCreatedAtDesc(Long userId, LocalDate dueDate);
    List<AgentTask> findByUserIdAndStatusOrderByDueDateAscCreatedAtDesc(Long userId, String status);
    List<AgentTask> findByUserIdAndDueDateBetweenOrderByDueDateDescCreatedAtDesc(Long userId, LocalDate from, LocalDate to);
    Optional<AgentTask> findByUserIdAndDueDateAndTaskKey(Long userId, LocalDate dueDate, String taskKey);
    Optional<AgentTask> findByUserIdAndTaskKey(Long userId, String taskKey);
    List<AgentTask> findTop20ByUserIdAndTaskTypeOrderByCreatedAtDesc(Long userId, String taskType);

    @Query("SELECT t.taskType, COUNT(t) FROM AgentTask t WHERE t.userId = :userId AND t.status = 'DISMISSED' AND t.dueDate BETWEEN :from AND :to GROUP BY t.taskType")
    List<Object[]> countDismissedByTaskTypeSince(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COUNT(t) FROM AgentTask t WHERE t.userId = :userId AND t.status = 'DONE' AND t.dueDate BETWEEN :from AND :to")
    long countDoneSince(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COUNT(t) FROM AgentTask t WHERE t.userId = :userId AND t.dueDate BETWEEN :from AND :to")
    long countAllSince(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    List<AgentTask> findByParentTaskIdOrderBySubIndexAsc(Long parentTaskId);

    boolean existsByParentTaskId(Long parentTaskId);
}
