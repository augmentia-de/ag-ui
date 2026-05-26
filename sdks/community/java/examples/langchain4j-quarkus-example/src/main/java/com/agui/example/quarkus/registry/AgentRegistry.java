package com.agui.example.quarkus.registry;

import com.agui.core.state.State;
import com.agui.langchain4j.Langchain4jAgent;
import com.agui.langchain4j.tool.LsTool;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AgentRegistry {

    private static final String DEFAULT_MODEL = "gpt-4o";
    private static final String DEFAULT_SYSTEM_MESSAGE = "You are a helpful AI assistant, called Moira.";

    private final Map<String, Langchain4jAgent> agents = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        var agenticChat = createDefaultAgent("moira", DEFAULT_SYSTEM_MESSAGE);
        var sharedState = createSharedStateAgent();

        register("1", agenticChat);
        register("agentic-chat", agenticChat);
        register("AgenticChat", agenticChat);
        register("agentic_chat", agenticChat);

        register("shared-state", sharedState);
        register("SharedState", sharedState);
        register("shared_state", sharedState);

        register("tool-based", agenticChat);
        register("ToolBased", agenticChat);
        register("tool_based_generative_ui", agenticChat);

        register("human_in_the_loop", agenticChat);
        register("agentic_generative_ui", agenticChat);
    }

    public Langchain4jAgent getAgenticChatAgent() {
        return agents.get("agentic-chat");
    }

    public Langchain4jAgent getSharedStateAgent() {
        return agents.get("shared-state");
    }

    public Langchain4jAgent getToolBasedAgent() {
        return getAgenticChatAgent();
    }

    public void register(String id, Langchain4jAgent agent) {
        agents.put(id, agent);
    }

    public Optional<Langchain4jAgent> get(String id) {
        return Optional.ofNullable(agents.get(id));
    }

    public Langchain4jAgent getOrFallback(String id) {
        var found = agents.get(id);
        if (found != null) {
            return found;
        }
        return getAgenticChatAgent();
    }

    private OpenAiStreamingChatModel createChatModel() {
        var apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = "demo";
        }
        var baseUrl = System.getenv("OPENAI_BASE_URL");
        var modelName = System.getenv("OPENAI_MODEL");
        if (modelName == null || modelName.isEmpty()) {
            modelName = DEFAULT_MODEL;
        }

        var chatModelBuilder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (baseUrl != null && !baseUrl.isEmpty()) {
            chatModelBuilder.baseUrl(baseUrl);
        }
        return chatModelBuilder.build();

    }

    private Langchain4jAgent createDefaultAgent(String agentId, String systemMessage) {
        var chatModel = createChatModel();
        var state = new State();

        return Langchain4jAgent.builder()
                .agentId(agentId)
                .chatModel(chatModel)
                .executableTool(LsTool.create(Path.of(System.getProperty("user.dir"))))
                .systemMessage(systemMessage)
                .state(state)
                .build();
    }

    private Langchain4jAgent createSharedStateAgent() {
        var chatModel = createChatModel();
        var state = new State();
        state.set("Language", "Dutch");

        return Langchain4jAgent.builder()
                .agentId("moira-shared")
                .chatModel(chatModel)
                .systemMessage("You are a helpful AI assistant, called Moira. Check the state for preferred language.")
                .state(state)
                .build();
    }
}
