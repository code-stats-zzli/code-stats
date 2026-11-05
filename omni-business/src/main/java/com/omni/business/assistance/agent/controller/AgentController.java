package com.omni.business.assistance.agent.controller;

import com.omni.framework.spring.annotation.Controller;
import com.omni.framework.spring.annotation.RequestMapping;
import com.omni.framework.spring.annotation.ResponseBody;
import com.omni.framework.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/agent/api")
public class AgentController {

    /**
     * 检查 Ollama 是否可用
     * GET /agent/api/ollama/status
     */
    @RequestMapping("/ollama/status")
    @ResponseBody
    public String checkOllamaStatus() {
        Map<String, Object> result = new HashMap<>();
        try {
            String output = runProcessWithTimeout(3, "ollama", "--version");
            result.put("status", "ok");
            result.put("version", output.trim());
            result.put("url", "http://localhost:11434");
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return JsonUtil.toJson(result);
    }

    /**
     * 拉取模型 (ollama pull) - 异步执行，避免长时间阻塞
     * POST /agent/api/ollama/pull
     * Body: {"model": "deepseek-r1:7b"}
     */
    @RequestMapping("/ollama/pull")
    @ResponseBody
    public String pullModel(String body) {
        Map<String, Object> req = parseJson(body);
        String model = (String) req.get("model");
        Map<String, Object> result = new HashMap<>();
        if (model == null || model.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "模型名称不能为空");
            return JsonUtil.toJson(result);
        }
        // 改为后台线程执行，避免接口超时，并设置总超时 5 分钟
        new Thread(() -> {
            try {
                String output = runProcessWithTimeout(300, "ollama", "pull", model);
                // 注意：此处无法直接返回给客户端，实际生产应使用任务ID轮询
                System.out.println("Pull completed for " + model + ": " + output);
            } catch (Exception e) {
                System.err.println("Pull failed: " + e.getMessage());
            }
        }).start();
        result.put("success", true);
        result.put("message", "模型拉取任务已启动，请稍后通过状态接口查询");
        return JsonUtil.toJson(result);
    }

    /**
     * 运行模型（实际是确保模型可用）
     * POST /agent/api/ollama/run
     * Body: {"model": "deepseek-r1:7b"}
     */
    @RequestMapping("/ollama/run")
    @ResponseBody
    public String runModel(String body) {
        Map<String, Object> req = parseJson(body);
        String model = (String) req.get("model");
        Map<String, Object> result = new HashMap<>();
        if (model == null || model.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "模型名称不能为空");
            return JsonUtil.toJson(result);
        }
        try {
            // 检查模型是否存在
            String listOutput = runProcessWithTimeout(10, "ollama", "list");
            if (!listOutput.contains(model)) {
                result.put("success", false);
                result.put("message", "模型尚未安装，请先使用「安装模型」功能");
                return JsonUtil.toJson(result);
            }
            result.put("success", true);
            result.put("message", "模型已就绪，可通过 Ollama API 调用");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return JsonUtil.toJson(result);
    }

    /**
     * 执行外部命令，带超时控制，超时则强制销毁进程
     */
    private String runProcessWithTimeout(long timeoutSeconds, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("命令执行超时 (" + timeoutSeconds + "秒): " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            String error = readStream(process.getInputStream());
            throw new RuntimeException("命令执行失败，退出码: " + process.exitValue() + ", 输出: " + error);
        }
        return readStream(process.getInputStream());
    }

    // 工具方法：读取进程输出流
    private String readStream(InputStream inputStream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    // 简易 JSON 解析
    private Map<String, Object> parseJson(String json) {
        Map<String, Object> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;
        try {
            int idx = json.indexOf("\"model\"");
            if (idx > 0) {
                int start = json.indexOf("\"", idx + 8);
                int end = json.indexOf("\"", start + 1);
                if (start > 0 && end > start) {
                    String model = json.substring(start + 1, end);
                    map.put("model", model);
                }
            }
        } catch (Exception ignored) {}
        return map;
    }
}