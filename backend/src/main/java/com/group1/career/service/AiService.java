package com.group1.career.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

public interface AiService {
    /** One user-safe message shared by every AI entry point. */
    String UNAVAILABLE_MESSAGE =
            "AI 助手暂时繁忙，请稍后再试 🙏（若持续出现请联系客服）";

    /**
     * Chat with AI (Multi-turn, synchronous)
     */
    String chat(List<Map<String, String>> messages);

    /**
     * Chat with AI using a specific model name (e.g., qwen-turbo for summarization).
     */
    String chat(List<Map<String, String>> messages, String model);

    /**
     * Chat with AI (Single-turn shortcut)
     */
    String chat(String prompt);

    /**
     * Stream chat with AI via SSE (Multi-turn, token-by-token)
     */
    SseEmitter streamChat(List<Map<String, String>> messages);

    /**
     * F13: Single model turn with OpenAI-compatible tool schemas.
     * Messages use {@code Map<String, Object>} to support role=tool messages
     * (which carry a {@code tool_call_id} field) and assistant messages that
     * carry a {@code tool_calls} list rather than a plain content string.
     *
     * @param messages   full conversation so far (mutable — caller may append)
     * @param toolSchemas tool schema array built by {@link com.group1.career.service.ai.tools.ToolRegistry}
     * @return raw success JSON for finish_reason/tool_calls, or a stable generic
     * error envelope that never contains upstream response/exception details
     */
    String chatWithTools(List<Map<String, Object>> messages, List<Map<String, Object>> toolSchemas);
}
