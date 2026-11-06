package com.omni.business.assistance.agent.controller;

import com.omni.business.assistance.agent.service.AIChatService;
import com.omni.business.common.util.BaseController;
import com.omni.framework.spring.annotation.Autowired;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/** ★ L1-01 */
@Controller
@RequestMapping("/agent/api")
public class AIChatController extends BaseController {

    @Autowired
    private AIChatService chatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @RequestMapping("/chat")
    @ResponseBody
    public String chat() {
        HttpServletRequest request = RequestContextHolder.getRequest();
        if (request == null) return error("无法获取请求对象");
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

            List<Map<String, String>> messages = new ArrayList<>();
            if (root.has("messages") && root.get("messages").isArray()) {
                for (JsonNode msg : root.get("messages")) {
                    Map<String, String> m = new HashMap<>();
                    if (msg.has("role")) m.put("role", msg.get("role").asText());
                    if (msg.has("content")) m.put("content", msg.get("content").asText());
                    messages.add(m);
                }
            } else if (question != null && !question.trim().isEmpty()) {
                messages.add(Map.of("role", "user", "content", question));
            }
            if (messages.isEmpty()) return error("缺少消息内容");

            String reply = chatService.chat(sessionId, question, messages, backend, model, temperature, customUrl, customKey);
            return okOne("response", reply);
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    @RequestMapping("/close")
    @ResponseBody
    public String closeSession() {
        HttpServletRequest request = RequestContextHolder.getRequest();
        if (request == null) return error("无法获取请求对象");
        try {
            JsonNode root = objectMapper.readTree(request.getBody());
            String sessionId = root.has("sessionId") ? root.get("sessionId").asText() : null;
            if (sessionId != null) chatService.closeSession(sessionId);
            return ok();
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }
}