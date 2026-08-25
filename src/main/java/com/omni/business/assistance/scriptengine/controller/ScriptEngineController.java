package com.omni.business.assistance.scriptengine.controller;

import com.omni.business.assistance.scriptengine.service.ScriptExecutionService;
import com.omni.framework.spring.annotation.*;
import com.omni.framework.spring.mvc.FileItem;
import com.omni.framework.spring.mvc.MultipartResolver;
import com.omni.framework.spring.mvc.RequestContextHolder;
import com.omni.framework.tomcat.servlet.HttpServletRequest;
import com.omni.framework.util.JsonUtil;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/script/api")
public class ScriptEngineController {

    @Autowired
    private ScriptExecutionService executionService;

    /**
     * 执行脚本（直接传递参数）
     */
    @RequestMapping("/execute")
    @ResponseBody
    public String execute(@RequestBody Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            String output = executionService.execute(params);
            result.put("success", true);
            result.put("output", output);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return JsonUtil.toJson(result);
    }



    /**
     * 上传脚本文件并执行
     */
    @RequestMapping("/upload")
    @ResponseBody
    public String uploadAndExecute() throws IOException {
        HttpServletRequest request = RequestContextHolder.getRequest();
        MultipartResolver resolver = request.getMultipartResolver();
        Map<String, Object> result = new HashMap<>();

        if (resolver == null) {
            result.put("success", false);
            result.put("error", "请求不是 multipart 类型");
            return JsonUtil.toJson(result);
        }

        FileItem fileItem = resolver.getFileItem("scriptFile");
        if (fileItem == null) {
            result.put("success", false);
            result.put("error", "缺少文件");
            return JsonUtil.toJson(result);
        }

        String language = resolver.getParameter("language");
        String executionMode = resolver.getParameter("executionMode");
        String paramsJson = resolver.getParameter("params");

        try {
            String scriptContent = new String(fileItem.getBytes(), StandardCharsets.UTF_8);
            Map<String, Object> params = new HashMap<>();
            params.put("scriptContent", scriptContent);
            params.put("language", language != null ? language : "python");
            params.put("executionMode", executionMode != null ? executionMode : "source");
            if (paramsJson != null && !paramsJson.isEmpty()) {
                params.putAll(JsonUtil.parseJson(paramsJson));
            }
            String output = executionService.execute(params);
            result.put("success", true);
            result.put("output", output);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return JsonUtil.toJson(result);
    }
}