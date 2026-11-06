package com.omni.business.assistance.agent.controller;

import com.omni.business.common.util.BaseController;
import com.omni.common.util.ThreadPoolUtils;
import com.omni.framework.jstats.infrastructure.CommandExecutor;
import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.util.HashMap;
import java.util.Map;

/** ★ L1-04：parseJson → JsonUtil；★ L1-07：runProcessWithTimeout → CommandExecutor */
@Controller
@RequestMapping("/agent/api")
public class AgentController extends BaseController {

    @RequestMapping("/ollama/status")
    @ResponseBody
    public String checkOllamaStatus() {
        Map<String, Object> result = new HashMap<>();
        try {
            String output = new CommandExecutor(3).execute("ollama", "--version");
            result.put("status", "ok");
            result.put("version", output.trim());
            result.put("url", "http://localhost:11434");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return JsonUtil.toJson(result);
    }

    @RequestMapping("/ollama/pull")
    @ResponseBody
    public String pullModel(String body) {
        Map<String, Object> req = JsonUtil.parseJson(body);
        String model = (String) req.get("model");
        if (model == null || model.trim().isEmpty()) return error("模型名称不能为空");
        ThreadPoolUtils.daemonThreadFactory("ollama-pull").newThread(() -> {
            try {
                String output = new CommandExecutor(300).execute("ollama", "pull", model);
                System.out.println("Pull completed for " + model + ": " + output);
            } catch (Exception e) {
                System.err.println("Pull failed: " + e.getMessage());
            }
        }).start();
        return okOne("message", "模型拉取任务已启动，请稍后通过状态接口查询");
    }

    @RequestMapping("/ollama/run")
    @ResponseBody
    public String runModel(String body) {
        Map<String, Object> req = JsonUtil.parseJson(body);
        String model = (String) req.get("model");
        if (model == null || model.trim().isEmpty()) return error("模型名称不能为空");
        try {
            String listOutput = new CommandExecutor(10).execute("ollama", "list");
            if (!listOutput.contains(model)) return error("模型尚未安装，请先使用「安装模型」功能");
            return okOne("message", "模型已就绪，可通过 Ollama API 调用");
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }
}