package com.omni.business.assistance.agent.service.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Component;
import com.omni.framework.spring.config.ConfigLoader;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OllamaChatBackend implements ChatBackend {
    private static final Logger log = LoggerFactory.getLogger(OllamaChatBackend.class);

    private final String ollamaUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaChatBackend() {
        this.ollamaUrl = ConfigLoader.getProperty("ollama.api.url", "http://localhost:11434/api/chat");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getName() {
        return "ollama";
    }

    @Override
    public String chat(String model, List<Map<String, String>> messages, double temperature,
                       String customUrl, String customKey) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model != null ? model : "deepseek-r1:7b");
        request.put("messages", messages);
        request.put("stream", false);
        request.put("options", Map.of("temperature", temperature));

        String json = objectMapper.writeValueAsString(request);
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return "Ollama 错误: " + response.statusCode();
        }
        JsonNode node = objectMapper.readTree(response.body());
        return node.path("message").path("content").asText();
    }
}