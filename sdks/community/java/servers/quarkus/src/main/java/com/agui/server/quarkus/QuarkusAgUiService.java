package com.agui.server.quarkus;

import com.agui.core.agent.RunAgentParameters;
import com.agui.core.event.BaseEvent;
import com.agui.core.type.EventType;
import com.agui.core.stream.EventStream;
import com.agui.json.ObjectMapperFactory;
import com.agui.server.LocalAgent;
import com.agui.server.dto.AgUiParameters;
import com.agui.server.streamer.AgentStreamer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;

import java.util.function.Consumer;

public class QuarkusAgUiService {

    private final AgentStreamer agentStreamer;
    private final ObjectMapper objectMapper;

    public QuarkusAgUiService(AgentStreamer agentStreamer, ObjectMapper objectMapper) {
        this.agentStreamer = agentStreamer;
        this.objectMapper = objectMapper;
        ObjectMapperFactory.addMixins(this.objectMapper);
    }

    public Multi<String> runAgent(LocalAgent agent, AgUiParameters agUiParameters) {
        var parameters = RunAgentParameters.builder()
                .threadId(agUiParameters.getThreadId())
                .runId(agUiParameters.getRunId())
                .messages(agUiParameters.getMessages())
                .tools(agUiParameters.getTools())
                .context(agUiParameters.getContext())
                .forwardedProps(agUiParameters.getForwardedProps())
                .state(agUiParameters.getState())
                .build();

        return Multi.createFrom().emitter(new Consumer<MultiEmitter<? super String>>() {
            @Override
            public void accept(MultiEmitter<? super String> emitter) {
                var eventStream = new EventStream<BaseEvent>(
                        event -> {
                            try {
                                String eventJson = objectMapper.writeValueAsString(event);
                                EventType type = event.getType();
                                emitter.emit(" " + eventJson);
                            } catch (JsonProcessingException e) {
                                emitter.fail(e);
                            }
                        },
                        err -> {
                            emitter.fail(err);
                        },
                        () -> {
                            emitter.complete();
                        }
                );

                agentStreamer.streamEvents(agent, parameters, eventStream);
            }
        });
    }
}
