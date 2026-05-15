package com.omni.business.assistance.agent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.config.ConfigLoader;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.util.JsonUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Controller
@RequestMapping("/agent/api")
public class ChatController {

    private static final String OLLAMA_API_URL = "http://localhost:11434/api/chat";
    private static final String DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final String DEEPSEEK_API_KEY = ConfigLoader.getProperty("deepseek.api.key", "");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @RequestMapping("/chat")
    @ResponseBody
    public String chat() {
        Request request = RequestContextHolder.getRequest();
        if (request == null) {
            return errorResponse("无法获取请求对象");
        }
        String body = request.getBody();
        if (body == null || body.trim().isEmpty()) {
            return errorResponse("请求体为空");
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            String backend = root.has("backend") ? root.get("backend").asText() : "ollama";

            switch (backend) {
                case "deepseek":
                    return chatWithDeepSeek(root);
                case "custom":
                    return chatWithCustom(root);
                default:
                    return chatWithOllama(root);
            }
        } catch (Exception e) {
            return errorResponse("请求解析失败: " + e.getMessage());
        }
    }

    private String chatWithOllama(JsonNode root) {
        String model = root.has("model") ? root.get("model").asText() : "deepseek-r1:7b";
        double temperature = root.has("temperature") ? root.get("temperature").asDouble() : 0.7;
        List<Map<String, String>> messages = extractMessages(root);

        Map<String, Object> ollamaRequest = new HashMap<>();
        ollamaRequest.put("model", model);
        ollamaRequest.put("messages", messages);
        ollamaRequest.put("stream", false);
        ollamaRequest.put("options", Map.of("temperature", temperature));

        try {
            String json = JsonUtil.toJson(ollamaRequest);
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(OLLAMA_API_URL))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String reply = extractOllamaResponse(response.body());
                return successResponse(reply, model);
            } else {
                return errorResponse("Ollama 返回错误: " + response.statusCode());
            }
        } catch (Exception e) {
            return errorResponse("调用 Ollama 失败: " + e.getMessage());
        }
    }

    private String chatWithDeepSeek(JsonNode root) {
        if (DEEPSEEK_API_KEY.isEmpty()) {
            return errorResponse("DeepSeek API Key 未配置，请在 application.properties 中设置 deepseek.api.key");
        }
        String model = root.has("model") ? root.get("model").asText() : "deepseek-chat";
        if (model.startsWith("deepseek-r1:")) {
            model = "deepseek-reasoner";
        } else if (model.startsWith("deepseek-coder:")) {
            model = "deepseek-coder";
        } else {
            model = "deepseek-chat";
        }
        double temperature = root.has("temperature") ? root.get("temperature").asDouble() : 0.7;
        List<Map<String, String>> messages = extractMessages(root);

        Map<String, Object> deepseekRequest = new HashMap<>();
        deepseekRequest.put("model", model);
        deepseekRequest.put("messages", messages);
        deepseekRequest.put("temperature", temperature);
        deepseekRequest.put("stream", false);

        try {
            String json = JsonUtil.toJson(deepseekRequest);
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(DEEPSEEK_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + DEEPSEEK_API_KEY)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String reply = extractDeepSeekResponse(response.body());
                return successResponse(reply, model);
            } else {
                return errorResponse("DeepSeek API 错误: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            return errorResponse("调用 DeepSeek API 失败: " + e.getMessage());
        }
    }

    private String chatWithCustom(JsonNode root) {
        String customUrl = root.has("customUrl") ? root.get("customUrl").asText() : null;
        if (customUrl == null || customUrl.trim().isEmpty()) {
            return errorResponse("自定义 API 地址不能为空");
        }
        String customKey = root.has("customKey") ? root.get("customKey").asText() : null;
        String model = root.has("model") ? root.get("model").asText() : "deepseek-r1:7b";
        double temperature = root.has("temperature") ? root.get("temperature").asDouble() : 0.7;
        List<Map<String, String>> messages = extractMessages(root);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", messages);
        requestBody.put("temperature", temperature);
        requestBody.put("stream", false);

        try {
            String json = JsonUtil.toJson(requestBody);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(customUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(json));
            if (customKey != null && !customKey.trim().isEmpty()) {
                builder.header("Authorization", "Bearer " + customKey);
            }
            HttpRequest httpReq = builder.build();
            HttpResponse<String> response = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String reply = extractGenericResponse(response.body());
                return successResponse(reply, model);
            } else {
                return errorResponse("自定义 API 错误: " + response.statusCode() + " - " + response.body());
            }
        } catch (Exception e) {
            return errorResponse("调用自定义 API 失败: " + e.getMessage());
        }
    }

    private List<Map<String, String>> extractMessages(JsonNode root) {
        List<Map<String, String>> messages = new ArrayList<>();
        JsonNode msgArray = root.get("messages");
        if (msgArray != null && msgArray.isArray()) {
            for (JsonNode msg : msgArray) {
                Map<String, String> m = new HashMap<>();
                if (msg.has("role")) m.put("role", msg.get("role").asText());
                if (msg.has("content")) m.put("content", msg.get("content").asText());
                messages.add(m);
            }
        }
        if (messages.isEmpty()) {
            messages.add(Map.of("role", "user", "content", "你好"));
        }
        return messages;
    }

    private String extractOllamaResponse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.path("message").path("content").asText();
        } catch (Exception e) {
            return "解析响应失败";
        }
    }

    private String extractDeepSeekResponse(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            return "解析响应失败";
        }
    }

    private String extractGenericResponse(String json) {
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

    private String successResponse(String reply, String model) {
        Map<String, Object> result = new HashMap<>();
        result.put("response", reply);
        result.put("model", model);
        return JsonUtil.toJson(result);
    }

    private String errorResponse(String msg) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", msg);
        return JsonUtil.toJson(error);
    }
}