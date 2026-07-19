package com.group1.career.service;

import com.group1.career.model.entity.AssistantMessage;
import com.group1.career.model.entity.ConversationSummary;
import com.group1.career.repository.AssistantMessageRepository;
import com.group1.career.repository.ConversationSummaryRepository;
import com.group1.career.service.impl.ConversationSummaryServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationSummaryServiceTest {

    @Mock
    private AssistantMessageRepository messageRepository;

    @Mock
    private ConversationSummaryRepository summaryRepository;

    @Mock
    private AiService aiService;

    @InjectMocks
    private ConversationSummaryServiceImpl service;

    @Test
    void advancesDurableCursorAndDoesNotReplayTheSameWindow() {
        Long userId = 3L;
        String persona = "MENTOR";
        List<AssistantMessage> window = messages(1, 20);
        AtomicReference<ConversationSummary> stored = new AtomicReference<>();

        when(summaryRepository.findByUserIdAndPersona(userId, persona))
                .thenAnswer(ignored -> Optional.ofNullable(stored.get()));
        when(messageRepository.findUnsummarized(userId, persona, 0L)).thenReturn(window);
        when(messageRepository.findUnsummarized(userId, persona, 20L)).thenReturn(List.of());
        when(aiService.chat(anyList(), eq("qwen-turbo"))).thenReturn("用户希望成为后端工程师。");
        when(summaryRepository.saveAndFlush(any(ConversationSummary.class))).thenAnswer(invocation -> {
            ConversationSummary row = invocation.getArgument(0);
            stored.set(row);
            return row;
        });

        service.triggerRollupIfNeeded(userId, persona, 99L);
        service.triggerRollupIfNeeded(userId, persona, 99L);

        assertEquals(20L, stored.get().getLastMessageId());
        assertEquals(20, stored.get().getTurnCount());
        verify(aiService, times(1)).chat(anyList(), eq("qwen-turbo"));
        verify(summaryRepository, times(1)).saveAndFlush(any(ConversationSummary.class));
    }

    @Test
    void leavesCursorUntouchedWhenThresholdIsNotReached() {
        when(summaryRepository.findByUserIdAndPersona(1L, "MENTOR")).thenReturn(Optional.empty());
        when(messageRepository.findUnsummarized(1L, "MENTOR", 0L)).thenReturn(messages(1, 19));

        service.triggerRollupIfNeeded(1L, "mentor", 10L);

        verifyNoInteractions(aiService);
        verify(summaryRepository, never()).saveAndFlush(any());
    }

    @Test
    void failedAiResponseDoesNotCommitTheWindow() {
        when(summaryRepository.findByUserIdAndPersona(1L, "MENTOR")).thenReturn(Optional.empty());
        when(messageRepository.findUnsummarized(1L, "MENTOR", 0L)).thenReturn(messages(1, 20));
        when(aiService.chat(anyList(), eq("qwen-turbo"))).thenReturn("AI service busy");

        service.triggerRollupIfNeeded(1L, "MENTOR", 10L);

        verify(summaryRepository, never()).saveAndFlush(any());
    }

    private List<AssistantMessage> messages(int fromInclusive, int toInclusive) {
        List<AssistantMessage> rows = new ArrayList<>();
        for (int id = fromInclusive; id <= toInclusive; id++) {
            rows.add(AssistantMessage.builder()
                    .msgId((long) id)
                    .sessionId(99L)
                    .role(id % 2 == 0
                            ? AssistantMessage.MessageRole.assistant
                            : AssistantMessage.MessageRole.user)
                    .content("message-" + id)
                    .build());
        }
        return rows;
    }
}
