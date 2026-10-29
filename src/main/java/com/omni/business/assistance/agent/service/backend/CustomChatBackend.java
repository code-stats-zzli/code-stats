package com.omni.business.assistance.agent.service.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class CustomChatBackend implements ChatBackend {

    private final String defaultUrl;
    private final String defaultKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CustomChatBackend() {
        this.defaultUrl = ConfigLoader.getProperty("custom.api.url", "");
        this.defaultKey = ConfigLoader.getProperty("custom.api.key", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getName() {
        return "custom";
    }

    @Override
    public String chat(String model, List<Map<String, String>> messages, double temperature,
                       String customUrl, String customKey) throws Exception {
        String url = (customUrl != null && !customUrl.isEmpty()) ? customUrl : defaultUrl;
        String key = (customKey != null && !customKey.isEmpty()) ? customKey : defaultKey;
        if (url == null || url.isEmpty()) {
            return "错误：未配置自定义 API 地址";
        }

        Map<String, Object> request = new HashMap<>();
        request.put("model", model != null ? model : "gpt-3.5-turbo");
        request.put("messages", messages);
        request.put("temperature", temperature);
        request.put("stream", false);

        String json = objectMapper.writeValueAsString(request);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (key != null && !key.isEmpty()) {
            builder.header("Authorization", "Bearer " + key);
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return "自定义 API 错误: " + response.statusCode();
        }
        return extractResponse(response.body());
    }

    private String extractResponse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.has("choices")) {
                return node.path("choices").get(0).path("message").path("content").asText();
            }
            if (node.has("response")) {
                return node.get("response").asText();
            }
            return json;
        } catch (Exception e) {
            return json;
        }
    }
}