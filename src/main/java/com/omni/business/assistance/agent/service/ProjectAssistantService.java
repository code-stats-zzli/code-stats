package com.omni.business.assistance.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.business.common.model.CharNgramLM;
import com.omni.business.common.model.support.ProjectModelData;
import com.omni.common.cache.Cache;
import com.omni.common.cache.CacheFactory;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ProjectAssistantService {
    private static final Logger log = LoggerFactory.getLogger(ProjectAssistantService.class);

    private static class KnowledgeEntry {
        final List<String> keywords;
        final String answer;
        KnowledgeEntry(List<String> keywords, String answer) {
            this.keywords = keywords.stream().map(String::toLowerCase).collect(Collectors.toList());
            this.answer = answer;
        }
    }

    // 静态知识库缓存（整个服务生命周期只加载一次，但改为懒加载）
    private static volatile List<KnowledgeEntry> knowledgeBase;
    private static final Object lock = new Object();

    // ngram 模型懒加载（构造成本高）
    private volatile CharNgramLM ngramLM;

    private final Cache<String, String> answerCache;
    private final Map<String, List<Map<String, String>>> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean enableNgramFallback;

    public ProjectAssistantService() {
        // ★ 关键：构造函数只读轻量配置，不做任何 IO / 模型加载
        this.enableNgramFallback = "true".equalsIgnoreCase(
                ConfigLoader.getProperty("project.assistant.ngram.fallback", "true"));
        this.answerCache = CacheFactory.createDefaultCache();
        log.info("ProjectAssistantService 已创建（知识库将懒加载）");
    }

    // ============ 懒加载入口 ============
    private void ensureKnowledgeBase() {
        if (knowledgeBase != null) return;
        synchronized (lock) {
            if (knowledgeBase != null) return;
            knowledgeBase = loadKnowledgeBase();
            log.info("项目助手知识库加载完成，共 {} 条条目", knowledgeBase.size());
        }
    }

    private CharNgramLM getNgramLM() {
        if (ngramLM != null) return ngramLM;
        synchronized (this) {
            if (ngramLM != null) return ngramLM;
            ProjectModelData modelData = new ProjectModelData();
            ngramLM = new CharNgramLM(modelData.getTrigramProbs(), modelData.getVocab());
            log.info("ngram 模型初始化完成");
            return ngramLM;
        }
    }

    public String chat(String sessionId, String userInput) {
        ensureKnowledgeBase();   // ★ 首次 chat 才加载
        if (sessionId == null) sessionId = "default";
        List<Map<String, String>> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());

        String question = extractQuestion(userInput);
        if (question == null || question.trim().isEmpty()) {
            return "请提出具体问题。";
        }

        String cached = answerCache.get(question);
        if (cached != null) {
            saveHistory(history, question, cached);
            return cached;
        }

        String answer = matchFromKnowledgeBase(question);
        if (answer != null) {
            answerCache.put(question, answer);
            saveHistory(history, question, answer);
            return answer;
        }

        if (enableNgramFallback) {
            String generated = generateWithNgram(history, question);
            if (generated != null && !generated.isEmpty()) {
                answerCache.put(question, generated);
                saveHistory(history, question, generated);
                return generated;
            }
        }

        String fallback = "抱歉，我是项目助手，暂时无法回答这个问题。你可以尝试询问关于 Spring、Tomcat、日志、连接池、自研框架等关键词。";
        answerCache.put(question, fallback);
        saveHistory(history, question, fallback);
        return fallback;
    }

    public void closeSession(String sessionId) {
        if (sessionId != null) sessions.remove(sessionId);
    }

    // ============ 以下私有方法保持原实现不变 ============

    private String extractQuestion(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        if (trimmed.startsWith("[")) {
            try {
                JsonNode arr = objectMapper.readTree(trimmed);
                if (arr.isArray()) {
                    for (int i = arr.size() - 1; i >= 0; i--) {
                        JsonNode msg = arr.get(i);
                        if ("user".equals(msg.path("role").asText())) {
                            return msg.path("content").asText();
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("解析消息数组失败: {}", e.getMessage());
            }
            return null;
        }
        return trimmed;
    }

    private List<KnowledgeEntry> loadKnowledgeBase() {
        String pathConfig = ConfigLoader.getProperty("project.assistant.kb.path", "project_knowledge.json");
        String[] paths = pathConfig.split(",");
        List<KnowledgeEntry> allEntries = new ArrayList<>();

        for (String path : paths) {
            path = path.trim();
            if (path.isEmpty()) continue;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
                if (is != null) {
                    JsonNode root = objectMapper.readTree(is);
                    for (JsonNode node : root) {
                        List<String> keywords = new ArrayList<>();
                        node.get("keywords").forEach(kw -> keywords.add(kw.asText()));
                        String answer = node.get("answer").asText();
                        allEntries.add(new KnowledgeEntry(keywords, answer));
                    }
                    log.info("从 {} 加载了 {} 条知识条目", path, root.size());
                } else {
                    log.warn("知识库文件未找到: {}", path);
                }
            } catch (Exception e) {
                log.error("加载知识库失败: {}", path, e);
            }
        }

        if (allEntries.isEmpty()) {
            log.warn("未加载到任何知识库，使用内置默认知识库");
            return buildDefaultKnowledgeBase();
        }
        return allEntries;
    }

    private List<KnowledgeEntry> buildDefaultKnowledgeBase() {
        List<KnowledgeEntry> list = new ArrayList<>();
        list.add(new KnowledgeEntry(Arrays.asList("default"), "请确保 project_knowledge.json 存在于 classpath 中。"));
        return list;
    }

    private String matchFromKnowledgeBase(String question) {
        String q = question.toLowerCase().replaceAll("[？?！!。，,]", "");
        KnowledgeEntry best = null;
        double bestScore = 0.0;

        for (KnowledgeEntry entry : knowledgeBase) {
            double score = 0.0;
            for (String kw : entry.keywords) {
                if (q.contains(kw)) {
                    score += 1.0;
                } else {
                    String[] kwWords = kw.split("\\s+");
                    for (String w : kwWords) {
                        if (q.contains(w)) score += 0.5;
                    }
                }
            }
            score = score / (entry.keywords.size() + 0.5);
            if (score > bestScore && score > 0.2) {
                bestScore = score;
                best = entry;
            }
        }
        return best != null ? best.answer : null;
    }

    private String generateWithNgram(List<Map<String, String>> history, String question) {
        String prompt = buildPromptFromHistory(history, question);
        String generated = getNgramLM().generate(prompt, 80);   // ★ 懒加载 ngram
        String cleaned = cleanGenerated(generated);
        if (cleaned.isEmpty() || cleaned.length() < 5) return null;
        return cleaned;
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
}