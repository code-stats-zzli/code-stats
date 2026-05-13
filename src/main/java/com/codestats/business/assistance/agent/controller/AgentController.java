package com.codestats.business.assistance.agent.controller;

import com.framework.spring.annotation.Controller;
import com.framework.spring.annotation.RequestMapping;
import com.framework.spring.annotation.ResponseBody;
import com.framework.util.JsonUtil;

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
            Process process = new ProcessBuilder("ollama", "--version")
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(3, TimeUnit.SECONDS);
            if (finished && process.exitValue() == 0) {
                String version = readStream(process.getInputStream());
                result.put("status", "ok");
                result.put("version", version.trim());
                result.put("url", "http://localhost:11434");
            } else {
                result.put("status", "error");
                result.put("message", "Ollama 未响应或未安装");
            }
        } catch (Exception e) {
            result.put("status", "error");
            result.put("message", e.getMessage());
        }
        return JsonUtil.toJson(result);
    }

    /**
     * 拉取模型 (ollama pull)
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
        try {
            // 异步执行拉取，实际生产应使用后台任务，这里简单同步（可能耗时）
            ProcessBuilder pb = new ProcessBuilder("ollama", "pull", model);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            // 为了不阻塞接口超时，可以简单等待或返回任务ID，这里简化等待最多5分钟
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (finished && process.exitValue() == 0) {
                result.put("success", true);
                result.put("message", "模型拉取成功");
            } else {
                String error = readStream(process.getErrorStream());
                result.put("success", false);
                result.put("message", "拉取失败: " + error);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return JsonUtil.toJson(result);
    }

    /**
     * 运行模型（实际是确保模型可用，Ollama 服务默认常驻）
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
        // 验证模型是否存在
        try {
            Process check = new ProcessBuilder("ollama", "list")
                    .redirectErrorStream(true)
                    .start();
            String listOutput = readStream(check.getInputStream());
            if (!listOutput.contains(model)) {
                result.put("success", false);
                result.put("message", "模型尚未安装，请先使用「安装模型」功能");
                return JsonUtil.toJson(result);
            }
            // Ollama 服务本身常驻，无需额外启动；此处仅返回模型可用信息
            result.put("success", true);
            result.put("message", "模型已就绪，可通过 Ollama API 调用");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return JsonUtil.toJson(result);
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

    // 简易 JSON 解析（因项目 JsonUtil 仅支持序列化，这里简单处理）
    private Map<String, Object> parseJson(String json) {
        // 实际可引入 fastjson，但为保持简单，手工提取 model 字段
        Map<String, Object> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) return map;
        try {
            // 寻找 "model":"xxx"
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