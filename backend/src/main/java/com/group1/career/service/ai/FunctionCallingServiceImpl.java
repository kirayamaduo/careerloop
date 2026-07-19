package com.group1.career.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.group1.career.service.AiService;
import com.group1.career.service.ai.tools.AiTool;
import com.group1.career.service.ai.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * F13: Orchestrates the function-calling loop against the DashScope
 * OpenAI-compatible endpoint.
 *
 * <p>Security invariant: every tool's {@code execute()} receives
 * {@code userId} from the authenticated session (passed in by the caller
 * from {@link com.group1.career.utils.SecurityUtil}), never from the LLM's
 * argument payload. The tool schemas deliberately omit a {@code user_id}
 * parameter so the model cannot hallucinate it.</p>
 *
 * <p>Loop cap: at most {@value #MAX_TOOL_CALLS} tool invocations per
 * conversational turn. If the model still wants more after the cap, the
 * most recent assistant message is returned as the final reply.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionCallingServiceImpl implements FunctionCallingService {

    private final AiService aiService;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Hard cap: model cannot issue more than this many tool calls per turn. */
    private static final int MAX_TOOL_CALLS = 5;

    /** Per-tool execution timeout in milliseconds. */
    private static final long TOOL_TIMEOUT_MS = 5_000;

    private static final String AI_SERVICE_ERROR =
            AiService.UNAVAILABLE_MESSAGE;
    private static final String TOOL_UNAVAILABLE_RESULT =
            "{\"ok\":false,\"error\":\"TOOL_UNAVAILABLE\"}";

    @Override
    public String chat(List<Map<String, String>> stringMessages, Long userId) {
        List<Map<String, Object>> messages = toObjectMessages(stringMessages);
        List<Map<String, Object>> toolSchemas = toolRegistry.buildToolSchemas();

        int callCount = 0;
        String lastAssistantText = "";

        while (callCount < MAX_TOOL_CALLS) {
            String rawResponse = aiService.chatWithTools(messages, toolSchemas);

            JsonNode respJson;
            try {
                respJson = objectMapper.readTree(rawResponse);
            } catch (Exception e) {
                log.error("[F13] Model response parse failed: exception={}",
                        e.getClass().getSimpleName());
                return lastAssistantText.isBlank() ? AI_SERVICE_ERROR : lastAssistantText;
            }

            if (respJson.has("error")) {
                log.error("[F13] Model returned an error envelope");
                return lastAssistantText.isBlank() ? AI_SERVICE_ERROR : lastAssistantText;
            }

            JsonNode choices = respJson.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                log.warn("[F13] No choices in response");
                break;
            }

            JsonNode choice = choices.get(0);
            String finishReason = choice.path("finish_reason").asText("");
            JsonNode messageNode = choice.path("message");

            // ── Plain text response ──────────────────────────────────────────
            if (!"tool_calls".equals(finishReason)) {
                return messageNode.path("content").asText(lastAssistantText);
            }

            // ── Tool calls ───────────────────────────────────────────────────
            JsonNode toolCallsNode = messageNode.path("tool_calls");
            if (!toolCallsNode.isArray() || toolCallsNode.isEmpty()) break;

            // Append the assistant's tool_calls message verbatim
            Map<String, Object> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", null);
            List<Map<String, Object>> toolCallsList = new ArrayList<>();
            toolCallsNode.forEach(tc -> {
                Map<String, Object> tcMap = new HashMap<>();
                tcMap.put("id", tc.path("id").asText());
                tcMap.put("type", "function");
                Map<String, Object> fn = new HashMap<>();
                fn.put("name", tc.path("function").path("name").asText());
                fn.put("arguments", tc.path("function").path("arguments").asText());
                tcMap.put("function", fn);
                toolCallsList.add(tcMap);
            });
            assistantMsg.put("tool_calls", toolCallsList);
            messages.add(assistantMsg);

            // Execute each tool and append result
            for (JsonNode tc : toolCallsNode) {
                String toolCallId = tc.path("id").asText();
                String toolName = tc.path("function").path("name").asText();
                String argsJson = tc.path("function").path("arguments").asText("{}");

                String toolResult = executeTool(toolName, argsJson, userId);
                callCount++;
                log.info("[F13] Tool '{}' called by user={}, result length={}", toolName, userId, toolResult.length());

                Map<String, Object> toolMsg = new HashMap<>();
                toolMsg.put("role", "tool");
                toolMsg.put("tool_call_id", toolCallId);
                toolMsg.put("content", toolResult);
                messages.add(toolMsg);

                if (callCount >= MAX_TOOL_CALLS) {
                    log.warn("[F13] Tool call cap ({}) reached for user={}", MAX_TOOL_CALLS, userId);
                    break;
                }
            }
        }

        // Fallback: get a final reply without tools
        if (!lastAssistantText.isBlank()) return lastAssistantText;
        return aiService.chat(toStringMessages(messages));
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private String executeTool(String toolName, String argsJson, Long userId) {
        Optional<AiTool> toolOpt = toolRegistry.find(toolName);
        if (toolOpt.isEmpty()) {
            log.warn("[F13] Unknown tool: {}", toolName);
            return TOOL_UNAVAILABLE_RESULT;
        }
        AiTool tool = toolOpt.get();
        try {
            Map<String, Object> args = objectMapper.readValue(argsJson,
                    objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
            long start = System.currentTimeMillis();
            String result = tool.execute(args, userId);
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > TOOL_TIMEOUT_MS) {
                log.warn("[F13] Tool '{}' took {}ms (>{} ms timeout threshold)", toolName, elapsed, TOOL_TIMEOUT_MS);
            }
            return result != null ? result : "(empty result)";
        } catch (Exception e) {
            log.error("[F13] Tool '{}' execution failed: exception={}",
                    toolName, e.getClass().getSimpleName());
            return TOOL_UNAVAILABLE_RESULT;
        }
    }

    /** Convert simple string messages to Object map format. */
    private List<Map<String, Object>> toObjectMessages(List<Map<String, String>> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (messages == null) return result;
        for (Map<String, String> m : messages) {
            Map<String, Object> om = new HashMap<>();
            om.put("role", m.get("role"));
            om.put("content", m.get("content"));
            result.add(om);
        }
        return result;
    }

    /** Best-effort conversion back to string messages for fallback chat call. */
    private List<Map<String, String>> toStringMessages(List<Map<String, Object>> messages) {
        List<Map<String, String>> result = new ArrayList<>();
        for (Map<String, Object> m : messages) {
            Object role = m.get("role");
            Object content = m.get("content");
            if ("tool".equals(role) || m.containsKey("tool_calls")) continue;
            if (role != null && content instanceof String) {
                result.add(Map.of("role", role.toString(), "content", content.toString()));
            }
        }
        return result;
    }
}
