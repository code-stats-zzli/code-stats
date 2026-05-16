// 文件路径: src/main/java/com/omni/business/assistance/agent/service/AIChatService.java
package com.omni.business.assistance.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.util.JsonUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Service
public class AIChatService {
    private static final Logger log = LoggerFactory.getLogger(AIChatService.class);

    public static final String BACKEND_OLLAMA = "ollama";
    public static final String BACKEND_DEEPSEEK = "deepseek";
    public static final String BACKEND_CUSTOM = "custom";
    public static final String BACKEND_PROJECT = "project";

    private final String defaultBackend;
    private final String deepseekApiKey;
    private final String ollamaUrl;
    private final String customApiUrl;
    private final String customApiKey;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // 独立项目助手服务
    private final ProjectAssistantService projectAssistant;

    public AIChatService() {
        this.defaultBackend = ConfigLoader.getProperty("ai.backend.default", BACKEND_OLLAMA);
        this.deepseekApiKey = ConfigLoader.getProperty("deepseek.api.key", "");
        this.ollamaUrl = ConfigLoader.getProperty("ollama.api.url", "http://localhost:11434/api/chat");
        this.customApiUrl = ConfigLoader.getProperty("custom.api.url", "");
        this.customApiKey = ConfigLoader.getProperty("custom.api.key", "");

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();

        // 初始化项目助手
        this.projectAssistant = new ProjectAssistantService();
        log.info("AIChatService 初始化完成，默认后端: {}", defaultBackend);
    }

    /**
     * 统一聊天入口
     * @param sessionId 会话ID
     * @param question 原始问题（可为 null）
     * @param messages 消息列表（优先使用）
     * @param backend 后端类型
     * @param model 模型名称
     * @param temperature 温度
     * @param customUrlOverride 自定义URL
     * @param customKeyOverride 自定义Key
     * @return 回答文本
     */
    public String chat(String sessionId, String question, List<Map<String, String>> messages,
                       String backend, String model, double temperature,
                       String customUrlOverride, String customKeyOverride) {
        String useBackend = backend != null ? backend : defaultBackend;

        // 项目助手分支
        if (BACKEND_PROJECT.equals(useBackend)) {
            // 将消息列表转换为 JSON 字符串传递给项目助手
            String userInput = (messages != null && !messages.isEmpty()) ? JsonUtil.toJson(messages.get(messages.size() - 1)) : question;
            return projectAssistant.chat(sessionId, userInput);
        }

        // 其他后端：提取用户最后一条消息作为问题
        String userQuestion = extractLastUserMessage(messages, question);
        if (userQuestion == null || userQuestion.trim().isEmpty()) {
            return "请提供有效的问题内容。";
        }

        switch (useBackend) {
            case BACKEND_OLLAMA:
                return chatWithOllama(model, messages, temperature);
            case BACKEND_DEEPSEEK:
                return chatWithDeepSeek(model, messages, temperature);
            case BACKEND_CUSTOM:
                String url = customUrlOverride != null && !customUrlOverride.isEmpty() ? customUrlOverride : customApiUrl;
                String key = customKeyOverride != null && !customKeyOverride.isEmpty() ? customKeyOverride : customApiKey;
                if (url == null || url.isEmpty()) {
                    return "错误：未配置自定义 API 地址";
                }
                return chatWithCustom(url, key, model, messages, temperature);
            default:
                return "不支持的后端类型: " + useBackend;
        }
    }

    /**
     * 关闭会话（仅对项目助手有状态）
     */
    public void closeSession(String sessionId) {
        projectAssistant.closeSession(sessionId);
    }

    // ==================== 私有辅助方法 ====================
    private String extractLastUserMessage(List<Map<String, String>> messages, String question) {
        if (messages != null && !messages.isEmpty()) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                if ("user".equals(messages.get(i).get("role"))) {
                    return messages.get(i).get("content");
                }
            }
        }
        return question;
    }

    // ==================== Ollama 实现 ====================
    private String chatWithOllama(String model, List<Map<String, String>> messages, double temperature) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model != null ? model : "deepseek-r1:7b");
        request.put("messages", messages);
        request.put("stream", false);
        request.put("options", Map.of("temperature", temperature));
        try {
            String json = objectMapper.writeValueAsString(request);
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());
                return node.path("message").path("content").asText();
            } else {
                return "Ollama 错误: " + response.statusCode();
            }
        } catch (Exception e) {
            log.error("调用 Ollama 失败", e);
            return "调用 Ollama 失败: " + e.getMessage();
        }
    }

    // ==================== DeepSeek 实现 ====================
    private String chatWithDeepSeek(String model, List<Map<String, String>> messages, double temperature) {
        if (deepseekApiKey.isEmpty()) {
            return "DeepSeek API Key 未配置";
        }
        String apiModel = "deepseek-chat";
        if (model != null) {
            if (model.startsWith("deepseek-r1:")) apiModel = "deepseek-reasoner";
            else if (model.startsWith("deepseek-coder:")) apiModel = "deepseek-coder";
            else apiModel = "deepseek-chat";
        }
        Map<String, Object> request = new HashMap<>();
        request.put("model", apiModel);
        request.put("messages", messages);
        request.put("temperature", temperature);
        request.put("stream", false);
        try {
            String json = objectMapper.writeValueAsString(request);
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.deepseek.com/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + deepseekApiKey)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());
                return node.path("choices").get(0).path("message").path("content").asText();
            } else {
                return "DeepSeek API 错误: " + response.statusCode();
            }
        } catch (Exception e) {
            log.error("调用 DeepSeek API 失败", e);
            return "调用 DeepSeek API 失败: " + e.getMessage();
        }
    }

    // ==================== 自定义 API 实现 ====================
    private String chatWithCustom(String url, String apiKey, String model,
                                  List<Map<String, String>> messages, double temperature) {
        Map<String, Object> request = new HashMap<>();
        request.put("model", model != null ? model : "gpt-3.5-turbo");
        request.put("messages", messages);
        request.put("temperature", temperature);
        request.put("stream", false);
        try {
            String json = objectMapper.writeValueAsString(request);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (apiKey != null && !apiKey.isEmpty()) {
                builder.header("Authorization", "Bearer " + apiKey);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return extractResponse(response.body());
            } else {
                return "自定义 API 错误: " + response.statusCode();
            }
        } catch (Exception e) {
            log.error("调用自定义 API 失败", e);
            return "调用自定义 API 失败: " + e.getMessage();
        }
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
            log.warn("解析自定义 API 响应失败: {}", json, e);
            return json;
        }
    }
}