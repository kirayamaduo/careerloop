package com.group1.career.repository;

import com.group1.career.model.entity.AssistantMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {
    List<AssistantMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);

    /**
     * Read the next unsummarized messages across all sessions that belong to
     * one user/persona. A global msg_id cursor avoids both duplicate roll-ups
     * and gaps when the user alternates between multiple sessions.
     */
    @Query("""
            select m from AssistantMessage m, AssistantSession s
            where m.sessionId = s.sessionId
              and s.userId = :userId
              and s.persona = :persona
              and m.msgId > :afterMessageId
            order by m.msgId asc
            """)
    List<AssistantMessage> findUnsummarized(
            @Param("userId") Long userId,
            @Param("persona") String persona,
            @Param("afterMessageId") Long afterMessageId);

    void deleteBySessionId(Long sessionId);
}
