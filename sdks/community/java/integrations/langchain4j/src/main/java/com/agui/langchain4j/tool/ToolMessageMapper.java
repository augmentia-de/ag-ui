package com.agui.langchain4j.tool;

import com.agui.core.function.FunctionCall;
import com.agui.core.message.*;
import com.agui.core.tool.ToolCall;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ToolMessageMapper {

    public static List<ChatMessage> toLangChain4jMessages(List<BaseMessage> aguiMessages, String systemMessageContent) {
        List<ChatMessage> result = new ArrayList<>();

        if (systemMessageContent != null && !systemMessageContent.isEmpty()) {
            result.add(dev.langchain4j.data.message.SystemMessage.from(systemMessageContent));
        }

        for (BaseMessage msg : aguiMessages) {
            if (msg instanceof com.agui.core.message.UserMessage userMsg) {
                result.add(dev.langchain4j.data.message.UserMessage.from(userMsg.getContent()));
            } else if (msg instanceof AssistantMessage assistantMsg) {
                if (assistantMsg.getToolCalls() != null && !assistantMsg.getToolCalls().isEmpty()) {
                    List<ToolExecutionRequest> requests = toToolExecutionRequests(assistantMsg.getToolCalls());
                    String text = assistantMsg.getContent() != null ? assistantMsg.getContent() : "";
                    result.add(AiMessage.from(text, requests));
                } else {
                    String text = assistantMsg.getContent() != null ? assistantMsg.getContent() : "";
                    result.add(AiMessage.from(text));
                }
            } else if (msg instanceof ToolMessage toolMsg) {
                if (toolMsg.getToolCallId() != null) {
                    result.add(ToolExecutionResultMessage.from(
                            toolMsg.getToolCallId(),
                            "tool_result",
                            toolMsg.getContent() != null ? toolMsg.getContent() : ""
                    ));
                }
            } else if (msg instanceof SystemMessage systemMsg) {
                result.add(dev.langchain4j.data.message.SystemMessage.from(systemMsg.getContent()));
            } else if (msg instanceof DeveloperMessage devMsg) {
                result.add(dev.langchain4j.data.message.UserMessage.from(devMsg.getContent()));
            }
        }

        return result;
    }

    public static List<ToolExecutionRequest> toToolExecutionRequests(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return Collections.emptyList();
        }
        return toolCalls.stream()
                .map(tc -> ToolExecutionRequest.builder()
                        .id(tc.id())
                        .name(tc.function().name())
                        .arguments(tc.function().arguments())
                        .build())
                .collect(Collectors.toList());
    }

    public static List<ToolCall> toAgUiToolCalls(List<ToolExecutionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptyList();
        }
        List<ToolCall> result = new ArrayList<>();
        for (ToolExecutionRequest req : requests) {
            result.add(new ToolCall(
                    req.id(),
                    "function",
                    new FunctionCall(req.name(), req.arguments())
            ));
        }
        return result;
    }

    public static ToolMessage toAgUiToolMessage(ToolExecutionResultMessage resultMsg) {
        ToolMessage tm = new ToolMessage();
        tm.setToolCallId(resultMsg.id());
        tm.setContent(resultMsg.text());
        return tm;
    }

    public static ToolMessage toAgUiToolMessage(String toolCallId, String content, String error) {
        ToolMessage tm = new ToolMessage();
        tm.setToolCallId(toolCallId);
        tm.setContent(content);
        if (error != null) {
            tm.setError(error);
        }
        return tm;
    }

    public static AssistantMessage toAgUiAssistantMessage(AiMessage aiMessage, String messageId, String agentId) {
        AssistantMessage am = new AssistantMessage();
        am.setId(messageId);
        am.setName(agentId);
        am.setContent(aiMessage.text() != null ? aiMessage.text() : "");

        if (aiMessage.hasToolExecutionRequests()) {
            am.setToolCalls(toAgUiToolCalls(aiMessage.toolExecutionRequests()));
        }

        return am;
    }
}
