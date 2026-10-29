package com.omni.business.assistance.agent.service;

import com.omni.business.assistance.agent.service.backend.ChatBackend;
import com.omni.business.assistance.agent.service.backend.ChatBackendRegistry;
import com.omni.framework.log.LoggerFactory;
import com.omni.framework.log.core.Logger;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Service;
import com.omni.framework.spring.config.ConfigLoader;

import java.util.List;
import java.util.Map;

@Service
public class AIChatService {
    private static final Logger log = LoggerFactory.getLogger(AIChatService.class);

    public static final String BACKEND_OLLAMA   = "ollama";
    public static final String BACKEND_DEEPSEEK = "deepseek";
    public static final String BACKEND_CUSTOM   = "custom";
    public static final String BACKEND_PROJECT  = "project";

    private final String defaultBackend;

    @Autowired
    private ChatBackendRegistry backendRegistry;

    @Autowired
    private ProjectAssistantService projectAssistant;

    public AIChatService() {
        this.defaultBackend = ConfigLoader.getProperty("ai.backend.default", BACKEND_OLLAMA);
        log.info("AIChatService 初始化完成，默认后端: {}", defaultBackend);
    }

    /**
     * 统一聊天入口。
     * 不再使用 switch-case 硬编码后端类型，而是通过 {@link ChatBackendRegistry} 策略分发。
     * 新增后端只需新增一个 {@link ChatBackend} 实现类。
     */
    public String chat(String sessionId, String question, List<Map<String, String>> messages,
                       String backend, String model, double temperature,
                       String customUrlOverride, String customKeyOverride) {
        String useBackend = (backend != null && !backend.isEmpty()) ? backend : defaultBackend;

        // 项目助手分支（有状态，走专门服务）
        if (BACKEND_PROJECT.equals(useBackend)) {
            String userInput = (messages != null && !messages.isEmpty())
                    ? toJsonLastMessage(messages) : question;
            return projectAssistant.chat(sessionId, userInput);
        }

        // 其他后端统一策略分发
        ChatBackend backendImpl = backendRegistry.get(useBackend);
        if (backendImpl == null) {
            return "不支持的后端类型: " + useBackend;
        }

        // 校验用户问题非空
        String userQuestion = extractLastUserMessage(messages, question);
        if (userQuestion == null || userQuestion.trim().isEmpty()) {
            return "请提供有效的问题内容。";
        }

        try {
            return backendImpl.chat(model, messages, temperature,
                    customUrlOverride, customKeyOverride);
        } catch (Exception e) {
            log.error("调用 ChatBackend[{}] 失败", useBackend, e);
            return "调用 AI 服务失败: " + e.getMessage();
        }
    }

    public void closeSession(String sessionId) {
        projectAssistant.closeSession(sessionId);
    }

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

    private String toJsonLastMessage(List<Map<String, String>> messages) {
        // 简化处理：取最后一条消息的 JSON 表达，供项目助手解析
        try {
            Map<String, String> last = messages.get(messages.size() - 1);
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, String> e : last.entrySet()) {
                if (!first) sb.append(",");
                sb.append("\"").append(escape(e.getKey())).append("\":\"")
                        .append(escape(e.getValue())).append("\"");
                first = false;
            }
            sb.append("}");
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}