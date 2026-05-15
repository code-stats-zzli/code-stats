package com.omni.business.assistance.agent.controller;

import com.omni.business.assistance.agent.service.AIChatService;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.tomcat.connector.Request;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

@Controller
@RequestMapping("/agent/api")
public class AIChatController {

    @Autowired
    private AIChatService chatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @RequestMapping("/chat")
    @ResponseBody
    public String chat() {
        HttpServletRequest request = RequestContextHolder.getRequest();
        if (request == null) {
            return errorResponse("无法获取请求对象");
        }
        String body = request.getBody();
        try {
            JsonNode root = objectMapper.readTree(body);
            String sessionId = root.has("sessionId") ? root.get("sessionId").asText() : null;
            String question = root.has("question") ? root.get("question").asText() : null;
            String backend = root.has("backend") ? root.get("backend").asText() : null;
            String model = root.has("model") ? root.get("model").asText() : null;
            double temperature = root.has("temperature") ? root.get("temperature").asDouble() : 0.7;
            String customUrl = root.has("customUrl") ? root.get("customUrl").asText() : null;
            String customKey = root.has("customKey") ? root.get("customKey").asText() : null;

            // 提取消息历史
            List<Map<String, String>> messages = new ArrayList<>();
            if (root.has("messages") && root.get("messages").isArray()) {
                for (JsonNode msg : root.get("messages")) {
                    Map<String, String> m = new HashMap<>();
                    if (msg.has("role")) m.put("role", msg.get("role").asText());
                    if (msg.has("content")) m.put("content", msg.get("content").asText());
                    messages.add(m);
                }
            } else if (question != null && !question.trim().isEmpty()) {
                // 简单模式：只有问题
                messages.add(Map.of("role", "user", "content", question));
            }

            if (messages.isEmpty()) {
                return errorResponse("缺少消息内容");
            }

            String reply = chatService.chat(sessionId, question, messages, backend, model, temperature, customUrl, customKey);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("response", reply);
            return JsonUtil.toJson(result);

        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    @RequestMapping("/close")
    @ResponseBody
    public String closeSession() {
        HttpServletRequest request = RequestContextHolder.getRequest();
        if (request == null) return errorResponse("无法获取请求对象");
        String body = request.getBody();
        try {
            JsonNode root = objectMapper.readTree(body);
            String sessionId = root.has("sessionId") ? root.get("sessionId").asText() : null;
            if (sessionId != null) chatService.closeSession(sessionId);
            return JsonUtil.toJson(Map.of("success", true));
        } catch (Exception e) {
            return errorResponse(e.getMessage());
        }
    }

    private String errorResponse(String msg) {
        return JsonUtil.toJson(Map.of("success", false, "error", msg));
    }
}