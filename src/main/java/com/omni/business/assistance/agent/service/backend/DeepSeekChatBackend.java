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
public class DeepSeekChatBackend implements ChatBackend {
    private static final Logger log = LoggerFactory.getLogger(DeepSeekChatBackend.class);
    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";

    private final String deepseekApiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DeepSeekChatBackend() {
        this.deepseekApiKey = ConfigLoader.getProperty("deepseek.api.key", "");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getName() {
        return "deepseek";
    }

    @Override
    public String chat(String model, List<Map<String, String>> messages, double temperature,
                       String customUrl, String customKey) throws Exception {
        if (deepseekApiKey == null || deepseekApiKey.isEmpty()) {
            return "DeepSeek API Key 未配置";
        }
        String apiModel = resolveApiModel(model);

        Map<String, Object> request = new HashMap<>();
        request.put("model", apiModel);
        request.put("messages", messages);
        request.put("temperature", temperature);
        request.put("stream", false);

        String json = objectMapper.writeValueAsString(request);
        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + deepseekApiKey)
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            return "DeepSeek API 错误: " + response.statusCode();
        }
        JsonNode node = objectMapper.readTree(response.body());
        return node.path("choices").get(0).path("message").path("content").asText();
    }

    /**
     * 模型名映射从代码中抽出，方便后续改为配置驱动。
     */
    private String resolveApiModel(String model) {
        if (model == null) return "deepseek-chat";
        if (model.startsWith("deepseek-r1:")) return "deepseek-reasoner";
        if (model.startsWith("deepseek-coder:")) return "deepseek-coder";
        return "deepseek-chat";
    }
}