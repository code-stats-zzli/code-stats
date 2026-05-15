package com.codestats.business.assistance.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.codestats.framework.spring.annotation.Service;
import com.codestats.framework.spring.config.ConfigLoader;
import com.codestats.framework.log.LoggerFactory;
import com.codestats.framework.log.core.Logger;
import com.microsoft.playwright.Page;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AIChatService {
    private static final Logger log = LoggerFactory.getLogger(AIChatService.class);

    public static final String BACKEND_OLLAMA = "ollama";
    public static final String BACKEND_DEEPSEEK = "deepseek";
    public static final String BACKEND_CUSTOM = "custom";

    private final String defaultBackend;
    private final boolean playwrightEnabled;
    private final String deepseekApiKey;
    private final String ollamaUrl;
    private final String customApiUrl;
    private final String customApiKey;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, Page> sessionPages = new ConcurrentHashMap<>();
    private final boolean playwrightAvailable;

    public AIChatService() {
        this.defaultBackend = ConfigLoader.getProperty("ai.backend.default", BACKEND_OLLAMA);
        this.playwrightEnabled = "true".equalsIgnoreCase(ConfigLoader.getProperty("playwright.enabled", "false"));
        this.deepseekApiKey = ConfigLoader.getProperty("deepseek.api.key", "");
        this.ollamaUrl = ConfigLoader.getProperty("ollama.api.url", "http://localhost:11434/api/chat");
        this.customApiUrl = ConfigLoader.getProperty("custom.api.url", "");
        this.customApiKey = ConfigLoader.getProperty("custom.api.key", "");

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();

        boolean tempAvailable = false;
        if (playwrightEnabled) {
            try {
                tempAvailable = com.codestats.common.util.PlaywrightStealthUtil.isAvailable();
            } catch (Exception e) {
                log.error("Playwright 不可用", e);
            }
        }
        this.playwrightAvailable = tempAvailable;
        log.info("AIChatService 初始化: backend={}, playwrightEnabled={}, playwrightAvailable={}",
                defaultBackend, playwrightEnabled, playwrightAvailable);
    }

    public String chat(String sessionId, String question, List<Map<String, String>> messages,
                       String backend, String model, double temperature,
                       String customUrlOverride, String customKeyOverride) {
        String useBackend = backend != null ? backend : defaultBackend;

        if (BACKEND_CUSTOM.equals(useBackend) && playwrightAvailable && sessionId != null) {
            return chatWithPlaywright(sessionId, question, 60);
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

    private String chatWithPlaywright(String sessionId, String question, int timeoutSeconds) {
        if (!playwrightAvailable) {
            return "Playwright 未启用或初始化失败，请检查配置和环境";
        }
        Page page = sessionPages.computeIfAbsent(sessionId, this::createNewPlaywrightPage);
        try {
            page.waitForSelector("textarea:visible", new Page.WaitForSelectorOptions().setTimeout(10000));
            page.locator("textarea").first().fill(question);
            page.keyboard().press("Enter");
            page.waitForSelector(".ds-markdown, .message-assistant, [class*='assistant']",
                    new Page.WaitForSelectorOptions().setTimeout(timeoutSeconds * 1000L));
            String reply = (String) page.evaluate("() => {\n" +
                    "  const assistants = document.querySelectorAll('[class*=\"assistant\"]');\n" +
                    "  if (assistants.length === 0) return '';\n" +
                    "  const last = assistants[assistants.length - 1];\n" +
                    "  return last.innerText.trim();\n" +
                    "}");
            return reply != null && !reply.isEmpty() ? reply : "未获取到回复";
        } catch (Exception e) {
            closeSession(sessionId);
            throw new RuntimeException("Playwright 聊天失败: " + e.getMessage(), e);
        }
    }

    private Page createNewPlaywrightPage(String sessionId) {
        Page page = com.codestats.common.util.PlaywrightStealthUtil.createDefaultPage();
        try {
            com.codestats.common.util.PlaywrightStealthUtil.navigateAndWait(page, "https://chat.deepseek.com/", 60);
            page.waitForSelector("textarea:visible", new Page.WaitForSelectorOptions().setTimeout(30000));
            return page;
        } catch (Exception e) {
            page.close();
            throw new RuntimeException("无法加载 DeepSeek 页面: " + e.getMessage(), e);
        }
    }

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

    public void closeSession(String sessionId) {
        Page page = sessionPages.remove(sessionId);
        if (page != null) {
            try { page.close(); } catch (Exception e) { log.warn("关闭 Playwright 页面异常", e); }
        }
    }

    public void closeAll() {
        sessionPages.values().forEach(p -> { try { p.close(); } catch (Exception e) { log.warn("关闭页面异常", e); } });
        sessionPages.clear();
        com.codestats.common.util.PlaywrightStealthUtil.close();
    }
}