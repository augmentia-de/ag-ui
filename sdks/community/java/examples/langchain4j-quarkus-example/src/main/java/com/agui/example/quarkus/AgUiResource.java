package com.agui.example.quarkus;

import com.agui.core.message.BaseMessage;
import com.agui.example.quarkus.registry.AgentRegistry;
import com.agui.server.quarkus.QuarkusAgUiService;
import com.agui.server.dto.AgUiParameters;
import com.agui.server.streamer.AgentStreamer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;

@Path("/")
@ApplicationScoped
public class AgUiResource {

    @Inject
    AgentRegistry agentRegistry;

    private QuarkusAgUiService agUiService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        var agentStreamer = new AgentStreamer();
        this.agUiService = new QuarkusAgUiService(agentStreamer, new ObjectMapper());
    }

    @POST
    @Path("agentic_chat/agui")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<String> agenticChat(AgUiParameters parameters, @Context HttpHeaders headers) {
        logIncomingRequest("/agentic_chat/agui", parameters);
        return agUiService.runAgent(agentRegistry.getAgenticChatAgent(), parameters);
    }

    @POST
    @Path("shared_state/agui")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<String> sharedState(AgUiParameters parameters, @Context HttpHeaders headers) {
        logIncomingRequest("/shared_state/agui", parameters);
        return agUiService.runAgent(agentRegistry.getSharedStateAgent(), parameters);
    }

    @POST
    @Path("tool_based_generative_ui/agui")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<String> toolBasedGenerativeUi(AgUiParameters parameters, @Context HttpHeaders headers) {
        logIncomingRequest("/tool_based_generative_ui/agui", parameters);
        return agUiService.runAgent(agentRegistry.getToolBasedAgent(), parameters);
    }

    @POST
    @Path("human_in_the_loop/agui")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<String> humanInTheLoop(AgUiParameters parameters, @Context HttpHeaders headers) {
        logIncomingRequest("/human_in_the_loop/agui", parameters);
        return agUiService.runAgent(agentRegistry.getAgenticChatAgent(), parameters);
    }

    @POST
    @Path("agentic_generative_ui/agui")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<String> agenticGenerativeUi(AgUiParameters parameters, @Context HttpHeaders headers) {
        logIncomingRequest("/agentic_generative_ui/agui", parameters);
        return agUiService.runAgent(agentRegistry.getAgenticChatAgent(), parameters);
    }

    @POST
    @Path("/sse/{agentId}")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    public Multi<String> sseAgent(
            @PathParam("agentId") String agentId,
            AgUiParameters parameters,
            @Context HttpHeaders headers
    ) {
        logIncomingRequest("/sse/" + agentId, parameters);
        var agent = agentRegistry.getOrFallback(agentId);
        return agUiService.runAgent(agent, parameters);
    }

    private void logIncomingRequest(String endpoint, AgUiParameters parameters) {
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "... (" + (s.length() - maxLen) + " more chars)";
    }
}
