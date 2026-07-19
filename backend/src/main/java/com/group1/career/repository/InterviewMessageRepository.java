package com.group1.career.repository;

import com.group1.career.model.entity.InterviewMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, Long> {
    List<InterviewMessage> findByInterviewIdOrderByCreatedAtAsc(Long interviewId);

    boolean existsByInterviewIdAndRoleIgnoreCase(Long interviewId, String role);

    void deleteByInterviewId(Long interviewId);
}
