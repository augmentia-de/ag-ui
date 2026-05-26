package com.agui.example.httpserver;

import com.agui.core.agent.RunAgentParameters;
import com.agui.core.event.BaseEvent;
import com.agui.core.state.State;
import com.agui.core.stream.EventStream;
import com.agui.json.ObjectMapperFactory;
import com.agui.langchain4j.Langchain4jAgent;
import com.agui.server.dto.AgUiParameters;
import com.agui.server.streamer.AgentStreamer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SimpleHttpServerExample {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final AgentStreamer agentStreamer = new AgentStreamer();

    static {
        ObjectMapperFactory.addMixins(objectMapper);
    }

    private static class AgentRegistry {
        private final Map<String, Langchain4jAgent> agents = new ConcurrentHashMap<>();
        private final Langchain4jAgent defaultAgent;

        public AgentRegistry(Langchain4jAgent agenticChat, Langchain4jAgent sharedState) {
            this.defaultAgent = agenticChat;
            register("1", agenticChat);
            register("agentic-chat", agenticChat);
            register("AgenticChat", agenticChat);
            register("agentic_chat", agenticChat);

            register("shared-state", sharedState);
            register("SharedState", sharedState);
            register("shared_state", sharedState);
        }

        public void register(String id, Langchain4jAgent agent) {
            agents.put(id, agent);
        }

        public Langchain4jAgent getAgenticChatAgent() {
            return defaultAgent;
        }

        public Langchain4jAgent getSharedStateAgent() {
            return agents.get("shared-state");
        }

        public Langchain4jAgent getOrFallback(String id) {
            var found = agents.get(id);
            if (found != null) {
                return found;
            }
            return defaultAgent;
        }
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void handleOptions(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(204, -1);
    }

    private static void handleNotFound(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        var response = "Not Found".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(404, response.length);
        exchange.getResponseBody().write(response);
        exchange.getResponseBody().close();
    }

    private static void handleMethodNotAllowed(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        exchange.sendResponseHeaders(405, -1);
    }

    private static void handleHealth(HttpExchange exchange) throws IOException {
        addCorsHeaders(exchange);
        var ok = "OK".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, ok.length);
        exchange.getResponseBody().write(ok);
        exchange.getResponseBody().close();
    }

    private static void handleSse(HttpExchange exchange, Langchain4jAgent agent) throws IOException {
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            handleOptions(exchange);
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            handleMethodNotAllowed(exchange);
            return;
        }

        AgUiParameters agUiParams;
        try {
            agUiParams = objectMapper.readValue(exchange.getRequestBody(), AgUiParameters.class);
        } catch (Exception e) {
            addCorsHeaders(exchange);
            var error = ("Invalid request body: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, error.length);
            exchange.getResponseBody().write(error);
            exchange.getResponseBody().close();
            return;
        }

        addCorsHeaders(exchange);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().add("Cache-Control", "no-cache");
        exchange.getResponseHeaders().add("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);

        OutputStream outputStream = exchange.getResponseBody();
        var parameters = RunAgentParameters.builder()
                .threadId(agUiParams.getThreadId())
                .runId(agUiParams.getRunId())
                .messages(agUiParams.getMessages())
                .tools(agUiParams.getTools())
                .context(agUiParams.getContext())
                .forwardedProps(agUiParams.getForwardedProps())
                .state(agUiParams.getState())
                .build();

        var future = new CompletableFuture<Void>();
        var eventStream = new EventStream<BaseEvent>(
                event -> {
                    try {
                        var json = objectMapper.writeValueAsString(event);
                        synchronized (outputStream) {
                            outputStream.write("data: ".getBytes(StandardCharsets.UTF_8));
                            outputStream.write(json.getBytes(StandardCharsets.UTF_8));
                            outputStream.write("\n\n".getBytes(StandardCharsets.UTF_8));
                            outputStream.flush();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                },
                err -> {
                    try { outputStream.close(); } catch (Exception ignored) {}
                    future.completeExceptionally(err);
                },
                () -> {
                    try { outputStream.close(); } catch (Exception ignored) {}
                    future.complete(null);
                }
        );

        agentStreamer.streamEvents(agent, parameters, eventStream);
        try {
            future.get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            try { outputStream.close(); } catch (Exception ignored) {}
        }
    }

    public static void main(String[] args) throws Exception {
        var apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = "demo";
        }

        var baseUrl = System.getenv("OPENAI_BASE_URL");
        var modelName = System.getenv("OPENAI_MODEL");
        if (modelName == null || modelName.isEmpty()) {
            modelName = "gpt-4o";
        }

        var chatModelBuilder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName);
        if (baseUrl != null && !baseUrl.isEmpty()) {
            chatModelBuilder.baseUrl(baseUrl);
        }
        var chatModel = chatModelBuilder.build();

        var agenticChatAgent = Langchain4jAgent.builder()
                .agentId("moira")
                .chatModel(chatModel)
                .systemMessage("You are a helpful AI assistant, called Moira.")
                .state(new State())
                .build();

        var sharedState = new State();
        sharedState.set("Language", "Dutch");
        var sharedStateAgent = Langchain4jAgent.builder()
                .agentId("moira-shared")
                .chatModel(chatModel)
                .systemMessage("You are a helpful AI assistant, called Moira. Check the state for preferred language.")
                .state(sharedState)
                .build();

        var registry = new AgentRegistry(agenticChatAgent, sharedStateAgent);

        var port = Integer.parseInt(System.getenv().getOrDefault("HTTP_PORT", "8094"));
        var server = HttpServer.create(new InetSocketAddress(port), 0);

        server.setExecutor(Executors.newCachedThreadPool());

        HttpHandler mainRouter = exchange -> {
            var path = exchange.getRequestURI().getPath();
            var method = exchange.getRequestMethod();

            if ("OPTIONS".equals(method)) {
                handleOptions(exchange);
                return;
            }

            if ("/".equals(path) && "GET".equals(method)) {
                handleHealth(exchange);
                return;
            }

            if ("/agentic_chat/agui".equals(path) && "POST".equals(method)) {
                handleSse(exchange, registry.getAgenticChatAgent());
                return;
            }

            if ("/shared_state/agui".equals(path) && "POST".equals(method)) {
                handleSse(exchange, registry.getSharedStateAgent());
                return;
            }

            if ("/tool_based_generative_ui/agui".equals(path) && "POST".equals(method)) {
                handleSse(exchange, registry.getAgenticChatAgent());
                return;
            }

            if ("/human_in_the_loop/agui".equals(path) && "POST".equals(method)) {
                handleSse(exchange, registry.getAgenticChatAgent());
                return;
            }

            if ("/agentic_generative_ui/agui".equals(path) && "POST".equals(method)) {
                handleSse(exchange, registry.getAgenticChatAgent());
                return;
            }

            if (path.startsWith("/sse/") && "POST".equals(method)) {
                var agentId = path.substring("/sse/".length());
                var agent = registry.getOrFallback(agentId);
                handleSse(exchange, agent);
                return;
            }

            handleNotFound(exchange);
        };

        server.createContext("/", mainRouter);

        server.start();
    }
}
