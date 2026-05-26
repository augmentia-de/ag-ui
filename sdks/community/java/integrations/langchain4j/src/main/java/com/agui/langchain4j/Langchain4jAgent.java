package com.agui.langchain4j;

import com.agui.core.agent.AgentSubscriber;
import com.agui.core.agent.AgentSubscriberParams;
import com.agui.core.agent.RunAgentInput;
import com.agui.core.event.BaseEvent;
import com.agui.core.exception.AGUIException;
import com.agui.core.message.*;
import com.agui.core.state.State;
import com.agui.core.tool.ToolCall;
import com.agui.langchain4j.tool.ExecutableTool;
import com.agui.langchain4j.tool.ToolMessageMapper;
import com.agui.server.LocalAgent;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static com.agui.server.EventFactory.*;

public class Langchain4jAgent extends LocalAgent {

    private final StreamingChatLanguageModel chatModel;
    private final List<ToolSpecification> toolSpecifications;
    private final Map<String, Function<String, String>> toolExecutors;
    protected String systemMessage;
    protected Function<LocalAgent, String> systemMessageProvider;

    protected Langchain4jAgent(Builder builder) {
        super(builder.agentId, builder.state, builder.messages);
        this.chatModel = builder.chatModel;
        this.toolSpecifications = new ArrayList<>(builder.toolSpecifications);
        this.toolExecutors = new HashMap<>(builder.toolExecutors);
        this.systemMessage = builder.systemMessage;
        this.systemMessageProvider = builder.systemMessageProvider;

        if (Objects.isNull(this.systemMessage) && Objects.isNull(this.systemMessageProvider)) {
            throw new AGUIException("Either SystemMessage or SystemMessageProvider should be set.");
        }
    }

    @Override
    protected void run(RunAgentInput input, AgentSubscriber subscriber) {
        this.combineMessages(input);

        var threadId = input.threadId();
        var runId = input.runId();
        var state = input.state();

        com.agui.core.message.UserMessage latestUserMessage;
        try {
            latestUserMessage = (com.agui.core.message.UserMessage) this.getLatestUserMessage(messages);
        } catch (AGUIException e) {
            this.emitEvent(runErrorEvent(e.getMessage()), subscriber);
            return;
        }

        this.emitEvent(runStartedEvent(threadId, runId), subscriber);

        String systemMsgContent = Objects.nonNull(this.systemMessageProvider)
                ? this.systemMessageProvider.apply(this)
                : this.systemMessage;

        var turnContext = new TurnContext(
                threadId,
                runId,
                state,
                systemMsgContent,
                input
        );

        executeTurn(turnContext, subscriber, 0);
    }

    private void executeTurn(TurnContext ctx, AgentSubscriber subscriber, int turnCount) {
        var messageId = UUID.randomUUID().toString();



        this.emitEvent(textMessageStartEvent(messageId, "assistant"), subscriber);

        List<ChatMessage> chatMessages = ToolMessageMapper.toLangChain4jMessages(messages, ctx.systemMessageContent);

        AssistantMessage assistantMessage = new AssistantMessage();
        assistantMessage.setId(messageId);
        assistantMessage.setName(this.agentId);
        assistantMessage.setContent("");

        StringBuilder fullResponse = new StringBuilder();

        var self = this;

        chatModel.generate(chatMessages, toolSpecifications, new StreamingResponseHandler<AiMessage>() {
            @Override
            public void onNext(String token) {
                fullResponse.append(token);
                emitEvent(textMessageContentEvent(messageId, token), subscriber);
                assistantMessage.setContent(assistantMessage.getContent() + token);
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                AiMessage aiMessage = response.content();

                boolean hasToolCalls = aiMessage.hasToolExecutionRequests()
                        && aiMessage.toolExecutionRequests() != null
                        && !aiMessage.toolExecutionRequests().isEmpty();

                if (hasToolCalls) {
                    List<ToolExecutionRequest> toolRequests = aiMessage.toolExecutionRequests();

                    List<BaseEvent> deferredEvents = new ArrayList<>();
                    List<ToolCall> aguiToolCalls = ToolMessageMapper.toAgUiToolCalls(toolRequests);
                    assistantMessage.setToolCalls(aguiToolCalls);

                    for (ToolCall tc : aguiToolCalls) {
                        String toolName = tc.function().name();
                        String toolArgs = tc.function().arguments();
                        String toolCallId = tc.id();

                        deferredEvents.add(toolCallStartEvent(messageId, toolName, toolCallId));
                        deferredEvents.add(toolCallArgsEvent(toolArgs, toolCallId));
                        deferredEvents.add(toolCallEndEvent(toolCallId));
                    }

                    emitEvent(textMessageEndEvent(messageId), subscriber);

                    for (BaseEvent e : deferredEvents) {
                        emitEvent(e, subscriber);
                    }

                    subscriber.onNewMessage(assistantMessage);

                    for (ToolCall tc : aguiToolCalls) {
                        subscriber.onNewToolCall(tc);
                    }

                    messages.add(assistantMessage);

                    List<ToolMessage> toolResults = executeTools(toolRequests, messageId, subscriber);

                    for (ToolMessage tm : toolResults) {
                        messages.add(tm);
                    }

                    executeTurn(ctx, subscriber, turnCount + 1);

                } else {

                    emitEvent(textMessageEndEvent(messageId), subscriber);
                    subscriber.onNewMessage(assistantMessage);
                    emitEvent(runFinishedEvent(ctx.threadId, ctx.runId), subscriber);
                    subscriber.onRunFinalized(new AgentSubscriberParams(
                            ctx.input.messages(),
                            ctx.state,
                            self,
                            ctx.input
                    ));
                }
            }

            @Override
            public void onError(Throwable error) {
                emitEvent(runErrorEvent(error.getMessage()), subscriber);
            }
        });
    }

    private List<ToolMessage> executeTools(
            List<ToolExecutionRequest> requests,
            String parentMessageId,
            AgentSubscriber subscriber
    ) {
        List<ToolMessage> results = new ArrayList<>();

        for (ToolExecutionRequest req : requests) {
            String toolName = req.name();
            String toolCallId = req.id();
            String args = req.arguments();

            String result;
            String error = null;

            Function<String, String> executor = toolExecutors.get(toolName);
            if (executor != null) {
                try {
                    result = executor.apply(args);
                } catch (Exception e) {
                    error = "Tool execution failed: " + e.getMessage();
                    result = "{\"error\": \"" + e.getMessage() + "\"}";
                }
            } else {
                error = "Unknown tool: " + toolName;
                result = "{\"error\": \"Unknown tool: " + toolName + "\"}";
            }

            ToolMessage tm = ToolMessageMapper.toAgUiToolMessage(toolCallId, result, error);
            results.add(tm);

            emitEvent(toolCallResultEvent(toolCallId, result, parentMessageId, Role.tool), subscriber);
        }

        return results;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private StreamingChatLanguageModel chatModel;
        private List<ToolSpecification> toolSpecifications = new ArrayList<>();
        private Map<String, Function<String, String>> toolExecutors = new HashMap<>();
        private String agentId;
        private State state;
        private String systemMessage;
        private Function<LocalAgent, String> systemMessageProvider;
        private List<BaseMessage> messages = new ArrayList<>();

        public Builder chatModel(StreamingChatLanguageModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder toolSpecifications(List<ToolSpecification> toolSpecifications) {
            this.toolSpecifications.addAll(toolSpecifications);
            return this;
        }

        public Builder executableTool(ExecutableTool executableTool) {
            this.toolSpecifications.add(executableTool.specification());
            this.toolExecutors.put(executableTool.name(), executableTool.executor());
            return this;
        }

        public Builder executableTools(List<ExecutableTool> tools) {
            for (ExecutableTool t : tools) {
                executableTool(t);
            }
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder state(State state) {
            this.state = state;
            return this;
        }

        public Builder systemMessage(String systemMessage) {
            this.systemMessage = systemMessage;
            return this;
        }

        public Builder systemMessageProvider(Function<LocalAgent, String> systemMessageProvider) {
            this.systemMessageProvider = systemMessageProvider;
            return this;
        }

        public Builder messages(List<BaseMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Langchain4jAgent build() {
            return new Langchain4jAgent(this);
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "... (" + (s.length() - maxLen) + " more chars)";
    }

    private static class TurnContext {
        final String threadId;
        final String runId;
        final State state;
        final String systemMessageContent;
        final RunAgentInput input;

        TurnContext(String threadId, String runId, State state,
                    String systemMessageContent, RunAgentInput input) {
            this.threadId = threadId;
            this.runId = runId;
            this.state = state;
            this.systemMessageContent = systemMessageContent;
            this.input = input;
        }
    }
}
