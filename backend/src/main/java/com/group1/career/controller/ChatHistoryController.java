package com.group1.career.controller;

import com.group1.career.common.ErrorCode;
import com.group1.career.common.Result;
import com.group1.career.exception.BizException;
import com.group1.career.model.entity.AssistantMessage;
import com.group1.career.model.entity.AssistantSession;
import com.group1.career.repository.AssistantMessageRepository;
import com.group1.career.repository.AssistantSessionRepository;
import com.group1.career.service.ConversationSummaryService;
import com.group1.career.service.UserFactService;
import com.group1.career.utils.SecurityUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Chat History API", description = "Cloud-synced chat session persistence")
@RestController
@RequestMapping("/api/chat/history")
@RequiredArgsConstructor
public class ChatHistoryController {

    private final AssistantSessionRepository sessionRepository;
    private final AssistantMessageRepository messageRepository;
    private final ConversationSummaryService summaryService;
    private final UserFactService userFactService;

    @Operation(summary = "Get all chat sessions for the current user (sorted by latest)")
    @GetMapping("/{userId}")
    public Result<List<AssistantSession>> getUserSessions(@PathVariable Long userId) {
        // Path userId is kept for backwards compat with the frontend, but
        // we always honour the JWT-derived id and reject mismatches so a
        // caller cannot enumerate other users' sessions.
        Long uid = SecurityUtil.requireCurrentUserId();
        if (!uid.equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return Result.success(sessionRepository.findByUserIdOrderByUpdatedAtDesc(uid));
    }

    @Operation(summary = "Get all messages in a session (owner-only)")
    @GetMapping("/session/{sessionId}")
    public Result<List<AssistantMessage>> getSessionMessages(@PathVariable Long sessionId) {
        Long uid = SecurityUtil.requireCurrentUserId();
        assertOwnership(sessionId, uid);
        return Result.success(messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId));
    }

    @Operation(summary = "Create a new chat session for the current user")
    @PostMapping("/create")
    public Result<AssistantSession> createSession(@RequestBody CreateSessionDto request) {
        // Always trust the JWT subject; never allow the body to set userId.
        Long uid = SecurityUtil.requireCurrentUserId();
        AssistantSession session = AssistantSession.builder()
                .userId(uid)
                .title(request.getTitle() != null ? request.getTitle() : "新的求职对话")
                .persona(request.getPersona() != null ? request.getPersona() : "MENTOR")
                .build();
        return Result.success(sessionRepository.save(session));
    }

    @Operation(summary = "Append a message pair to an existing session (owner-only)")
    @PostMapping("/session/{sessionId}/append")
    @Transactional
    public Result<AssistantSession> appendMessage(
            @PathVariable Long sessionId,
            @RequestBody AppendMessageDto request) {

        Long uid = SecurityUtil.requireCurrentUserId();
        AssistantSession session = assertOwnership(sessionId, uid);

        messageRepository.save(AssistantMessage.builder()
                .sessionId(sessionId)
                .role(AssistantMessage.MessageRole.user)
                .content(request.getUserMessage())
                .build());

        messageRepository.save(AssistantMessage.builder()
                .sessionId(sessionId)
                .role(AssistantMessage.MessageRole.assistant)
                .content(request.getAssistantReply())
                .build());

        if ("New Conversation".equals(session.getTitle()) || "新的求职对话".equals(session.getTitle())) {
            String firstMsg = request.getUserMessage();
            if (firstMsg != null && !firstMsg.isBlank()) {
                session.setTitle(firstMsg.length() > 20 ? firstMsg.substring(0, 20) + "..." : firstMsg);
            }
        }
        // Touch updatedAt for every appended turn so history ordering reflects
        // the most recently active session, not only the first-title update.
        sessionRepository.save(session);

        // The async readers must not run until both message rows are committed.
        // Triggering them inside this transaction lets a fast executor observe
        // the previous turn and causes duplicate/missed roll-ups.
        triggerMemoryWorkAfterCommit(uid, session.getPersona(), sessionId);

        return Result.success(session);
    }

    @Operation(summary = "Delete a chat session and all its messages (owner-only)")
    @DeleteMapping("/session/{sessionId}")
    @Transactional
    public Result<String> deleteSession(@PathVariable Long sessionId) {
        Long uid = SecurityUtil.requireCurrentUserId();
        assertOwnership(sessionId, uid);
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
        return Result.success("Session deleted");
    }

    private AssistantSession assertOwnership(Long sessionId, Long userId) {
        AssistantSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException("Session not found"));
        if (!userId.equals(session.getUserId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return session;
    }

    private void triggerMemoryWorkAfterCommit(Long userId, String persona, Long sessionId) {
        Runnable work = () -> {
            summaryService.triggerRollupIfNeeded(userId, persona, sessionId);
            userFactService.extractAndSaveAsync(userId, sessionId);
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            work.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                work.run();
            }
        });
    }

    @Data
    public static class CreateSessionDto {
        private String title;
        private String persona;
    }

    @Data
    public static class AppendMessageDto {
        private String userMessage;
        private String assistantReply;
        private String persona;
    }
}
