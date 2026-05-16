package com.omni.business.assistance.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.business.common.model.CharNgramLM;
import com.omni.business.common.model.support.ProjectModelData;
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
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AIChatService {
    private static final Logger log = LoggerFactory.getLogger(AIChatService.class);

    public static final String BACKEND_OLLAMA = "ollama";
    public static final String BACKEND_DEEPSEEK = "deepseek";
    public static final String BACKEND_CUSTOM = "custom";
    public static final String BACKEND_PROJECT = "project";   // 新增：项目助手

    private final String defaultBackend;
    private final boolean playwrightEnabled;
    private final String deepseekApiKey;
    private final String ollamaUrl;
    private final String customApiUrl;
    private final String customApiKey;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // 项目助手的相关组件
    private final CharNgramLM ngramLM;
    private final Map<String, String> projectKnowledge;
    private final Map<String, List<Map<String, String>>> projectSessions = new ConcurrentHashMap<>();

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

        // 初始化项目助手：加载预训练的 n-gram 模型和知识库
        ProjectModelData modelData = new ProjectModelData();
        this.ngramLM = new CharNgramLM(modelData.getTrigramProbs(), modelData.getVocab());
        this.projectKnowledge = buildProjectKnowledgeBase();

        log.info("AIChatService 初始化: backend={}, projectAssistant 已加载", defaultBackend);
    }

    private Map<String, String> buildProjectKnowledgeBase() {
        Map<String, String> kb = new HashMap<>();
        kb.put("什么是CodeStats", "CodeStats 是一个自研的 Java Web 平台，包含 IoC 容器、MVC、嵌入式 Tomcat、JDBC 连接池、MyBatis 风格 Mapper 和代码分析引擎。");
        kb.put("框架核心组件", "核心组件包括：DispatcherServlet、SimpleApplicationContext、Pipeline-Valve 责任链、WebappClassLoader、JdbcTemplate 等。");
        kb.put("如何启动", "通过 Bootstrap 的 main 方法启动，加载 Catalina 配置，创建 AnnotationConfigApplicationContext，然后启动 Connector 监听端口。");
        kb.put("支持数据库", "支持 MySQL，通过 DatabaseMetadataService 获取元数据，提供树形浏览和表结构展示。");
        kb.put("代码分析功能", "多线程统计代码行数，支持扩展名过滤、注释排除、复杂度估算，可生成 JSON/CSV 报告。");
        kb.put("agent 模块", "AI Agent 模块集成大模型推理（支持 Ollama、DeepSeek API、自定义 API 以及内置项目助手），提供聊天接口。");
        kb.put("目录管理", "提供基于 HTTP 的文件浏览、编辑、上传、下载，支持路径安全校验。");
        kb.put("自研框架", "完全自研的轻量级框架，实现 IoC 容器、MVC、连接池、MyBatis 风格的注解 SQL 执行。");
        return kb;
    }

    public String chat(String sessionId, String question, List<Map<String, String>> messages,
                       String backend, String model, double temperature,
                       String customUrlOverride, String customKeyOverride) {
        String useBackend = backend != null ? backend : defaultBackend;

        // 项目助手分支
        if (BACKEND_PROJECT.equals(useBackend)) {
            return chatWithProject(sessionId, JsonUtil.toJson(messages));
        }

        // 原有后端逻辑
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

    // ==================== 项目助手实现 ====================
    private String chatWithProject(String sessionId, String question) {
        if (sessionId == null) sessionId = "default";
        List<Map<String, String>> history = projectSessions.computeIfAbsent(sessionId, k -> new ArrayList<>());

        // 1. 尝试知识库匹配
        String answer = matchKnowledge(question);
        if (answer != null) {
            saveHistory(history, question, answer);
            return answer;
        }

        // 2. 使用 n-gram 语言模型生成回答
        String prompt = buildPromptFromHistory(history, question);
        String generated = ngramLM.generate(prompt, 80);
        String cleaned = cleanGenerated(generated);
        if (cleaned.isEmpty()) {
            cleaned = "抱歉，我是项目助手，暂时无法回答这个问题。你可以询问关于 CodeStats 架构、模块、功能等内容。";
        }
        saveHistory(history, question, cleaned);
        return cleaned;
    }

    private String matchKnowledge(String question) {
        String q = question.toLowerCase().replaceAll("[？?]", "");
        for (Map.Entry<String, String> entry : projectKnowledge.entrySet()) {
            if (q.contains(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String buildPromptFromHistory(List<Map<String, String>> history, String question) {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, history.size() - 3);
        for (int i = start; i < history.size(); i++) {
            sb.append("用户: ").append(history.get(i).get("user")).append("\n");
            sb.append("助手: ").append(history.get(i).get("assistant")).append("\n");
        }
        sb.append("用户: ").append(question).append("\n");
        sb.append("助手:");
        return sb.toString();
    }

    private String cleanGenerated(String text) {
        int end = text.indexOf('。');
        if (end == -1) end = text.indexOf('?');
        if (end != -1) text = text.substring(0, end + 1);
        if (text.contains("用户:")) {
            text = text.substring(text.lastIndexOf("助手:") + 3);
        }
        return text.trim();
    }

    private void saveHistory(List<Map<String, String>> history, String user, String assistant) {
        Map<String, String> turn = new HashMap<>();
        turn.put("user", user);
        turn.put("assistant", assistant);
        history.add(turn);
        if (history.size() > 10) history.remove(0);
    }

    public void closeSession(String sessionId) {
        projectSessions.remove(sessionId);
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