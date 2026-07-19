package com.group1.career.model.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * F11: Rolling AI conversation summary per (user, persona).
 *
 * <p>After every N turns the assistant service condenses the oldest messages
 * into a paragraph using qwen-turbo and upserts this row. On the next
 * conversation the summary is injected at the top of the context window so
 * the AI remembers what was discussed without re-sending the full history.</p>
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "conversation_summaries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_summary_user_persona", columnNames = {"user_id", "persona"})
})
public class ConversationSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** MENTOR | CHALLENGER — matches AssistantSession.persona */
    @Column(name = "persona", length = 20, nullable = false)
    @Builder.Default
    private String persona = "MENTOR";

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "turn_count", nullable = false)
    @Builder.Default
    private Integer turnCount = 0;

    @Column(name = "model_used", length = 50)
    private String modelUsed;

    @Column(name = "tokens_consumed", nullable = false)
    @Builder.Default
    private Integer tokensConsumed = 0;

    /**
     * Global assistant-message cursor for this user/persona pair. Message IDs
     * are monotonically increasing across sessions, so the roll-up can include
     * every unsummarized message without replaying the first 20 rows of each
     * session.
     */
    @Column(name = "last_message_id", nullable = false)
    @Builder.Default
    private Long lastMessageId = 0L;

    /**
     * Prevents two application instances from silently overwriting a newer
     * summary when async roll-ups overlap.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
